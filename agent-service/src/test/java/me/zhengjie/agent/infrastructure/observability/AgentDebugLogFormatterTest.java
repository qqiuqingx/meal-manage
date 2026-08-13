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

    /** 草稿敏感字段、控制字段和换行不得以原值进入调试日志。 */
    @Test
    void shouldRedactFormDraftPayloadAndControlFields() {
        String result = AgentDebugLogFormatter.text(
            "{\"phone\":\"13812345678\",\"addressDetail\":\"天府大道1号\","
                + "\"authorization\":\"Bearer secret\",\"sql\":\"select * from customer\"}\nnext", true);

        assertFalse(result.contains("13812345678"));
        assertFalse(result.contains("天府大道1号"));
        assertFalse(result.contains("Bearer secret"));
        assertFalse(result.contains("select * from customer"));
        assertTrue(result.contains("[REDACTED]"));
        assertTrue(result.contains("\\n"));
    }
}
