package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleCellDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsRowDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarOverrideDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarSaveResult;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerScheduledMealDto;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerMealStatsServiceImplTest {

    private static final Long ORDER_ID = 10L;
    private static final Long CUSTOMER_ID = 1L;
    private static final String MONTH = "2026-10";
    private static final LocalDate MONTH_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate MONTH_END = LocalDate.of(2026, 10, 31);
    private static final LocalDate EARLIEST_DATE = LocalDate.of(1000, 1, 1);

    @Mock
    private CustomerOrderMapper customerOrderMapper;
    @Mock
    private CustomerProfileMapper customerProfileMapper;
    @Mock
    private CustomerProfileAddressMapper addressMapper;
    @Mock
    private CustomerMealScheduleAdditionMapper additionMapper;
    @Mock
    private MealPlanCustomerMapper mealPlanCustomerMapper;
    @Mock
    private MealPlanService mealPlanService;

    @InjectMocks
    private CustomerMealStatsServiceImpl service;

    private CustomerOrder order;
    private CustomerProfile profile;
    private List<CustomerMealScheduleAddition> storedOverrides;

    @BeforeEach
    void setUp() {
        order = buildOrder();
        profile = buildProfile();
        storedOverrides = new ArrayList<>();

        when(customerOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(customerOrderMapper.selectInlineUpdateByIdForUpdate(ORDER_ID)).thenReturn(order);
        when(customerProfileMapper.selectByIdWithJson(CUSTOMER_ID)).thenReturn(profile);
        when(customerProfileMapper.selectByIdForInlineUpdate(CUSTOMER_ID)).thenReturn(profile);
        when(additionMapper.selectActiveByOrderIdAndDateRange(ORDER_ID, EARLIEST_DATE, MONTH_END))
                .thenAnswer(invocation -> new ArrayList<>(storedOverrides));
        when(mealPlanCustomerMapper.selectScheduledMealsByOrderIdAndDateRange(ORDER_ID, MONTH_START, MONTH_END))
                .thenReturn(Collections.emptyList());
        when(mealPlanCustomerMapper.countSuccessfulScheduledByOrderIdGroupedByMealType(ORDER_ID))
                .thenReturn(Collections.emptyList());
        when(customerOrderMapper.sumVerifiedCountByOrderIds(Collections.singletonList(ORDER_ID)))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void shouldReadOneOrderAndKeepCustomerAndOrderStopSourcesSeparate() {
        order.setImportedVerifiedCount(1);
        ExcludedDateDto excluded = new ExcludedDateDto();
        excluded.setDate("2026-10-01");
        excluded.setMealTypes(Collections.singletonList("LUNCH"));
        profile.setExcludedDates(Collections.singletonList(excluded));
        storedOverrides.add(overrideEntity(21L, "2026-10-01", "LUNCH", 0, null, "停餐"));

        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);

        assertEquals(ORDER_ID, calendar.getOrderId());
        assertEquals(CUSTOMER_ID, calendar.getCustomerId());
        assertTrue(calendar.getEditable());
        assertEquals(3, calendar.getAvailableLunchDinnerCount());
        assertNotNull(calendar.getRevision());
        CustomerMealScheduleCellDto lunch = calendar.getCells().stream()
                .filter(cell -> "2026-10-01".equals(cell.getDate()) && "LUNCH".equals(cell.getMealType()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(0, lunch.getQuantity());
        assertTrue(lunch.getManualOverride());
        assertTrue(lunch.getCustomerExcluded());
        assertTrue(lunch.getOrderExcluded());
        assertEquals(1, calendar.getOverrides().size());
        assertEquals(0, calendar.getOverrides().get(0).getQuantity());
        verify(additionMapper).selectActiveByOrderIdAndDateRange(ORDER_ID, EARLIEST_DATE, MONTH_END);
    }

    @Test
    void shouldShowImportedHistoryBeforeOrderStartWithoutConsumingFutureBudget() {
        order.setStartDate(LocalDate.of(2026, 10, 21));
        order.setEndDate(null);
        order.setMealType("LUNCH_DINNER");
        order.setStartMealType("LUNCH");
        order.setBreakfastCount(0);
        order.setLunchDinnerCount(7);
        order.setImportedVerifiedCount(3);
        order.setVerifiedCount(3);
        order.setRemainingCount(4);
        storedOverrides.add(overrideEntity(61L, "2026-10-19", "LUNCH", 1, null,
                CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK));
        storedOverrides.add(overrideEntity(62L, "2026-10-20", "DINNER", 2, 1,
                CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK));
        storedOverrides.add(overrideEntity(63L, "2026-10-21", "LUNCH", 2, null, "客户用餐计划表导入"));

        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);

        List<CustomerMealScheduleCellDto> history = calendar.getCells().stream()
                .filter(cell -> Boolean.TRUE.equals(cell.getImportedHistory()))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(2, history.size());
        assertEquals("2026-10-19", history.get(0).getDate());
        assertEquals("2026-10-20", history.get(1).getDate());
        assertEquals(2, history.get(1).getQuantity());
        assertEquals(1, history.get(1).getSoupQuantity());
        assertTrue(history.stream().allMatch(cell -> cell.getGeneratedCount() == 0 && cell.getVerifiedCount() == 0));
        assertEquals(4, calendar.getAvailableLunchDinnerCount());
        assertEquals(4, calendar.getCells().stream()
                .filter(cell -> !Boolean.TRUE.equals(cell.getImportedHistory()))
                .mapToInt(CustomerMealScheduleCellDto::getQuantity).sum());
        assertEquals(1, calendar.getOverrides().size());
        assertEquals("2026-10-21", calendar.getOverrides().get(0).getDate());

        order.setStatus(2);
        order.setImportedVerifiedCount(7);
        order.setVerifiedCount(7);
        order.setRemainingCount(0);
        CustomerOrderMealCalendarDto completed = service.getOrderCalendar(ORDER_ID, MONTH);
        assertFalse(completed.getEditable());
        assertEquals(2, completed.getCells().stream()
                .filter(cell -> Boolean.TRUE.equals(cell.getImportedHistory())).count());
    }

    @Test
    void shouldLoadConsumedQuantityBeforeNewPlanningWindow() {
        order.setBreakfastCount(0);
        order.setLunchDinnerCount(158);
        order.setImportedVerifiedCount(131);
        order.setVerifiedCount(133);
        order.setRemainingCount(25);
        order.setMealType("LUNCH_DINNER");
        order.setStartDate(LocalDate.of(2026, 6, 2));
        order.setImportDate(LocalDate.of(2026, 10, 20));
        order.setEndDate(null);
        when(customerOrderMapper.countAllocatedBeforeImport(Collections.singletonList(ORDER_ID)))
                .thenReturn(Collections.singletonList(poolCount("LUNCH", 2)));
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, "2026-11");
        assertEquals(3, calendar.getCells().stream().mapToInt(CustomerMealScheduleCellDto::getQuantity).sum());
        assertEquals(2, order.getQuantityAllocatedBeforeImport());
    }

    @Test
    void shouldArchiveEarlierManualStopsWithoutBreakingCurrentMonthSave() {
        order.setStartDate(LocalDate.of(2026, 6, 2));
        order.setImportDate(LocalDate.of(2026, 10, 20));
        order.setEndDate(null);
        order.setBreakfastCount(0);
        storedOverrides.add(overrideEntity(91L, "2026-10-15", "LUNCH", 0, null, "人工停餐"));
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        assertTrue(calendar.getOverrides().isEmpty());
        service.saveOrderCalendar(ORDER_ID, saveRequest(calendar.getRevision(), Collections.emptyList()));
        verify(additionMapper, never()).softDeleteMissingByOrderIdAndDateRange(any(), any(), any(), any());
    }

    @Test
    void shouldKeepRealProgressWhenEarlierFuturePlanBecomesImportedHistory() {
        order.setStartDate(LocalDate.of(2026, 6, 2));
        order.setImportDate(LocalDate.of(2026, 10, 20));
        order.setEndDate(null);
        storedOverrides.add(overrideEntity(81L, "2026-10-19", "LUNCH", 2, null,
                CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK));
        when(mealPlanCustomerMapper.selectScheduledMealsByOrderIdAndDateRange(ORDER_ID, MONTH_START, MONTH_END))
                .thenReturn(Collections.singletonList(progress("2026-10-19", "LUNCH", 2, 0, 1)));
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        CustomerMealScheduleCellDto cell = calendar.getCells().stream()
                .filter(value -> "2026-10-19".equals(value.getDate()) && "LUNCH".equals(value.getMealType()))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(cell.getImportedHistory());
        assertEquals(2, cell.getQuantity());
        assertEquals(2, cell.getGeneratedCount());
        assertEquals(1, cell.getVerifiedCount());
        assertTrue(calendar.getOverrides().isEmpty());
    }

    @Test
    void shouldPreserveImportedHistoryWhenClearingEditableMonthOverrides() {
        order.setStartDate(LocalDate.of(2026, 10, 21));
        order.setEndDate(null);
        order.setMealType("LUNCH_DINNER");
        order.setStartMealType("LUNCH");
        order.setBreakfastCount(0);
        order.setImportedVerifiedCount(1);
        storedOverrides.add(overrideEntity(61L, "2026-10-20", "LUNCH", 1, null,
                CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK));
        storedOverrides.add(overrideEntity(62L, "2026-10-21", "LUNCH", 0, null, "停餐"));

        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        service.saveOrderCalendar(ORDER_ID, saveRequest(calendar.getRevision(), Collections.emptyList()));

        verify(additionMapper).softDeleteMissingByOrderIdAndDateRange(
                ORDER_ID, MONTH_START, MONTH_END, Collections.singletonList(61L));
        verify(additionMapper, never()).updateOrderCalendarOverride(eq(61L), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectEditingOrForgingImportedHistory() {
        order.setStartDate(LocalDate.of(2026, 6, 2));
        order.setImportDate(LocalDate.of(2026, 10, 20));
        order.setEndDate(null);
        order.setMealType("LUNCH_DINNER");
        order.setStartMealType("LUNCH");
        order.setBreakfastCount(0);
        storedOverrides.add(overrideEntity(61L, "2026-10-20", "LUNCH", 1, null,
                CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK));
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        CustomerOrderMealCalendarOverrideDto changed = override("2026-10-20", "LUNCH", 0, null, "改历史");

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.saveOrderCalendar(ORDER_ID,
                        saveRequest(calendar.getRevision(), Collections.singletonList(changed))));

        assertTrue(error.getMessage().contains("历史导入数量只读"));
        CustomerOrderMealCalendarOverrideDto forged = override("2026-10-21", "LUNCH", 1, null,
                CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK);
        BadRequestException forgedError = assertThrows(BadRequestException.class,
                () -> service.saveOrderCalendar(ORDER_ID,
                        saveRequest(calendar.getRevision(), Collections.singletonList(forged))));
        assertTrue(forgedError.getMessage().contains("历史导入来源标识"));
        CustomerOrderMealCalendarOverrideDto beforeImport = override("2026-10-19", "LUNCH", 1, null, "不能补排历史缺格");
        BadRequestException beforeImportError = assertThrows(BadRequestException.class,
                () -> service.saveOrderCalendar(ORDER_ID,
                        saveRequest(calendar.getRevision(), Collections.singletonList(beforeImport))));
        assertTrue(beforeImportError.getMessage().contains("排餐日期不在当前订单有效期内"));
        verify(additionMapper, never()).softDeleteMissingByOrderIdAndDateRange(any(), any(), any(), any());
    }

    @Test
    void shouldReturnOneAccuratelyMappedRowPerPagedOrderAndUseCurrentPageBatchCounts() {
        CustomerOrder first = buildOrder();
        first.setOrderCode("ORD-10");
        first.setCustomerCode("A010");
        first.setBreakfastCount(5);
        first.setLunchDinnerCount(8);
        first.setVerifiedCount(3);
        first.setImportedVerifiedCount(1);
        first.setRemainingCount(6);
        first.setDealTime(LocalDateTime.of(2026, 9, 12, 13, 45, 0));
        first.setMainDishCount(2);
        first.setSideDishCount(1);
        first.setVegCount(1);
        first.setCustomMenuImage("/uploads/menu-a.png");

        CustomerOrder second = buildOrder();
        second.setId(11L);
        second.setOrderCode("ORD-11");
        second.setCustomerCode("A010");
        second.setBreakfastCount(0);
        second.setLunchDinnerCount(12);
        second.setVerifiedCount(5);
        second.setRemainingCount(7);
        second.setDealTime(LocalDateTime.of(2026, 9, 20, 9, 5, 0));

        Page<CustomerOrder> orderPage = new Page<>(1, 20);
        orderPage.setRecords(Arrays.asList(first, second));
        orderPage.setTotal(2);
        when(customerOrderMapper.findMealStatsOrders(any(CustomerMealStatsQueryCriteria.class),
                eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 11, 1)),
                eq(CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK), any(Page.class))).thenReturn(orderPage);
        profile.setSpecialRequirements("米饭加量");
        profile.setMedicalRequirements("少盐");
        profile.setPostoperativeInfo("4个月");
        profile.setCustomerName("张三");
        profile.setAllergyTags(Collections.singletonList("花生"));
        when(customerProfileMapper.findByIds(Collections.singleton(CUSTOMER_ID)))
                .thenReturn(Collections.singletonList(profile));
        CustomerProfileAddress address = new CustomerProfileAddress();
        address.setCustomerId(CUSTOMER_ID);
        address.setAddressType("DEFAULT");
        address.setContactName("张三");
        address.setContactPhone("13800138000");
        address.setAddressDetail("示例路 1 号");
        when(addressMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(address));
        when(mealPlanCustomerMapper.countAllScheduledByOrderIds(Arrays.asList(ORDER_ID, 11L)))
                .thenReturn(Arrays.asList(poolCount("LUNCH", 4), countForOrder(11L, 7)));
        when(mealPlanCustomerMapper.countTodayUnverifiedScheduledByOrderIds(
                Arrays.asList(ORDER_ID, 11L), LocalDate.now()))
                .thenReturn(Collections.singletonList(countForOrder(ORDER_ID, 2)));

        CustomerMealStatsQueryCriteria criteria = new CustomerMealStatsQueryCriteria();
        criteria.setStatsMonth(MONTH);
        PageResult<CustomerMealStatsRowDto> result = service.queryMealStats(criteria, 1, 20);

        assertEquals(2L, result.getTotalElements());
        assertEquals(Arrays.asList(ORDER_ID, 11L), result.getContent().stream()
                .map(CustomerMealStatsRowDto::getOrderId).collect(java.util.stream.Collectors.toList()));
        CustomerMealStatsRowDto firstRow = result.getContent().get(0);
        assertEquals("张三", firstRow.getCustomerName());
        assertEquals("A010", firstRow.getCustomerCode());
        assertEquals("联系人：张三\n电话：13800138000\n地址：示例路 1 号", firstRow.getAddressText());
        assertEquals("米饭加量", firstRow.getSpecialRequirements());
        assertEquals("少盐", firstRow.getMedicalRequirements());
        assertEquals("4个月", firstRow.getPostoperativeInfo());
        assertEquals("早餐、午晚餐", firstRow.getMealTypeText());
        assertEquals("主2 / 副1 / 素1", firstRow.getSpecification());
        assertEquals(13, firstRow.getTotalCount());
        assertEquals(3, firstRow.getVerifiedCount());
        assertEquals(4, firstRow.getScheduledCount());
        assertEquals(6, firstRow.getRemainingCount());
        assertEquals(4, firstRow.getEstimatedRemainingCount());
        assertEquals("2026-09-12 13:45:00", firstRow.getDealTime());
        assertEquals(Collections.singletonList("花生"), firstRow.getAllergyTags());
        assertEquals("/uploads/menu-a.png", firstRow.getCustomMenuImage());
        verify(customerOrderMapper).findMealStatsOrders(eq(criteria), eq(LocalDate.of(2026, 10, 1)),
                eq(LocalDate.of(2026, 11, 1)), eq(CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK), any(Page.class));
    }

    @Test
    void shouldExposeSeptemberHistoryAndCalendarForCompletedOrderImportedOnOctoberFirst() {
        order.setStartDate(LocalDate.of(2026, 6, 2));
        order.setImportDate(LocalDate.of(2026, 10, 1));
        order.setEndDate(null);
        order.setBreakfastCount(0);
        order.setLunchDinnerCount(3);
        order.setImportedVerifiedCount(3);
        order.setVerifiedCount(3);
        order.setRemainingCount(0);
        order.setStatus(2);
        order.setMealType("LUNCH_DINNER");
        order.setStartMealType("LUNCH");
        Page<CustomerOrder> orderPage = new Page<>(1, 20);
        orderPage.setRecords(Collections.singletonList(order));
        orderPage.setTotal(1);
        CustomerMealStatsQueryCriteria criteria = new CustomerMealStatsQueryCriteria();
        criteria.setStatsMonth("2026-09");
        when(customerOrderMapper.findMealStatsOrders(eq(criteria), eq(LocalDate.of(2026, 9, 1)),
                eq(LocalDate.of(2026, 10, 1)), eq(CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK), any(Page.class)))
                .thenReturn(orderPage);
        when(customerProfileMapper.findByIds(Collections.singleton(CUSTOMER_ID)))
                .thenReturn(Collections.singletonList(profile));
        storedOverrides.add(overrideEntity(71L, "2026-09-01", "LUNCH", 1, null,
                CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK));
        storedOverrides.add(overrideEntity(72L, "2026-09-30", "DINNER", 2, null,
                CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK));
        when(additionMapper.selectActiveByOrderIdAndDateRange(ORDER_ID, EARLIEST_DATE, LocalDate.of(2026, 9, 30)))
                .thenReturn(storedOverrides);

        PageResult<CustomerMealStatsRowDto> result = service.queryMealStats(criteria, 1, 20);
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(result.getContent().get(0).getOrderId(), "2026-09");

        assertEquals(1L, result.getTotalElements());
        assertEquals("已完成", result.getContent().get(0).getStatusLabel());
        assertEquals(0, result.getContent().get(0).getRemainingCount());
        assertFalse(calendar.getEditable());
        assertEquals(LocalDate.of(2026, 6, 2), calendar.getStartDate());
        assertEquals(2, calendar.getCells().size());
        assertEquals(3, calendar.getCells().stream().mapToInt(CustomerMealScheduleCellDto::getQuantity).sum());
        assertTrue(calendar.getCells().stream().allMatch(cell -> Boolean.TRUE.equals(cell.getImportedHistory())));
        assertTrue(calendar.getOverrides().isEmpty());
        verify(customerOrderMapper).findMealStatsOrders(eq(criteria), eq(LocalDate.of(2026, 9, 1)),
                eq(LocalDate.of(2026, 10, 1)), eq(CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK), any(Page.class));
    }

    @Test
    void shouldSaveZeroOverrideForOnlyTheRouteOrderAndCleanItsUnverifiedServing() {
        CustomerScheduledMealDto progress = progress("2026-10-01", "LUNCH", 1, 0, 0);
        when(mealPlanCustomerMapper.selectScheduledMealsByOrderIdAndDateRange(ORDER_ID, MONTH_START, MONTH_END))
                .thenReturn(Collections.singletonList(progress));
        when(mealPlanCustomerMapper.countSuccessfulScheduledByOrderIdGroupedByMealType(ORDER_ID))
                .thenReturn(Collections.singletonList(poolCount("LUNCH", 1)));
        when(additionMapper.selectAnyByOrderDateMeal(ORDER_ID, MONTH_START, "LUNCH")).thenReturn(null);
        when(additionMapper.insert(any(CustomerMealScheduleAddition.class))).thenAnswer(invocation -> {
            CustomerMealScheduleAddition addition = invocation.getArgument(0);
            addition.setId(99L);
            storedOverrides.add(addition);
            return 1;
        });
        when(additionMapper.softDeleteMissingByOrderIdAndDateRange(
                ORDER_ID, MONTH_START, MONTH_END, Collections.singletonList(99L))).thenReturn(0);
        when(mealPlanService.deleteExcessUnverifiedCustomerServingsForCalendarAdjustment(
                CUSTOMER_ID, ORDER_ID, "2026-10-01", "LUNCH", 0)).thenReturn(1);

        CustomerOrderMealCalendarSaveResult saved = save("2026-10-01", "LUNCH", 0, null, "停一天");

        assertEquals(1, saved.getDeletedUnverifiedPlanCount());
        assertNotNull(saved.getRevision());
        ArgumentCaptor<CustomerMealScheduleAddition> additionCaptor = ArgumentCaptor.forClass(CustomerMealScheduleAddition.class);
        verify(additionMapper).insert(additionCaptor.capture());
        assertEquals(CUSTOMER_ID, additionCaptor.getValue().getCustomerId());
        assertEquals(ORDER_ID, additionCaptor.getValue().getOrderId());
        assertEquals(0, additionCaptor.getValue().getQuantity());
        verify(additionMapper).softDeleteMissingByOrderIdAndDateRange(
                ORDER_ID, MONTH_START, MONTH_END, Collections.singletonList(99L));
        verify(mealPlanService).deleteExcessUnverifiedCustomerServingsForCalendarAdjustment(
                CUSTOMER_ID, ORDER_ID, "2026-10-01", "LUNCH", 0);
        verify(customerProfileMapper, never()).updateById(any(CustomerProfile.class));
    }

    @Test
    void shouldRestoreOnlyThisOrdersCurrentMonthWhenOverrideSnapshotIsEmpty() {
        storedOverrides.add(overrideEntity(31L, "2026-10-01", "LUNCH", 0, null, "停餐"));
        storedOverrides.add(overrideEntity(32L, "2026-09-30", "DINNER", 0, null, "历史停餐"));
        when(additionMapper.softDeleteMissingByOrderIdAndDateRange(
                ORDER_ID, MONTH_START, MONTH_END, Collections.emptyList())).thenReturn(1);

        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        CustomerOrderMealCalendarSaveDto request = saveRequest(calendar.getRevision(), Collections.emptyList());
        CustomerOrderMealCalendarSaveResult result = service.saveOrderCalendar(ORDER_ID, request);

        assertEquals(ORDER_ID, result.getOrderId());
        verify(additionMapper).softDeleteMissingByOrderIdAndDateRange(
                ORDER_ID, MONTH_START, MONTH_END, Collections.emptyList());
    }

    @Test
    void shouldExplicitlyClearPersistedSoupQuantityWhenOverrideSetsItToDefault() {
        storedOverrides.add(overrideEntity(41L, "2026-10-01", "LUNCH", 1, 1, "含汤"));
        when(additionMapper.updateOrderCalendarOverride(41L, ORDER_ID, 1, null, "含汤", "system"))
                .thenReturn(1);
        when(additionMapper.softDeleteMissingByOrderIdAndDateRange(
                ORDER_ID, MONTH_START, MONTH_END, Collections.singletonList(41L))).thenReturn(0);

        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        CustomerOrderMealCalendarOverrideDto override = override("2026-10-01", "LUNCH", 1, null, "含汤");
        CustomerOrderMealCalendarSaveDto request = saveRequest(calendar.getRevision(), Collections.singletonList(override));

        service.saveOrderCalendar(ORDER_ID, request);

        verify(additionMapper).updateOrderCalendarOverride(41L, ORDER_ID, 1, null, "含汤", "system");
    }

    @Test
    void shouldRejectChangesToAnOverrideHiddenByCustomerWideStop() {
        ExcludedDateDto excluded = new ExcludedDateDto();
        excluded.setDate("2026-10-01");
        excluded.setMealTypes(Collections.singletonList("LUNCH"));
        profile.setExcludedDates(Collections.singletonList(excluded));
        storedOverrides.add(overrideEntity(51L, "2026-10-01", "LUNCH", 1, null, "原覆盖"));

        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        CustomerOrderMealCalendarSaveDto request = saveRequest(calendar.getRevision(), Collections.emptyList());
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.saveOrderCalendar(ORDER_ID, request));

        assertTrue(error.getMessage().contains("客户档案已统一停餐"));
        verify(additionMapper, never()).softDeleteMissingByOrderIdAndDateRange(
                eq(ORDER_ID), any(LocalDate.class), any(LocalDate.class), anyList());
        verify(mealPlanService, never()).deleteExcessUnverifiedCustomerServingsForCalendarAdjustment(
                any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void shouldReturnConflictForStaleRevisionBeforeWritingAnyOverride() {
        CustomerOrderMealCalendarSaveDto request = saveRequest("stale-revision", Collections.emptyList());

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.saveOrderCalendar(ORDER_ID, request));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT.value(), error.getStatus());
        verify(additionMapper, never()).insert(any(CustomerMealScheduleAddition.class));
        verify(additionMapper, never()).softDeleteMissingByOrderIdAndDateRange(
                eq(ORDER_ID), any(LocalDate.class), any(LocalDate.class), anyList());
    }

    @Test
    void shouldExposePausedOrdersAsReadonlyAndRejectSave() {
        order.setStatus(4);
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);

        assertFalse(calendar.getEditable());
        assertTrue(calendar.getReadOnlyReason().contains("暂停"));
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.saveOrderCalendar(ORDER_ID, saveRequest(calendar.getRevision(), Collections.emptyList())));
        assertTrue(error.getMessage().contains("暂停"));
        verify(additionMapper, never()).insert(any(CustomerMealScheduleAddition.class));
    }

    @Test
    void shouldExposeUnconfirmedMealTypeAsReadonlyAndRejectSave() {
        order.setMealType(null);
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);

        assertFalse(calendar.getEditable());
        assertTrue(calendar.getReadOnlyReason().contains("尚未指定"));
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.saveOrderCalendar(ORDER_ID, saveRequest(calendar.getRevision(), Collections.emptyList())));
        assertTrue(error.getMessage().contains("尚未指定"));
    }

    @Test
    void shouldRejectDuplicateDateMealOverridesBeforeWriting() {
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        CustomerOrderMealCalendarOverrideDto override = override("2026-10-01", "LUNCH", 0, null, "停餐");
        CustomerOrderMealCalendarSaveDto request = saveRequest(calendar.getRevision(), Arrays.asList(override, override));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.saveOrderCalendar(ORDER_ID, request));

        assertTrue(error.getMessage().contains("不能重复"));
        verify(additionMapper, never()).insert(any(CustomerMealScheduleAddition.class));
    }

    @Test
    void shouldRejectRequestedQuantityThatCannotFitTheRemainingMealPool() {
        order.setLunchDinnerCount(1);
        order.setRemainingCount(3);
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        CustomerOrderMealCalendarOverrideDto override = override("2026-10-01", "LUNCH", 2, null, "");
        CustomerOrderMealCalendarSaveDto request = saveRequest(calendar.getRevision(), Collections.singletonList(override));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.saveOrderCalendar(ORDER_ID, request));

        assertTrue(error.getMessage().contains("当前可用餐数"));
        verify(additionMapper, never()).insert(any(CustomerMealScheduleAddition.class));
    }

    @Test
    void shouldKeepUnchangedOverrideSaveIdempotent() {
        storedOverrides.add(overrideEntity(61L, "2026-10-01", "LUNCH", 1, null, "保留"));
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        CustomerOrderMealCalendarSaveDto request = saveRequest(calendar.getRevision(), calendar.getOverrides());

        CustomerOrderMealCalendarSaveResult result = service.saveOrderCalendar(ORDER_ID, request);

        assertEquals(calendar.getRevision(), result.getRevision());
        verify(additionMapper, never()).updateOrderCalendarOverride(
                eq(61L), eq(ORDER_ID), any(), any(), any(), any());
        verify(additionMapper, never()).insert(any(CustomerMealScheduleAddition.class));
        verify(additionMapper, never()).softDeleteMissingByOrderIdAndDateRange(
                eq(ORDER_ID), any(LocalDate.class), any(LocalDate.class), anyList());
    }

    private CustomerOrderMealCalendarSaveResult save(String date,
                                                     String mealType,
                                                     int quantity,
                                                     Integer soupQuantity,
                                                     String remark) {
        CustomerOrderMealCalendarDto calendar = service.getOrderCalendar(ORDER_ID, MONTH);
        CustomerOrderMealCalendarOverrideDto override = override(date, mealType, quantity, soupQuantity, remark);
        return service.saveOrderCalendar(ORDER_ID,
                saveRequest(calendar.getRevision(), Collections.singletonList(override)));
    }

    private CustomerOrderMealCalendarSaveDto saveRequest(String revision,
                                                         List<CustomerOrderMealCalendarOverrideDto> overrides) {
        CustomerOrderMealCalendarSaveDto request = new CustomerOrderMealCalendarSaveDto();
        request.setStatsMonth(MONTH);
        request.setExpectedRevision(revision);
        request.setOverrides(overrides);
        return request;
    }

    private CustomerOrderMealCalendarOverrideDto override(String date,
                                                          String mealType,
                                                          int quantity,
                                                          Integer soupQuantity,
                                                          String remark) {
        CustomerOrderMealCalendarOverrideDto dto = new CustomerOrderMealCalendarOverrideDto();
        dto.setDate(date);
        dto.setMealType(mealType);
        dto.setQuantity(quantity);
        dto.setSoupQuantity(soupQuantity);
        dto.setRemark(remark);
        return dto;
    }

    private CustomerMealScheduleAddition overrideEntity(Long id,
                                                        String date,
                                                        String mealType,
                                                        int quantity,
                                                        Integer soupQuantity,
                                                        String remark) {
        CustomerMealScheduleAddition addition = new CustomerMealScheduleAddition();
        addition.setId(id);
        addition.setCustomerId(CUSTOMER_ID);
        addition.setOrderId(ORDER_ID);
        addition.setRecordDate(LocalDate.parse(date));
        addition.setMealType(mealType);
        addition.setQuantity(quantity);
        addition.setSoupQuantity(soupQuantity);
        addition.setRemark(remark);
        addition.setDeleted(false);
        return addition;
    }

    private CustomerScheduledMealDto progress(String date, String mealType, int generated, int failed, int verified) {
        CustomerScheduledMealDto progress = new CustomerScheduledMealDto();
        progress.setCustomerId(CUSTOMER_ID);
        progress.setOrderId(ORDER_ID);
        progress.setRecordDate(LocalDate.parse(date));
        progress.setMealType(mealType);
        progress.setGeneratedCount(generated);
        progress.setFailedCount(failed);
        progress.setVerifiedCount(verified);
        return progress;
    }

    private OrderScheduledCountDto poolCount(String mealType, int count) {
        OrderScheduledCountDto result = new OrderScheduledCountDto();
        result.setOrderId(ORDER_ID);
        result.setMealType(mealType);
        result.setScheduledCount(count);
        return result;
    }

    private OrderScheduledCountDto countForOrder(Long orderId, int count) {
        OrderScheduledCountDto result = new OrderScheduledCountDto();
        result.setOrderId(orderId);
        result.setScheduledCount(count);
        return result;
    }

    private CustomerOrder buildOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setId(ORDER_ID);
        order.setCustomerId(CUSTOMER_ID);
        order.setCustomerCode("A010");
        order.setOrderCode("ORD-10");
        order.setStatus(1);
        order.setMealType("ALL");
        order.setStartMealType("BREAKFAST");
        order.setScheduleMode("DAILY");
        order.setStartDate(MONTH_START);
        order.setEndDate(LocalDate.of(2026, 10, 2));
        order.setBreakfastCount(2);
        order.setLunchDinnerCount(4);
        order.setRemainingCount(6);
        order.setImportedVerifiedCount(0);
        order.setSoupCount(1);
        return order;
    }

    private CustomerProfile buildProfile() {
        CustomerProfile profile = new CustomerProfile();
        profile.setId(CUSTOMER_ID);
        profile.setCustomerCode("A010");
        profile.setCustomerName("示例客户");
        profile.setExcludedDates(Collections.emptyList());
        return profile;
    }

}
