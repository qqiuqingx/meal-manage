package me.zhengjie.modules.customer.profile.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import me.zhengjie.modules.customer.profile.domain.CustomerDietImportData;
import me.zhengjie.modules.customer.profile.domain.CustomerDietSourceRow;
import me.zhengjie.modules.customer.profile.domain.ImportCandidate;
import me.zhengjie.modules.customer.profile.domain.ParsedCustomer;
import me.zhengjie.modules.customer.profile.domain.ParsedWorkbook;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietMatchDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerDietMatchServiceTest {

    private final CustomerDietMatchService service = new CustomerDietMatchService();

    @Test
    void shouldKeepDirectionFromColumnAndApplyAllCrossTypeCandidates() {
        CustomerDietSourceRow row = sourceRow("A100", "不吃香菜，芹菜", "喜欢吃牛肉");
        Fixture fixture = fixture(row);

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);

        CustomerDietImportData data = fixture.parsed.getDietImportData();
        assertEquals(3, data.getMatches().size());
        CustomerDietMatchDto multi = data.getMatches().get(0);
        assertEquals("DIET:4:4:0", multi.getSourceKey());
        assertEquals("DISH_REQUIREMENTS", multi.getSide());
        assertEquals("不吃香菜", multi.getRawText());
        assertEquals("香菜", multi.getLookupText());
        assertEquals("MULTI", multi.getStatus());
        assertEquals(2, multi.getSelectedItems().size());
        assertEquals("DIETARY_RESTRICTIONS", data.getMatches().get(2).getSide());
        assertEquals("UNIQUE", data.getMatches().get(2).getStatus());
        assertEquals("不吃香菜，芹菜", data.getDishRequirementsRaw().get(0));

        service.applyAllMatches(fixture.candidates);

        assertEquals(3, data.getDishRequirements().size());
        assertEquals("香菜", data.getDishRequirements().get(0).getName());
        assertEquals("香菜", data.getDishRequirements().get(1).getName());
        assertEquals("芹菜", data.getDishRequirements().get(2).getName());
        assertEquals("牛肉", data.getDietaryRestrictions().get(0).getName());
    }

    @Test
    void shouldSerializeRepeatedCandidatesAsCompleteOptions() {
        Fixture fixture = fixture(sourceRow("A100", "香菜，香菜", null));
        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);

        String json = JSON.toJSONString(fixture.parsed.getDietImportData(), JSONWriter.Feature.ReferenceDetection);
        assertFalse(json.contains("\"$ref\""), json);
        com.alibaba.fastjson2.JSONArray matches = JSON.parseObject(json).getJSONArray("matches");

        assertEquals("DISH", matches.getJSONObject(1).getJSONArray("candidates")
                .getJSONObject(0).getString("type"));
        assertEquals(Long.valueOf(1L), matches.getJSONObject(1).getJSONArray("candidates")
                .getJSONObject(0).getLong("id"));

        service.applyAllMatches(fixture.candidates);
        assertEquals(2, fixture.parsed.getDietImportData().getDishRequirements().size());
    }

    @Test
    void shouldApplyAllSameNameObjectsAndKeepRawText() {
        Fixture fixture = fixture(sourceRow("A100", "香菜", null));
        fixture.options.add(option("INGREDIENT_TAG", 99L, "香菜"));
        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);
        CustomerDietImportData data = fixture.parsed.getDietImportData();

        service.applyAllMatches(fixture.candidates);

        assertEquals("MULTI", data.getMatches().get(0).getStatus());
        assertEquals(3, data.getDishRequirements().size());
        assertEquals(Arrays.asList("DISH", "INGREDIENT", "INGREDIENT_TAG"),
                data.getDishRequirements().stream().map(CustomerDietItemDto::getType)
                        .collect(java.util.stream.Collectors.toList()));
        assertEquals(Collections.singletonList("香菜"), data.getDishRequirementsRaw());
    }

    @Test
    void shouldLeaveUnmatchedTextAsNonBlockingRawSource() {
        Fixture fixture = fixture(sourceRow("A100", "最近不太想吃有味道的东西", null));
        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);
        CustomerDietImportData data = fixture.parsed.getDietImportData();

        assertTrue(fixture.parsed.isImportable());
        assertEquals("UNMATCHED", data.getMatches().get(0).getStatus());
        assertEquals(Collections.singletonList("最近不太想吃有味道的东西"), data.getDishRequirementsRaw());
        service.applyAllMatches(fixture.candidates);
        assertTrue(data.getDishRequirements().isEmpty());
    }

    @Test
    void shouldNotFallBackToOldCodeWhenNewCodeDoesNotMatch() {
        CustomerDietSourceRow row = sourceRow("A999", "旧编号内容", null);
        row.setOriginalCodeA("A100");
        row.setOriginalCodeB("A999");
        Fixture fixture = fixture(row);

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);

        assertTrue(fixture.parsed.getDietImportData() == null);
        assertEquals(1, fixture.workbook.getIssues().size());
        assertTrue(fixture.workbook.getIssues().get(0).getMessage().contains("未关联"));
    }

    @Test
    void shouldBlockConflictingMedicalPostoperativeAndDealTimeRows() {
        CustomerDietSourceRow first = sourceRow("A100", null, null);
        first.setMedicalRequirements("少盐");
        first.setPostoperativeInfo("5天");
        first.setDealTime(LocalDateTime.of(2026, 6, 11, 0, 0));
        first.setDealTimeSource("6.11");
        CustomerDietSourceRow second = sourceRow("A100", null, null);
        second.setSourceRow(5);
        second.setMedicalRequirements("少油");
        second.setPostoperativeInfo("4个月");
        second.setDealTime(LocalDateTime.of(2026, 6, 12, 0, 0));
        second.setDealTimeSource("6.12");
        Fixture fixture = fixture(first);
        fixture.workbook.setDietRows(Arrays.asList(first, second));

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);

        assertEquals(3, fixture.parsed.getIssues().size());
        assertTrue(fixture.parsed.getIssues().stream().anyMatch(issue -> issue.getMessage().contains("医嘱内容不同")));
        assertTrue(fixture.parsed.getIssues().stream().anyMatch(issue -> issue.getMessage().contains("术后内容不同")));
        assertTrue(fixture.parsed.getIssues().stream().anyMatch(issue -> issue.getMessage().contains("成交时间不同")));
    }

    @Test
    void shouldNotOverwriteExistingMedicalOrPostoperativeText() {
        CustomerDietSourceRow row = sourceRow("A100", null, null);
        row.setMedicalRequirements("不同医嘱");
        row.setPostoperativeInfo("不同术后信息");
        Fixture fixture = fixture(row);
        me.zhengjie.modules.customer.profile.domain.CustomerProfile existing =
                new me.zhengjie.modules.customer.profile.domain.CustomerProfile();
        existing.setMedicalRequirements("已有医嘱");
        existing.setPostoperativeInfo("已有术后信息");
        fixture.candidates.get(0).setExistingProfile(existing);

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);

        assertEquals(2, fixture.parsed.getIssues().size());
        assertTrue(fixture.parsed.getIssues().stream().anyMatch(issue -> issue.getMessage().contains("已有客户医嘱")));
        assertTrue(fixture.parsed.getIssues().stream().anyMatch(issue -> issue.getMessage().contains("已有客户术后信息")));
    }

    private Fixture fixture(CustomerDietSourceRow source) {
        ParsedCustomer parsed = new ParsedCustomer();
        parsed.setEffectiveCode("A100");
        ImportCandidate candidate = new ImportCandidate();
        candidate.setParsed(parsed);
        ParsedWorkbook workbook = new ParsedWorkbook();
        workbook.setDietRows(Collections.singletonList(source));
        List<CustomerDietOptionDto> options = Arrays.asList(
                option("DISH", 1L, "香菜"),
                option("INGREDIENT", 2L, "香菜"),
                option("INGREDIENT_CATEGORY", 3L, "芹菜"),
                option("INGREDIENT_CATEGORY", 4L, "牛肉"));
        Fixture fixture = new Fixture();
        fixture.parsed = parsed;
        fixture.workbook = workbook;
        fixture.candidates = Collections.singletonList(candidate);
        fixture.options = new java.util.ArrayList<>(options);
        return fixture;
    }

    private CustomerDietSourceRow sourceRow(String effectiveCode, String wants, String restrictions) {
        CustomerDietSourceRow row = new CustomerDietSourceRow();
        row.setSourceRow(4);
        row.setEffectiveCode(effectiveCode);
        row.setDishRequirementsRaw(wants);
        row.setDietaryRestrictionsRaw(restrictions);
        return row;
    }

    private CustomerDietOptionDto option(String type, long id, String name) {
        CustomerDietOptionDto option = new CustomerDietOptionDto();
        option.setType(type);
        option.setId(id);
        option.setName(name);
        return option;
    }

    private static class Fixture {
        private ParsedCustomer parsed;
        private ParsedWorkbook workbook;
        private List<ImportCandidate> candidates;
        private List<CustomerDietOptionDto> options;
    }
}
