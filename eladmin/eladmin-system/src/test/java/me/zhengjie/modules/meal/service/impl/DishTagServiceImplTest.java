package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.domain.DishTag;
import me.zhengjie.modules.meal.domain.DishTagRelation;
import me.zhengjie.modules.meal.domain.dto.DishTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishTagRelationDto;
import me.zhengjie.modules.meal.domain.dto.DishTagSaveDto;
import me.zhengjie.modules.meal.mapper.DishTagMapper;
import me.zhengjie.modules.meal.mapper.DishTagRelationMapper;
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

class DishTagServiceImplTest {
    private final org.springframework.context.ApplicationEventPublisher events =
            org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);

    private DishTagMapper tagMapper;
    private DishTagRelationMapper relationMapper;
    private DishTagServiceImpl service;

    @BeforeEach
    void setUp() {
        tagMapper = mock(DishTagMapper.class);
        relationMapper = mock(DishTagRelationMapper.class);
        service = new DishTagServiceImpl(tagMapper, relationMapper, events);
    }

    @Test
    void createTrimsNameAndReturnsGeneratedId() {
        doAnswer(invocation -> {
            ((DishTag) invocation.getArgument(0)).setId(12);
            return 1;
        }).when(tagMapper).insert(any(DishTag.class));
        DishTagSaveDto request = new DishTagSaveDto();
        request.setName("  清真  ");

        DishTag created = service.create(request);

        assertEquals(12, created.getId());
        assertEquals("清真", created.getName());
        org.mockito.Mockito.verify(events).publishEvent(org.mockito.ArgumentMatchers.any(me.zhengjie.modules.meal.domain.event.DietDictionaryChangedEvent.class));
    }

    @Test
    void createRejectsBlankAndOverlongNamesBeforeInsert() {
        DishTagSaveDto blank = new DishTagSaveDto();
        blank.setName("　 ");
        assertThrows(BadRequestException.class, () -> service.create(blank));

        DishTagSaveDto tooLong = new DishTagSaveDto();
        tooLong.setName(String.join("", Collections.nCopies(65, "a")));
        assertThrows(BadRequestException.class, () -> service.create(tooLong));
        verify(tagMapper, never()).insert(any(DishTag.class));
    }

    @Test
    void createConvertsDatabaseDuplicateToBusinessError() {
        doThrow(new DuplicateKeyException("duplicate"))
            .when(tagMapper).insert(any(DishTag.class));
        DishTagSaveDto request = new DishTagSaveDto();
        request.setName("清真");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertEquals("标签名称已存在", error.getMessage());
    }

    @Test
    void updateLocksTagAndConvertsDuplicateName() {
        when(tagMapper.selectByIdForUpdate(3)).thenReturn(tag(3));
        doThrow(new DuplicateKeyException("duplicate"))
            .when(tagMapper).updateById(any(DishTag.class));
        DishTagSaveDto request = new DishTagSaveDto();
        request.setId(3);
        request.setName("已存在");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.update(request));

        assertEquals("标签名称已存在", error.getMessage());
        verify(tagMapper).selectByIdForUpdate(3);
    }

    @Test
    void deleteRejectsReferencedTagAndAllowsUnusedTag() {
        when(tagMapper.selectByIdForUpdate(5)).thenReturn(tag(5));
        when(relationMapper.countRelationsByTagId(5)).thenReturn(1L);

        assertThrows(BadRequestException.class, () -> service.delete(5));
        verify(tagMapper, never()).deleteById(5);

        when(relationMapper.countRelationsByTagId(5)).thenReturn(0L);
        service.delete(5);
        verify(tagMapper).deleteById(5);
    }

    @Test
    void replaceDishTagsDeduplicatesAndLocksInAscendingOrder() {
        when(tagMapper.selectByIdForUpdate(2)).thenReturn(tag(2));
        when(tagMapper.selectByIdForUpdate(8)).thenReturn(tag(8));

        service.replaceDishTags(40, Arrays.asList(8, 2, 8));

        org.mockito.InOrder order = inOrder(tagMapper, relationMapper);
        order.verify(tagMapper).selectByIdForUpdate(2);
        order.verify(tagMapper).selectByIdForUpdate(8);
        order.verify(relationMapper).deleteByDishIds(Collections.singletonList(40));
        org.mockito.ArgumentCaptor<DishTagRelation> captor =
            org.mockito.ArgumentCaptor.forClass(DishTagRelation.class);
        verify(relationMapper, org.mockito.Mockito.times(2)).insertRelation(captor.capture());
        List<DishTagRelation> saved = captor.getAllValues();
        assertEquals(Arrays.asList(2, 8), Arrays.asList(saved.get(0).getTagId(), saved.get(1).getTagId()));
    }

    @Test
    void replaceDishTagsRejectsMissingOrInvalidIdsBeforeReplacingRelations() {
        when(tagMapper.selectByIdForUpdate(7)).thenReturn(null);

        assertThrows(BadRequestException.class,
            () -> service.replaceDishTags(40, Collections.singletonList(7)));
        assertThrows(BadRequestException.class,
            () -> service.replaceDishTags(40, Arrays.asList(2, null)));
        assertThrows(BadRequestException.class,
            () -> service.replaceDishTags(40, Collections.singletonList(0)));

        verify(relationMapper, never()).deleteByDishIds(any());
    }

    @Test
    void replaceDishTagsWithEmptyListClearsOnlyThatDish() {
        service.replaceDishTags(40, Collections.emptyList());

        verify(relationMapper).deleteByDishIds(Collections.singletonList(40));
        verify(relationMapper, never()).insertRelation(any(DishTagRelation.class));
    }

    @Test
    void findTagsByDishIdsGroupsRowsInOneQueryAndSkipsEmptyInput() {
        DishTagRelationDto first = relation(10, 1, "清真");
        DishTagRelationDto second = relation(10, 4, "低盐");
        when(relationMapper.selectTagsByDishIds(Arrays.asList(10, 11)))
            .thenReturn(Arrays.asList(first, second));

        Map<Integer, ?> result = service.findTagsByDishIds(Arrays.asList(10, 11));

        assertEquals(2, ((List<?>) result.get(10)).size());
        assertTrue(!result.containsKey(11));
        verify(relationMapper).selectTagsByDishIds(Arrays.asList(10, 11));
        assertTrue(service.findTagsByDishIds(Collections.emptyList()).isEmpty());
        verify(relationMapper, never()).selectTagsByDishIds(Collections.emptyList());
    }

    @Test
    void queryUsesDefaultPageWhenValuesAreNull() {
        DishTagQueryCriteria criteria = new DishTagQueryCriteria();
        criteria.setPage(null);
        criteria.setSize(null);
        when(tagMapper.selectPageByCriteria(any(), any()))
            .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        service.query(criteria);

        assertEquals(0, criteria.getPage());
        assertEquals(20, criteria.getSize());
    }

    private DishTag tag(Integer id) {
        DishTag tag = new DishTag();
        tag.setId(id);
        tag.setName("标签" + id);
        return tag;
    }

    private DishTagRelationDto relation(Integer dishId, Integer tagId, String name) {
        DishTagRelationDto relation = new DishTagRelationDto();
        relation.setDishId(dishId);
        relation.setTagId(tagId);
        relation.setName(name);
        return relation;
    }
}
