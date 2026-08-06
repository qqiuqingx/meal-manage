package me.zhengjie.agent.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LLM 展示候选的路径、类型、完整性和类别数量二次校验。 */
class PresentationSuggestionValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CardSchemaInspector inspector = new CardSchemaInspector();
    private final PresentationSuggestionValidator validator = new PresentationSuggestionValidator();

    @Test
    void shouldAcceptOnlyCompleteNumericBreakdownForBar() throws Exception {
        var data = mapper.readTree("{\"data\":{\"breakdown\":[{\"label\":\"A\",\"value\":2}]}}");
        var schema = inspector.inspect(data);
        var suggestion = new PresentationSuggestion("BAR", "分组数量", "data.breakdown", "label",
            List.of("value"), "分组", List.of("数量"));

        PresentationSuggestionValidator.ValidationResult result = validator.validate(suggestion, schema, data);

        assertTrue(result.valid());
        assertEquals("BAR", result.suggestion().view());
    }

    @Test
    void shouldRejectSensitiveOrIncompleteChartReferences() throws Exception {
        var data = mapper.readTree("{\"items\":[{\"customerId\":1,\"label\":\"A\",\"value\":2}],\"warnings\":[\"LIMITED\"]}");
        var schema = inspector.inspect(data);
        var sensitive = new PresentationSuggestion("BAR", "客户", "items", "customerId",
            List.of("value"), "客户", List.of("数量"));

        assertFalse(validator.validate(sensitive, schema, data).valid());
    }

    @Test
    void shouldDowngradePieToBarWithinBarLimitAndRejectHtml() throws Exception {
        StringBuilder rows = new StringBuilder("[");
        for (int i = 0; i < 9; i++) {
            if (i > 0) rows.append(',');
            rows.append("{\"label\":\"x\",\"value\":1}");
        }
        rows.append(']');
        var data = mapper.readTree("{\"items\":" + rows + "}");
        var schema = inspector.inspect(data);
        var pie = new PresentationSuggestion("PIE", "分类", "items", "label", List.of("value"), null, List.of());
        var result = validator.validate(pie, schema, data);

        assertTrue(result.valid());
        assertEquals("BAR", result.suggestion().view());

        var html = new PresentationSuggestion("TABLE", "<b>bad</b>", "items", null, List.of(), null, List.of());
        assertFalse(validator.validate(html, schema, data).valid());
    }
}
