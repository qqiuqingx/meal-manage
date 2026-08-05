package me.zhengjie.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.guardrail.ToolGuardrailException;
import me.zhengjie.agent.guardrail.ToolInputGuardrail;
import me.zhengjie.agent.guardrail.ToolOutputGuardrail;
import me.zhengjie.agent.tool.input.SearchServiceCustomersInput;
import me.zhengjie.agent.tool.output.ToolOutputs;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 统一 Agent 工具目录、输入输出护栏和预算契约测试。 */
class UnifiedToolContractTest {

    private static final Set<String> TOOL_NAMES = Set.of(
        "searchCustomerProfiles", "searchServiceCustomers", "getServiceCustomerDetail", "listMealPlans",
        "listVerifications", "listRefunds", "previewDishCandidates", "listScheduledDishes", "searchDishes",
        "getPackageDetail", "queryBusinessMetrics", "explainBusinessRule");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SensitiveDataPolicy sensitiveDataPolicy = new SensitiveDataPolicy();

    /** 目录必须精确登记 12 个可由模型调用的业务工具。 */
    @Test
    void shouldRegisterExactlyTwelveUnifiedTools() {
        ToolRegistry registry = new ToolRegistry();

        assertEquals(12, registry.all().size());
        assertEquals(TOOL_NAMES, registry.all().stream().map(ToolRegistry.ToolSpec::name).collect(Collectors.toSet()));
        assertTrue(registry.all().stream().allMatch(spec -> spec.maxResults() > 0 && spec.timeoutMillis() == 3000));
    }

    /** 输入必须拒绝未知字段、越权字段和无界历史查询。 */
    @Test
    void shouldRejectUntrustedAndUnboundedInputs() {
        ToolRegistry registry = new ToolRegistry();
        ToolInputGuardrail guardrail = new ToolInputGuardrail(objectMapper, sensitiveDataPolicy);

        assertThrows(ToolGuardrailException.class, () -> guardrail.validate(
            registry.require(ToolRegistry.SEARCH_CUSTOMER_PROFILES),
            "{\"customerCode\":\"C1001\",\"unknown\":true}"));
        assertThrows(ToolGuardrailException.class, () -> guardrail.validate(
            registry.require(ToolRegistry.SEARCH_CUSTOMER_PROFILES),
            "{\"customerCode\":\"C1001\",\"sql\":\"select 1\"}"));
        assertThrows(ToolGuardrailException.class, () -> guardrail.validate(
            registry.require(ToolRegistry.LIST_VERIFICATIONS), "{\"page\":1,\"size\":20}"));
        assertTrue(guardrail.validate(registry.require(ToolRegistry.LIST_VERIFICATIONS),
            "{\"customerCode\":\"C1001\",\"mealType\":\"LUNCH\",\"page\":1,\"size\":20}") != null);
    }

    /** 搜索工具应把模型占位符归一化，并提前拦截负数 ID 和非法日期。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeSearchPlaceholdersBeforeValidation() {
        ToolRegistry registry = new ToolRegistry();
        ToolInputGuardrail guardrail = new ToolInputGuardrail(objectMapper, sensitiveDataPolicy);
        ToolRegistry.ToolSpec<SearchServiceCustomersInput> spec =
            (ToolRegistry.ToolSpec<SearchServiceCustomersInput>) (ToolRegistry.ToolSpec<?>)
                registry.require(ToolRegistry.SEARCH_SERVICE_CUSTOMERS);

        SearchServiceCustomersInput input = guardrail.validate(
            spec,
            "{\"customerId\":0,\"orderId\":0,\"dealTimeFrom\":\"\",\"dealTimeTo\":\"\",\"status\":\"ACTIVE\",\"page\":1,\"size\":20}");

        assertNull(input.getCustomerId());
        assertNull(input.getOrderId());
        assertNull(input.getDealTimeFrom());
        assertNull(input.getDealTimeTo());
        assertThrows(ToolGuardrailException.class, () -> guardrail.validate(
            spec, "{\"customerId\":-1,\"page\":1,\"size\":20}"));
        assertThrows(ToolGuardrailException.class, () -> guardrail.validate(
            spec, "{\"dealTimeFrom\":\"2026/08/05\",\"page\":1,\"size\":20}"));
    }

    /** 工具 Schema 应向模型展示服务客户查询的关键字段约束。 */
    @Test
    void shouldExposeSearchServiceCustomerFieldDescriptions() throws Exception {
        Function<SearchServiceCustomersInput, Object> function = input -> null;
        String schema = FunctionToolCallback.builder("searchServiceCustomers",
                function)
            .inputType(SearchServiceCustomersInput.class)
            .build().getToolDefinition().inputSchema();
        JsonNode root = objectMapper.readTree(schema);

        assertTrue(root.path("properties").path("status").path("description").asText().contains("ACTIVE"));
        assertTrue(root.path("properties").path("size").path("description").asText().contains("1-20"));
        assertFalse(root.path("required").toString().contains("customerId"));
    }

