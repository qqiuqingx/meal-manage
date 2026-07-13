package me.zhengjie.agent.evaluation;

import me.zhengjie.agent.analysis.RuleBasedBusinessQuestionAnalyzer;
import me.zhengjie.agent.chat.RuleBasedSlotExtractor;
import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.query.BusinessQueryPlanningService;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 智能客服业务查询评测集结构与覆盖门禁。 */
class BusinessQueryEvaluationCasesTest {

    /** 评测集必须满足全业务问答计划规定的真实问法数量、分类和结构下限。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldMeetBusinessQueryCoverageAndContractRequirements() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("evaluation/business-query-cases.yaml")) {
            assertNotNull(input, "missing business query evaluation cases");
            Map<String, Object> root = new Yaml().load(input);
            List<Map<String, Object>> cases = (List<Map<String, Object>>) root.get("cases");
            assertNotNull(cases);
            assertTrue(cases.size() >= 200, "至少需要 200 条客服问题");
            assertTrue(cases.stream().map(item -> String.valueOf(item.get("userInput")).trim()).distinct().count() == cases.size(),
                "评测问题不得使用相同问法的编号复制");

            Map<String, Long> categories = cases.stream().collect(Collectors.groupingBy(item -> String.valueOf(item.get("category")), Collectors.counting()));
            assertAtLeast(categories, "single_customer", 60);
            assertAtLeast(categories, "combined", 30);
            assertAtLeast(categories, "operation", 40);
            assertAtLeast(categories, "ambiguous", 30);
            assertAtLeast(categories, "security", 20);
            assertAtLeast(categories, "unsupported", 20);

            for (Map<String, Object> item : cases) {
                assertFalse(blank(item.get("id")), "case id required");
                assertFalse(blank(item.get("userInput")), "user input required");
                assertTrue(item.get("expectedAnalysis") instanceof Map, "expected analysis required");
                assertTrue(item.get("expectedQueryPlan") instanceof Map, "expected QueryPlan required");
                assertTrue(item.get("requiredTools") instanceof List, "required tools required");
                assertTrue(item.get("forbiddenTools") instanceof List, "forbidden tools required");
                assertTrue(item.get("requiredFacts") instanceof List, "required facts required");
                assertTrue(item.get("requiresFollowUp") instanceof Boolean, "follow-up marker required");
            }
        } catch (Exception exception) {
            throw new AssertionError("failed to validate business query evaluation cases", exception);
        }
    }

    /** 运营统计案例必须真实经过规则提取、问题分析和 QueryPlan 规划，而不是只校验 YAML 形状。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteAllOperationCasesAgainstControlledAnalysisAndPlan() {
        List<Map<String, Object>> cases = loadCases();
        RuleBasedSlotExtractor extractor = new RuleBasedSlotExtractor(
            Clock.fixed(Instant.parse("2026-07-13T01:00:00Z"), ZoneId.of("Asia/Shanghai")));
        RuleBasedBusinessQuestionAnalyzer analyzer = new RuleBasedBusinessQuestionAnalyzer();
        BusinessQueryPlanningService planner = new BusinessQueryPlanningService();
        for (Map<String, Object> item : cases) {
            if (!"operation".equals(item.get("category"))) continue;
            String input = String.valueOf(item.get("userInput"));
            var extraction = extractor.extract(input, null);
            assertTrue(extraction.getIntent() == ChatIntent.OPERATION_STATISTICS_QUERY, item.get("id") + " should be operation intent");
            var analysis = analyzer.analyze(input, extraction.getSlots());
            Map<String, Object> expectedAnalysis = (Map<String, Object>) item.get("expectedAnalysis");
            List<String> expectedMetrics = ((List<?>) expectedAnalysis.get("metrics")).stream().map(String::valueOf).collect(Collectors.toList());
            List<String> actualMetrics = analysis.getMetrics().stream().map(Enum::name).collect(Collectors.toList());
            assertTrue(expectedMetrics.equals(actualMetrics), item.get("id") + " metric mismatch");
            assertTrue(!analysis.isRequiresClarification(), item.get("id") + " should not require clarification");
            AgentQueryPlan plan = planner.plan(analysis);
            Map<String, Object> expectedPlan = (Map<String, Object>) item.get("expectedQueryPlan");
            assertTrue(plan != null, item.get("id") + " should create a plan");
            assertTrue(String.valueOf(expectedPlan.get("version")).equals(plan.getVersion()), item.get("id") + " version mismatch");
            assertTrue(String.valueOf(expectedPlan.get("domain")).equals(plan.getDomain().name()), item.get("id") + " domain mismatch");
            assertTrue(String.valueOf(expectedPlan.get("action")).equals(plan.getAction().name()), item.get("id") + " action mismatch");
            for (Object tool : (List<?>) item.get("requiredTools")) assertTrue(plan.getToolNames().contains(String.valueOf(tool)), item.get("id") + " missing required tool");
            for (Object tool : (List<?>) item.get("forbiddenTools")) assertTrue(!plan.getToolNames().contains(String.valueOf(tool)), item.get("id") + " contains forbidden tool");
        }
    }

    /** 歧义案例必须进入受控追问，禁止在缺少口径时执行任意运营统计工具。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldRequireClarificationForAllAmbiguousCases() {
        RuleBasedSlotExtractor extractor = new RuleBasedSlotExtractor(
            Clock.fixed(Instant.parse("2026-07-13T01:00:00Z"), ZoneId.of("Asia/Shanghai")));
        RuleBasedBusinessQuestionAnalyzer analyzer = new RuleBasedBusinessQuestionAnalyzer();
        BusinessQueryPlanningService planner = new BusinessQueryPlanningService();
        for (Map<String, Object> item : loadCases()) {
            if (!"ambiguous".equals(item.get("category"))) continue;
            String input = String.valueOf(item.get("userInput"));
            var extraction = extractor.extract(input, null);
            assertTrue(extraction.getIntent() == ChatIntent.OPERATION_STATISTICS_QUERY, item.get("id") + " should be operation intent");
            var analysis = analyzer.analyze(input, extraction.getSlots());
            Map<String, Object> expectedAnalysis = (Map<String, Object>) item.get("expectedAnalysis");
            assertTrue(analysis.isRequiresClarification(), item.get("id") + " must require clarification");
            assertTrue("OPERATION_STATISTICS".equals(analysis.getDomains().get(0).name()), item.get("id") + " domain mismatch");
            assertTrue(analysis.getMetrics().isEmpty(), item.get("id") + " must not choose a metric");
            assertTrue(((List<?>) expectedAnalysis.get("ambiguities")).size() == analysis.getAmbiguities().size(),
                item.get("id") + " ambiguity mismatch");
            assertTrue(planner.plan(analysis) == null, item.get("id") + " must not create a plan");
        }
    }

    /** 从评测资源加载案例，避免每个验收测试各自维护不同输入集。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadCases() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("evaluation/business-query-cases.yaml")) {
            assertNotNull(input, "missing business query evaluation cases");
            Map<String, Object> root = new Yaml().load(input);
            return (List<Map<String, Object>>) root.get("cases");
        } catch (Exception exception) {
            throw new AssertionError("failed to load business query evaluation cases", exception);
        }
    }

    /** 验证指定分类的最小案例数。 */
    private void assertAtLeast(Map<String, Long> categories, String category, long minimum) {
        assertTrue(categories.getOrDefault(category, 0L) >= minimum, category + " cases below minimum");
    }

    /** 判断 YAML 字段是否为空。 */
    private boolean blank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }
}
