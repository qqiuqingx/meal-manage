package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import me.zhengjie.modules.customer.numberpool.mapper.NumberPoolMapper;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerDietSourceRow;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.ImportCandidate;
import me.zhengjie.modules.customer.profile.domain.ParsedCustomer;
import me.zhengjie.modules.customer.profile.domain.ParsedWorkbook;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportItemResultDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportDraftDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportPreviewDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportResultDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerDietDictionaryService;
import me.zhengjie.modules.customer.profile.service.CustomerDietMatchService;
import me.zhengjie.modules.customer.profile.service.CustomerOrderImportParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerProfileImportDietOnlyTest {

    @Mock private CustomerOrderMonthlyImportService monthlyImportService;
    @Mock private CustomerOrderImportParser parser;
    @Mock private CustomerProfileMapper profileMapper;
    @Mock private ParentPackageMapper parentPackageMapper;
    @Mock private CustomerProfileImportWriter importWriter;
    @Mock private CustomerDietDictionaryService dictionaryService;
    @Mock private CustomerProfileAddressMapper addressMapper;
    @Mock private CustomerMealScheduleAdditionMapper scheduleAdditionMapper;
    @Mock private NumberPoolMapper numberPoolMapper;
    @Mock private CustomerOrderService orderService;

    @Test
    void shouldPreviewAndWriteOnlyExistingSecondSheetCustomers() {
        ParsedWorkbook workbook = workbook();
        when(parser.parseDietOnly(any(byte[].class), anyString(), any(LocalDate.class))).thenReturn(workbook);
        CustomerProfile existing = new CustomerProfile();
        existing.setId(10L);
        existing.setCustomerCode("A100");
        existing.setPhone("13800138100");
        when(profileMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(existing));
        when(dictionaryService.listActiveOptions()).thenReturn(Arrays.asList(
                option("DISH", 1L, "海鲜"), option("INGREDIENT", 2L, "海鲜")));
        CustomerProfileImportServiceImpl service = new CustomerProfileImportServiceImpl(
                parser, profileMapper, parentPackageMapper, importWriter, dictionaryService, new CustomerDietMatchService(), monthlyImportService);

        CustomerImportPreviewDto preview = service.previewDietOnly(new byte[]{1}, "sample.xlsx", LocalDate.of(2026, 9, 29));

        assertEquals("客户禁忌", preview.getSheetName());
        assertEquals(1, preview.getImportableCount());
        assertEquals("MULTI", preview.getDrafts().get(0).getDietMatches().get(0).getStatus());
        assertEquals(2, preview.getDrafts().get(0).getDietMatches().get(0).getSelectedItems().size());
        assertEquals(1, preview.getErrorCount());
        assertEquals(Arrays.asList(4, 5), preview.getDrafts().get(0).getSourceRows());
        assertEquals("138****8100", preview.getDrafts().get(0).getPhoneMasked());
        assertTrue(preview.getDrafts().get(1).getErrors().get(0).contains("未找到"));

        CustomerImportItemResultDto updated = new CustomerImportItemResultDto();
        updated.setStatus("UPDATED");
        updated.setCustomerCode("A100");
        updated.setSourceRows(Arrays.asList(4, 5));
        when(importWriter.writeDietOnly(any(ImportCandidate.class))).thenReturn(updated);
        CustomerImportResultDto result = service.importDietOnly(new byte[]{1}, "sample.xlsx",
                preview.getFileHash(), preview.getDictionaryHash(), LocalDate.of(2026, 9, 29));

        assertEquals(1, result.getUpdatedCount());
        assertEquals(0, result.getCreatedCount());
        assertEquals(1, result.getSkippedCount());
        ArgumentCaptor<ImportCandidate> candidate = ArgumentCaptor.forClass(ImportCandidate.class);
        verify(importWriter).writeDietOnly(candidate.capture());
        assertEquals(Arrays.asList("海鲜", "海鲜"), candidate.getValue().getParsed().getDietImportData()
                .getDishRequirements().stream().map(item -> item.getName()).collect(java.util.stream.Collectors.toList()));
        verify(importWriter, never()).write(any(ImportCandidate.class), any(LocalDate.class), any());
        verify(parentPackageMapper, never()).selectList(any(QueryWrapper.class));
    }

    @Test
    void writerShouldOnlyCheckCodeAndNeverCreateOrderInDietOnlyMode() {
        CustomerProfileImportWriter writer = new CustomerProfileImportWriter(profileMapper, addressMapper,
                scheduleAdditionMapper, numberPoolMapper, orderService, monthlyImportService);
        CustomerProfile profile = new CustomerProfile();
        profile.setId(10L);
        profile.setCustomerCode("A100");
        when(profileMapper.selectByIdForImportUpdate(10L)).thenReturn(profile);
        ImportCandidate candidate = new ImportCandidate();
        candidate.setSourceMonth(LocalDate.of(2026, 9, 1));
        candidate.setSupplemental(true);
        candidate.setExistingProfile(profile);
        ParsedCustomer parsed = new ParsedCustomer();
        parsed.setEffectiveCode("A100");
        parsed.setSourceRows(Collections.singletonList(4));
        me.zhengjie.modules.customer.profile.domain.CustomerDietImportData data =
                new me.zhengjie.modules.customer.profile.domain.CustomerDietImportData();
        data.setMedicalRequirements("少盐");
        parsed.setDietImportData(data);
        candidate.setParsed(parsed);
        CustomerImportDraftDto draft = new CustomerImportDraftDto();
        draft.setCustomerCode("A100");
        candidate.setDraft(draft);

        CustomerImportItemResultDto result = writer.writeDietOnly(candidate);

        assertEquals("UPDATED", result.getStatus());
        assertEquals("少盐", profile.getMedicalRequirements());
        verify(profileMapper).updateById(profile);
        verify(profileMapper, never()).insert(any(CustomerProfile.class));
        verify(orderService, never()).createImportedFirstOrder(any());
    }

    private ParsedWorkbook workbook() {
        ParsedWorkbook workbook = new ParsedWorkbook();
        workbook.setFileHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        workbook.setStructureValid(true);
        workbook.setCalendarMonthStart(LocalDate.of(2026, 9, 1));
        workbook.setImportDate(LocalDate.of(2026, 9, 25));
        workbook.setDietSheetPresent(true);
        workbook.setSheetName("客户禁忌");
        workbook.setCalendarMonthStart(LocalDate.of(2026, 9, 1));
        workbook.setCustomers(Arrays.asList(customer("A100", 4, 5), customer("B200", 6)));
        workbook.setCustomerCount(2);
        workbook.setDataRowCount(3);
        CustomerDietSourceRow first = dietRow("A100", 4);
        first.setDishRequirementsRaw("海鲜");
        workbook.setDietRows(Arrays.asList(first, dietRow("A100", 5), dietRow("B200", 6)));
        return workbook;
    }

    private ParsedCustomer customer(String code, Integer... rows) {
        ParsedCustomer customer = new ParsedCustomer();
        customer.setEffectiveCode(code);
        customer.setSourceRows(Arrays.asList(rows));
        customer.setSheetMealCount(0);
        customer.setSheetRemainingCount(0);
        return customer;
    }

    private CustomerDietSourceRow dietRow(String code, int row) {
        CustomerDietSourceRow source = new CustomerDietSourceRow();
        source.setEffectiveCode(code);
        source.setSourceRow(row);
        source.setMedicalRequirements("少盐");
        return source;
    }

    private CustomerDietOptionDto option(String type, Long id, String name) {
        CustomerDietOptionDto option = new CustomerDietOptionDto();
        option.setType(type);
        option.setId(id);
        option.setName(name);
        return option;
    }
}
