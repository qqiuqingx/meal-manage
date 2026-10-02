package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.domain.DishIngredientTag;
import me.zhengjie.modules.meal.domain.DishIngredientTagRelation;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagRelationDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagSaveDto;
import me.zhengjie.modules.meal.mapper.DishIngredientTagMapper;
import me.zhengjie.modules.meal.mapper.DishIngredientTagRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DishIngredientTagServiceImplTest {
    private final org.springframework.context.ApplicationEventPublisher events =
            org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);

    private DishIngredientTagMapper tagMapper;
    private DishIngredientTagRelationMapper relationMapper;
    private DishIngredientTagServiceImpl service;

    @BeforeEach
    void setUp() {
        tagMapper = mock(DishIngredientTagMapper.class);
        relationMapper = mock(DishIngredientTagRelationMapper.class);
        service = new DishIngredientTagServiceImpl(tagMapper, relationMapper, events);
    }

    @Test
    void create_trimsNameAndReturnsGeneratedId() {
        doAnswer(invocation -> {
            ((DishIngredientTag) invocation.getArgument(0)).setId(12);
            return 1;
        }).when(tagMapper).insert(any(DishIngredientTag.class));

        DishIngredientTagSaveDto request = new DishIngredientTagSaveDto();
        request.setName("  清真  ");

        DishIngredientTag created = service.create(request);

        assertEquals(12, created.getId());
        assertEquals("清真", created.getName());
        org.mockito.Mockito.verify(events).publishEvent(org.mockito.ArgumentMatchers.any(me.zhengjie.modules.meal.domain.event.DietDictionaryChangedEvent.class));
    }

    @Test
    void create_rejectsBlankAndOverlongNamesBeforeInsert() {
        DishIngredientTagSaveDto blank = new DishIngredientTagSaveDto();
        blank.setName("　 ");
        assertThrows(BadRequestException.class, () -> service.create(blank));

        DishIngredientTagSaveDto tooLong = new DishIngredientTagSaveDto();
        tooLong.setName(String.join("", Collections.nCopies(65, "a")));
        assertThrows(BadRequestException.class, () -> service.create(tooLong));
        verify(tagMapper, never()).insert(any(DishIngredientTag.class));
    }

    @Test
    void create_reportsDatabaseDuplicateAsBusinessError() {
        doThrow(new DuplicateKeyException("duplicate"))
            .when(tagMapper).insert(any(DishIngredientTag.class));
        DishIngredientTagSaveDto request = new DishIngredientTagSaveDto();
        request.setName("seafood");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertEquals("标签名称已存在", error.getMessage());
    }

    @Test
    void updateLocksTagAndReportsDuplicateName() {
        when(tagMapper.selectByIdForUpdate(3)).thenReturn(tag(3, "原名"));
        doThrow(new DuplicateKeyException("duplicate"))
            .when(tagMapper).updateById(any(DishIngredientTag.class));
        DishIngredientTagSaveDto request = new DishIngredientTagSaveDto();
        request.setId(3);
        request.setName("已有名称");

        assertThrows(BadRequestException.class, () -> service.update(request));

        verify(tagMapper).selectByIdForUpdate(3);
        verify(tagMapper).updateById(any(DishIngredientTag.class));
    }

    @Test
    void deleteRejectsTagThatIsStillReferenced() {
        when(tagMapper.selectByIdForUpdate(5)).thenReturn(tag(5, "午餐"));
        when(tagMapper.countRelationsByTagId(5)).thenReturn(1L);

        assertThrows(BadRequestException.class, () -> service.delete(5));

        verify(tagMapper, never()).deleteById(5);
    }

    @Test
    void deleteAllowsUnusedTag() {
        when(tagMapper.selectByIdForUpdate(5)).thenReturn(tag(5, "午餐"));
        when(tagMapper.countRelationsByTagId(5)).thenReturn(0L);

        service.delete(5);

        verify(tagMapper).deleteById(5);
    }

    @Test
    void replaceIngredientTagsDeduplicatesAndLocksInAscendingOrder() {
        when(tagMapper.selectByIdForUpdate(2)).thenReturn(tag(2, "甲"));
        when(tagMapper.selectByIdForUpdate(8)).thenReturn(tag(8, "乙"));

        service.replaceIngredientTags(40, Arrays.asList(8, 2, 8));

        org.mockito.InOrder order = inOrder(tagMapper, relationMapper);
        order.verify(tagMapper).selectByIdForUpdate(2);
        order.verify(tagMapper).selectByIdForUpdate(8);
        order.verify(relationMapper).deleteByIngredientId(40);
        org.mockito.ArgumentCaptor<DishIngredientTagRelation> captor =
            org.mockito.ArgumentCaptor.forClass(DishIngredientTagRelation.class);
        org.mockito.Mockito.verify(relationMapper, org.mockito.Mockito.times(2)).insertRelation(captor.capture());
        List<DishIngredientTagRelation> saved = captor.getAllValues();
        assertEquals(Arrays.asList(2, 8), Arrays.asList(saved.get(0).getTagId(), saved.get(1).getTagId()));
    }

    @Test
    void replaceIngredientTagsRejectsMissingTagBeforeReplacingRelations() {
        when(tagMapper.selectByIdForUpdate(7)).thenReturn(null);

        assertThrows(BadRequestException.class, () -> service.replaceIngredientTags(40, Collections.singletonList(7)));

        verify(relationMapper, never()).deleteByIngredientId(40);
    }

    @Test
    void replaceIngredientTagsWithEmptyListClearsOnlyThatIngredient() {
        service.replaceIngredientTags(40, Collections.emptyList());

        verify(relationMapper).deleteByIngredientId(40);
        verify(relationMapper, never()).insertRelation(any(DishIngredientTagRelation.class));
    }

    @Test
    void findTagsByIngredientIdsGroupsResultsInOneQuery() {
        DishIngredientTagRelationDto first = relation(10, 1, "清真");
        DishIngredientTagRelationDto second = relation(10, 4, "無麩質");
        when(relationMapper.selectTagsByIngredientIds(Arrays.asList(10, 11)))
            .thenReturn(Arrays.asList(first, second));

        Map<Integer, ?> result = service.findTagsByIngredientIds(Arrays.asList(10, 11));

        assertEquals(2, ((List<?>) result.get(10)).size());
        assertTrue(!result.containsKey(11));
        verify(relationMapper).selectTagsByIngredientIds(Arrays.asList(10, 11));
    }

    @Test
    void findTagsByIngredientIdsSkipsEmptyInQuery() {
        assertTrue(service.findTagsByIngredientIds(Collections.emptyList()).isEmpty());
        verify(relationMapper, never()).selectTagsByIngredientIds(any());
    }

    @Test
    void queryUsesDefaultPageWhenValuesAreNull() {
        DishIngredientTagQueryCriteria criteria = new DishIngredientTagQueryCriteria();
        criteria.setPage(null);
        criteria.setSize(null);
        when(tagMapper.selectPageByCriteria(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        service.query(criteria);

        assertEquals(0, criteria.getPage());
        assertEquals(20, criteria.getSize());
    }

    private DishIngredientTag tag(Integer id, String name) {
        DishIngredientTag tag = new DishIngredientTag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }

    private DishIngredientTagRelationDto relation(Integer ingredientId, Integer tagId, String name) {
        DishIngredientTagRelationDto relation = new DishIngredientTagRelationDto();
        relation.setIngredientId(ingredientId);
        relation.setTagId(tagId);
        relation.setName(name);
        return relation;
    }
}