    /** 工具输出必须阻断金额字段和超过登记上限的结果集。 */
    @Test
    void shouldRejectSensitiveAndOversizedOutputs() {
        ToolRegistry registry = new ToolRegistry();
        ToolOutputGuardrail guardrail = new ToolOutputGuardrail(objectMapper, sensitiveDataPolicy);

        assertThrows(ToolGuardrailException.class, () -> guardrail.validate(
            registry.require(ToolRegistry.SEARCH_CUSTOMER_PROFILES), "{\"items\":[{\"amount\":1}]}"));
        String item = "{\"customerCode\":\"C1001\"}";
        String items = String.join(",", Collections.nCopies(21, item));
        assertThrows(ToolGuardrailException.class, () -> guardrail.validate(
            registry.require(ToolRegistry.SEARCH_CUSTOMER_PROFILES), "{\"items\":[" + items + "]}"));
    }

    /** 工具结果 DTO 不得声明金额、价格或未脱敏联系方式字段。 */
    @Test
    void shouldKeepUnifiedOutputDtosSensitiveFieldFree() {
        List<Class<?>> types = List.of(
            ToolOutputs.CustomerProfile.class, ToolOutputs.MealBalance.class, ToolOutputs.ServiceCustomer.class,
            ToolOutputs.ServiceCustomerDetail.class, ToolOutputs.MealPlan.class, ToolOutputs.Verification.class,
            ToolOutputs.Refund.class, ToolOutputs.Dish.class, ToolOutputs.PackageDetail.class,
            ToolOutputs.Metric.class, ToolOutputs.BusinessRule.class);
        for (Class<?> type : types) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                assertFalse(name.matches(".*(amount|price|money|payment|refundamount|discount).*"),
                    type.getSimpleName() + "." + field.getName());
                assertFalse(Set.of("phone", "mobile", "contactphone", "address").contains(name),
                    type.getSimpleName() + "." + field.getName());
            }
        }
    }

    /** 工具调用、模型回合和同参缓存均受同一轮硬预算约束。 */
    @Test
    void shouldEnforceCallRoundAndCacheBudgets() {
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 1, 1);
        context.beforeCall(ToolRegistry.SEARCH_CUSTOMER_PROFILES);
        assertThrows(ToolGuardrailException.class, () -> context.beforeCall(ToolRegistry.SEARCH_CUSTOMER_PROFILES));
        context.beforeModelRound(1);
        assertThrows(ToolGuardrailException.class, () -> context.beforeModelRound(1));

        String output = "{\"items\":[{\"customerCode\":\"C1001\"}]}";
        context.record(ToolRegistry.SEARCH_CUSTOMER_PROFILES, "CUSTOMER_PROFILE_LIST", "{}", output, true);
        context.recordCached(ToolRegistry.SEARCH_CUSTOMER_PROFILES, "CUSTOMER_PROFILE_LIST", output);
        assertEquals(1, context.records());
        assertEquals(1, context.cacheHits());
        assertEquals(2, context.facts().size());
    }

    /** 失败工具事实不得被同参缓存，避免后续修复调用复用旧错误。 */
    @Test
    void shouldNotCacheFailedToolResult() {
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);
        String input = "{\"page\":1,\"size\":20}";
        context.record(ToolRegistry.SEARCH_SERVICE_CUSTOMERS, "SERVICE_CUSTOMER_LIST", input,
            "{\"errorCode\":\"TOOL_INPUT_INVALID\"}", false);

        assertNull(context.cached(context.cacheKey(ToolRegistry.SEARCH_SERVICE_CUSTOMERS, input)));
    }

    /** 同一工具的等价 JSON 参数即使字段顺序不同，也必须命中同轮缓存键。 */
    @Test
    void shouldCanonicalizeEquivalentCacheInputs() {
        ToolExecutionContext context = new ToolExecutionContext(objectMapper, 6, 100);

        String first = context.cacheKey(ToolRegistry.SEARCH_SERVICE_CUSTOMERS,
            "{\"customerCode\":\"C1001\",\"page\":1,\"size\":20}");
        String second = context.cacheKey(ToolRegistry.SEARCH_SERVICE_CUSTOMERS,
            "{\"size\":20,\"page\":1,\"customerCode\":\"C1001\"}");

        assertEquals(first, second);
    }
}
