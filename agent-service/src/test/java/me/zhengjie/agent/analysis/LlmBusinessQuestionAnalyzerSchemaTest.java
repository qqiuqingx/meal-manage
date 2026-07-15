package me.zhengjie.agent.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 模型分析 JSON 必须拒绝根字段和嵌套对象中的自由字段。 */
class LlmBusinessQuestionAnalyzerSchemaTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptDeclaredBusinessAnalysisSchema() throws Exception {
        assertTrue(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree("{\"questionType\":\"BUSINESS_QUERY\",\"domains\":[\"OPERATION_STATISTICS\"],\"entities\":{},\"filters\":{},\"metrics\":[\"DAILY_UNSCHEDULED_CUSTOMER_COUNT\"],\"dimensions\":[],\"ambiguities\":[],\"temporal\":{\"expression\":\"CURRENT_DAY\"},\"confidence\":0.96,\"requiresClarification\":false}")));
    }

    @Test
    void shouldRejectUnknownNestedFieldAndForbiddenInstruction() throws Exception {
        assertFalse(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree("{\"domains\":[\"ORDER\"],\"entities\":{\"customerCode\":\"B3303\",\"sql\":\"select * from customer\"}}")));
        assertFalse(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree("{\"domains\":[\"ORDER\"],\"filters\":{\"freeSort\":\"id desc\"}}")));
        assertFalse(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree("{\"domains\":[\"ORDER\"],\"ambiguities\":[{\"field\":\"customer\",\"options\":[\"A\"],\"toolName\":\"listOrders\"}]}")));
    }

    @Test
    void shouldAcceptOnlyControlledCorrectionPayload() throws Exception {
        assertTrue(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree("{\"questionType\":\"BUSINESS_QUERY\",\"queryTarget\":\"SCHEDULED_MENU\",\"interactionMode\":\"CORRECTION\",\"referenceTurn\":\"PREVIOUS_BUSINESS_QUERY\",\"domains\":[\"DISH\"],\"entities\":{},\"filters\":{\"recordDate\":\"2026-07-13\"},\"mealScope\":\"ALL_AVAILABLE\",\"correction\":{\"reason\":\"PREVIOUS_RESULT_IMPLAUSIBLE\",\"observations\":[\"ONLY_RICE_RETURNED\"],\"requiresReplan\":true},\"metrics\":[],\"dimensions\":[],\"ambiguities\":[],\"confidence\":0.9,\"requiresClarification\":false}")));
        assertFalse(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree("{\"correction\":{\"reason\":\"UNKNOWN\",\"observations\":[\"SELECT * FROM dish\"],\"requiresReplan\":true}}")));
    }

    @Test
    void shouldAcceptExplicitNullForOptionalSemanticFields() throws Exception {
        assertTrue(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree(
            "{\"questionType\":\"BUSINESS_QUERY\",\"domains\":[\"MEAL_PLAN\"],\"entities\":{},\"filters\":{},"
                + "\"ambiguities\":null,\"correction\":null,\"operation\":null,\"metrics\":[],\"dimensions\":[],"
                + "\"subjects\":[],\"relations\":[],\"requestedFacts\":[],\"groupBy\":[],\"confidence\":0.9,\"requiresClarification\":false}")));
    }

    @Test
    void shouldStripOnlyRegisteredNonExecutableModelFields() throws Exception {
        var normalized = LlmBusinessQuestionAnalyzer.stripNonExecutableFields(objectMapper.readTree(
            "{\"questionType\":\"BUSINESS_QUERY\",\"domains\":[\"MEAL_PLAN\"],\"observations\":[\"scope\"],"
                + "\"analysisContext\":{\"note\":\"all meals\"},\"metrics\":[],\"dimensions\":[],\"confidence\":0.9}"));
        assertTrue(LlmBusinessQuestionAnalyzer.isSafePayload(normalized));
        assertFalse(normalized.has("observations"));
        assertFalse(normalized.has("analysisContext"));
        assertFalse(LlmBusinessQuestionAnalyzer.isSafePayload(
            LlmBusinessQuestionAnalyzer.stripNonExecutableFields(objectMapper.readTree("{\"freeTool\":\"anything\"}"))));
    }

    @Test
    void shouldGenerateBusinessSchemaFromDtoWithoutInternalSource() {
        String schema = new BeanOutputConverter<>(BusinessQuestionAnalysis.class, objectMapper).getJsonSchema();

        assertTrue(schema.contains("MEAL_PLAN_ALLERGY_ANALYSIS"));
        assertTrue(schema.contains("MEAL_PLAN_DIAGNOSIS"));
        assertTrue(schema.contains("NEW_QUERY"));
        assertTrue(schema.contains("CURRENT_DAY"));
        assertFalse(schema.contains("\"source\""));
    }
}
