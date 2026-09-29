package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerDietSourceRow;
import me.zhengjie.modules.customer.profile.domain.ImportCandidate;
import me.zhengjie.modules.customer.profile.domain.ParsedCustomer;
import me.zhengjie.modules.customer.profile.domain.ParsedWorkbook;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietSelectionDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportItemResultDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportPreviewDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerImportResultDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerDietDictionaryService;
import me.zhengjie.modules.customer.profile.service.CustomerDietMatchService;
import me.zhengjie.modules.customer.profile.service.CustomerOrderImportParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerProfileImportDietFlowTest {

    @Mock private CustomerOrderImportParser parser;
    @Mock private CustomerProfileMapper profileMapper;
    @Mock private ParentPackageMapper parentPackageMapper;
    @Mock private CustomerProfileImportWriter importWriter;
    @Mock private CustomerDietDictionaryService dictionaryService;

    private CustomerProfileImportServiceImpl importService;
    private ParsedWorkbook workbook;

    @BeforeEach
    void setUp() {
        importService = new CustomerProfileImportServiceImpl(parser, profileMapper, parentPackageMapper,
                importWriter, dictionaryService, new CustomerDietMatchService());

        ParsedCustomer customer = new ParsedCustomer();
        customer.setSourceRows(Collections.singletonList(4));
        customer.setOriginalCodeA("A004");
        customer.setEffectiveCode("A004");
        customer.setPhoneNormalized("13800138000");
        customer.setSheetMealCount(0);
        CustomerDietSourceRow dietRow = new CustomerDietSourceRow();
        dietRow.setSourceRow(4);
        dietRow.setOriginalCodeA("A004");
        dietRow.setEffectiveCode("A004");
        dietRow.setDishRequirementsRaw("香菜，牛肉");

        workbook = new ParsedWorkbook();
        workbook.setFileHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        workbook.setStructureValid(true);
        workbook.setDietSheetPresent(true);
        workbook.setCalendarMonthStart(LocalDate.of(2026, 9, 1));
        workbook.setCustomerCount(1);
        workbook.setCustomers(Collections.singletonList(customer));
        workbook.setDietRows(Collections.singletonList(dietRow));
        when(parser.parse(any(byte[].class), any(String.class), any(LocalDate.class))).thenReturn(workbook);
        when(parentPackageMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(parentPackage()));
        lenient().when(profileMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(dictionaryService.listActiveOptions()).thenReturn(Arrays.asList(
                option("DISH", 1L, "香菜"),
                option("INGREDIENT", 2L, "香菜"),
                option("INGREDIENT_CATEGORY", 3L, "牛肉")));
    }

    @Test
    void shouldPreviewAmbiguityAndRejectInvalidSelectionBeforeAnyWrite() {
        CustomerImportPreviewDto preview = importService.preview(
                new byte[]{1}, "anonymous.xlsx", LocalDate.of(2026, 9, 25));
        assertNotNull(preview.getDictionaryHash());
        assertEquals(1, preview.getImportableCount());
        assertEquals(Boolean.FALSE, preview.getDrafts().get(0).getImportable());
        assertEquals("AMBIGUOUS", preview.getDrafts().get(0).getDietMatches().get(0).getStatus());
        assertEquals("UNIQUE", preview.getDrafts().get(0).getDietMatches().get(1).getStatus());

        assertThrows(BadRequestException.class, () -> importService.importCustomers(
                new byte[]{1}, "anonymous.xlsx", preview.getFileHash(), "stale-dictionary-hash",
                Collections.emptyList(), LocalDate.of(2026, 9, 25)));

        CustomerDietSelectionDto invalid = selection("DIET:4:4:0", "DISH_TAG", 999L);
        assertThrows(BadRequestException.class, () -> importService.importCustomers(
                new byte[]{1}, "anonymous.xlsx", preview.getFileHash(), preview.getDictionaryHash(),
                Collections.singletonList(invalid), LocalDate.of(2026, 9, 25)));

        verify(importWriter, never()).write(any(ImportCandidate.class), any(LocalDate.class), any());
    }

    @Test
    void shouldPassConfirmedSelectionsAndRawBlocksToWriter() {
        CustomerImportPreviewDto preview = importService.preview(
                new byte[]{1}, "anonymous.xlsx", LocalDate.of(2026, 9, 25));
        CustomerDietSelectionDto selected = selection("DIET:4:4:0", "INGREDIENT", 2L);
        CustomerImportItemResultDto created = new CustomerImportItemResultDto();
        created.setStatus("CREATED");
        created.setCustomerCode("A004");
        created.setSourceRows(Collections.singletonList(4));
        when(importWriter.write(any(ImportCandidate.class), any(LocalDate.class), any())).thenReturn(created);

        CustomerImportResultDto result = importService.importCustomers(
                new byte[]{1}, "anonymous.xlsx", preview.getFileHash(), preview.getDictionaryHash(),
                Collections.singletonList(selected), LocalDate.of(2026, 9, 25));

        assertEquals(1, result.getCreatedCount());
        ArgumentCaptor<ImportCandidate> candidate = ArgumentCaptor.forClass(ImportCandidate.class);
        verify(importWriter).write(candidate.capture(), any(LocalDate.class), any());
        assertEquals("香菜", candidate.getValue().getParsed().getDietImportData()
                .getDishRequirements().get(0).getName());
        assertEquals("香菜，牛肉", candidate.getValue().getParsed().getDietImportData()
                .getDishRequirementsRaw().get(0));
        assertEquals("SELECTED", candidate.getValue().getParsed().getDietImportData()
                .getMatches().get(0).getStatus());
    }

    @Test
    void shouldCountExistingSameIdentityAsSupplementalAndReturnUpdatedResult() {
        me.zhengjie.modules.customer.profile.domain.CustomerProfile existing =
                new me.zhengjie.modules.customer.profile.domain.CustomerProfile();
        existing.setId(90L);
        existing.setCustomerCode("A004");
        existing.setPhone("13800138000");
        when(profileMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(existing));
        workbook.getDietRows().get(0).setMedicalRequirements("少盐");
        CustomerImportPreviewDto preview = importService.preview(
                new byte[]{1}, "anonymous.xlsx", LocalDate.of(2026, 9, 25));
        assertEquals(1, preview.getSupplementalCount());

        CustomerDietSelectionDto selected = selection("DIET:4:4:0", "INGREDIENT", 2L);
        CustomerImportItemResultDto updated = new CustomerImportItemResultDto();
        updated.setStatus("UPDATED");
        updated.setCustomerCode("A004");
        updated.setSourceRows(Collections.singletonList(4));
        when(importWriter.write(any(ImportCandidate.class), any(LocalDate.class), any())).thenReturn(updated);

        CustomerImportResultDto result = importService.importCustomers(
                new byte[]{1}, "anonymous.xlsx", preview.getFileHash(), preview.getDictionaryHash(),
                Collections.singletonList(selected), LocalDate.of(2026, 9, 25));

        assertEquals(0, result.getCreatedCount());
        assertEquals(1, result.getUpdatedCount());
        ArgumentCaptor<ImportCandidate> candidate = ArgumentCaptor.forClass(ImportCandidate.class);
        verify(importWriter).write(candidate.capture(), any(LocalDate.class), any());
        assertTrue(candidate.getValue().isSupplemental());
        assertEquals("少盐", candidate.getValue().getParsed().getDietImportData().getMedicalRequirements());
    }

    private CustomerDietSelectionDto selection(String sourceKey, String type, Long id) {
        CustomerDietSelectionDto selection = new CustomerDietSelectionDto();
        selection.setSourceKey(sourceKey);
        selection.setAction("SELECT");
        selection.setType(type);
        selection.setId(id);
        return selection;
    }

    private CustomerDietOptionDto option(String type, Long id, String name) {
        CustomerDietOptionDto option = new CustomerDietOptionDto();
        option.setType(type);
        option.setId(id);
        option.setName(name);
        return option;
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
