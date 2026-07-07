package me.zhengjie.agent.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentChatDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeChatResponseWithSlotsAndStatus() throws Exception {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerCode("C10001");
        slots.setRecordDate("2026-05-22");
        slots.setMealType("LUNCH");

        AgentChatResponse response = new AgentChatResponse();
        response.setRequestId("request-1");
        response.setSessionId("session-1");
        response.setStatus(ChatStatus.NEED_MORE_INFO);
        response.setAssistantMessage("请提供客户 ID 或客户编号。");
        response.setSlots(slots);
        response.setSlotConfidence(Map.of("customer", "HIGH"));
        response.setMissingSlots(List.of(MissingSlot.CUSTOMER));
        response.setQuickReplies(List.of("今天", "明天", "午餐"));
        response.setConversationStage("COLLECTING_SLOTS");

        String json = objectMapper.writeValueAsString(response);
        AgentChatResponse parsed = objectMapper.readValue(json, AgentChatResponse.class);

        assertEquals("request-1", parsed.getRequestId());
        assertEquals("session-1", parsed.getSessionId());
        assertEquals(ChatStatus.NEED_MORE_INFO, parsed.getStatus());
        assertEquals("C10001", parsed.getSlots().getCustomerCode());
        assertEquals("2026-05-22", parsed.getSlots().getRecordDate());
        assertEquals("LUNCH", parsed.getSlots().getMealType());
        assertEquals("HIGH", parsed.getSlotConfidence().get("customer"));
        assertEquals(List.of(MissingSlot.CUSTOMER), parsed.getMissingSlots());
        assertEquals(List.of("今天", "明天", "午餐"), parsed.getQuickReplies());
        assertEquals("COLLECTING_SLOTS", parsed.getConversationStage());
    }

    @Test
    void shouldSerializeExtractionResult() throws Exception {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerId(1001L);
        slots.setCustomerConfidence("HIGH");
        slots.setCustomerSource("EXPLICIT_INPUT");

        ChatExtractionResult result = new ChatExtractionResult();
        result.setIntent(ChatIntent.DIAGNOSE);
        result.setSlots(slots);
        result.setMissingSlots(List.of(MissingSlot.RECORD_DATE, MissingSlot.MEAL_TYPE));
        result.setAmbiguousSlots(List.of(MissingSlot.CUSTOMER));
        result.setReply("");

        String json = objectMapper.writeValueAsString(result);
        ChatExtractionResult parsed = objectMapper.readValue(json, ChatExtractionResult.class);

        assertEquals(ChatIntent.DIAGNOSE, parsed.getIntent());
        assertEquals(1001L, parsed.getSlots().getCustomerId());
        assertEquals("HIGH", parsed.getSlots().getCustomerConfidence());
        assertEquals("EXPLICIT_INPUT", parsed.getSlots().getCustomerSource());
        assertEquals(List.of(MissingSlot.RECORD_DATE, MissingSlot.MEAL_TYPE), parsed.getMissingSlots());
        assertEquals(List.of(MissingSlot.CUSTOMER), parsed.getAmbiguousSlots());
        assertEquals("", parsed.getReply());
    }

    @Test
    void shouldDeserializeNullableSessionIdRequest() throws Exception {
        AgentChatRequest request = objectMapper.readValue("{\"sessionId\":null,\"message\":\"查 C10001 明天午餐\"}", AgentChatRequest.class);

        assertNull(request.getSessionId());
        assertEquals("查 C10001 明天午餐", request.getMessage());
    }
}
