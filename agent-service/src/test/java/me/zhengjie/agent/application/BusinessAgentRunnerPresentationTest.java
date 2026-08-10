package me.zhengjie.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.guardrail.FinalAnswerGuardrail;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.presentation.PresentationDescriptor;
import me.zhengjie.agent.presentation.PresentationRegistry;
import me.zhengjie.agent.presentation.PresentationService;
import me.zhengjie.agent.presentation.PresentationSuggestionValidator;
import me.zhengjie.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** BusinessAgentRunner 卡片与系统展示描述一对一接线契约测试。 */
class BusinessAgentRunnerPresentationTest {

    /** 成功工具事实只能产生同 callId 的 SYSTEM 展示描述，且展示不改变业务 partial。 */
    @Test
    void shouldAssemblePresentationForSuccessfulKnownCard() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        BusinessAgentRunner runner = runner(objectMapper, registry);
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        context.record(ToolRegistry.SEARCH_SERVICE_CUSTOMERS, "SERVICE_CUSTOMER_LIST", "{}",
            "{\"items\":[{\"customerCode\":\"C1001\",\"customerName\":\"张三\",\"orderCode\":\"O1\",\"orderTime\":\"2026-08-05T10:00:00\",\"status\":\"ACTIVE\",\"parentPackageName\":\"套餐\"}],\"warnings\":[]}", true);
        AgentChatRequest request = request("查询客户订单");

        me.zhengjie.agent.domain.dto.AgentChatResponse response = runner.runWithAnswer(request, "已查询到客户订单。", context);

