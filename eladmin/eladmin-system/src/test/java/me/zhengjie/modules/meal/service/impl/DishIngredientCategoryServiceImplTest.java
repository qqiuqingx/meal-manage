package me.zhengjie.modules.meal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.domain.DishIngredientCategory;
import me.zhengjie.modules.meal.domain.dto.DishIngredientCategoryCreateDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientCategoryUpdateDto;
import me.zhengjie.modules.meal.mapper.DishIngredientCategoryMapper;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DishIngredientCategoryServiceImplTest {

    private DishIngredientCategoryMapper categoryMapper;
    private DishIngredientMapper ingredientMapper;
    private DishIngredientCategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        categoryMapper = mock(DishIngredientCategoryMapper.class);
        ingredientMapper = mock(DishIngredientMapper.class);
        service = new DishIngredientCategoryServiceImpl(categoryMapper, ingredientMapper);
        when(categoryMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(categoryMapper.selectMaxSort(anyInt(), nullable(Integer.class))).thenReturn(20);
    }

    @Test
    void createCategory_appendsSortAndSetsEnabledWhenSortIsMissing() {
        doAnswer(invocation -> {
            ((DishIngredientCategory) invocation.getArgument(0)).setId(12);
            return 1;
        }).when(categoryMapper).insert(any(DishIngredientCategory.class));

        DishIngredientCategory created = service.createCategory(createRequest("  蔬菜  ", 1, null, null));

        assertEquals(12, created.getId());
        assertEquals("蔬菜", created.getName());
        assertEquals(30, created.getSort());
        assertEquals(true, created.getEnabled());
        assertNotNull(created.getCreateTime());
    }

    @Test
    void createCategory_preservesExplicitSortZero() {
        doAnswer(invocation -> {
            ((DishIngredientCategory) invocation.getArgument(0)).setId(13);
            return 1;
        }).when(categoryMapper).insert(any(DishIngredientCategory.class));

        DishIngredientCategory created = service.createCategory(createRequest("水果", 1, null, 0));

        assertEquals(0, created.getSort());
        verify(categoryMapper, never()).selectMaxSort(anyInt(), nullable(Integer.class));
    }

    @Test
    void createCategory_rejectsParentForLevelOne() {
        assertThrows(BadRequestException.class, () -> service.createCategory(createRequest("蔬菜", 1, 4, null)));
        verify(categoryMapper, never()).insert(any(DishIngredientCategory.class));
    }

    @Test
    void createCategory_requiresExistingLevelOneParentForLevelTwo() {
        when(categoryMapper.selectById(4)).thenReturn(null);
        assertThrows(BadRequestException.class, () -> service.createCategory(createRequest("叶菜", 2, 4, null)));

        DishIngredientCategory wrongLevel = category(4, 2, null, "叶菜");
        when(categoryMapper.selectById(5)).thenReturn(wrongLevel);
        assertThrows(BadRequestException.class, () -> service.createCategory(createRequest("根茎", 2, 5, null)));
        verify(categoryMapper, never()).insert(any(DishIngredientCategory.class));
    }

    @Test
    void createCategory_rejectsDuplicateRootNameUsingNullParentPredicate() {
        when(categoryMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        assertThrows(BadRequestException.class,
            () -> service.createCategory(createRequest("蔬菜", 1, null, null)));

        org.mockito.ArgumentCaptor<QueryWrapper> queryCaptor = org.mockito.ArgumentCaptor.forClass(QueryWrapper.class);
        verify(categoryMapper).selectCount(queryCaptor.capture());
        assertEquals(true, queryCaptor.getValue().getSqlSegment().contains("parent_id IS NULL"));
        verify(categoryMapper, never()).insert(any(DishIngredientCategory.class));
    }

    @Test
    void createCategory_rejectsBlankAndOverlongNames() {
        assertThrows(BadRequestException.class,
            () -> service.createCategory(createRequest("　 ", 1, null, null)));
        assertThrows(BadRequestException.class,
            () -> service.createCategory(createRequest(String.join("", Collections.nCopies(65, "a")), 1, null, null)));
        verify(categoryMapper, never()).insert(any(DishIngredientCategory.class));
    }

    @Test
    void createCategory_translatesSecondaryDuplicateKeyToBusinessError() {
        when(categoryMapper.selectById(4)).thenReturn(category(4, 1, null, "蔬菜"));
        doThrow(new DuplicateKeyException("duplicate"))
            .when(categoryMapper).insert(any(DishIngredientCategory.class));

        BadRequestException error = assertThrows(BadRequestException.class,
            () -> service.createCategory(createRequest("叶菜", 2, 4, null)));

        assertEquals("同级分类名称已存在", error.getMessage());
    }

    @Test
    void updateCategory_changesNameAndSortButKeepsHierarchy() {
        DishIngredientCategory existing = category(8, 2, 4, "叶菜");
        existing.setSort(20);
        when(categoryMapper.selectById(8)).thenReturn(existing);
        when(categoryMapper.updateById(any(DishIngredientCategory.class))).thenReturn(1);
        DishIngredientCategoryUpdateDto request = new DishIngredientCategoryUpdateDto();
        request.setName("绿叶菜");
        request.setSort(10);

        service.updateCategory(8, request);

        assertEquals("绿叶菜", existing.getName());
        assertEquals(10, existing.getSort());
        assertEquals(2, existing.getLevel());
        assertEquals(4, existing.getParentId());
        assertNotNull(existing.getUpdateTime());
        verify(categoryMapper).updateById(existing);
    }

    @Test
    void updateCategory_preservesSortWhenSortIsMissing() {
        DishIngredientCategory existing = category(8, 1, null, "蔬菜");
        existing.setSort(40);
        when(categoryMapper.selectById(8)).thenReturn(existing);
        when(categoryMapper.updateById(any(DishIngredientCategory.class))).thenReturn(1);
        DishIngredientCategoryUpdateDto request = new DishIngredientCategoryUpdateDto();
        request.setName("绿叶菜");

        service.updateCategory(8, request);

        assertEquals(40, existing.getSort());
    }

    @Test
    void updateCategory_rejectsDuplicateAndMissingCategories() {
        when(categoryMapper.selectById(8)).thenReturn(category(8, 1, null, "蔬菜"));
        when(categoryMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        DishIngredientCategoryUpdateDto duplicate = new DishIngredientCategoryUpdateDto();
        duplicate.setName("水果");
        assertThrows(BadRequestException.class, () -> service.updateCategory(8, duplicate));

        when(categoryMapper.selectById(9)).thenReturn(null);
        assertThrows(BadRequestException.class, () -> service.updateCategory(9, duplicate));
        verify(categoryMapper, never()).updateById(any(DishIngredientCategory.class));
    }

    private DishIngredientCategoryCreateDto createRequest(String name, Integer level, Integer parentId, Integer sort) {
        DishIngredientCategoryCreateDto request = new DishIngredientCategoryCreateDto();
        request.setName(name);
        request.setLevel(level);
        request.setParentId(parentId);
        request.setSort(sort);
        return request;
    }

    private DishIngredientCategory category(Integer id, Integer level, Integer parentId, String name) {
        DishIngredientCategory category = new DishIngredientCategory();
        category.setId(id);
        category.setLevel(level);
        category.setParentId(parentId);
        category.setName(name);
        return category;
    }
}
