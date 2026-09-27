package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.DishIngredientCategory;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagDto;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import me.zhengjie.modules.meal.service.DishIngredientTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DishIngredientServiceImplTest {

    private DishIngredientMapper ingredientMapper;
    private DishIngredientCategoryService categoryService;
    private DishIngredientTagService tagService;
    private DishIngredientServiceImpl service;

    @BeforeEach
    void setUp() {
        ingredientMapper = mock(DishIngredientMapper.class);
        categoryService = mock(DishIngredientCategoryService.class);
        tagService = mock(DishIngredientTagService.class);
        service = new DishIngredientServiceImpl(ingredientMapper, categoryService, tagService);
    }

    @Test
    void updateWithNullTagIdsKeepsCurrentRelations() {
        when(ingredientMapper.selectByIdForUpdate(6)).thenReturn(ingredient(6));
        DishIngredient request = ingredient(6);
        request.setTagIds(null);

        service.update(request);

        verify(ingredientMapper).updateById(any(DishIngredient.class));
        verify(tagService, never()).replaceIngredientTags(any(), any());
    }

    @Test
    void updateWithEmptyTagIdsClearsCurrentRelations() {
        when(ingredientMapper.selectByIdForUpdate(6)).thenReturn(ingredient(6));
        DishIngredient request = ingredient(6);
        request.setTagIds(Collections.emptyList());

        service.update(request);

        verify(tagService).replaceIngredientTags(6, Collections.emptyList());
    }

    @Test
    void createPassesTagIdsToRelationServiceAfterInsert() {
        doAnswer(invocation -> {
            ((DishIngredient) invocation.getArgument(0)).setId(14);
            return 1;
        }).when(ingredientMapper).insert(any(DishIngredient.class));
        DishIngredient request = ingredient(null);
        request.setTagIds(Arrays.asList(2, 4));

        service.create(request);

        org.mockito.InOrder order = inOrder(ingredientMapper, tagService);
        order.verify(ingredientMapper).insert(request);
        order.verify(tagService).replaceIngredientTags(14, Arrays.asList(2, 4));
    }

    @Test
    void deleteLocksIngredientsInAscendingOrderAndScopesRelationCleanup() {
        List<Integer> ids = Arrays.asList(9, 2, 9, null);

        service.delete(ids);

        org.mockito.InOrder order = inOrder(ingredientMapper, tagService);
        order.verify(ingredientMapper).selectByIdForUpdate(2);
        order.verify(ingredientMapper).selectByIdForUpdate(9);
        order.verify(tagService).deleteIngredientRelations(Arrays.asList(2, 9));
        order.verify(ingredientMapper).deleteBatchIds(Arrays.asList(2, 9));
    }

    @Test
    void listQueryPassesTagFilterToMapper() {
        DishIngredientQueryCriteria criteria = new DishIngredientQueryCriteria();
        criteria.setTagId(17);
        when(ingredientMapper.selectPageByCriteria(criteria)).thenReturn(Collections.emptyList());

        service.queryAll(criteria);

        verify(ingredientMapper).selectPageByCriteria(criteria);
    }

    @Test
    void findByIdReturnsTagIdsAndNamesForEditPrefill() {
        DishIngredient ingredient = ingredient(6);
        DishIngredientTagDto tag = new DishIngredientTagDto();
        tag.setId(13);
        tag.setName("清真");
        Map<Integer, List<DishIngredientTagDto>> tagsByIngredient =
            Collections.singletonMap(6, Collections.singletonList(tag));
        when(ingredientMapper.findById(6)).thenReturn(Collections.singletonList(ingredient));
        when(tagService.findTagsByIngredientIds(Collections.singletonList(6))).thenReturn(tagsByIngredient);

        DishIngredient result = service.findById(6);

        assertEquals(Collections.singletonList(13), result.getTagIds());
        assertEquals("清真", result.getTags().get(0).getName());
    }

    @Test
    void createWithCategoryIdDoesNotInferLegacyEnumFromParentName() {
        DishIngredientCategory parent = new DishIngredientCategory();
        parent.setId(20);
        parent.setName("肉类");
        DishIngredientCategory child = new DishIngredientCategory();
        child.setId(21);
        child.setParentId(20);
        when(categoryService.getById(21)).thenReturn(child);
        when(categoryService.getById(20)).thenReturn(parent);
        DishIngredient request = ingredient(null);
        request.setCategoryId(21);

        service.create(request);

        assertEquals(21, request.getCategoryId());
        assertNull(request.getCategory());
        verify(ingredientMapper).insert(request);
    }

    private DishIngredient ingredient(Integer id) {
        DishIngredient ingredient = new DishIngredient();
        ingredient.setId(id);
        ingredient.setName("鸡肉");
        ingredient.setEnabled(true);
        return ingredient;
    }
}
