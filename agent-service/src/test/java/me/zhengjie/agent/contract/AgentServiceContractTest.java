package me.zhengjie.agent.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import me.zhengjie.agent.api.contract.AgentExecutionEnvelope;
import me.zhengjie.agent.api.contract.ChatMessageRequest;
import me.zhengjie.agent.application.conversation.ConversationPatch;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
            JsonNode resultSchema = root.path("components").path("schemas").path("AgentChatResult");
            assertTrue(resultSchema.path("required").toString().contains("clientMessageId"));
            assertFalse(resultSchema.path("additionalProperties").asBoolean(true));
            assertTrue(resultSchema.path("properties").has("sessionId"));
            assertTrue(resultSchema.path("properties").has("status"));
            assertFalse(root.path("components").path("schemas").path("ConversationPatch")
                .path("properties").has("assistantMessage"));
            JsonNode errorCodes = root.path("components").path("schemas").path("AgentApiError")
                .path("properties").path("code").path("enum");
            assertTrue(errorCodes.toString().contains("SESSION_VERSION_CONFLICT"));
            assertTrue(errorCodes.toString().contains("MODEL_CAPABILITY_UNSUPPORTED"));
            String document = root.toString().toLowerCase();
            assertFalse(document.contains("password"));
            assertFalse(document.contains("phone"));
            assertFalse(document.contains("address"));
            assertFalse(document.contains("amount"));
        }
    }

    @Test
    void javaContractFieldsMustMatchOpenApiSchemas() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/openapi/agent-service-v2.yaml")) {
            JsonNode schemas = new YAMLMapper().readTree(input).path("components").path("schemas");

            assertEquals(fieldsOf(AgentExecutionEnvelope.class), propertiesOf(schemas.path("AgentExecutionEnvelope")));
            assertEquals(fieldsOf(ChatMessageRequest.class), propertiesOf(schemas.path("ChatMessageRequest")));
            assertEquals(fieldsOf(AgentChatResponse.class), propertiesOf(schemas.path("AgentChatResult")));
            Set<String> patchComponents = Arrays.stream(ConversationPatch.class.getRecordComponents())
                .map(component -> component.getName()).collect(Collectors.toSet());
            assertEquals(patchComponents, propertiesOf(schemas.path("ConversationPatch")));
        }
    }

    private Set<String> fieldsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toSet());
    }

    private Set<String> propertiesOf(JsonNode schema) {
        return java.util.stream.StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(
                schema.path("properties").fieldNames(), java.util.Spliterator.ORDERED), false)
            .collect(Collectors.toSet());
    }
}
