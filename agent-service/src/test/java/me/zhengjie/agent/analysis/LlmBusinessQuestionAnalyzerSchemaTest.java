package me.zhengjie.agent.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 模型分析 JSON 必须拒绝根字段和嵌套对象中的自由字段。 */
class LlmBusinessQuestionAnalyzerSchemaTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptDeclaredBusinessAnalysisSchema() throws Exception {
        assertTrue(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree("{\"questionType\":\"BUSINESS_QUERY\",\"domains\":[\"ORDER\"],\"entities\":{\"customerCode\":\"B3303\"},\"filters\":{\"recordDate\":\"2026-07-13\"},\"metrics\":[],\"dimensions\":[],\"ambiguities\":[],\"confidence\":0.9,\"requiresClarification\":false}")));
    }

    @Test
    void shouldRejectUnknownNestedFieldAndForbiddenInstruction() throws Exception {
        assertFalse(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree("{\"domains\":[\"ORDER\"],\"entities\":{\"customerCode\":\"B3303\",\"sql\":\"select * from customer\"}}")));
        assertFalse(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree("{\"domains\":[\"ORDER\"],\"filters\":{\"freeSort\":\"id desc\"}}")));
        assertFalse(LlmBusinessQuestionAnalyzer.isSafePayload(objectMapper.readTree("{\"domains\":[\"ORDER\"],\"ambiguities\":[{\"field\":\"customer\",\"options\":[\"A\"],\"toolName\":\"listOrders\"}]}")));
    }
}
