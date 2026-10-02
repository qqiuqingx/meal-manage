package me.zhengjie.modules.customer.profile.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealVerifiedCountDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.numberpool.mapper.NumberPoolMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.domain.ParsedWorkbook;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportPreviewDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportResultDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.service.CustomerOrderImportParser;
import me.zhengjie.modules.customer.profile.service.CustomerDietDictionaryService;
import me.zhengjie.modules.customer.profile.service.CustomerDietMatchService;
import me.zhengjie.modules.customer.profile.domain.CustomerDietImportData;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.ImportCandidate;
import me.zhengjie.modules.customer.profile.domain.ParsedCustomer;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportDraftDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportMealCellDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerScheduledMealDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.service.MealPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 通过真实预览和确认业务方法验证月度优先级及数量保护；只使用匿名内存数据。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerOrderMonthlyImportServiceTest {
    @Mock private CustomerOrderMapper orderMapper;
    @Mock private CustomerProfileMapper profileMapper;
    @Mock private CustomerProfileAddressMapper addressMapper;
    @Mock private NumberPoolMapper numberPoolMapper;
    @Mock private CustomerOrderService orderService;
    @Mock private ParentPackageMapper parentMapper;
    @Mock private CustomerOrderImportParser parser;
    @Mock private CustomerDietDictionaryService dictionaryService;
    @Mock private CustomerMealScheduleAdditionMapper additionMapper;
    @Mock private MealPlanCustomerMapper planMapper;
    @Mock private MealPlanService mealPlanService;
    private CustomerOrderMonthlyImportService service;
    private CustomerOrder order;
    private CustomerProfile profile;
    private List<CustomerOrder> orders;
    private List<CustomerMealScheduleAddition> records;
    private List<CustomerScheduledMealDto> progress;
    private int actualVerified;
    private LocalDate actualVerificationDate;
    private long sequence;

    @BeforeEach
    void setUp() {
        service = new CustomerOrderMonthlyImportService(orderMapper, additionMapper, planMapper, mealPlanService);
        profile = new CustomerProfile();
        profile.setId(10L);
        profile.setCustomerCode("A004");
        profile.setPhone("13800138000");
        order = new CustomerOrder();
        order.setId(20L);
        order.setCustomerId(10L);
        order.setParentPackageId(1L);
        order.setCustomerCode("A004");
        order.setDealTime(LocalDateTime.of(2026, 6, 2, 0, 0));
        order.setStartDate(LocalDate.of(2026, 6, 2));
        order.setImportMonth(LocalDate.of(2026, 9, 1));
        order.setImportDate(LocalDate.of(2026, 9, 20));
        order.setLunchDinnerCount(158);
        order.setBreakfastCount(0);
        order.setVerifiedCount(128);
        order.setImportedVerifiedCount(128);
        order.setRemainingCount(30);
        order.setStatus(1);
        orders = new ArrayList<>(Collections.singletonList(order));
        records = new ArrayList<>();
        progress = new ArrayList<>();
        actualVerified = 0;
        actualVerificationDate = LocalDate.of(2026, 9, 21);
        sequence = 1;
        when(orderMapper.selectList(any())).thenAnswer(invocation -> orders);
        when(orderMapper.selectInlineUpdateByIdForUpdate(20L)).thenAnswer(invocation -> order);
        when(orderMapper.updateById(any(CustomerOrder.class))).thenReturn(1);
        when(orderMapper.sumVerifiedCountByOrderDate(anyLong())).thenAnswer(invocation -> {
            OrderMealVerifiedCountDto count = new OrderMealVerifiedCountDto();
            count.setOrderId(20L);
            count.setMealType("LUNCH");
            count.setVerifiedCount(actualVerified);
            count.setRecordDate(actualVerificationDate);
            return Collections.singletonList(count);
        });
        when(additionMapper.selectActiveByOrderIdAndDateRange(anyLong(), any(), any()))
                .thenAnswer(invocation -> records.stream().filter(record -> !Boolean.TRUE.equals(record.getDeleted()))
                        .collect(Collectors.toList()));
        when(planMapper.selectScheduledMealsByOrderIdAndDateRange(anyLong(), any(), any())).thenAnswer(invocation -> progress);
        when(additionMapper.insert(any(CustomerMealScheduleAddition.class))).thenAnswer(invocation -> {
            CustomerMealScheduleAddition record = invocation.getArgument(0);
            record.setId(sequence++);
            records.add(record);
            return 1;
        });
        when(additionMapper.updateOrderCalendarOverride(anyLong(), anyLong(), any(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    CustomerMealScheduleAddition record = records.stream().filter(r -> r.getId().equals(invocation.getArgument(0)))
                            .findFirst().orElseThrow(AssertionError::new);
                    record.setQuantity(invocation.getArgument(2));
                    record.setSoupQuantity(invocation.getArgument(3));
                    record.setRemark(invocation.getArgument(4));
                    return 1;
                });
        when(additionMapper.softDeleteImportedByIds(anyLong(), anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(1);
            records.stream().filter(r -> ids.contains(r.getId())).forEach(r -> r.setDeleted(true));
            return ids.size();
        });
        when(additionMapper.selectAnyByOrderDateMeal(anyLong(), any(), anyString())).thenAnswer(invocation -> records.stream()
                .filter(r -> r.getRecordDate().equals(invocation.getArgument(1)) && r.getMealType().equals(invocation.getArgument(2)))
                .findFirst().orElse(null));
        when(additionMapper.reviveOrderCalendarOverride(anyLong(), anyLong(), any(), anyString(), any(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    CustomerMealScheduleAddition record = records.stream().filter(r -> r.getId().equals(invocation.getArgument(0)))
                            .findFirst().orElseThrow(AssertionError::new);
                    record.setDeleted(false);
                    record.setQuantity(invocation.getArgument(4));
                    record.setSoupQuantity(invocation.getArgument(5));
                    record.setRemark(invocation.getArgument(6));
                    return 1;
                });
    }

    @Test
    void shouldContinueOctoberOnSameOrderKeepSeptemberAndUseTwentyPlusFive() {
        add("2026-09-01", 1, true);
        CustomerMealScheduleAddition lateSeptember = add("2026-09-25", 1, false);
        ImportCandidate october = candidate(10, 158, 20, cell("2026-10-21", 5));

        prepare(october, 10, 20);
        assertEquals("UPDATE_MONTH", october.getDraft().getImportAction());
        assertEquals(25, october.getDraft().getAfterRemainingCount());
        assertTrue(service.apply(october, service.lockOrder(october), LocalDate.of(2026, 10, 20)));

        assertEquals(20L, order.getId());
        assertEquals(LocalDate.of(2026, 6, 2), order.getStartDate());
        assertEquals(LocalDate.of(2026, 10, 1), order.getImportMonth());
        assertEquals(158, order.getLunchDinnerCount());
        assertEquals(25, order.getRemainingCount());
        assertEquals(133, order.getVerifiedCount());
        assertEquals(133, order.getImportedVerifiedCount());
        assertEquals(3, records.size());
        assertTrue(lateSeptember.isImportedHistory());
        assertTrue(records.stream().anyMatch(r -> "2026-09-01".equals(r.getRecordDate().toString())));
    }

    @Test
    void shouldCompareFullYearMonthAcrossDecemberAndJanuary() {
        order.setImportMonth(LocalDate.of(2026, 12, 1));
        order.setImportDate(LocalDate.of(2026, 12, 20));
        ImportCandidate january = candidate(1, 158, 20);
        january.getDraft().getMealCells().add(cell("2027-01-21", 1));
        january.getDraft().setFutureMealCount(1);
        service.prepare(january, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 20));
        assertTrue(january.getParsed().isImportable(), january.getParsed().getIssues().toString());
        assertEquals("UPDATE_MONTH", january.getDraft().getImportAction());
        service.apply(january, order, LocalDate.of(2027, 1, 20));
        assertEquals(LocalDate.of(2027, 1, 1), order.getImportMonth());
        assertEquals(21, order.getRemainingCount());
    }

    @Test
    void shouldBackfillSeptemberWithoutRevertingOctoberCountersOrBoundary() {
        order.setImportMonth(LocalDate.of(2026, 10, 1));
        order.setImportDate(LocalDate.of(2026, 10, 20));
        add("2026-10-21", 1, false);
        ImportCandidate september = candidate(9, 99, 0, cell("2026-09-30", 2));
        prepare(september, 9, 25);

        assertEquals("BACKFILL_MONTH", september.getDraft().getImportAction());
        assertEquals(30, september.getDraft().getAfterRemainingCount());
        assertTrue(service.apply(september, order, LocalDate.of(2026, 9, 25)));

        assertEquals(158, order.getLunchDinnerCount());
        assertEquals(30, order.getRemainingCount());
        assertEquals(128, order.getImportedVerifiedCount());
        assertEquals(LocalDate.of(2026, 10, 20), order.getImportDate());
        assertEquals(LocalDate.of(2026, 10, 1), order.getImportMonth());
        assertTrue(records.get(1).isImportedHistory());
        verify(orderMapper, never()).updateById(any(CustomerOrder.class));
    }

    @Test
    void shouldNotRepeatRecordsOrCountersForSameMonthSameContent() {
        ImportCandidate first = candidate(10, 158, 20, cell("2026-10-21", 5));
        prepare(first, 10, 20);
        service.apply(first, order, LocalDate.of(2026, 10, 20));
        ImportCandidate again = candidate(10, 158, 20, cell("2026-10-21", 5));
        prepare(again, 10, 20);

        assertEquals("UPDATE_SAME_MONTH", again.getDraft().getImportAction());
        assertFalse(service.apply(again, order, LocalDate.of(2026, 10, 20)));
        assertEquals(1, records.size());
        assertEquals(25, order.getRemainingCount());
        verify(orderMapper, times(1)).updateById(any(CustomerOrder.class));
        verify(additionMapper, times(1)).insert(any(CustomerMealScheduleAddition.class));
    }

    @Test
    void shouldKeepLatestMonthEvenWhenItsCalendarIsEmpty() {
        ImportCandidate october = candidate(10, 158, 20);
        prepare(october, 10, 20);
        service.apply(october, order, LocalDate.of(2026, 10, 20));
        assertEquals(LocalDate.of(2026, 10, 1), order.getImportMonth());
        assertEquals(20, order.getRemainingCount());
        ImportCandidate september = candidate(9, 99, 0, cell("2026-09-01", 1));
        prepare(september, 9, 25);
        service.apply(september, order, LocalDate.of(2026, 9, 25));
        assertEquals(158, order.getLunchDinnerCount());
        assertEquals(20, order.getRemainingCount());
    }

    @Test
    void shouldCountExistingRealVerificationOnlyOnceAndRejectNegativeBase() {
        actualVerified = 2;
        order.setVerifiedCount(130);
        order.setRemainingCount(28);
        ImportCandidate october = candidate(10, 158, 20, cell("2026-10-21", 5));
        prepare(october, 10, 20);
        service.apply(october, order, LocalDate.of(2026, 10, 20));
        assertEquals(25, order.getRemainingCount());
        assertEquals(133, order.getVerifiedCount());
        assertEquals(131, order.getImportedVerifiedCount());

        ImportCandidate invalid = candidate(10, 158, 157);
        prepare(invalid, 10, 20);
        assertFalse(invalid.getParsed().isImportable());
        assertTrue(invalid.getParsed().getIssues().stream().anyMatch(issue -> issue.getMessage().contains("负的历史核销基数")));
        assertThrows(BadRequestException.class, () -> service.apply(invalid, order, LocalDate.of(2026, 10, 20)));
        assertEquals(25, order.getRemainingCount());
    }

    @Test
    void shouldIgnoreDisplayOnlyFieldsFilledByLockQuery() {
        ImportCandidate october = candidate(10, 158, 20);
        prepare(october, 10, 20);
        CustomerOrder locked = com.alibaba.fastjson2.JSON.parseObject(com.alibaba.fastjson2.JSON.toJSONString(order), CustomerOrder.class);
        locked.setTotalCount(158);
        locked.setCustomerName("匿名展示名");
        locked.setPhone("13800138000");
        assertTrue(service.apply(october, locked, LocalDate.of(2026, 10, 20)));
        assertEquals(20, locked.getRemainingCount());
    }

    @Test
    void shouldNotReturnOctoberConsumptionWhenSeptemberIsUploadedAgain() {
        order.setImportMonth(LocalDate.of(2026, 9, 1));
        order.setImportDate(LocalDate.of(2026, 10, 1));
        order.setImportedVerifiedCount(128);
        order.setVerifiedCount(130);
        order.setRemainingCount(28);
        actualVerified = 2;
        actualVerificationDate = LocalDate.of(2026, 10, 2);
        ImportCandidate september = candidate(9, 158, 30);
        service.prepare(september, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 20));
        assertEquals(28, september.getDraft().getAfterRemainingCount());
        assertEquals(2, september.getDraft().getPostSnapshotVerifiedCount());
        assertFalse(service.apply(september, order, LocalDate.of(2026, 10, 20)));
        assertEquals(LocalDate.of(2026, 10, 1), order.getImportDate());
        assertEquals(128, order.getImportedVerifiedCount());
        assertEquals(28, order.getRemainingCount());
    }

    @Test
    void shouldSubtractEarlierRealVerificationFromNewPlanningWindowOnlyOnce() {
        actualVerified = 2;
        order.setVerifiedCount(130);
        order.setRemainingCount(28);
        order.setMealType("LUNCH_DINNER");
        order.setScheduleMode("DAILY");
        ImportCandidate october = candidate(10, 158, 20, cell("2026-10-21", 5));
        prepare(october, 10, 20);
        service.apply(october, order, LocalDate.of(2026, 10, 20));
        java.util.Map<String, Integer> planned = me.zhengjie.modules.customer.profile.util.CustomerMealStatsScheduleUtil.buildOrderQuantities(
                order, Collections.emptyList(), Collections.emptyList(), LocalDate.of(2026, 11, 30));
        assertEquals(25, planned.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(25, order.getRemainingCount());
        assertEquals(2, order.getQuantityAllocatedBeforeImport());
    }

    @Test
    void shouldRejectVerificationAfterPreviewBeforeAnyWrite() {
        ImportCandidate october = candidate(10, 158, 20, cell("2026-10-21", 5));
        prepare(october, 10, 20);
        actualVerified = 1;
        order.setVerifiedCount(129);
        order.setRemainingCount(29);
        assertThrows(BadRequestException.class, () -> service.apply(october, order, LocalDate.of(2026, 10, 20)));
        assertTrue(records.isEmpty());
        verify(orderMapper, never()).updateById(any(CustomerOrder.class));
        verify(additionMapper, never()).insert(any(CustomerMealScheduleAddition.class));
    }

    @Test
    void shouldProtectManualSourceVerifiedQuantityAndOtherMonth() {
        order.setImportMonth(LocalDate.of(2026, 10, 1));
        order.setImportDate(LocalDate.of(2026, 10, 20));
        CustomerMealScheduleAddition manual = add("2026-10-19", 2, false);
        manual.setRemark("人工调整");
        ImportCandidate conflict = candidate(10, 158, 20, cell("2026-10-19", 5));
        prepare(conflict, 10, 20);
        assertFalse(conflict.getParsed().isImportable());
        assertThrows(BadRequestException.class, () -> service.apply(conflict, order, LocalDate.of(2026, 10, 20)));
        manual.setRemark(CustomerMealScheduleAddition.IMPORTED_PLAN_REMARK);
        CustomerScheduledMealDto verified = progress("2026-10-19", 2, 0, 2);
        actualVerified = 2;
        actualVerificationDate = LocalDate.of(2026, 10, 19);
        order.setVerifiedCount(130);
        order.setRemainingCount(28);
        progress.add(verified);
        ImportCandidate missing = candidate(10, 158, 20);
        prepare(missing, 10, 20);
        assertFalse(missing.getParsed().isImportable());
        assertThrows(BadRequestException.class, () -> service.apply(missing, order, LocalDate.of(2026, 10, 20)));
        assertFalse(Boolean.TRUE.equals(manual.getDeleted()));
    }

    @Test
    void shouldClearOnlyRemovedMonthImportsAndCleanExcessUnverifiedResults() {
        add("2026-09-01", 1, true);
        CustomerMealScheduleAddition removed = add("2026-10-02", 2, false);
        progress.add(progress("2026-10-02", 2, 0, 0));
        ImportCandidate october = candidate(10, 158, 20);
        prepare(october, 10, 20);
        service.apply(october, order, LocalDate.of(2026, 10, 20));
        assertTrue(removed.getDeleted());
        assertFalse(records.get(0).getDeleted());
        verify(additionMapper).softDeleteImportedByIds(20L, Collections.singletonList(removed.getId()));
        verify(mealPlanService).deleteExcessUnverifiedCustomerServingsForCalendarAdjustment(10L, 20L, "2026-10-02", "LUNCH", 0);
    }

    @Test
    void shouldRestoreDeletedImportedCellUsingItsExistingId() {
        CustomerMealScheduleAddition old = add("2026-10-21", 1, false);
        old.setDeleted(true);
        ImportCandidate october = candidate(10, 158, 20, cell("2026-10-21", 5));
        prepare(october, 10, 20);
        service.apply(october, order, LocalDate.of(2026, 10, 20));
        assertEquals(1, records.size());
        assertFalse(old.getDeleted());
        assertEquals(5, old.getQuantity());
        verify(additionMapper, never()).insert(any(CustomerMealScheduleAddition.class));
    }

    @Test
    void shouldRejectAmbiguousOrHandCreatedOrderAndInferKnownLegacyMonth() {
        orders.add(order);
        ImportCandidate ambiguous = candidate(10, 158, 20);
        prepare(ambiguous, 10, 20);
        assertFalse(ambiguous.getParsed().isImportable());
        orders.remove(1);
        order.setImportMonth(null);
        order.setImportDate(null);
        order.setImportedVerifiedCount(0);
        ImportCandidate handCreated = candidate(10, 158, 20);
        prepare(handCreated, 10, 20);
        assertFalse(handCreated.getParsed().isImportable());
        add("2026-09-01", 1, true);
        actualVerified = 0;
        order.setImportedVerifiedCount(128);
        ImportCandidate legacy = candidate(10, 158, 20);
        prepare(legacy, 10, 20);
        assertTrue(legacy.getParsed().isImportable(), legacy.getParsed().getIssues().toString());
        assertEquals("2026-09", legacy.getDraft().getCurrentImportMonth());
    }

    @Test
    void shouldNotCreateAnotherFirstOrderWhenCustomerAlreadyHasDifferentPackageOrder() {
        order.setParentPackageId(2L);
        ImportCandidate incoming = candidate(10, 158, 20);
        prepare(incoming, 10, 20);
        assertFalse(incoming.getParsed().isImportable());
        assertNull(incoming.getExistingOrder());
        assertThrows(BadRequestException.class, () -> service.checkNoOrder(incoming));
    }

    @Test
    void shouldValidateActualStartMealWithoutResettingItAtEachImport() {
        order.setMealType("LUNCH_DINNER");
        order.setStartMealType("DINNER");
        ImportCandidate valid = candidate(10, 158, 20, cell("2026-10-21", 1));
        prepare(valid, 10, 20);
        assertTrue(valid.getParsed().isImportable(), valid.getParsed().getIssues().toString());
        order.setStartDate(LocalDate.of(2026, 10, 21));
        ImportCandidate beforeFirstMeal = candidate(10, 158, 20, cell("2026-10-21", 1));
        prepare(beforeFirstMeal, 10, 20);
        assertFalse(beforeFirstMeal.getParsed().isImportable());
    }

    @Test
    void shouldRejectFutureMealTypeUnsupportedByOriginalOrder() {
        order.setMealType("LUNCH");
        CustomerImportMealCellDto dinner = cell("2026-10-21", 1);
        dinner.setMealType("DINNER");
        ImportCandidate incoming = candidate(10, 158, 20, dinner);
        prepare(incoming, 10, 20);
        assertFalse(incoming.getParsed().isImportable());
        assertTrue(incoming.getParsed().getIssues().stream().anyMatch(issue -> issue.getMessage().contains("原订单餐次")));
        assertThrows(BadRequestException.class, () -> service.apply(incoming, order, LocalDate.of(2026, 10, 20)));
        assertTrue(records.isEmpty());
    }

    @Test
    void shouldPreservePauseAndRejectCompletedOrderPositiveBalanceOrDateRollback() {
        order.setStatus(4);
        ImportCandidate paused = candidate(10, 158, 20);
        prepare(paused, 10, 20);
        service.apply(paused, order, LocalDate.of(2026, 10, 20));
        assertEquals(4, order.getStatus());
        order.setStatus(2);
        ImportCandidate completed = candidate(10, 158, 20);
        prepare(completed, 10, 20);
        assertFalse(completed.getParsed().isImportable());
        order.setStatus(1);
        ImportCandidate backwards = candidate(10, 158, 20);
        prepare(backwards, 10, 19);
        assertFalse(backwards.getParsed().isImportable());
    }

    @Test
    void shouldKeepMalformedCustomerAsRowErrorInsteadOfRejectingWholePreview() {
        ImportCandidate invalid = candidate(10, 158, -1);
        assertDoesNotThrow(() -> prepare(invalid, 10, 20));
        assertFalse(invalid.getParsed().isImportable());
        assertEquals(30, order.getRemainingCount());
    }

    @Test
    void shouldContinueThroughRealPreviewAndConfirmAndLockOrderBeforeProfile() {
        CustomerProfileImportServiceImpl imports = imports();
        CustomerImportPreviewDto preview = imports.preview(new byte[]{1}, "anonymous.xlsx", LocalDate.of(2026, 10, 20));
        assertEquals(1, preview.getImportableCount());
        assertEquals("UPDATE_MONTH", preview.getDrafts().get(0).getImportAction());
        assertEquals(25, preview.getDrafts().get(0).getAfterRemainingCount());
        CustomerImportResultDto result = imports.importCustomers(new byte[]{1}, "anonymous.xlsx", preview.getFileHash(),
                null, LocalDate.of(2026, 10, 20), preview.getOrderStateHash());
        assertEquals(1, result.getUpdatedCount());
        assertEquals(20L, result.getResults().get(0).getOrderId());
        assertEquals(25, order.getRemainingCount());
        assertEquals(LocalDate.of(2026, 6, 2), order.getStartDate());
        org.mockito.InOrder lockOrder = inOrder(orderMapper, profileMapper);
        lockOrder.verify(orderMapper).selectInlineUpdateByIdForUpdate(20L);
        lockOrder.verify(profileMapper).selectByIdForImportUpdate(10L);
        lockOrder.verify(orderMapper).updateById(any(CustomerOrder.class));
        verify(orderService, never()).createImportedFirstOrder(any());

        CustomerImportPreviewDto repeat = imports.preview(new byte[]{1}, "anonymous.xlsx", LocalDate.of(2026, 10, 20));
        CustomerImportResultDto repeated = imports.importCustomers(new byte[]{1}, "anonymous.xlsx", repeat.getFileHash(),
                null, LocalDate.of(2026, 10, 20), repeat.getOrderStateHash());
        assertEquals(1, repeated.getAlreadyExistsCount());
        assertEquals(5, records.size());
        verify(orderMapper, times(1)).updateById(any(CustomerOrder.class));
    }

    @Test
    void shouldRejectStalePreviewHashBeforeEnteringWriterTransaction() {
        CustomerProfileImportServiceImpl imports = imports();
        CustomerImportPreviewDto preview = imports.preview(new byte[]{1}, "anonymous.xlsx", LocalDate.of(2026, 10, 20));
        actualVerified = 1;
        order.setVerifiedCount(129);
        order.setRemainingCount(29);
        assertThrows(BadRequestException.class, () -> imports.importCustomers(new byte[]{1}, "anonymous.xlsx", preview.getFileHash(),
                null, LocalDate.of(2026, 10, 20), preview.getOrderStateHash()));
        assertTrue(records.isEmpty());
        verify(orderMapper, never()).selectInlineUpdateByIdForUpdate(anyLong());
        verify(profileMapper, never()).selectByIdForImportUpdate(anyLong());
    }

    @Test
    void shouldRejectChangedImportDateInsteadOfSilentlyRecalculatingPreview() {
        CustomerProfileImportServiceImpl imports = imports();
        CustomerImportPreviewDto preview = imports.preview(new byte[]{1}, "anonymous.xlsx", LocalDate.of(2026, 10, 20));
        assertThrows(BadRequestException.class, () -> imports.importCustomers(new byte[]{1}, "anonymous.xlsx", preview.getFileHash(),
                null, LocalDate.of(2026, 10, 21), preview.getOrderStateHash()));
        verify(orderMapper, never()).updateById(any(CustomerOrder.class));
        assertTrue(records.isEmpty());
    }

    @Test
    void shouldCreateFirstOrderForExistingProfileWithoutDuplicatingAddresses() {
        orders.clear();
        CustomerProfileImportServiceImpl imports = imports();
        ParentPackage parent = packageFixture();
        when(numberPoolMapper.selectForUpdate(1L)).thenReturn(parent);
        when(orderService.createImportedFirstOrder(any())).thenReturn(80L);
        CustomerImportPreviewDto preview = imports.preview(new byte[]{1}, "anonymous.xlsx", LocalDate.of(2026, 10, 20));
        assertEquals("NEW_ORDER", preview.getDrafts().get(0).getImportAction());
        CustomerImportResultDto result = imports.importCustomers(new byte[]{1}, "anonymous.xlsx", preview.getFileHash(),
                null, LocalDate.of(2026, 10, 20), preview.getOrderStateHash());
        assertEquals(1, result.getUpdatedCount());
        assertEquals(80L, result.getResults().get(0).getOrderId());
        org.mockito.ArgumentCaptor<CustomerOrder> captured = org.mockito.ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderService).createImportedFirstOrder(captured.capture());
        assertEquals(LocalDate.of(2026, 10, 1), captured.getValue().getImportMonth());
        assertEquals(25, captured.getValue().getRemainingCount());
        verify(profileMapper, never()).insert(any(CustomerProfile.class));
        verify(addressMapper, never()).insert(any());
    }

    private ParentPackage packageFixture() {
        ParentPackage parent = new ParentPackage();
        parent.setId(1L);
        parent.setPoolPrefix("A");
        parent.setPoolStart(1);
        parent.setPoolEnd(999);
        parent.setStatus(true);
        return parent;
    }

    /** 完整导入用匿名解析工作簿，Writer与月度服务均走真实生产方法。 */
    private CustomerProfileImportServiceImpl imports() {
        ParsedWorkbook workbook = new ParsedWorkbook();
        workbook.setStructureValid(true);
        workbook.setCalendarMonthStart(LocalDate.of(2026, 10, 1));
        workbook.setImportDate(LocalDate.of(2026, 10, 20));
        ParsedCustomer parsed = candidate(10, 158, 20).getParsed();
        parsed.setSheetMealCount(158);
        parsed.setSheetRemainingCount(20);
        List<CustomerImportMealCellDto> future = new ArrayList<>();
        for (int day = 21; day <= 25; day++) {
            future.add(cell("2026-10-" + day, 1));
        }
        parsed.setFutureMealCells(future);
        workbook.setCustomers(Collections.singletonList(parsed));
        when(parser.parse(any(), anyString(), any())).thenAnswer(invocation -> {
            workbook.setFileHash(invocation.getArgument(1));
            workbook.setImportDate(invocation.getArgument(2));
            return workbook;
        });
        when(profileMapper.selectList(any())).thenReturn(Collections.singletonList(profile));
        when(profileMapper.selectByIdForImportUpdate(10L)).thenReturn(profile);
        when(parentMapper.selectList(any())).thenReturn(Collections.singletonList(packageFixture()));
        CustomerProfileImportWriter writer = new CustomerProfileImportWriter(profileMapper, addressMapper,
                additionMapper, numberPoolMapper, orderService, service);
        return new CustomerProfileImportServiceImpl(parser, profileMapper, parentMapper, writer,
                dictionaryService, new CustomerDietMatchService(), service);
    }

    private void prepare(ImportCandidate candidate, int month, int day) {
        service.prepare(candidate, LocalDate.of(2026, month, 1), LocalDate.of(2026, month, day));
    }

    private ImportCandidate candidate(int month, int total, int remaining, CustomerImportMealCellDto... cells) {
        ImportCandidate candidate = new ImportCandidate();
        candidate.setExistingProfile(profile);
        candidate.setSupplemental(true);
        ParsedCustomer parsed = new ParsedCustomer();
        parsed.setEffectiveCode("A004");
        parsed.setPhoneNormalized("13800138000");
        parsed.setSourceRows(Collections.singletonList(4));
        CustomerDietImportData diet = new CustomerDietImportData();
        diet.setDealTime(LocalDateTime.of(2026, 6, 2, 0, 0));
        parsed.setDietImportData(diet);
        candidate.setParsed(parsed);
        CustomerImportDraftDto draft = new CustomerImportDraftDto();
        draft.setCustomerCode("A004");
        draft.setParentPackageId(1L);
        draft.setLunchDinnerCount(total);
        draft.setSheetRemainingCount(remaining);
        draft.setFutureMealCount(Arrays.stream(cells).filter(c -> LocalDate.parse(c.getDate()).getDayOfMonth() > 20)
                .mapToInt(CustomerImportMealCellDto::getQuantity).sum());
        for (CustomerImportMealCellDto cell : cells) {
            if (LocalDate.parse(cell.getDate()).getDayOfMonth() > 20) {
                draft.getMealCells().add(cell);
            } else {
                draft.getHistoricalMealCells().add(cell);
            }
        }
        candidate.setDraft(draft);
        return candidate;
    }

    private CustomerImportMealCellDto cell(String date, int quantity) {
        CustomerImportMealCellDto cell = new CustomerImportMealCellDto();
        cell.setDate(date);
        cell.setMealType("LUNCH");
        cell.setQuantity(quantity);
        return cell;
    }

    private CustomerMealScheduleAddition add(String date, int quantity, boolean historical) {
        CustomerMealScheduleAddition record = new CustomerMealScheduleAddition();
        record.setId(sequence++);
        record.setOrderId(20L);
        record.setCustomerId(10L);
        record.setRecordDate(LocalDate.parse(date));
        record.setMealType("LUNCH");
        record.setQuantity(quantity);
        record.setDeleted(false);
        record.setRemark(historical ? CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK : CustomerMealScheduleAddition.IMPORTED_PLAN_REMARK);
        records.add(record);
        return record;
    }

    private CustomerScheduledMealDto progress(String date, int generated, int failed, int verified) {
        CustomerScheduledMealDto value = new CustomerScheduledMealDto();
        value.setOrderId(20L);
        value.setRecordDate(LocalDate.parse(date));
        value.setMealType("LUNCH");
        value.setGeneratedCount(generated);
        value.setFailedCount(failed);
        value.setVerifiedCount(verified);
        return value;
    }
}
