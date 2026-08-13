package me.zhengjie.agent.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import me.zhengjie.agent.api.contract.AgentExecutionEnvelope;
import me.zhengjie.agent.api.contract.ChatMessageRequest;
import me.zhengjie.agent.application.conversation.ConversationPatch;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.presentation.PresentationDescriptor;
import me.zhengjie.agent.presentation.PresentationRegistry;
import me.zhengjie.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
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
            assertTrue(resultSchema.path("properties").has("formDraftSummary"));
            assertEquals(Set.of("OPEN_CREATE_CUSTOMER_WITH_ORDER_FORM", "OPEN_CREATE_ORDER_FORM", "CONVERT_TO_CREATE_CUSTOMER_WITH_ORDER"),
                enumValues(root.path("components").path("schemas").path("AgentUiAction")
                    .path("properties").path("type")));
            assertEquals(Set.of("ANSWERED", "NEED_MORE_INFO", "ERROR"),
                enumValues(resultSchema.path("properties").path("status")));
            JsonNode missingSlots = resultSchema.path("properties").path("missingSlots");
            assertEquals(4, missingSlots.path("maxItems").asInt());
            assertTrue(missingSlots.path("uniqueItems").asBoolean(false));
            assertEquals(Set.of("CUSTOMER_OR_ORDER", "RECORD_DATE", "DATE_RANGE", "MEAL_TYPE", "PACKAGE", "RULE_TOPIC"),
                enumValues(missingSlots.path("items")));
            JsonNode quickReplies = resultSchema.path("properties").path("quickReplies");
            assertEquals(6, quickReplies.path("maxItems").asInt());
            assertTrue(quickReplies.path("uniqueItems").asBoolean(false));
            assertEquals(20, quickReplies.path("items").path("maxLength").asInt());
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

    /** 结构化展示契约必须声明严格对象、受控来源/视图枚举及服务客户固定列顺序。 */
    @Test
    void structuredPresentationContractMustRemainStrictAndStable() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/openapi/agent-service-v2.yaml")) {
            JsonNode schemas = new YAMLMapper().readTree(input).path("components").path("schemas");
            JsonNode result = schemas.path("AgentChatResult");
            JsonNode presentations = result.path("properties").path("presentations");
            assertEquals("#/components/schemas/PresentationDescriptor",
                presentations.path("items").path("$ref").asText());
            assertEquals(100, presentations.path("maxItems").asInt());

            JsonNode descriptor = schemas.path("PresentationDescriptor");
            assertFalse(descriptor.path("additionalProperties").asBoolean(true));
            assertEquals(Set.of("schemaVersion", "sourceToolCallId", "cardType", "decisionSource", "title",
                "layout", "defaultView", "availableViews", "summary", "table", "chart"),
                propertiesOf(descriptor));
            assertEquals(Set.of("SYSTEM", "LLM"), enumValues(descriptor.path("properties").path("decisionSource")));
            assertEquals(Set.of("TEXT", "TABLE", "BAR", "LINE", "PIE"),
                enumValues(schemas.path("PresentationView")));

            assertFalse(schemas.path("PresentationField").path("additionalProperties").asBoolean(true));
            assertFalse(schemas.path("PresentationTable").path("additionalProperties").asBoolean(true));
            assertFalse(schemas.path("PresentationChart").path("additionalProperties").asBoolean(true));
            assertEquals(Set.of("type", "dataPath", "dimensionField", "metricFields", "dimensionLabel", "metricLabels"),
                propertiesOf(schemas.path("PresentationChart")));
        }

        List<String> serviceCustomerColumns = new PresentationRegistry(new ToolRegistry())
            .require("SERVICE_CUSTOMER_LIST").template().table().columns().stream()
            .map(PresentationDescriptor.Field::field).collect(Collectors.toList());
        assertEquals(List.of("customerCode", "customerName", "orderCode", "orderTime", "status", "parentPackageName"),
            serviceCustomerColumns);
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

    /** 读取 OpenAPI 枚举值，避免契约测试只检查字符串是否出现。 */
    private Set<String> enumValues(JsonNode schema) {
        return java.util.stream.StreamSupport.stream(schema.path("enum").spliterator(), false)
            .map(JsonNode::asText).collect(Collectors.toSet());
    }
}
