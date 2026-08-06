package me.zhengjie.agent.infrastructure.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Agent 调试日志正文的脱敏、单行化和开关契约测试。 */
class AgentDebugLogFormatterTest {

    @Test
    void shouldRedactSensitiveValuesAndEscapeLineBreaks() {
        String result = AgentDebugLogFormatter.text(
            "phone=13812345678\n token=secret-value", true);

        assertTrue(result.contains("138****5678"));
        assertFalse(result.contains("13812345678"));
        assertTrue(result.contains("\\n"));
        assertTrue(result.contains("token=[REDACTED]"));
    }

    @Test
    void shouldDisableContentWithoutRemovingTheLogEvent() {
        assertEquals("[content logging disabled]",
            AgentDebugLogFormatter.text("customerCode=B5600", false));
    }

    @Test
    void shouldSerializeObjectsAsSingleLineJson() {
        String result = AgentDebugLogFormatter.json(Map.of("customerCode", "B5600"), new ObjectMapper(), true);

        assertEquals("{\"customerCode\":\"B5600\"}", result);
    }
}
