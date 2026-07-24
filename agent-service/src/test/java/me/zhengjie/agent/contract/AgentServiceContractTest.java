package me.zhengjie.agent.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证跨服务 OpenAPI 契约的关键兼容和敏感字段约束。 */
class AgentServiceContractTest {

    @Test
    void shouldExposeVersionedTrustedEnvelopeAndStableErrorContract() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/openapi/agent-service-v2.yaml")) {
            assertNotNull(input);
            JsonNode root = new YAMLMapper().readTree(input);

            assertEquals("3.0.3", root.path("openapi").asText());
            JsonNode operation = root.path("paths").path("/api/agent/v2/chat").path("post");
            assertFalse(operation.isMissingNode());
            assertEquals("#/components/schemas/AgentExecutionEnvelope",
                operation.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText());
            assertTrue(root.path("components").path("schemas").has("AgentApiError"));
            assertTrue(root.path("components").path("schemas").path("AgentChatResult").path("required").toString().contains("clientMessageId"));
            String document = root.toString().toLowerCase();
            assertFalse(document.contains("password"));
            assertFalse(document.contains("phone"));
            assertFalse(document.contains("address"));
            assertFalse(document.contains("amount"));
        }
    }
}
