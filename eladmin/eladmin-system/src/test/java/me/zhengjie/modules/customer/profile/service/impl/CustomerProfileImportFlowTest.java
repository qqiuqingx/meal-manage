package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import me.zhengjie.modules.customer.numberpool.mapper.NumberPoolMapper;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.CustomerOrderStatus;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.ImportCandidate;
import me.zhengjie.modules.customer.profile.domain.ParsedCustomer;
import me.zhengjie.modules.customer.profile.domain.ParsedWorkbook;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportDraftDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportItemResultDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportMealCellDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportPreviewDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerOrderImportParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户批量导入预览与写入回归测试。
 */
@ExtendWith(MockitoExtension.class)
class CustomerProfileImportFlowTest {

    @Mock private CustomerOrderImportParser parser;
    @Mock private CustomerProfileMapper profileMapper;
    @Mock private ParentPackageMapper parentPackageMapper;
    @Mock private CustomerProfileImportWriter importWriter;
    @Mock private CustomerProfileAddressMapper addressMapper;
    @Mock private CustomerMealScheduleAdditionMapper scheduleAdditionMapper;
    @Mock private NumberPoolMapper numberPoolMapper;
    @Mock private CustomerOrderService customerOrderService;

    @InjectMocks private CustomerProfileImportServiceImpl importService;

