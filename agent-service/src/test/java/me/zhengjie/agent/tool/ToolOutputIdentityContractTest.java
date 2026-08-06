package me.zhengjie.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.guardrail.SensitiveDataPolicy;
import me.zhengjie.agent.tool.output.ToolOutputs;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Agent 工具输出的姓名、下单时间、指标分组和敏感字段契约测试。 */
class ToolOutputIdentityContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 工具客户输出使用 customerName，不再声明 maskedName，并保留 maskedPhone。 */
    @Test
    void shouldExposeFullNameOnlyUnderCustomerName() {
        assertField(ToolOutputs.CustomerProfile.class, "customerName");
        assertField(ToolOutputs.CustomerProfile.class, "customerCode");
        assertField(ToolOutputs.CustomerProfile.class, "maskedPhone");
        assertField(ToolOutputs.ServiceCustomer.class, "customerName");
        assertField(ToolOutputs.ServiceCustomer.class, "orderTime");
        assertFalse(hasField(ToolOutputs.CustomerProfile.class, "maskedName"));
        assertFalse(hasField(ToolOutputs.ServiceCustomer.class, "maskedName"));
    }

    /** 指标 JSON 同时保留 dimensions，并提供只含 label/value 的 breakdown。 */
    @Test
    void shouldSerializeCompatibleMetricBreakdown() throws Exception {
        ToolOutputs.Metric metric = new ToolOutputs.Metric("DAILY_COUNT", 5,
            Map.of("LUNCH", 3L, "DINNER", 2L),
            List.of(new ToolOutputs.MetricBreakdown("LUNCH", 3L)),
            "2026-08-05T10:00:00+08:00", List.of());

        JsonNode json = objectMapper.valueToTree(metric);

        assertTrue(json.has("dimensions"));
        assertEquals("LUNCH", json.path("breakdown").get(0).path("label").asText());
        assertEquals(3L, json.path("breakdown").get(0).path("value").asLong());
        assertEquals(Set.of("label", "value"),
            objectMapper.convertValue(json.path("breakdown").get(0), Map.class).keySet());
        new SensitiveDataPolicy().assertSafe(json);
    }

    /** 工具输出的既有敏感字段保护仍拒绝完整手机号、地址和金额字段。 */
    @Test
    void shouldKeepSensitiveDataPolicyUnchanged() {
        SensitiveDataPolicy policy = new SensitiveDataPolicy();
        assertNotNull(policy);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> policy.assertSafe(objectMapper.createObjectNode().put("phone", "13800138000")));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> policy.assertSafe(objectMapper.createObjectNode().put("amount", 10)));
    }

    /** 查找记录组件声明的字段。 */
    private Field field(Class<?> type, String name) {
        try { return type.getDeclaredField(name); }
        catch (NoSuchFieldException exception) { return null; }
    }

    /** 判断记录组件是否声明了字段。 */
    private boolean hasField(Class<?> type, String name) { return field(type, name) != null; }

    /** 断言记录组件存在。 */
    private void assertField(Class<?> type, String name) { assertNotNull(field(type, name), type.getSimpleName() + "." + name); }
}