        assertEquals(1, response.getCards().size());
        assertEquals(1, response.getPresentations().size());
        assertEquals("call-1", response.getPresentations().get(0).sourceToolCallId());
        assertEquals(PresentationDescriptor.DecisionSource.SYSTEM, response.getPresentations().get(0).decisionSource());
        assertTrue(!response.isPartial());
    }

    /** 失败工具事实不得生成卡片或展示描述，但仍保留工具追踪摘要。 */
    @Test
    void shouldNotAssemblePresentationForFailedTool() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        BusinessAgentRunner runner = runner(objectMapper, registry);
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        context.record(ToolRegistry.SEARCH_SERVICE_CUSTOMERS, "SERVICE_CUSTOMER_LIST", "{}",
            "{\"errorCode\":\"DEPENDENCY_UNAVAILABLE\"}", false);

        me.zhengjie.agent.domain.dto.AgentChatResponse response = runner.runWithAnswer(
            request("你好"), "暂时无法完成查询。", context);

        assertTrue(response.getCards().isEmpty());
        assertTrue(response.getPresentations().isEmpty());
        assertEquals(1, response.getToolTraceSummary().size());
    }

    /** 同一业务告警重复出现时只保留首次出现值，并保持业务告警对 partial 的语义。 */
    @Test
    void shouldDeduplicateBusinessWarningsAndKeepPartialSemantics() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        BusinessAgentRunner runner = runner(objectMapper, registry);
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        context.record(ToolRegistry.SEARCH_SERVICE_CUSTOMERS, "SERVICE_CUSTOMER_LIST", "{}",
            "{\"items\":[],\"warnings\":[\"MENU_RESULT_IMPLAUSIBLE\",\"MENU_RESULT_IMPLAUSIBLE\"]}", true);

        me.zhengjie.agent.domain.dto.AgentChatResponse response = runner.runWithAnswer(
            request("查询客户订单"), "已完成查询。", context);

        assertEquals(java.util.List.of("MENU_RESULT_IMPLAUSIBLE"), response.getWarnings());
        assertTrue(response.isPartial());
    }

    /** 未知卡片没有 presentation profile 时生成 SYSTEM 通用降级和稳定告警。 */
    @Test
    void shouldFallbackUnknownCardWithoutChangingBusinessPartial() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        PresentationService.PresentationResult result = new PresentationService(new PresentationRegistry(registry)).present(
            "call-1", "futureTool", "UNKNOWN_CARD", objectMapper.readTree(
                "{\"items\":[{\"label\":\"A\",\"value\":1}],\"warnings\":[]}"));

        assertEquals(PresentationDescriptor.DecisionSource.SYSTEM, result.descriptor().decisionSource());
        assertTrue(result.warnings().contains("PRESENTATION_FALLBACK_APPLIED"));
        assertFalse(result.warnings().contains("BUSINESS_PARTIAL"));
    }

    /** 已知卡片即使提供 planner 也必须只走系统规则，模型规划调用次数保持为零。 */
    @Test
    void shouldNotCallPlannerForKnownCard() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        int[] calls = {0};
        PresentationSuggestionValidator validator = new PresentationSuggestionValidator();
        PresentationService service = new PresentationService(new PresentationRegistry(registry),
            (cardType, schema) -> { calls[0]++; throw new IllegalStateException("MUST_NOT_CALL"); },
            new me.zhengjie.agent.presentation.CardSchemaInspector(), validator,
            new me.zhengjie.agent.presentation.GenericPresentationFactory(validator));

        PresentationService.PresentationResult result = service.present("call-1",
            ToolRegistry.SEARCH_SERVICE_CUSTOMERS, "SERVICE_CUSTOMER_LIST", objectMapper.createObjectNode());

        assertEquals(PresentationDescriptor.DecisionSource.SYSTEM, result.descriptor().decisionSource());
        assertEquals(0, calls[0]);
    }

    /** 系统存在结构化展示时，提示词必须要求模型只总结且不得重复输出 Markdown 表格。 */
    @Test
    void shouldRequireSummaryOnlyForStructuredToolResults() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        String prompt = runner(objectMapper, registry).systemPrompt(request("查询客户订单"), registry.all());

        assertTrue(prompt.contains("只总结用户最关心的结论"));
        assertTrue(prompt.contains("不逐行复述明细"));
        assertTrue(prompt.contains("不输出 Markdown 表格"));
        assertTrue(prompt.contains("详细数据交给结构化展示"));
    }

    /** 简单核销总数问题必须提示模型使用唯一总数指标，并在成功后停止额外工具调用。 */
    @Test
    void shouldRequireSingleVerificationRecordCountMetric() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();

        String prompt = runner(objectMapper, registry).systemPrompt(
            request("现在系统中有多少核销数据了"), registry.all());

        assertTrue(prompt.contains("queryBusinessMetrics(metric=VERIFICATION_RECORD_COUNT)"));
        assertTrue(prompt.contains("不传日期、餐次或维度"));
        assertTrue(prompt.contains("不得用 listVerifications 分餐次拼总数"));
        assertTrue(prompt.contains("成功取得完整结果后立即回答"));
    }

    /** 重复指标和额外规则不得重复占据业务区，但全部事实、追踪和失败告警必须保留。 */
    @Test
    void shouldConvergeDuplicateMetricsAndUnrequestedRuleCards() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        context.record(ToolRegistry.QUERY_BUSINESS_METRICS, "METRIC_RESULT", "{}",
            metricOutput("2026-08-07T16:28:22+08:00"), true);
        context.record(ToolRegistry.EXPLAIN_BUSINESS_RULE, "BUSINESS_RULE", "{}",
            "{\"data\":{\"ruleId\":\"VERIFICATION_REFUND_EFFECT\",\"title\":\"核销规则\"},\"warnings\":[]}", true);
        context.record(ToolRegistry.QUERY_BUSINESS_METRICS, "METRIC_RESULT", "{\"mealType\":\"LUNCH\"}",
            metricOutput("2026-08-07T16:28:26+08:00"), true);
        context.record(ToolRegistry.LIST_VERIFICATIONS, "VERIFICATION_LIST", "{}",
            "{\"errorCode\":\"TOOL_BUDGET_EXCEEDED\",\"items\":[]}", false);

        me.zhengjie.agent.domain.dto.AgentChatResponse response = runner(objectMapper, registry).runWithAnswer(
            request("现在系统中有多少核销数据了"), "当前共有 27 条核销记录。", context);

        assertEquals(1, response.getCards().size());
        assertEquals("METRIC_RESULT", response.getCards().get(0).get("type"));
        assertEquals(1, response.getPresentations().size());
        assertEquals(3, response.getToolFacts().size());
        assertEquals(4, response.getToolTraceSummary().size());
        assertTrue(response.getWarnings().contains("listVerifications:TOOL_BUDGET_EXCEEDED"));
    }

    /** 非简单总数问题必须保留同指标的不同筛选条件，避免把跨日期对比误去重。 */
    @Test
    void shouldKeepSameMetricWithDifferentFiltersForComparisonQuestion() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry();
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        context.record(ToolRegistry.QUERY_BUSINESS_METRICS, "METRIC_RESULT", "{\"recordDate\":\"2026-08-06\"}",
            metricOutput("2026-08-07T16:28:22+08:00"), true);
        context.record(ToolRegistry.QUERY_BUSINESS_METRICS, "METRIC_RESULT", "{\"recordDate\":\"2026-08-07\"}",
            metricOutput("2026-08-07T16:28:26+08:00"), true);

        me.zhengjie.agent.domain.dto.AgentChatResponse response = runner(objectMapper, registry).runWithAnswer(
            request("对比昨天和今天的核销客户"), "已完成两个日期的核销客户对比。", context);

        assertEquals(2, response.getCards().size());
        assertEquals(2, response.getPresentations().size());
    }

    /** 构造只有查询时间不同的同指标结果，验证展示语义去重。 */
    private String metricOutput(String queriedAt) {
        return "{\"data\":{\"metric\":\"VERIFICATION_RECORD_COUNT\",\"total\":27,\"breakdown\":[],\"queriedAt\":\""
            + queriedAt + "\"},\"warnings\":[]}";
    }

    /** 创建使用真实系统注册表的最小 Runner，避免测试调用模型或远程工具。 */
    private BusinessAgentRunner runner(ObjectMapper objectMapper, ToolRegistry registry) {
        PresentationService presentationService = new PresentationService(new PresentationRegistry(registry));
        return new BusinessAgentRunner(null, null, registry,
            new FinalAnswerGuardrail(new SensitiveDataPolicy()), new SensitiveDataPolicy(), objectMapper,
            new AgentProperties(), null, presentationService);
    }

    /** 构造最小内部 Agent 请求。 */
    private AgentChatRequest request(String message) {
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage(message);
        request.setSessionId("session-1");
        request.setClientMessageId("message-1");
        return request;
    }
}
