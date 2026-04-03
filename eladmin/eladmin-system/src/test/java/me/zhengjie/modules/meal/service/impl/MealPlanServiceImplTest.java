package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishIngredientRelation;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerItemMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealPlanServiceImplTest {

    @Mock
    private MealPlanMapper mealPlanMapper;
    @Mock
    private MealPlanCustomerMapper mealPlanCustomerMapper;
    @Mock
    private MealPlanCustomerItemMapper mealPlanCustomerItemMapper;
    @Mock
    private CustomerOrderMapper customerOrderMapper;
    @Mock
    private CustomerProfileMapper customerProfileMapper;
    @Mock
    private ParentPackageMapper parentPackageMapper;
    @Mock
    private SubPackageMapper subPackageMapper;
    @Mock
    private DishMapper dishMapper;
    @Mock
    private DishIngredientMapper dishIngredientMapper;

    @InjectMocks
    private MealPlanServiceImpl mealPlanService;

    @Test
    void shouldRejectInvalidMealType() {
        assertThrows(BadRequestException.class, () -> mealPlanService.generateMealPlan("2026-04-01", "BREAKFAST", null));
        verify(mealPlanMapper, never()).insert(any(MealPlan.class));
    }

    @Test
    void shouldGenerateMealPlanSuccessfully() {
        CustomerOrder order = buildOrder();
        CustomerProfile customer = buildCustomer();
        SubPackage subPackage = buildSubPackage(1, 1, true, false);
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        Dish vegDish = buildDish(12, "清炒菜心", "VEGETABLE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 2);
        Dish soupDish = buildDish(13, "玉米排骨汤", "SOUP", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 3);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");
        DishIngredientRelation vegIngredient = buildIngredient(12, "菜心");
        DishIngredientRelation soupIngredient = buildIngredient(13, "玉米");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(subPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(subPackage));
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(dishMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(mainDish, vegDish, soupDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(mainIngredient, vegIngredient, soupIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertTrue(result.getFailDetails().isEmpty());
        verify(dishMapper).findBySchedule(1, 3, "LUNCH");
        verify(dishMapper, never()).selectList(null);
        verify(customerOrderMapper, never()).selectList(null);
        verify(mealPlanMapper).insert(any(MealPlan.class));
        verify(mealPlanCustomerMapper).insert(any());
        verify(mealPlanCustomerItemMapper).insert(any());
    }

    @Test
    void shouldRecordFailureWhenRequiredDishMissing() {
        CustomerOrder order = buildOrder();
        CustomerProfile customer = buildCustomer();
        SubPackage subPackage = buildSubPackage(2, 0, false, false);
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(subPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(subPackage));
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(dishMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Collections.singletonList(mainDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Collections.singletonList(mainIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertEquals("荤菜不足，必须同时选出主菜和副菜", result.getFailDetails().get(0).getFailReason());
        verify(mealPlanCustomerMapper).insert(any());
        verify(mealPlanCustomerItemMapper, never()).insert(any());
    }

    @Test
    void shouldSoftDeleteExistingPlanBeforeInsert() {
        MealPlan existingPlan = new MealPlan();
        existingPlan.setId(99L);

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(existingPlan);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.emptyList());
        when(dishMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Collections.emptyList());
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Collections.emptyList());

        mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        verify(mealPlanMapper).softDeleteItemsByMealPlanId(99L);
        verify(mealPlanMapper).softDeleteCustomersByMealPlanId(99L);
        verify(mealPlanMapper).softDeletePlanById(99L);
        verify(mealPlanMapper).insert(any(MealPlan.class));
    }

    @Test
    void shouldLoadOnlyScheduledDishIngredients() {
        CustomerOrder order = buildOrder();
        CustomerProfile customer = buildCustomer();
        SubPackage subPackage = buildSubPackage(1, 0, false, false);
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        Dish sideDish = buildDish(12, "小炒肉", "SIDE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 2);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");
        DishIngredientRelation sideIngredient = buildIngredient(12, "猪肉");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(subPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(subPackage));
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(dishMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(mainDish, sideDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(mainIngredient, sideIngredient));

        mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        ArgumentCaptor<List> dishIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(dishIngredientMapper).findRelationsByDishIds(dishIdsCaptor.capture());
        assertEquals(Arrays.asList(11, 12), dishIdsCaptor.getValue());
    }
    @Test
    void shouldUseExactAllergyMatch() {
        CustomerOrder order = buildOrder();
        CustomerProfile customer = buildCustomer();
        customer.setAllergyTags(Collections.singletonList("虾"));
        SubPackage subPackage = buildSubPackage(1, 0, false, false);
        ParentPackage parentPackage = buildParentPackage();
        Dish blockedDish = buildDish(11, "白灼虾", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        Dish allowedDish = buildDish(12, "虾仁蒸蛋", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 2);
        DishIngredientRelation blockedIngredient = buildIngredient(11, "虾");
        DishIngredientRelation allowedIngredient = buildIngredient(12, "虾仁");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(subPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(subPackage));
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(dishMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(blockedDish, allowedDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(blockedIngredient, allowedIngredient));

        mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        ArgumentCaptor<me.zhengjie.modules.meal.domain.MealPlanCustomerItem> captor = ArgumentCaptor.forClass(me.zhengjie.modules.meal.domain.MealPlanCustomerItem.class);
        verify(mealPlanCustomerItemMapper).insert(captor.capture());
        assertEquals(12, captor.getValue().getDishId());
    }

    private CustomerOrder buildOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setCustomerId(1L);
        order.setParentPackageId(1L);
        order.setChildPackageId(2L);
        order.setStatus(1);
        order.setRemainingCount(5);
        order.setStartDate(LocalDate.of(2026, 3, 1));
        order.setEndDate(LocalDate.of(2026, 4, 30));
        order.setMealType("LUNCH");
        order.setScheduleMode("DAILY");
        return order;
    }

    private CustomerProfile buildCustomer() {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(1L);
        customer.setCustomerName("张三");
        customer.setPhone("13800138000");
        customer.setAllergyTags(Collections.emptyList());
        return customer;
    }

    private ParentPackage buildParentPackage() {
        ParentPackage parentPackage = new ParentPackage();
        parentPackage.setId(1L);
        parentPackage.setPackageCode("PKG001");
        parentPackage.setPackageName("月子餐");
        return parentPackage;
    }

    private SubPackage buildSubPackage(int meatCount, int vegCount, boolean includeSoup, boolean includeRice) {
        SubPackage subPackage = new SubPackage();
        subPackage.setId(2L);
        subPackage.setMeatCount(meatCount);
        subPackage.setVegCount(vegCount);
        subPackage.setIncludeSoup(includeSoup);
        subPackage.setIncludeRice(includeRice);
        return subPackage;
    }

    private Dish buildDish(int id, String name, String dishType, List<String> mealTypes, List<String> mealPackages, List<String> schedule, int sort) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setName(name);
        dish.setDishType(dishType);
        dish.setMealTypes(mealTypes);
        dish.setMealPackages(mealPackages);
        dish.setSchedule(schedule);
        dish.setEnabled(true);
        dish.setSort(sort);
        return dish;
    }

    private DishIngredientRelation buildIngredient(int dishId, String ingredientName) {
        DishIngredientRelation relation = new DishIngredientRelation();
        relation.setDishId(dishId);
        relation.setIngredientName(ingredientName);
        return relation;
    }
}
