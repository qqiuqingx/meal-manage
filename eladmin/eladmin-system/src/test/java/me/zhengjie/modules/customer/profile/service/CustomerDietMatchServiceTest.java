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
    void shouldTraceRestrictionsToColumnF() {
        Fixture fixture = fixture(sourceRow("A100", "香菜", "牛肉"));

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);

        List<CustomerDietMatchDto> matches = fixture.parsed.getDietImportData().getMatches();
        assertEquals(1, matches.size());
        assertEquals(Integer.valueOf(6), matches.get(0).getSourceColumn());
        assertEquals("DIET:4:6:0", matches.get(0).getSourceKey());
        assertEquals("DIETARY_RESTRICTIONS", matches.get(0).getSide());
    }

    @Test
    void shouldKeepRequirementsAsRawAndApplyAllRestrictionCandidates() {
        CustomerDietSourceRow row = sourceRow("A100", "不吃香菜，芹菜", "喜欢吃香菜，牛肉");
        Fixture fixture = fixture(row);

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);

        CustomerDietImportData data = fixture.parsed.getDietImportData();
        assertEquals(2, data.getMatches().size());
        CustomerDietMatchDto multi = data.getMatches().get(0);
        assertEquals("DIET:4:6:0", multi.getSourceKey());
        assertEquals("DIETARY_RESTRICTIONS", multi.getSide());
        assertEquals("喜欢吃香菜", multi.getRawText());
        assertEquals("香菜", multi.getLookupText());
        assertEquals("MULTI", multi.getStatus());
        assertEquals(2, multi.getSelectedItems().size());
        assertEquals("DIETARY_RESTRICTIONS", data.getMatches().get(1).getSide());
        assertEquals("UNIQUE", data.getMatches().get(1).getStatus());
        assertEquals("不吃香菜，芹菜", data.getDishRequirementsRaw().get(0));

        service.applyAllMatches(fixture.candidates);

        assertTrue(data.getDishRequirements().isEmpty());
        assertEquals(Arrays.asList("香菜", "香菜", "牛肉"), data.getDietaryRestrictions().stream()
                .map(CustomerDietItemDto::getName).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void shouldSerializeRepeatedCandidatesAsCompleteOptions() {
        Fixture fixture = fixture(sourceRow("A100", null, "香菜，香菜"));
        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);

        String json = JSON.toJSONString(fixture.parsed.getDietImportData(), JSONWriter.Feature.ReferenceDetection);
        assertFalse(json.contains("\"$ref\""), json);
        com.alibaba.fastjson2.JSONArray matches = JSON.parseObject(json).getJSONArray("matches");

        assertEquals("DISH", matches.getJSONObject(1).getJSONArray("candidates")
                .getJSONObject(0).getString("type"));
        assertEquals(Long.valueOf(1L), matches.getJSONObject(1).getJSONArray("candidates")
                .getJSONObject(0).getLong("id"));

        service.applyAllMatches(fixture.candidates);
        assertEquals(2, fixture.parsed.getDietImportData().getDietaryRestrictions().size());
    }

    @Test
    void shouldApplyAllSameNameObjectsAndKeepRawText() {
        Fixture fixture = fixture(sourceRow("A100", null, "香菜"));
        fixture.options.add(option("INGREDIENT_TAG", 99L, "香菜"));
        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);
        CustomerDietImportData data = fixture.parsed.getDietImportData();

        service.applyAllMatches(fixture.candidates);

        assertEquals("MULTI", data.getMatches().get(0).getStatus());
        assertEquals(3, data.getDietaryRestrictions().size());
        assertEquals(Arrays.asList("DISH", "INGREDIENT", "INGREDIENT_TAG"),
                data.getDietaryRestrictions().stream().map(CustomerDietItemDto::getType)
                        .collect(java.util.stream.Collectors.toList()));
        assertEquals(Collections.singletonList("香菜"), data.getDietaryRestrictionsRaw());
    }

    @Test
    void shouldSaveRequirementsVerbatimWithoutMatchingEvenKnownFoods() {
        String raw = " 香菜，芹菜\r\n最近不太想吃有味道的东西 ";
        Fixture fixture = fixture(sourceRow("A100", raw, null));
        CustomerDietSourceRow repeated = sourceRow("A100", raw, null);
        repeated.setSourceRow(5);
        fixture.workbook.setDietRows(Arrays.asList(fixture.workbook.getDietRows().get(0), repeated));
        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);
        CustomerDietImportData data = fixture.parsed.getDietImportData();

        assertTrue(fixture.parsed.isImportable());
        assertTrue(data.getMatches().isEmpty());
        assertEquals(Collections.singletonList(raw), data.getDishRequirementsRaw());
        service.applyAllMatches(fixture.candidates);
        assertTrue(data.getDishRequirements().isEmpty());
    }

    @Test
    void shouldMatchAdjacentFoodsInAllergyListAndKeepFullSource() {
        String raw = "干净，卫生，新鲜，煮熟煮透[偷笑]，吃了千万不要拉肚子[偷笑]！少油，味道合适\r\n过敏食物：芒果菠萝";
        Fixture fixture = fixture(sourceRow("A100", null, raw));
        fixture.options.add(option("INGREDIENT", 501L, "芒果"));
        fixture.options.add(option("INGREDIENT", 502L, "菠萝"));
        fixture.options.add(option("DISH", 503L, "芒果"));

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);
        service.applyAllMatches(fixture.candidates);

        CustomerDietImportData data = fixture.parsed.getDietImportData();
        assertEquals(Collections.singletonList(raw), data.getDietaryRestrictionsRaw());
        assertEquals(Arrays.asList("芒果", "芒果", "菠萝"), data.getDietaryRestrictions().stream()
                .map(CustomerDietItemDto::getName).collect(java.util.stream.Collectors.toList()));
        CustomerDietMatchDto mango = data.getMatches().stream()
                .filter(match -> "芒果".equals(match.getLookupText())).findFirst().get();
        assertEquals("MULTI", mango.getStatus());
        assertEquals(raw, mango.getCellText());
    }

    @Test
    void shouldKeepUnknownFoodInExplicitAllergyList() {
        Fixture fixture = fixture(sourceRow("A100", null, "过敏食物：芒果菠萝"));
        fixture.options.add(option("INGREDIENT", 501L, "芒果"));

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);
        service.applyAllMatches(fixture.candidates);

        CustomerDietImportData data = fixture.parsed.getDietImportData();
        assertEquals(Arrays.asList("芒果", "菠萝"), data.getMatches().stream()
                .map(CustomerDietMatchDto::getLookupText).collect(java.util.stream.Collectors.toList()));
        assertEquals("UNMATCHED", data.getMatches().get(1).getStatus());
        assertEquals(1, data.getDietaryRestrictions().size());
        assertEquals("芒果", data.getDietaryRestrictions().get(0).getName());
    }

    @Test
    void shouldRecognizeAdjacentFoodsWithoutMatchingInsideCompoundWords() {
        Fixture fixture = fixture(sourceRow("A100", null, "香菜芹菜，牛肉丸"));

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);
        service.applyAllMatches(fixture.candidates);

        CustomerDietImportData data = fixture.parsed.getDietImportData();
        assertTrue(data.getDishRequirements().isEmpty());
        assertEquals(3, data.getDietaryRestrictions().size());
        assertEquals("牛肉丸", data.getMatches().get(2).getLookupText());
        assertEquals("UNMATCHED", data.getMatches().get(2).getStatus());
    }

    @Test
    void shouldPreferCompleteDictionaryNameOverItsParts() {
        Fixture fixture = fixture(sourceRow("A100", null, "过敏食物：芒果菠萝"));
        fixture.options.add(option("INGREDIENT", 501L, "芒果"));
        fixture.options.add(option("INGREDIENT", 502L, "菠萝"));
        fixture.options.add(option("DISH", 503L, "芒果菠萝"));

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);

        List<CustomerDietMatchDto> matches = fixture.parsed.getDietImportData().getMatches();
        assertEquals(1, matches.size());
        assertEquals("芒果菠萝", matches.get(0).getLookupText());
        assertEquals("DISH", matches.get(0).getSelectedItems().get(0).getType());
    }

    @Test
    void shouldProtectCustomFoodNamesDuringSegmentation() {
        Fixture fixture = fixture(sourceRow("A100", null, "不吃三月瓜芒果"));
        fixture.options.add(option("INGREDIENT", 501L, "三月瓜"));
        fixture.options.add(option("INGREDIENT", 502L, "芒果"));

        service.attachDietRows(fixture.workbook, fixture.candidates, fixture.options);
        service.applyAllMatches(fixture.candidates);

        assertEquals(Arrays.asList("三月瓜", "芒果"), fixture.parsed.getDietImportData()
                .getDietaryRestrictions().stream().map(CustomerDietItemDto::getName)
                .collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void shouldNotReuseAnotherImportsDictionary() {
        Fixture first = fixture(sourceRow("A100", null, "芒果菠萝"));
        first.options.add(option("INGREDIENT", 501L, "芒果"));
        service.attachDietRows(first.workbook, first.candidates, first.options);
        service.applyAllMatches(first.candidates);
        assertEquals(1, first.parsed.getDietImportData().getDietaryRestrictions().size());

        Fixture second = fixture(sourceRow("A100", null, "芒果菠萝"));
        service.attachDietRows(second.workbook, second.candidates, second.options);
        service.applyAllMatches(second.candidates);
        assertTrue(second.parsed.getDietImportData().getDietaryRestrictions().isEmpty());
        assertEquals("芒果菠萝", second.parsed.getDietImportData().getMatches().get(0).getLookupText());
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