    /**
     * 零餐数客户在预览中仍可导入，且不产生面向页面的错误或警告。
     */
    @Test
    void previewShouldAcceptZeroRemainingWithoutNotice() {
        ParsedCustomer parsed = parsedCustomer();
        ParsedWorkbook workbook = new ParsedWorkbook();
        workbook.setFileHash("hash");
        workbook.setStructureValid(true);
        workbook.setCustomerCount(1);
        workbook.setCustomers(Collections.singletonList(parsed));
        when(parser.parse(any(byte[].class), any(String.class), any(LocalDate.class))).thenReturn(workbook);
        when(parentPackageMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(parentPackage()));
        when(profileMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        CustomerImportPreviewDto preview = importService.preview(new byte[]{1}, "sample.xlsx", LocalDate.of(2026, 9, 25));

        assertEquals(1, preview.getImportableCount());
        assertEquals(0, preview.getErrorCount());
        assertTrue(preview.getIssues().isEmpty());
        assertTrue(preview.getDrafts().get(0).getWarnings().isEmpty());
        assertEquals(Integer.valueOf(0), preview.getDrafts().get(0).getLunchDinnerCount());
    }

    /**
     * 来源午晚餐数 7 中历史 3 份、未来 4 份分别进入合计和导入前已核销数。
     */
    @Test
    void previewShouldKeepSourceMealCountAndImportedHistory() {
        ParsedCustomer parsed = parsedCustomer();
        parsed.setSheetMealCount(7);
        CustomerImportMealCellDto lunch = new CustomerImportMealCellDto();
        lunch.setDate("2026-09-26");
        lunch.setMealType("LUNCH");
        lunch.setQuantity(2);
        CustomerImportMealCellDto dinner = new CustomerImportMealCellDto();
        dinner.setDate("2026-09-27");
        dinner.setMealType("DINNER");
        dinner.setQuantity(2);
        parsed.setFutureMealCells(Arrays.asList(lunch, dinner));
        ParsedWorkbook workbook = new ParsedWorkbook();
        workbook.setFileHash("hash");
        workbook.setStructureValid(true);
        workbook.setCustomerCount(1);
        workbook.setCustomers(Collections.singletonList(parsed));
        when(parser.parse(any(byte[].class), any(String.class), any(LocalDate.class))).thenReturn(workbook);
        when(parentPackageMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(parentPackage()));
        when(profileMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        CustomerImportPreviewDto preview = importService.preview(new byte[]{1}, "sample.xlsx", LocalDate.of(2026, 9, 25));

        assertEquals(1, preview.getImportableCount());
        assertTrue(preview.getDrafts().get(0).getWarnings().isEmpty());
        assertEquals(Integer.valueOf(7), preview.getDrafts().get(0).getLunchDinnerCount());
        assertEquals(Integer.valueOf(3), preview.getDrafts().get(0).getImportedVerifiedCount());
        assertEquals(Integer.valueOf(4), preview.getDrafts().get(0).getFutureMealCount());
    }

    /**
     * 零餐数客户正常建档，避免创建无法使用的零餐数首单。
     */
    @Test
    void writerShouldCreateProfileWithoutZeroMealOrder() {
        ParentPackage parent = parentPackage();
        CustomerImportDraftDto draft = new CustomerImportDraftDto();
        draft.setCustomerCode("A004");
        draft.setParentPackageId(parent.getId());
        draft.setLunchDinnerCount(0);
        ParsedCustomer parsed = parsedCustomer();
        ImportCandidate candidate = new ImportCandidate();
        candidate.setParsed(parsed);
        candidate.setDraft(draft);
        when(numberPoolMapper.selectForUpdate(parent.getId())).thenReturn(parent);
        doAnswer(invocation -> {
            CustomerProfile profile = invocation.getArgument(0);
            profile.setId(123L);
            return 1;
        }).when(profileMapper).insert(any(CustomerProfile.class));
        CustomerProfileImportWriter writer = new CustomerProfileImportWriter(profileMapper, addressMapper,
                scheduleAdditionMapper, numberPoolMapper, customerOrderService);

        CustomerImportItemResultDto result = writer.write(candidate, LocalDate.of(2026, 9, 25));

        assertEquals("CREATED", result.getStatus());
        assertEquals(Long.valueOf(123L), result.getCustomerId());
        assertNull(result.getOrderId());
        verify(customerOrderService, never()).createImportedFirstOrder(any());
        verify(scheduleAdditionMapper, never()).insert(any());
    }

    /**
     * 有餐数的待通知导入首单保留来源购买数与历史核销基数，并保持暂停和餐次留空。
     */
    @Test
    void writerShouldCreatePausedOrderWithoutMealTypeOrSubPackage() {
        ParentPackage parent = parentPackage();
        CustomerImportDraftDto draft = new CustomerImportDraftDto();
        draft.setCustomerCode("A004");
        draft.setParentPackageId(parent.getId());
        draft.setLunchDinnerCount(7);
        draft.setImportedVerifiedCount(3);
        draft.setPaused(true);
        ImportCandidate candidate = new ImportCandidate();
        ParsedCustomer parsed = parsedCustomer();
        parsed.setDeliveryPhoneInfo("13900139018、13700137018\n13600136018");
        candidate.setParsed(parsed);
        candidate.setDraft(draft);
        when(numberPoolMapper.selectForUpdate(parent.getId())).thenReturn(parent);
        doAnswer(invocation -> {
            CustomerProfile profile = invocation.getArgument(0);
            profile.setId(123L);
            return 1;
        }).when(profileMapper).insert(any(CustomerProfile.class));
        when(customerOrderService.createImportedFirstOrder(any(CustomerOrder.class))).thenReturn(456L);
        CustomerProfileImportWriter writer = new CustomerProfileImportWriter(profileMapper, addressMapper,
                scheduleAdditionMapper, numberPoolMapper, customerOrderService);

        CustomerImportItemResultDto result = writer.write(candidate, LocalDate.of(2026, 9, 25));

        ArgumentCaptor<CustomerProfile> profileCaptor = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(profileMapper).insert(profileCaptor.capture());
        assertEquals("13800138000", profileCaptor.getValue().getPhone());
        assertEquals("13900139018、13700137018\n13600136018", profileCaptor.getValue().getDeliveryPhoneInfo());
        ArgumentCaptor<CustomerOrder> orderCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(customerOrderService).createImportedFirstOrder(orderCaptor.capture());
        assertNull(orderCaptor.getValue().getChildPackageId());
        assertNull(orderCaptor.getValue().getMealType());
        assertEquals(Integer.valueOf(7), orderCaptor.getValue().getLunchDinnerCount());
        assertEquals(Integer.valueOf(3), orderCaptor.getValue().getImportedVerifiedCount());
        assertEquals(Integer.valueOf(3), orderCaptor.getValue().getVerifiedCount());
        assertEquals(Integer.valueOf(4), orderCaptor.getValue().getRemainingCount());
        assertEquals(LocalDate.of(2026, 9, 26), orderCaptor.getValue().getStartDate());
        assertEquals(Integer.valueOf(CustomerOrderStatus.PAUSED.getCode()), orderCaptor.getValue().getStatus());
        assertEquals(Long.valueOf(456L), result.getOrderId());
        assertEquals("CREATED", result.getStatus());
    }

    @Test
    void writerShouldKeepFullyHistoricalOrderAsCompleted() {
        ParentPackage parent = parentPackage();
        CustomerImportDraftDto draft = new CustomerImportDraftDto();
        draft.setCustomerCode("A004");
        draft.setParentPackageId(parent.getId());
        draft.setLunchDinnerCount(3);
        draft.setImportedVerifiedCount(3);
        draft.setMealType("LUNCH");
        ImportCandidate candidate = new ImportCandidate();
        candidate.setParsed(parsedCustomer());
        candidate.setDraft(draft);
        when(numberPoolMapper.selectForUpdate(parent.getId())).thenReturn(parent);
        doAnswer(invocation -> {
            CustomerProfile profile = invocation.getArgument(0);
            profile.setId(123L);
            return 1;
        }).when(profileMapper).insert(any(CustomerProfile.class));
        when(customerOrderService.createImportedFirstOrder(any(CustomerOrder.class))).thenReturn(456L);
        CustomerProfileImportWriter writer = new CustomerProfileImportWriter(profileMapper, addressMapper,
                scheduleAdditionMapper, numberPoolMapper, customerOrderService);

        writer.write(candidate, LocalDate.of(2026, 9, 25));

        ArgumentCaptor<CustomerOrder> orderCaptor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(customerOrderService).createImportedFirstOrder(orderCaptor.capture());
        assertEquals(Integer.valueOf(3), orderCaptor.getValue().getVerifiedCount());
        assertEquals(Integer.valueOf(0), orderCaptor.getValue().getRemainingCount());
        assertEquals(Integer.valueOf(CustomerOrderStatus.COMPLETED.getCode()), orderCaptor.getValue().getStatus());
    }

    private ParsedCustomer parsedCustomer() {
        ParsedCustomer parsed = new ParsedCustomer();
        parsed.setSourceRows(Collections.singletonList(4));
        parsed.setEffectiveCode("A004");
        parsed.setPhoneNormalized("13800138000");
        parsed.setSheetMealCount(0);
        parsed.setSheetRemainingCount(0);
        return parsed;
    }

    private ParentPackage parentPackage() {
        ParentPackage parent = new ParentPackage();
        parent.setId(1L);
        parent.setStatus(true);
        parent.setPackageName("月子餐");
        parent.setPoolPrefix("A");
        parent.setPoolStart(1);
        parent.setPoolEnd(999);
        return parent;
    }
}
