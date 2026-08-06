package me.zhengjie.agent.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.guardrail.FinalAnswerGuardrail;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.guardrail.ToolGuardrailException;
import me.zhengjie.agent.presentation.CardSchemaInspector;
import me.zhengjie.agent.presentation.GenericPresentationFactory;
import me.zhengjie.agent.presentation.PresentationDescriptor;
import me.zhengjie.agent.presentation.PresentationRegistry;
import me.zhengjie.agent.presentation.PresentationService;
import me.zhengjie.agent.presentation.PresentationSuggestion;
import me.zhengjie.agent.presentation.PresentationSuggestionValidator;
import me.zhengjie.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 跨 Agent 服务展示规则、事实卡片和安全边界的服务客户验收测试。 */
class ServiceCustomerPresentationAcceptanceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolRegistry toolRegistry = new ToolRegistry();

    /** 验收“现在系统中的客户分别是什么时候下单的”的事实卡片和系统表格契约。 */
    @Test
    void shouldAcceptServiceCustomerOrderTimeQuestionWithSystemTable() throws Exception {
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        context.record(ToolRegistry.SEARCH_SERVICE_CUSTOMERS, "SERVICE_CUSTOMER_LIST", "{\"status\":\"ACTIVE\"}",
            serviceCustomerOutput(), true);

        AgentChatResponse response = runner().runWithAnswer(request("现在系统中的客户分别是什么时候下单的"),
            "已查询到 2 笔进行中的客户订单。", context);

        assertEquals(1, response.getCards().size());
        assertEquals("SERVICE_CUSTOMER_LIST", response.getCards().get(0).get("type"));
        assertEquals("call-1", response.getCards().get(0).get("sourceToolCallId"));
        Map<String, Object> data = (Map<String, Object>) response.getCards().get(0).get("data");
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertEquals(2, items.size());
        for (Map<String, Object> item : items) {
            assertTrue(item.keySet().containsAll(List.of("customerCode", "customerName", "orderCode",
                "orderTime", "status", "parentPackageName")));
        }
        assertEquals("C10001", items.get(0).get("customerCode"));
        assertEquals("张三", items.get(0).get("customerName"));
        assertEquals("O-001", items.get(0).get("orderCode"));
        assertEquals("2026-08-01T09:00:00+08:00", items.get(0).get("orderTime"));
        assertEquals("O-002", items.get(1).get("orderCode"));
        assertEquals("2026-08-02T10:00:00+08:00", items.get(1).get("orderTime"));

        assertFalse(response.getAssistantMessage().contains("|"));
        assertEquals(1, response.getPresentations().size());
        PresentationDescriptor presentation = response.getPresentations().get(0);
        assertEquals("v1", presentation.schemaVersion());
        assertEquals("call-1", presentation.sourceToolCallId());
        assertEquals(PresentationDescriptor.DecisionSource.SYSTEM, presentation.decisionSource());
        assertEquals(PresentationDescriptor.View.TABLE, presentation.defaultView());
        assertEquals(List.of(PresentationDescriptor.View.TABLE), presentation.availableViews());
        assertNotNull(presentation.table());
        assertEquals("items", presentation.table().dataPath());
        assertEquals(List.of("customerCode", "customerName", "orderCode", "orderTime", "status", "parentPackageName"),
            presentation.table().columns().stream().map(PresentationDescriptor.Field::field).toList());

        String safeCardJson = objectMapper.writeValueAsString(response.getCards());
        assertFalse(safeCardJson.contains("customerId"));
        assertFalse(safeCardJson.contains("orderId"));
    }

    /** 单值指标只生成摘要，完整 breakdown 才生成表格和柱状图。 */
    @Test
    void shouldChooseSummaryForSingleValueAndChartForCompleteBreakdown() throws Exception {
        PresentationService service = new PresentationService(new PresentationRegistry(toolRegistry));
        PresentationDescriptor summary = service.present("call-summary", ToolRegistry.QUERY_BUSINESS_METRICS,
            "METRIC_RESULT", objectMapper.readTree(
                "{\"data\":{\"metric\":\"ORDER_COUNT\",\"total\":12,\"breakdown\":[]},\"warnings\":[]}"))
            .descriptor();

        assertEquals(PresentationDescriptor.View.TEXT, summary.defaultView());
        assertEquals(List.of(PresentationDescriptor.View.TEXT), summary.availableViews());
        assertNotNull(summary.summary());
        assertNull(summary.table());
        assertNull(summary.chart());

        PresentationDescriptor chart = service.present("call-chart", ToolRegistry.QUERY_BUSINESS_METRICS,
            "METRIC_RESULT", objectMapper.readTree(
                "{\"data\":{\"metric\":\"ORDER_COUNT\",\"total\":12,\"breakdown\":["
                    + "{\"label\":\"套餐 A\",\"value\":7},{\"label\":\"套餐 B\",\"value\":5}]},\"warnings\":[]}"))
            .descriptor();

        assertEquals(PresentationDescriptor.View.TEXT, chart.defaultView());
        assertEquals(List.of(PresentationDescriptor.View.TEXT, PresentationDescriptor.View.TABLE,
            PresentationDescriptor.View.BAR), chart.availableViews());
        assertEquals(PresentationDescriptor.View.BAR, chart.chart().type());
        assertEquals("data.breakdown", chart.chart().dataPath());
        assertEquals(List.of("value"), chart.chart().metricFields());
    }

    /** 截断或带完整性告警的指标保留摘要，不允许生成误导性的图表。 */
    @Test
    void shouldNotCreateMetricChartForTruncatedBreakdown() throws Exception {
        PresentationService service = new PresentationService(new PresentationRegistry(toolRegistry));
        PresentationDescriptor presentation = service.present("call-truncated", ToolRegistry.QUERY_BUSINESS_METRICS,
            "METRIC_RESULT", objectMapper.readTree(
                "{\"truncated\":true,\"data\":{\"metric\":\"ORDER_COUNT\",\"total\":12,\"breakdown\":["
                    + "{\"label\":\"套餐 A\",\"value\":7}]},\"warnings\":[]}"))
            .descriptor();

        assertEquals(List.of(PresentationDescriptor.View.TEXT), presentation.availableViews());
        assertEquals(PresentationDescriptor.View.TEXT, presentation.defaultView());
        assertNull(presentation.table());
        assertNull(presentation.chart());
    }

    /** 未知卡片收到非法字段建议时必须回退为可读安全表格，不能丢弃业务事实。 */
    @Test
    void shouldKeepUnknownCardFactsReadableAfterIllegalSuggestion() throws Exception {
        PresentationSuggestionValidator validator = new PresentationSuggestionValidator();
        PresentationService service = presentationService((cardType, schema) -> new PresentationSuggestion(
            "BAR", "非法图表建议", "items", "customerId", List.of("value"), "客户", List.of("数量")), validator);
        JsonNode safeData = objectMapper.readTree(
            "{\"items\":[{\"label\":\"套餐 A\",\"value\":7}],\"warnings\":[]}");

        PresentationService.PresentationResult result = service.present("call-unknown", "futureTool",
            "FUTURE_CARD", safeData);

        assertEquals(PresentationDescriptor.DecisionSource.SYSTEM, result.descriptor().decisionSource());
        assertEquals(PresentationDescriptor.View.TABLE, result.descriptor().defaultView());
        assertEquals("items", result.descriptor().table().dataPath());
        assertEquals(List.of("label", "value"), result.descriptor().table().columns().stream()
            .map(PresentationDescriptor.Field::field).toList());
        assertTrue(result.warnings().contains(PresentationService.FALLBACK_WARNING));
        assertFalse(objectMapper.writeValueAsString(result.descriptor()).contains("customerId"));
    }

    /** 未知卡片的合法受控建议可以渲染，但仍只能返回字段引用而不能携带业务值。 */
    @Test
    void shouldRenderLegalUnknownCardSuggestionWithoutCopyingValues() throws Exception {
        PresentationSuggestionValidator validator = new PresentationSuggestionValidator();
        PresentationService service = presentationService((cardType, schema) -> new PresentationSuggestion(
            "TABLE", "未来卡片安全表", "items", null, List.of(), null, List.of()), validator);

        PresentationDescriptor descriptor = service.present("call-llm", "futureTool", "FUTURE_CARD",
            objectMapper.readTree("{\"items\":[{\"label\":\"安全事实\",\"value\":3}],\"warnings\":[]}"))
            .descriptor();

        assertEquals(PresentationDescriptor.DecisionSource.LLM, descriptor.decisionSource());
        assertEquals(PresentationDescriptor.View.TABLE, descriptor.defaultView());
        assertEquals(List.of("label", "value"), descriptor.table().columns().stream()
            .map(PresentationDescriptor.Field::field).toList());
        assertFalse(objectMapper.writeValueAsString(descriptor).contains("安全事实"));
    }

    /** 姓名可作为受控 Agent 事实展示，手机号、地址、金额和内部 ID 仍由既有策略拦截。 */
    @Test
    void shouldKeepIdentityAndSensitiveDataBoundaries() throws Exception {
        SensitiveDataPolicy policy = new SensitiveDataPolicy();
        assertDoesNotThrow(() -> policy.assertSafe(objectMapper.readTree(
            "{\"customerCode\":\"C10001\",\"customerName\":\"张三\",\"maskedPhone\":\"138****8000\"}")));
        assertThrows(ToolGuardrailException.class, () -> policy.assertSafe(
            objectMapper.readTree("{\"phone\":\"13800138000\"}")));
        assertThrows(ToolGuardrailException.class, () -> policy.assertSafe(
            objectMapper.readTree("{\"address\":\"北京市某街道 1 号\"}")));
        assertThrows(ToolGuardrailException.class, () -> policy.assertSafe(
            objectMapper.readTree("{\"amount\":99.9}")));

        JsonNode safe = policy.hideInternalIdentifiers(objectMapper.readTree(
            "{\"customerId\":1001,\"orderId\":2001,\"customerCode\":\"C10001\"}"));
        assertFalse(safe.toString().contains("customerId"));
        assertFalse(safe.toString().contains("orderId"));
        assertEquals("C10001", safe.path("customerCode").asText());
    }

    /** 创建使用真实系统注册表的展示服务，并把规划器限制在测试候选内。 */
    private PresentationService presentationService(me.zhengjie.agent.presentation.PresentationPlanner planner,
                                                     PresentationSuggestionValidator validator) {
        return new PresentationService(new PresentationRegistry(toolRegistry), planner,
            new CardSchemaInspector(), validator, new GenericPresentationFactory(validator));
    }

    /** 创建不调用模型和远程工具的 Runner，使用真实系统展示注册表组装响应。 */
    private BusinessAgentRunner runner() {
        SensitiveDataPolicy policy = new SensitiveDataPolicy();
        return new BusinessAgentRunner(null, null, toolRegistry, new FinalAnswerGuardrail(policy), policy,
            objectMapper, new AgentProperties(), null,
            new PresentationService(new PresentationRegistry(toolRegistry)));
    }

    /** 创建包含稳定会话标识的内部 Agent 请求。 */
    private AgentChatRequest request(String message) {
        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId("session-acceptance");
        request.setClientMessageId("message-acceptance");
        request.setMessage(message);
        return request;
    }

    /** 构造服务客户工具的安全事实样例，同时保留内部 ID 供 Runner 验证移除。 */
    private String serviceCustomerOutput() {
        return "{\"items\":["
            + "{\"customerId\":1001,\"customerCode\":\"C10001\",\"customerName\":\"张三\","
            + "\"orderId\":2001,\"orderCode\":\"O-001\",\"dealTime\":\"2026-08-01T09:00:00+08:00\","
            + "\"createTime\":\"2026-07-31T18:00:00+08:00\",\"orderTime\":\"2026-08-01T09:00:00+08:00\","
            + "\"status\":\"ACTIVE\",\"parentPackageName\":\"标准套餐\"},"
            + "{\"customerId\":1002,\"customerCode\":\"C10002\",\"customerName\":\"李四\","
            + "\"orderId\":2002,\"orderCode\":\"O-002\",\"dealTime\":null,"
            + "\"createTime\":\"2026-08-02T10:00:00+08:00\",\"orderTime\":\"2026-08-02T10:00:00+08:00\","
            + "\"status\":\"ACTIVE\",\"parentPackageName\":\"轻食套餐\"}],"
            + "\"total\":2,\"page\":1,\"size\":2,\"truncated\":false,\"warnings\":[]}";
    }
}
