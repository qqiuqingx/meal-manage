package me.zhengjie.agent.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 卡片结构摘要不得携带业务值，并必须执行敏感字段、深度和路径边界。 */
class CardSchemaInspectorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldKeepShapeButNeverKeepBusinessValuesOrSensitiveIds() throws Exception {
        String json = "{\"items\":[{\"customerId\":7,\"customerName\":\"张三\",\"orderCode\":\"ORD-9001\",\"count\":3}],\"truncated\":false}";

        CardSchemaInspector.SchemaSummary summary = new CardSchemaInspector().inspect(mapper.readTree(json));
        String structure = summary.toString();

        assertTrue(summary.paths().stream().anyMatch(field -> field.path().equals("items[].customerName")));
        assertTrue(summary.paths().stream().anyMatch(field -> field.path().equals("items[].count")));
        assertFalse(summary.paths().stream().anyMatch(field -> field.path().contains("customerId")));
        assertFalse(structure.contains("张三"));
        assertFalse(structure.contains("ORD-9001"));
        assertFalse(structure.contains("7"));
        assertFalse(summary.truncated());
    }

    @Test
    void shouldMarkDepthAndPathTruncationWithoutReadingValues() throws Exception {
        CardSchemaInspector inspector = new CardSchemaInspector(1, 2);
        CardSchemaInspector.SchemaSummary summary = inspector.inspect(mapper.readTree(
            "{\"data\":{\"first\":{\"second\":\"secret-value\"},\"other\":true}}"));

        assertFalse(summary.complete());
        assertTrue(summary.warnings().stream().anyMatch(value -> value.contains("LIMIT_REACHED")));
        assertFalse(summary.toString().contains("secret-value"));
    }
}
