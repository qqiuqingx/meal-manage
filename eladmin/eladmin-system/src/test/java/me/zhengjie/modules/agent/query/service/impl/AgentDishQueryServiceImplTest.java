package me.zhengjie.modules.agent.query.service.impl;

import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerOverviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerPackageDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidatePreviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentScheduledMenuResponseDto;
import me.zhengjie.modules.agent.query.service.AgentCustomerQueryService;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishIngredientRelation;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealSchedulePlanMapper;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Agent 候选菜预览复用排期、套餐、排除菜与三级过敏数据源。 */
@ExtendWith(MockitoExtension.class)
class AgentDishQueryServiceImplTest {

    @Mock private DishMapper dishMapper;
    @Mock private DishIngredientMapper dishIngredientMapper;
    @Mock private MealSchedulePlanMapper mealSchedulePlanMapper;
    @Mock private ParentPackageMapper parentPackageMapper;
    @Mock private AgentCustomerQueryService customerQueryService;
    @Mock private DishIngredientCategoryService dishIngredientCategoryService;
    @InjectMocks private AgentDishQueryServiceImpl service;

    @Test
    void shouldReturnOnlyScheduledDishesGroupedByLunchAndDinner() {
        Dish lunch = dish(1, "番茄炒蛋", List.of("10")); lunch.setScheduleMealTime("LUNCH");
        Dish dinner = dish(2, "清蒸鲈鱼", List.of("10")); dinner.setScheduleMealTime("DINNER");
        when(mealSchedulePlanMapper.findByScheduleMealTimes(any(), any(), eq(List.of("LUNCH", "DINNER"))))
            .thenReturn(List.of(lunch, dinner));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(List.of());

        AgentScheduledMenuResponseDto result = service.listScheduled("2026-07-13", List.of("LUNCH", "DINNER"));

        assertEquals("2026-07-13", result.getRecordDate());
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getGroups().size());
        assertEquals("LUNCH", result.getGroups().get(0).getMealTypeCode());
        assertEquals(List.of("番茄炒蛋"), result.getGroups().get(0).getItems().stream().map(item -> item.getDishName()).collect(java.util.stream.Collectors.toList()));
        assertEquals("DINNER", result.getGroups().get(1).getMealTypeCode());
        assertEquals(List.of("清蒸鲈鱼"), result.getGroups().get(1).getItems().stream().map(item -> item.getDishName()).collect(java.util.stream.Collectors.toList()));
        verify(dishMapper, never()).selectList(any());
    }

    @Test
    void shouldMarkCandidateDishesUsingPackageExclusionAndCategoryAllergyRules() {
        AgentCustomerOverviewDto customer = new AgentCustomerOverviewDto();
        customer.setPresent(true); customer.setCustomerId(1L); customer.setCustomerCode("B3303");
        customer.setExcludedDishIds(List.of(2)); customer.setAllergyTags(List.of("禽肉"));
        AgentCustomerPackageDto packageDto = new AgentCustomerPackageDto();
        packageDto.setParentPackageId(10L); packageDto.setActive(true); customer.setPackages(List.of(packageDto));
        when(customerQueryService.getOverview(1L, null)).thenReturn(customer);
        ParentPackage parentPackage = new ParentPackage(); parentPackage.setId(10L); parentPackage.setPackageCode("PKG10");
        when(parentPackageMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(parentPackage));
        Dish matched = dish(1, "鸡肉", List.of("10"));
        Dish excluded = dish(2, "素菜", List.of("99"));
        when(mealSchedulePlanMapper.findBySchedule(any(), any(), any())).thenReturn(List.of(matched, excluded));
        when(dishMapper.selectList(any())).thenReturn(List.of());
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(List.of(relation(1, "鸡肉")));
        when(dishIngredientCategoryService.getCategoryIngredientMapping()).thenReturn(Map.of("禽肉", Set.of("鸡肉")));

        AgentDishCandidatePreviewDto result = service.previewCandidates(1L, "2026-07-11", "LUNCH");

        assertTrue(result.isPresent());
        assertEquals(2, result.getTotalCandidateCount());
        assertEquals(0, result.getAvailableCandidateCount());
        assertTrue(result.getItems().get(0).getFilterReasons().contains("ALLERGY:禽肉"));
        assertTrue(result.getItems().get(1).getFilterReasons().contains("PACKAGE_NOT_MATCHED"));
        assertTrue(result.getItems().get(1).getFilterReasons().contains("CUSTOMER_EXCLUDED_DISH"));
    }

    /** 构造最小排期菜品。 */
    private Dish dish(int id, String name, List<String> packages) {
        Dish dish = new Dish(); dish.setId(id); dish.setName(name); dish.setDishType("MAIN"); dish.setMealPackages(packages); return dish;
    }

    /** 构造菜品与配料关联。 */
    private DishIngredientRelation relation(int dishId, String ingredientName) {
        DishIngredientRelation relation = new DishIngredientRelation(); relation.setDishId(dishId); relation.setIngredientName(ingredientName); return relation;
    }
}
