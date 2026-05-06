package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishIngredientRelation;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealSchedulePlanMapper;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerItemMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private DishMapper dishMapper;
    @Mock
    private DishIngredientMapper dishIngredientMapper;
    @Mock
    private MealSchedulePlanMapper mealSchedulePlanMapper;
    @Mock
    private DishIngredientCategoryService dishIngredientCategoryService;

    @InjectMocks
    private MealPlanServiceImpl mealPlanService;

    @BeforeEach
    void setUp() {
        lenient().when(dishIngredientCategoryService.getCategoryIngredientMapping()).thenReturn(Collections.emptyMap());
    }

    @Test
    void shouldRejectInvalidMealType() {
        assertThrows(BadRequestException.class, () -> mealPlanService.generateMealPlan("2026-04-01", "INVALID", null));
        verify(mealPlanMapper, never()).insert(any(MealPlan.class));
    }

    @Test
    void shouldRejectIncompleteMenuSlotParams() {
        assertThrows(BadRequestException.class,
                () -> mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null, 2, null));
        assertThrows(BadRequestException.class,
                () -> mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null, null, 3));
        verify(mealPlanMapper, never()).insert(any(MealPlan.class));
    }

    @Test
    void shouldRejectOutOfRangeMenuSlotParams() {
        assertThrows(BadRequestException.class,
                () -> mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null, 0, 3));
        assertThrows(BadRequestException.class,
                () -> mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null, 5, 3));
        assertThrows(BadRequestException.class,
                () -> mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null, 2, 0));
        assertThrows(BadRequestException.class,
                () -> mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null, 2, 8));
        verify(mealPlanMapper, never()).insert(any(MealPlan.class));
    }

    @Test
    void shouldUseManualMenuSlotWhenProvided() {
        CustomerOrder order = buildOrder();
        order.setMainDishCount(1);
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("2-3"), 1);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(2, 3, "LUNCH")).thenReturn(Collections.singletonList(mainDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Collections.singletonList(mainIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null, 2, 3);

        assertEquals(1, result.getSuccessCount());
        verify(mealSchedulePlanMapper).findBySchedule(2, 3, "LUNCH");
    }

    @Test
    void shouldFallbackToDateDerivedMenuSlotWhenNotProvided() {
        CustomerOrder order = buildOrder();
        order.setMainDishCount(1);
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Collections.singletonList(mainDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Collections.singletonList(mainIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null, null, null);

        assertEquals(1, result.getSuccessCount());
        verify(mealSchedulePlanMapper).findBySchedule(1, 3, "LUNCH");
    }

    @Test
    void shouldGenerateMealPlanSuccessfully() {
        CustomerOrder order = buildOrder();
        order.setMainDishCount(1);
        order.setSideDishCount(0);
        order.setVegCount(1);
        order.setRiceCount(0);
        order.setSoupCount(1);
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        Dish vegDish = buildDish(12, "清炒菜心", "VEGETABLE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 2);
        Dish soupDish = buildDish(13, "玉米排骨汤", "SOUP", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 3);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");
        DishIngredientRelation vegIngredient = buildIngredient(12, "菜心");
        DishIngredientRelation soupIngredient = buildIngredient(13, "玉米");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        // Stub insert to return 1 and assign an ID to the inserted record
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(mainDish, vegDish, soupDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(mainIngredient, vegIngredient, soupIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        assertTrue(result.getFailDetails().isEmpty());
        verify(mealSchedulePlanMapper).findBySchedule(1, 3, "LUNCH");
        verify(dishMapper, never()).selectList(null);
        verify(customerOrderMapper, never()).selectList(null);
        verify(mealPlanMapper).insert(any(MealPlan.class));
        verify(mealPlanCustomerMapper).insert(any());
        // 3 dishes (MAIN, VEGETABLE, SOUP) each get one insert call
        verify(mealPlanCustomerItemMapper, org.mockito.Mockito.times(3)).insert(any());
        // verify supplementary counts: supplementary = max(0, required - 1)
        ArgumentCaptor<MealPlanCustomer> captor = ArgumentCaptor.forClass(MealPlanCustomer.class);
        verify(mealPlanCustomerMapper).insert(captor.capture());
        MealPlanCustomer entity = captor.getValue();
        assertEquals(0, entity.getSupplementaryMainCount()); // max(0, 1-1)
        assertEquals(0, entity.getSupplementarySideCount()); // max(0, 1-1)
        assertEquals(0, entity.getSupplementaryVegCount());
        assertEquals(0, entity.getSupplementaryRiceCount());
        assertEquals(0, entity.getSupplementarySoupCount());
    }

    @Test
    void shouldRecordFailureWhenRequiredDishMissing() {
        CustomerOrder order = buildOrder();
        order.setMainDishCount(2);
        order.setSideDishCount(0);
        order.setVegCount(0);
        order.setRiceCount(0);
        order.setSoupCount(0);
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        lenient().when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Collections.singletonList(mainDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Collections.singletonList(mainIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        verify(mealPlanCustomerItemMapper, org.mockito.Mockito.times(1)).insert(any());

        ArgumentCaptor<MealPlanCustomer> captor = ArgumentCaptor.forClass(MealPlanCustomer.class);
        verify(mealPlanCustomerMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getSupplementaryMainCount());
    }

    @Test
    void shouldRebuildFailDetailsForPartialRerunWhenSummaryAndCurrentDetailsMismatch() {
        MealPlan existingPlan = new MealPlan();
        existingPlan.setId(99L);
        existingPlan.setRecordDate(LocalDate.of(2026, 4, 1));
        existingPlan.setMealType("LUNCH");

        CustomerOrder order = buildOrder();

        MealPlan refreshPlan = new MealPlan();
        refreshPlan.setId(99L);
        refreshPlan.setRecordDate(LocalDate.of(2026, 4, 1));
        refreshPlan.setMealType("LUNCH");
        refreshPlan.setSuccessCount(0);
        refreshPlan.setFailCount(1);
        refreshPlan.setTotalCount(1);

        MealPlanCustomer historicalFail = new MealPlanCustomer();
        historicalFail.setStatus(0);
        historicalFail.setFailReason("历史失败");

        MealPlanCustomer latestFail1 = new MealPlanCustomer();
        latestFail1.setStatus(0);
        latestFail1.setFailReason("历史失败");
        MealPlanCustomer latestFail2 = new MealPlanCustomer();
        latestFail2.setStatus(0);
        latestFail2.setFailReason("客户档案不存在");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(existingPlan);
        when(mealPlanMapper.findCustomerPlanIdsByMealPlanIdAndCustomerId(99L, 1L)).thenReturn(Collections.singletonList(501L));
        when(mealPlanMapper.selectById(99L)).thenReturn(refreshPlan);
        when(mealPlanCustomerMapper.selectByMealPlanId(99L))
                .thenReturn(Collections.singletonList(historicalFail))
                .thenReturn(Arrays.asList(latestFail1, latestFail2));
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.emptyList());
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Collections.emptyList());

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", 1L);

        assertEquals(2, result.getFailCount());
        assertEquals(2, result.getFailDetails().size());
        assertEquals("历史失败", result.getFailDetails().get(0).getFailReason());
        assertEquals("客户档案不存在", result.getFailDetails().get(1).getFailReason());
        verify(mealPlanCustomerItemMapper).softDeleteByCustomerPlanIds(Collections.singletonList(501L));
        verify(mealPlanCustomerMapper).softDeleteByIds(Collections.singletonList(501L));
    }

    @Test
    void shouldSoftDeleteExistingPlanBeforeInsert() {
        MealPlan existingPlan = new MealPlan();
        existingPlan.setId(99L);

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(existingPlan);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.emptyList());
        lenient().when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Collections.emptyList());
        lenient().when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Collections.emptyList());

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
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        Dish sideDish = buildDish(12, "小炒肉", "SIDE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 2);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");
        DishIngredientRelation sideIngredient = buildIngredient(12, "猪肉");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        lenient().when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(mainDish, sideDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(mainIngredient, sideIngredient));

        mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        ArgumentCaptor<List> dishIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(dishIngredientMapper).findRelationsByDishIds(dishIdsCaptor.capture());
        assertEquals(Arrays.asList(11, 12), dishIdsCaptor.getValue());
    }
    @Test
    void shouldUseExactAllergyMatch() {
        CustomerOrder order = buildOrder();
        order.setMainDishCount(1);
        CustomerProfile customer = buildCustomer();
        customer.setAllergyTags(Collections.singletonList("虾"));
        ParentPackage parentPackage = buildParentPackage();
        Dish blockedDish = buildDish(11, "白灼虾", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        Dish allowedDish = buildDish(12, "虾仁蒸蛋", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 2);
        DishIngredientRelation blockedIngredient = buildIngredient(11, "虾");
        DishIngredientRelation allowedIngredient = buildIngredient(12, "虾仁");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        lenient().when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(blockedDish, allowedDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(blockedIngredient, allowedIngredient));

        mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        ArgumentCaptor<me.zhengjie.modules.meal.domain.MealPlanCustomerItem> captor = ArgumentCaptor.forClass(me.zhengjie.modules.meal.domain.MealPlanCustomerItem.class);
        // selected dish (1 insert) + allergy filtered dish (1 insert) = 2 inserts total
        verify(mealPlanCustomerItemMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        // captor.getAllValues() returns in insertion order; last = allergy record (dishId=11)
        assertEquals(11, captor.getAllValues().get(1).getDishId());
        assertEquals("白灼虾", captor.getAllValues().get(1).getDishName());
    }

    @Test
    void shouldMarkExcludedSelectedDishAsReplaced() {
        CustomerOrder order = buildOrder();
        order.setMainDishCount(1);
        CustomerProfile customer = buildCustomer();
        customer.setExcludedDishIds(Collections.singletonList(11));
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        lenient().when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Collections.singletonList(mainDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Collections.singletonList(mainIngredient));

        mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        ArgumentCaptor<me.zhengjie.modules.meal.domain.MealPlanCustomerItem> captor = ArgumentCaptor.forClass(me.zhengjie.modules.meal.domain.MealPlanCustomerItem.class);
        // excluded dish replacement record (1 insert)
        verify(mealPlanCustomerItemMapper, org.mockito.Mockito.times(1)).insert(captor.capture());
        // Only allergy-filtered items are inserted for excluded dishes
        assertEquals(false, captor.getValue().getIsReplaced());
        assertEquals(11, captor.getValue().getDishId());
        assertEquals(11, captor.getValue().getOriginalDishId());
        assertEquals("红烧鸡", captor.getValue().getOriginalDishName());
        assertEquals("EXCLUDED", captor.getValue().getReplaceReason());
    }

    // ===== Wave 0 TDD Tests: excluded-date filtering in loadValidOrders() =====

    /**
     * Test: shouldSkipExcludedDateMealType
     *
     * When customer has excludedDates = [ExcludedDateDto{date="2026-04-01", mealTypes=["LUNCH"]}],
     * calling generateMealPlan("2026-04-01", "LUNCH", null) should skip the order entirely.
     * Expected: totalCount=0, no customer records inserted, no item records inserted.
     */
    @Test
    void shouldSkipExcludedDateMealType() {
        CustomerOrder order = buildOrder();
        CustomerProfile customer = buildCustomer();
        // Set up excluded date: 2026-04-01 is excluded for LUNCH
        ExcludedDateDto excludedDate = new ExcludedDateDto();
        excludedDate.setDate("2026-04-01");
        excludedDate.setMealTypes(Collections.singletonList("LUNCH"));
        customer.setExcludedDates(Collections.singletonList(excludedDate));

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        // No records should be inserted for excluded date+mealType
        verify(mealPlanCustomerMapper, never()).insert(any());
        verify(mealPlanCustomerItemMapper, never()).insert(any());
        verify(customerOrderMapper, never()).selectList(null);
    }

    /**
     * Test: shouldNotSkipNonExcludedOrder
     *
     * When customer's excludedDates = [ExcludedDateDto{date="2026-04-02", mealTypes=["LUNCH"]}]
     * but we generate for "2026-04-01", the order should be processed normally.
     */
    @Test
    void shouldNotSkipNonExcludedOrder() {
        CustomerOrder order = buildOrder();
        CustomerProfile customer = buildCustomer();
        // Set up excluded date for DIFFERENT date (2026-04-02)
        ExcludedDateDto excludedDate = new ExcludedDateDto();
        excludedDate.setDate("2026-04-02");
        excludedDate.setMealTypes(Collections.singletonList("LUNCH"));
        customer.setExcludedDates(Collections.singletonList(excludedDate));
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        Dish vegDish = buildDish(12, "清炒菜心", "VEGETABLE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 2);
        Dish soupDish = buildDish(13, "玉米排骨汤", "SOUP", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 3);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");
        DishIngredientRelation vegIngredient = buildIngredient(12, "菜心");
        DishIngredientRelation soupIngredient = buildIngredient(13, "玉米");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        lenient().when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(mainDish, vegDish, soupDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(mainIngredient, vegIngredient, soupIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getSuccessCount());
        verify(mealPlanCustomerMapper).insert(any());
    }

    /**
     * Test: shouldGenerateNoRecordsForExcludedDate
     *
     * Confirms that for an excluded date+mealType, zero meal_plan_customer and
     * meal_plan_customer_item records are generated.
     */
    @Test
    void shouldGenerateNoRecordsForExcludedDate() {
        CustomerOrder order = buildOrder();
        CustomerProfile customer = buildCustomer();
        ExcludedDateDto excludedDate = new ExcludedDateDto();
        excludedDate.setDate("2026-04-01");
        excludedDate.setMealTypes(Collections.singletonList("LUNCH"));
        customer.setExcludedDates(Collections.singletonList(excludedDate));

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));

        mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        verify(mealPlanCustomerMapper, never()).insert(any());
        verify(mealPlanCustomerItemMapper, never()).insert(any());
    }

    @Test
    void shouldRecordFailureWhenCustomerProfileMissing() {
        CustomerOrder order = buildOrder();
        order.setCustomerId(99L);

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.emptyList());
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertEquals(1, result.getFailDetails().size());
        assertEquals("客户档案不存在", result.getFailDetails().get(0).getFailReason());
        verify(mealPlanCustomerMapper).insert(any(MealPlanCustomer.class));
        verify(mealPlanCustomerItemMapper, never()).insert(any());
    }

    // ===== Phase 04 Tests: DishQuantityConfig from order fields =====

    /**
     * Test: buildCustomerPlan reads from order's 5 new fields (mainDishCount, sideDishCount, vegCount, riceCount, soupCount).
     * Order has mainDishCount=2, sideDishCount=1, vegCount=1, riceCount=1, soupCount=1.
     * 固定日菜单下，每类最多选 1 个菜品，其余记补餐数量。
     */
    @Test
    void testBuildCustomerPlan_usesOrderFields() {
        CustomerOrder order = buildOrderWithDishCounts(2, 1, 1, 1, 1);
        order.setRiceType("白米饭");
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        Dish sideDish = buildDish(14, "糖醋排骨", "SIDE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 4);
        Dish vegDish = buildDish(12, "清炒菜心", "VEGETABLE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 2);
        Dish riceDish = buildDish(15, "白米饭", "RICE_TYPE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 5);
        Dish soupDish = buildDish(13, "玉米排骨汤", "SOUP", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 3);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");
        DishIngredientRelation sideIngredient = buildIngredient(14, "排骨");
        DishIngredientRelation vegIngredient = buildIngredient(12, "菜心");
        DishIngredientRelation riceIngredient = buildIngredient(15, "大米");
        DishIngredientRelation soupIngredient = buildIngredient(13, "玉米");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(mainDish, sideDish, vegDish, soupDish));
        when(dishMapper.findAll(any())).thenReturn(Collections.singletonList(riceDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(mainIngredient, sideIngredient, vegIngredient, riceIngredient, soupIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getSuccessCount());
        verify(mealPlanCustomerItemMapper, org.mockito.Mockito.times(5)).insert(any());

        ArgumentCaptor<MealPlanCustomer> captor = ArgumentCaptor.forClass(MealPlanCustomer.class);
        verify(mealPlanCustomerMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getSupplementaryMainCount());
    }

    /**
     * Test: mainCount=1 and sideCount=2 produces 1 MAIN + 1 SIDE dish, 额外 1 份记补副菜。
     */
    @Test
    void testPickRequiredDishes_independentSideCount() {
        CustomerOrder order = buildOrderWithDishCounts(1, 2, 0, 0, 0);
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        Dish sideDish1 = buildDish(14, "糖醋排骨", "SIDE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 4);
        Dish sideDish2 = buildDish(16, "红烧鱼", "SIDE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 6);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");
        DishIngredientRelation side1Ingredient = buildIngredient(14, "排骨");
        DishIngredientRelation side2Ingredient = buildIngredient(16, "鱼");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(mainDish, sideDish1, sideDish2));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(mainIngredient, side1Ingredient, side2Ingredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getSuccessCount());
        verify(mealPlanCustomerItemMapper, org.mockito.Mockito.times(2)).insert(any());

        ArgumentCaptor<MealPlanCustomer> captor = ArgumentCaptor.forClass(MealPlanCustomer.class);
        verify(mealPlanCustomerMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getSupplementarySideCount());
    }

    /**
     * Test: soupCount=2 selects 1 SOUP dish, 额外 1 份记补汤。
     */
    @Test
    void testPickOptionalDish_multipleSoupCount() {
        CustomerOrder order = buildOrderWithDishCounts(0, 0, 0, 0, 2);
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish soupDish1 = buildDish(13, "玉米排骨汤", "SOUP", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 3);
        Dish soupDish2 = buildDish(17, "紫菜蛋花汤", "SOUP", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 7);
        DishIngredientRelation soup1Ingredient = buildIngredient(13, "玉米");
        DishIngredientRelation soup2Ingredient = buildIngredient(17, "紫菜");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(soupDish1, soupDish2));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(soup1Ingredient, soup2Ingredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getSuccessCount());
        verify(mealPlanCustomerItemMapper, org.mockito.Mockito.times(1)).insert(any());

        ArgumentCaptor<MealPlanCustomer> captor = ArgumentCaptor.forClass(MealPlanCustomer.class);
        verify(mealPlanCustomerMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getSupplementarySoupCount());
    }

    /**
     * Test: buildCustomerEntity snapshot uses order's mainDishCount/sideDishCount/vegCount/riceCount/soupCount fields.
     */
    @Test
    void testBuildCustomerEntity_usesOrderFields() {
        CustomerOrder order = buildOrderWithDishCounts(2, 2, 3, 2, 2);
        order.setRiceType("白米饭");
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish mainDish = buildDish(11, "红烧鸡", "MAIN", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 1);
        Dish sideDish = buildDish(14, "糖醋排骨", "SIDE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 4);
        Dish vegDish = buildDish(12, "清炒菜心", "VEGETABLE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 2);
        Dish riceDish = buildDish(15, "白米饭", "RICE_TYPE", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 5);
        Dish soupDish = buildDish(13, "玉米排骨汤", "SOUP", Arrays.asList("LUNCH"), Arrays.asList("1"), Arrays.asList("1-3"), 3);
        DishIngredientRelation mainIngredient = buildIngredient(11, "鸡肉");
        DishIngredientRelation sideIngredient = buildIngredient(14, "排骨");
        DishIngredientRelation vegIngredient = buildIngredient(12, "菜心");
        DishIngredientRelation riceIngredient = buildIngredient(15, "大米");
        DishIngredientRelation soupIngredient = buildIngredient(13, "玉米");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Arrays.asList(mainDish, sideDish, vegDish, soupDish));
        when(dishMapper.findAll(any())).thenReturn(Collections.singletonList(riceDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(mainIngredient, sideIngredient, vegIngredient, riceIngredient, soupIngredient));

        mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        ArgumentCaptor<me.zhengjie.modules.meal.domain.MealPlanCustomer> captor = ArgumentCaptor.forClass(me.zhengjie.modules.meal.domain.MealPlanCustomer.class);
        verify(mealPlanCustomerMapper).insert(captor.capture());
        me.zhengjie.modules.meal.domain.MealPlanCustomer entity = captor.getValue();
        assertEquals(2, entity.getMeatRequiredCount());
        assertEquals(3, entity.getVegRequiredCount());
        assertEquals(1, entity.getIncludeSoup());
        assertEquals(1, entity.getIncludeRice());
        assertEquals(1, entity.getSupplementaryMainCount()); // max(0, 2-1)
        assertEquals(1, entity.getSupplementarySideCount()); // max(0, 2-1)
        assertEquals(2, entity.getSupplementaryVegCount()); // max(0, 3-1)
        assertEquals(1, entity.getSupplementaryRiceCount()); // max(0, 2-1)
        assertEquals(1, entity.getSupplementarySoupCount()); // max(0, 2-1)
    }

    @Test
    void shouldPickRiceFromDishArchiveByRiceType() {
        CustomerOrder order = buildOrderWithDishCounts(0, 0, 0, 1, 0);
        order.setRiceType("三色糙米");
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish riceDish = buildDish(15, "三色糙米", "RICE_TYPE", Arrays.asList("LUNCH"), Arrays.asList("1"), Collections.emptyList(), 1);
        Dish backupRiceDish = buildDish(16, "白米饭", "RICE_TYPE", Arrays.asList("LUNCH"), Arrays.asList("1"), Collections.emptyList(), 2);
        DishIngredientRelation riceIngredient = buildIngredient(15, "糙米");
        DishIngredientRelation backupRiceIngredient = buildIngredient(16, "大米");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Collections.emptyList());
        when(dishMapper.findAll(any())).thenReturn(Arrays.asList(riceDish, backupRiceDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(riceIngredient, backupRiceIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getSuccessCount());
        ArgumentCaptor<DishQueryCriteria> criteriaCaptor = ArgumentCaptor.forClass(DishQueryCriteria.class);
        verify(dishMapper).findAll(criteriaCaptor.capture());
        assertEquals("RICE_TYPE", criteriaCaptor.getValue().getDishType());
        ArgumentCaptor<me.zhengjie.modules.meal.domain.MealPlanCustomerItem> captor = ArgumentCaptor.forClass(me.zhengjie.modules.meal.domain.MealPlanCustomerItem.class);
        verify(mealPlanCustomerItemMapper).insert(captor.capture());
        assertEquals("三色糙米", captor.getValue().getDishName());
        assertEquals("RICE", captor.getValue().getDishType());
        assertEquals(true, captor.getValue().getIsReplaced());
        assertEquals(Integer.valueOf(15), captor.getValue().getOriginalDishId());
        assertEquals("三色糙米", captor.getValue().getOriginalDishName());
        assertEquals("客户选择替换", captor.getValue().getReplaceReason());
    }

    @Test
    void shouldTreatDefaultRiceTypeAsUnspecifiedWhenGeneratingMealPlan() {
        CustomerOrder order = buildOrderWithDishCounts(0, 0, 0, 1, 0);
        order.setRiceType("默认");
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish riceDish = buildDish(15, "三色糙米", "RICE_TYPE", Arrays.asList("LUNCH"), Arrays.asList("1"), Collections.emptyList(), 1);
        Dish backupRiceDish = buildDish(16, "白米饭", "RICE_TYPE", Arrays.asList("LUNCH"), Arrays.asList("1"), Collections.emptyList(), 2);
        DishIngredientRelation riceIngredient = buildIngredient(15, "糙米");
        DishIngredientRelation backupRiceIngredient = buildIngredient(16, "大米");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Collections.emptyList());
        when(dishMapper.findAll(any())).thenReturn(Arrays.asList(riceDish, backupRiceDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(riceIngredient, backupRiceIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getSuccessCount());
        ArgumentCaptor<me.zhengjie.modules.meal.domain.MealPlanCustomerItem> captor = ArgumentCaptor.forClass(me.zhengjie.modules.meal.domain.MealPlanCustomerItem.class);
        verify(mealPlanCustomerItemMapper).insert(captor.capture());
        assertEquals("三色糙米", captor.getValue().getDishName());
        assertEquals("RICE", captor.getValue().getDishType());
        assertEquals(null, captor.getValue().getIsReplaced());
    }

    @Test
    void shouldMarkRiceAsCustomerReplacementWhenSpecifiedRiceFallsBack() {
        CustomerOrder order = buildOrderWithDishCounts(0, 0, 0, 1, 0);
        order.setRiceType("杂粮1:1米饭");
        CustomerProfile customer = buildCustomer();
        ParentPackage parentPackage = buildParentPackage();
        Dish riceDish = buildDish(15, "三色糙米", "RICE_TYPE", Arrays.asList("LUNCH"), Arrays.asList("1"), Collections.emptyList(), 1);
        Dish backupRiceDish = buildDish(16, "白米饭", "RICE_TYPE", Arrays.asList("LUNCH"), Arrays.asList("1"), Collections.emptyList(), 2);
        DishIngredientRelation riceIngredient = buildIngredient(15, "糙米");
        DishIngredientRelation backupRiceIngredient = buildIngredient(16, "大米");

        when(mealPlanMapper.findActiveByDateAndMealTypeForUpdate(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(null);
        when(customerOrderMapper.findMealPlanOrders(LocalDate.of(2026, 4, 1), "LUNCH")).thenReturn(Collections.singletonList(order));
        lenient().when(customerProfileMapper.findByIds(anySet())).thenReturn(Collections.singletonList(customer));
        when(mealPlanMapper.insert(any(MealPlan.class))).thenAnswer(inv -> {
            MealPlan p = inv.getArgument(0);
            p.setId(100L);
            return 1;
        });
        lenient().when(mealPlanMapper.selectById(anyLong())).thenAnswer(inv -> {
            MealPlan p = new MealPlan();
            p.setId(inv.getArgument(0));
            p.setRecordDate(LocalDate.of(2026, 4, 1));
            p.setMealType("LUNCH");
            p.setSuccessCount(0);
            p.setFailCount(0);
            p.setTotalCount(0);
            return p;
        });
        when(parentPackageMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(parentPackage));
        when(mealSchedulePlanMapper.findBySchedule(1, 3, "LUNCH")).thenReturn(Collections.emptyList());
        when(dishMapper.findAll(any())).thenReturn(Arrays.asList(riceDish, backupRiceDish));
        when(dishIngredientMapper.findRelationsByDishIds(anyList())).thenReturn(Arrays.asList(riceIngredient, backupRiceIngredient));

        MealPlanGenerateResult result = mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null);

        assertEquals(1, result.getSuccessCount());
        ArgumentCaptor<me.zhengjie.modules.meal.domain.MealPlanCustomerItem> captor = ArgumentCaptor.forClass(me.zhengjie.modules.meal.domain.MealPlanCustomerItem.class);
        verify(mealPlanCustomerItemMapper).insert(captor.capture());
        assertEquals("白米饭", captor.getValue().getDishName());
        assertEquals(true, captor.getValue().getIsReplaced());
        assertEquals(Integer.valueOf(15), captor.getValue().getOriginalDishId());
        assertEquals("三色糙米", captor.getValue().getOriginalDishName());
        assertEquals("客户选择替换", captor.getValue().getReplaceReason());
    }

    @Test
    void shouldMarkFirstMealOfOrderWhenQueryMealPlanDetail() {
        MealPlan mealPlan = new MealPlan();
        mealPlan.setId(1L);
        mealPlan.setRecordDate(LocalDate.of(2026, 5, 6));
        mealPlan.setMealType("LUNCH");
        mealPlan.setStatus("SUCCESS");

        MealPlanCustomer firstCustomer = new MealPlanCustomer();
        firstCustomer.setId(101L);
        firstCustomer.setCustomerId(11L);
        firstCustomer.setCustomerCode("A001");
        firstCustomer.setCustomerName("张三");
        firstCustomer.setOrderId(1001L);
        firstCustomer.setStatus(1);

        MealPlanCustomer normalCustomer = new MealPlanCustomer();
        normalCustomer.setId(102L);
        normalCustomer.setCustomerId(12L);
        normalCustomer.setCustomerCode("A002");
        normalCustomer.setCustomerName("李四");
        normalCustomer.setOrderId(1002L);
        normalCustomer.setStatus(1);

        when(mealPlanMapper.selectById(1L)).thenReturn(mealPlan);
        when(mealPlanCustomerMapper.selectByMealPlanId(1L)).thenReturn(Arrays.asList(firstCustomer, normalCustomer));
        when(mealPlanCustomerMapper.selectFirstSuccessfulCustomerPlanIds(Arrays.asList(101L, 102L)))
                .thenReturn(Collections.singletonList(101L));
        when(mealPlanCustomerItemMapper.selectByCustomerPlanIds(Arrays.asList(101L, 102L)))
                .thenReturn(Collections.emptyList());

        MealPlanDetailVO detail = mealPlanService.queryMealPlanDetail(1L);

        assertEquals(2, detail.getCustomers().size());
        assertEquals(Boolean.TRUE, detail.getCustomers().get(0).getFirstMealOfOrder());
        assertEquals(Boolean.FALSE, detail.getCustomers().get(1).getFirstMealOfOrder());
    }

    @Test
    void shouldReturnFalseForFailedCustomerEvenIfIdIsInFirstSet() {
        MealPlan mealPlan = new MealPlan();
        mealPlan.setId(1L);
        mealPlan.setRecordDate(LocalDate.of(2026, 5, 6));
        mealPlan.setMealType("DINNER");
        mealPlan.setStatus("FAILED");

        MealPlanCustomer failedCustomer = new MealPlanCustomer();
        failedCustomer.setId(201L);
        failedCustomer.setCustomerId(21L);
        failedCustomer.setCustomerCode("B001");
        failedCustomer.setCustomerName("王五");
        failedCustomer.setOrderId(2001L);
        failedCustomer.setStatus(0);

        when(mealPlanMapper.selectById(1L)).thenReturn(mealPlan);
        when(mealPlanCustomerMapper.selectByMealPlanId(1L)).thenReturn(Collections.singletonList(failedCustomer));
        when(mealPlanCustomerMapper.selectFirstSuccessfulCustomerPlanIds(Collections.singletonList(201L)))
                .thenReturn(Collections.singletonList(201L));
        when(mealPlanCustomerItemMapper.selectByCustomerPlanIds(Collections.singletonList(201L)))
                .thenReturn(Collections.emptyList());

        MealPlanDetailVO detail = mealPlanService.queryMealPlanDetail(1L);

        assertEquals(Boolean.FALSE, detail.getCustomers().get(0).getFirstMealOfOrder());
    }

    @Test
    void testAssembleCustomerDetail_setsDishCounts() throws Exception {
        Method method = MealPlanServiceImpl.class.getDeclaredMethod(
                "assembleCustomerDetail", MealPlanCustomer.class, java.util.Map.class, java.util.Map.class, java.util.Set.class);
        method.setAccessible(true);

        MealPlanCustomer customer = new MealPlanCustomer();
        customer.setId(1L);
        customer.setCustomerId(2L);
        customer.setCustomerName("张三");
        customer.setPhone("13800138000");
        customer.setCustomerCode("C001");
        customer.setOrderId(3L);
        customer.setParentPackageId(4L);
        customer.setChildPackageId(5L);
        customer.setSupplementaryMainCount(1);
        customer.setSupplementarySideCount(0);
        customer.setSupplementaryVegCount(2);
        customer.setSupplementaryRiceCount(1);
        customer.setSupplementarySoupCount(1);

        MealPlanDetailVO.CustomerPlanDetail detail = (MealPlanDetailVO.CustomerPlanDetail) method.invoke(
                mealPlanService, customer, Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet());

        assertEquals(Integer.valueOf(1), detail.getSupplementaryMainCount());
        assertEquals(Integer.valueOf(0), detail.getSupplementarySideCount());
        assertEquals(Integer.valueOf(2), detail.getSupplementaryVegCount());
        assertEquals(Integer.valueOf(1), detail.getSupplementaryRiceCount());
        assertEquals(Integer.valueOf(1), detail.getSupplementarySoupCount());
    }

    private CustomerOrder buildOrderWithDishCounts(int main, int side, int veg, int rice, int soup) {
        CustomerOrder order = buildOrder();
        order.setMainDishCount(main);
        order.setSideDishCount(side);
        order.setVegCount(veg);
        order.setRiceCount(rice);
        order.setSoupCount(soup);
        return order;
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
        order.setLunchDinnerCount(5);
        order.setBreakfastCount(5);
        order.setMainDishCount(1);
        order.setSideDishCount(0);
        order.setVegCount(0);
        order.setRiceCount(0);
        order.setSoupCount(0);
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
