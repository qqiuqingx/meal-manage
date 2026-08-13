package me.zhengjie.agent.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.application.conversation.ConversationPatch;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.presentation.PresentationDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v2 聊天 DTO 只保留受控工具事实、卡片和会话摘要字段。
 */
class AgentChatDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 工具卡片和事实摘要必须可以稳定往返序列化。
     */
    @Test
    void shouldSerializeToolCardsAndTraceSummary() throws Exception {
        AgentChatResponse response = new AgentChatResponse();
        response.setRequestId("request-1");
        response.setSessionId("session-1");
        response.setClientMessageId("message-1");
        response.setStatus(ChatStatus.ANSWERED);
        response.setAssistantMessage("已完成查询");
        response.setMissingSlots(List.of(MissingSlot.MEAL_TYPE));
        response.setQuickReplies(List.of("早餐", "午餐", "晚餐"));
        response.setCards(List.of(Map.of(
            "type", "SERVICE_CUSTOMER_LIST",
            "sourceToolCallId", "call-1",
            "data", Map.of("items", List.of(Map.of("customerCode", "C10001"))))));
        response.setToolFacts(List.of(Map.of("callId", "call-1", "toolName", "searchServiceCustomers")));
        response.setToolTraceSummary(List.of(Map.of("toolName", "searchServiceCustomers", "status", "SUCCESS")));
        response.setWarnings(List.of("结果可能已截断"));
        response.setPartial(true);

        AgentChatResponse parsed = objectMapper.readValue(
            objectMapper.writeValueAsString(response), AgentChatResponse.class);

        assertEquals("request-1", parsed.getRequestId());
        assertEquals("message-1", parsed.getClientMessageId());
        assertEquals(ChatStatus.ANSWERED, parsed.getStatus());
        assertEquals(List.of(MissingSlot.MEAL_TYPE), parsed.getMissingSlots());
        assertEquals(List.of("早餐", "午餐", "晚餐"), parsed.getQuickReplies());
        assertEquals("SERVICE_CUSTOMER_LIST", parsed.getCards().get(0).get("type"));
        assertEquals("searchServiceCustomers", parsed.getToolFacts().get(0).get("toolName"));
        assertEquals("SUCCESS", parsed.getToolTraceSummary().get(0).get("status"));
        assertEquals(List.of("结果可能已截断"), parsed.getWarnings());
    }

    /** 展示描述必须可以和卡片一起序列化往返，且保持系统来源和字段引用。 */
    @Test
    void shouldSerializePresentationDescriptor() throws Exception {
        AgentChatResponse response = new AgentChatResponse();
        response.setPresentations(List.of(new PresentationDescriptor(
            "v1", "call-1", "SERVICE_CUSTOMER_LIST", PresentationDescriptor.DecisionSource.SYSTEM,
            "服务客户订单", PresentationDescriptor.Layout.TABS, PresentationDescriptor.View.TABLE,
            List.of(PresentationDescriptor.View.TABLE), null,
            new PresentationDescriptor.Table("items", List.of(
                new PresentationDescriptor.Field("customerCode", "客户编号", PresentationDescriptor.Format.TEXT)), List.of()),
            null)));

        AgentChatResponse parsed = objectMapper.readValue(
            objectMapper.writeValueAsString(response), AgentChatResponse.class);

        assertEquals(1, parsed.getPresentations().size());
        assertEquals("call-1", parsed.getPresentations().get(0).sourceToolCallId());
        assertEquals(PresentationDescriptor.DecisionSource.SYSTEM,
            parsed.getPresentations().get(0).decisionSource());
        assertEquals("customerCode", parsed.getPresentations().get(0).table().columns().get(0).field());
    }

    /**
     * 主系统下发的工具白名单和受控会话摘要必须能被 Agent 读取，空会话保持可选。
     */
    @Test
    void shouldDeserializeTrustedRequestSummary() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
            "sessionId", "session-1",
            "clientMessageId", "message-1",
            "message", "查询客户",
            "availableTools", List.of("searchCustomerProfiles"),
            "formDraftContext", Map.of("draftId", "afd_1234567890abcdef", "revision", 2),
            "lastBusinessQueryContext", Map.of("toolName", "searchCustomerProfiles")));

        AgentChatRequest parsed = objectMapper.readValue(json, AgentChatRequest.class);

        assertEquals("session-1", parsed.getSessionId());
        assertEquals("message-1", parsed.getClientMessageId());
        assertEquals("查询客户", parsed.getMessage());
        assertEquals(List.of("searchCustomerProfiles"), parsed.getAvailableTools());
        assertEquals("searchCustomerProfiles", parsed.getLastBusinessQueryContext().get("toolName"));
        assertEquals(2, parsed.getFormDraftContext().get("revision"));
        assertNull(parsed.getContextSlots());
    }

    /**
     * 会话 Patch 仅传递受控摘要，不携带固定业务规划类型。
     */
    @Test
    void shouldSerializeConversationPatchWithControlledMaps() throws Exception {
        ConversationPatch patch = new ConversationPatch(
            null,
            "ANSWERED",
            Map.of("toolName", "listMealPlans"));

        String json = objectMapper.writeValueAsString(patch);

        assertTrue(json.contains("\"conversationStage\":\"ANSWERED\""));
        assertTrue(json.contains("\"toolName\":\"listMealPlans\""));
    }
}
