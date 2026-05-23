package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealPlanChatServiceImplTest {

    @Test
    void shouldAskForCustomerWhenMissingCustomer() {
        StubExtractor extractor = new StubExtractor(result(ChatIntent.DIAGNOSE, slots(null, null, "2026-05-22", "LUNCH"), List.of(MissingSlot.CUSTOMER)));
        MealPlanChatServiceImpl service = service(extractor, request -> new DiagnosisResponse());

        AgentChatResponse response = service.chat(request(null, "查今天午餐"));

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals("请提供客户 ID 或客户编号。", response.getAssistantMessage());
        assertNotNull(response.getSessionId());
        assertNull(response.getDiagnosisResult());
        assertTrue(response.getQuickReplies().contains("重新排查"));
    }

    @Test
    void shouldAskForDateWhenMissingDate() {
        StubExtractor extractor = new StubExtractor(result(ChatIntent.DIAGNOSE, slots(null, "C10001", null, "LUNCH"), List.of(MissingSlot.RECORD_DATE)));
        MealPlanChatServiceImpl service = service(extractor, request -> new DiagnosisResponse());

        AgentChatResponse response = service.chat(request("session-1", "客户 C10001 午餐"));

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals("请补充要排查的日期，例如今天、明天或 2026-05-22。", response.getAssistantMessage());
    }

    @Test
    void shouldAskForMealTypeWhenMissingMealType() {
        StubExtractor extractor = new StubExtractor(result(ChatIntent.DIAGNOSE, slots(null, "C10001", "2026-05-22", null), List.of(MissingSlot.MEAL_TYPE)));
        MealPlanChatServiceImpl service = service(extractor, request -> new DiagnosisResponse());

        AgentChatResponse response = service.chat(request("session-1", "客户 C10001 今天"));

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals("请补充餐次：早餐、午餐还是晚餐？", response.getAssistantMessage());
        assertEquals(List.of("早餐", "午餐", "晚餐", "重新排查"), response.getQuickReplies());
    }

    @Test
    void shouldCallDiagnosisWhenSlotsComplete() {
        StubExtractor extractor = new StubExtractor(result(ChatIntent.DIAGNOSE, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of()));
        AtomicReference<DiagnosisRequest> captured = new AtomicReference<>();
        MealPlanDiagnosisService diagnosisService = request -> {
            captured.set(request);
            DiagnosisResponse response = new DiagnosisResponse();
            response.setCustomerId(1001L);
            response.setCustomerName("张三");
            response.setRecordDate(request.getRecordDate());
            response.setMealType(request.getMealType());
            response.setSummary("发现 2 个可能原因");
            response.setReasons(List.of(new DiagnosisReasonDto(), new DiagnosisReasonDto()));
            return response;
        };
        MealPlanChatServiceImpl service = service(extractor, diagnosisService);

        AgentChatResponse response = service.chat(request(null, "查 C10001 今天午餐"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("已完成诊断，发现 2 个可能原因，请结合证据人工确认。", response.getAssistantMessage());
        assertEquals("C10001", captured.get().getCustomerCode());
        assertEquals("2026-05-22", captured.get().getRecordDate());
        assertEquals("LUNCH", captured.get().getMealType());
        assertEquals("发现 2 个可能原因", response.getDiagnosisResult().getSummary());
        assertEquals(List.of("继续追问", "重新排查", "清空会话"), response.getQuickReplies());
    }

    @Test
    void shouldUseActualReasonCountInDiagnosisMessage() {
        StubExtractor extractor = new StubExtractor(result(ChatIntent.DIAGNOSE, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of()));
        MealPlanChatServiceImpl service = service(extractor, request -> {
            DiagnosisResponse response = new DiagnosisResponse();
            response.setSummary("暂无明显异常");
            response.setReasons(List.of());
            return response;
        });

        AgentChatResponse response = service.chat(request("session-1", "查 C10001 今天午餐"));

        assertEquals("已完成诊断，发现 0 个可能原因，请结合证据人工确认。", response.getAssistantMessage());
    }

    @Test
    void shouldResetSession() {
        StubExtractor extractor = new StubExtractor(result(ChatIntent.RESET, new DiagnosisSlots(), List.of()));
        MealPlanChatServiceImpl service = service(extractor, request -> new DiagnosisResponse());

        AgentChatResponse response = service.chat(request("session-1", "清空会话"));

        assertEquals(ChatStatus.RESET, response.getStatus());
        assertEquals("会话已清空，请重新提供客户、日期和餐次。", response.getAssistantMessage());
        assertEquals("session-1", response.getSessionId());
    }

    @Test
    void shouldReturnOutOfScopeMessage() {
        StubExtractor extractor = new StubExtractor(result(ChatIntent.OUT_OF_SCOPE, new DiagnosisSlots(), List.of()));
        MealPlanChatServiceImpl service = service(extractor, request -> new DiagnosisResponse());

        AgentChatResponse response = service.chat(request("session-1", "帮我改订单地址"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("我目前只能处理排餐诊断，请提供客户、日期和餐次。", response.getAssistantMessage());
        assertNull(response.getDiagnosisResult());
    }

    @Test
    void shouldAnswerFollowUpFromLastDiagnosisResult() {
        StubExtractor extractor = new StubExtractor(
            result(ChatIntent.DIAGNOSE, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of()),
            result(ChatIntent.FOLLOW_UP, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of())
        );
        MealPlanChatServiceImpl service = service(extractor, request -> {
            DiagnosisResponse response = new DiagnosisResponse();
            response.setSummary("订单无效，客户无有效午餐订单。");
            return response;
        });
        AgentChatResponse first = service.chat(request("session-1", "查 C10001 今天午餐"));

        AgentChatResponse followUp = service.chat(request(first.getSessionId(), "为什么订单无效？"));

        assertEquals(ChatStatus.ANSWERED, followUp.getStatus());
        assertEquals("上次诊断摘要：订单无效，客户无有效午餐订单。请结合结果卡片中的原因、建议和证据继续人工确认。", followUp.getAssistantMessage());
        assertNull(followUp.getDiagnosisResult());
    }

    private MealPlanChatServiceImpl service(MealPlanChatExtractor extractor, MealPlanDiagnosisService diagnosisService) {
        return new MealPlanChatServiceImpl(
            new InMemoryMealPlanChatSessionStore(Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneId.of("UTC")), Duration.ofMinutes(30)),
            extractor,
            diagnosisService
        );
    }

    private AgentChatRequest request(String sessionId, String message) {
        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId(sessionId);
        request.setMessage(message);
        return request;
    }

    private ChatExtractionResult result(ChatIntent intent, DiagnosisSlots slots, List<MissingSlot> missingSlots) {
        ChatExtractionResult result = new ChatExtractionResult();
        result.setIntent(intent);
        result.setSlots(slots);
        result.setMissingSlots(missingSlots);
        return result;
    }

    private DiagnosisSlots slots(Long customerId, String customerCode, String recordDate, String mealType) {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerId(customerId);
        slots.setCustomerCode(customerCode);
        slots.setRecordDate(recordDate);
        slots.setMealType(mealType);
        return slots;
    }

    private static class StubExtractor implements MealPlanChatExtractor {
        private final List<ChatExtractionResult> results;

        private StubExtractor(ChatExtractionResult... results) {
            this.results = new ArrayList<>(List.of(results));
        }

        @Override
        public ChatExtractionResult extract(String message, DiagnosisSlots existingSlots) {
            return results.remove(0);
        }
    }
}
