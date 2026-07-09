package me.zhengjie.agent.chat;

import me.zhengjie.agent.chat.impl.MealPlanFollowUpServiceImpl;
import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.client.DiagnosisToolDataClient;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealPlanChatServiceImplTest {

    @Test
    void shouldAskForCustomerInCollectingSlotsStage() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.DIAGNOSE, slots(null, null, "2026-05-22", "LUNCH"), List.of(MissingSlot.CUSTOMER)));
        MealPlanChatServiceImpl service = service(store, extractor, request -> new DiagnosisResponse());

        AgentChatResponse response = service.chat(request(null, "查今天午餐"));

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals("请提供客户 ID 或客户编号。", response.getAssistantMessage());
        assertEquals(DiagnosisConversationState.COLLECTING_SLOTS, response.getConversationStage());
        assertEquals(List.of(MissingSlot.CUSTOMER), response.getMissingSlots());
        assertEquals(List.of("客户编号 C10001", "客户ID 1001", "清空会话"), response.getQuickReplies());
        assertNotNull(store.getOrCreate(response.getSessionId()).getConversationState());
    }

    @Test
    void shouldTransitionToDiagnosedWhenSlotsComplete() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.DIAGNOSE, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of()));
        AtomicReference<DiagnosisRequest> captured = new AtomicReference<>();
        MealPlanDiagnosisService diagnosisService = request -> {
            captured.set(request);
            DiagnosisResponse response = new DiagnosisResponse();
            response.setRequestId("diag-1");
            response.setCustomerId(1001L);
            response.setCustomerName("张三");
            response.setRecordDate(request.getRecordDate());
            response.setMealType(request.getMealType());
            response.setSummary("发现 2 个可能原因");
            response.setReasons(List.of(new DiagnosisReasonDto(), new DiagnosisReasonDto()));
            return response;
        };
        MealPlanChatServiceImpl service = service(store, extractor, diagnosisService);

        AgentChatResponse response = service.chat(request("session-1", "查 C10001 今天午餐"));
        MealPlanChatSession saved = store.getOrCreate("session-1");

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals(DiagnosisConversationState.DIAGNOSED, response.getConversationStage());
        assertEquals("已完成诊断，发现 2 个可能原因，请结合证据人工确认。", response.getAssistantMessage());
        assertEquals("C10001", captured.get().getCustomerCode());
        assertEquals("2026-05-22", captured.get().getRecordDate());
        assertEquals("LUNCH", captured.get().getMealType());
        assertEquals("发现 2 个可能原因", response.getDiagnosisResult().getSummary());
        assertEquals(DiagnosisConversationState.DIAGNOSED, saved.getConversationState().getStage());
        assertEquals(1, saved.getConversationState().getRecentDiagnosisResults().size());
        assertEquals(2, saved.getConversationState().getRecentTurns().size());
    }

    @Test
    void shouldOverrideMealTypeOnlyWhenUserChangesMealType() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(
            result(ChatIntent.DIAGNOSE, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of()),
            result(ChatIntent.DIAGNOSE, slots(null, null, null, "DINNER"), List.of())
        );
        AtomicReference<DiagnosisRequest> firstRequest = new AtomicReference<>();
        AtomicReference<DiagnosisRequest> secondRequest = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        MealPlanDiagnosisService diagnosisService = request -> {
            if (count.incrementAndGet() == 1) {
                firstRequest.set(request);
            } else {
                secondRequest.set(request);
            }
            DiagnosisResponse response = new DiagnosisResponse();
            response.setRequestId("diag-" + count.get());
            response.setSummary("诊断完成");
            response.setReasons(List.of(new DiagnosisReasonDto()));
            return response;
        };
        MealPlanChatServiceImpl service = service(store, extractor, diagnosisService);

        service.chat(request("session-1", "查 C10001 今天午餐"));
        AgentChatResponse response = service.chat(request("session-1", "换成晚餐"));

        assertEquals("C10001", firstRequest.get().getCustomerCode());
        assertEquals("2026-05-22", firstRequest.get().getRecordDate());
        assertEquals("LUNCH", firstRequest.get().getMealType());
        assertEquals("C10001", secondRequest.get().getCustomerCode());
        assertEquals("2026-05-22", secondRequest.get().getRecordDate());
        assertEquals("DINNER", secondRequest.get().getMealType());
        assertEquals("DINNER", response.getSlots().getMealType());
        assertEquals(DiagnosisConversationState.DIAGNOSED, response.getConversationStage());
    }

    @Test
    void shouldRediagnoseWhenRetryIntentKeepsExistingSlots() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(
            result(ChatIntent.DIAGNOSE, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of()),
            result(ChatIntent.RETRY, new DiagnosisSlots(), List.of())
        );
        AtomicInteger count = new AtomicInteger();
        MealPlanDiagnosisService diagnosisService = request -> {
            int current = count.incrementAndGet();
            DiagnosisResponse response = new DiagnosisResponse();
            response.setRequestId("diag-" + current);
            response.setSummary("第 " + current + " 次诊断");
            response.setReasons(List.of(new DiagnosisReasonDto()));
            return response;
        };
        MealPlanChatServiceImpl service = service(store, extractor, diagnosisService);

        service.chat(request("session-1", "查 C10001 今天午餐"));
        AgentChatResponse response = service.chat(request("session-1", "重新排查"));
        MealPlanChatSession saved = store.getOrCreate("session-1");

        assertEquals(2, count.get());
        assertEquals("第 2 次诊断", response.getDiagnosisResult().getSummary());
        assertEquals(DiagnosisConversationState.DIAGNOSED, response.getConversationStage());
        assertEquals(2, saved.getConversationState().getRecentDiagnosisResults().size());
        assertEquals("第 2 次诊断", saved.getConversationState().getLastDiagnosisResult().getSummary());
    }

    @Test
    void shouldReturnEvidenceSummaryDuringFollowUp() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(
            result(ChatIntent.DIAGNOSE, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of()),
            result(ChatIntent.FOLLOW_UP, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of())
        );
        MealPlanDiagnosisService diagnosisService = request -> {
            DiagnosisReasonDto reason = new DiagnosisReasonDto();
            reason.setCode("CANDIDATE_DISH_EMPTY");
            reason.setTitle("候选菜为空");
            reason.setDescription("候选菜统计结果为空，当前没有可下发菜品。");
            reason.setSuggestion("请核对菜单配置和忌口过滤规则。");
            reason.setEvidence(List.of(new DiagnosisEvidenceDto("candidateCount", "0")));
            DiagnosisResponse response = new DiagnosisResponse();
            response.setRequestId("diag-1");
            response.setSummary("候选菜为空");
            response.setReasons(List.of(reason));
            return response;
        };
        MealPlanChatServiceImpl service = service(store, extractor, diagnosisService);

        service.chat(request("session-1", "查 C10001 今天午餐"));
        AgentChatResponse response = service.chat(request("session-1", "为什么候选菜为空？"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals(DiagnosisConversationState.FOLLOWING_UP, response.getConversationStage());
        assertTrue(response.getAssistantMessage().contains("候选菜为空"));
        assertTrue(response.getAssistantMessage().contains("candidateCount=0"));
        assertNotNull(response.getDiagnosisResult());
    }

    @Test
    void shouldAskForConfirmationWhenCustomerConfidenceIsLow() {
        InMemoryMealPlanChatSessionStore store = store();
        DiagnosisSlots slots = slots(null, "123", "2026-05-22", "LUNCH");
        slots.setCustomerConfidence("LOW");
        slots.setRecordDateConfidence("HIGH");
        slots.setMealTypeConfidence("HIGH");
        ChatExtractionResult extractionResult = result(ChatIntent.DIAGNOSE, slots, List.of());
        extractionResult.setAmbiguousSlots(List.of(MissingSlot.CUSTOMER));
        MealPlanChatServiceImpl service = service(store, new StubExtractor(extractionResult), request -> {
            throw new AssertionError("low confidence customer should not trigger diagnosis");
        });

        AgentChatResponse response = service.chat(request("session-1", "客户 123 今天午餐"));

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals("请确认客户标识，回复客户ID或客户编号。", response.getAssistantMessage());
        assertEquals("LOW", response.getSlotConfidence().get("customer"));
        assertEquals(List.of("客户编号 C10001", "客户ID 1001", "清空会话"), response.getQuickReplies());
    }

    @Test
    void shouldCapRecentTurnsAndDiagnosisHistory() {
        InMemoryMealPlanChatSessionStore store = store();
        List<ChatExtractionResult> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(result(ChatIntent.DIAGNOSE, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of()));
        }
        StubExtractor extractor = new StubExtractor(results.toArray(new ChatExtractionResult[0]));
        AtomicInteger count = new AtomicInteger();
        MealPlanDiagnosisService diagnosisService = request -> {
            int current = count.incrementAndGet();
            DiagnosisResponse response = new DiagnosisResponse();
            response.setRequestId("diag-" + current);
            response.setSummary("诊断-" + current);
            response.setReasons(List.of(new DiagnosisReasonDto()));
            return response;
        };
        MealPlanChatServiceImpl service = service(store, extractor, diagnosisService);

        for (int i = 0; i < 6; i++) {
            service.chat(request("session-1", "第 " + i + " 次"));
        }

        MealPlanChatSession saved = store.getOrCreate("session-1");
        assertEquals(10, saved.getConversationState().getRecentTurns().size());
        assertEquals(3, saved.getConversationState().getRecentDiagnosisResults().size());
        assertEquals("诊断-4", saved.getConversationState().getRecentDiagnosisResults().get(0).getSummary());
        assertEquals("诊断-6", saved.getConversationState().getRecentDiagnosisResults().get(2).getSummary());
    }

    @Test
    void shouldResetConversationStateWhenClearingSession() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(
            result(ChatIntent.DIAGNOSE, slots(null, "C10001", "2026-05-22", "LUNCH"), List.of()),
            result(ChatIntent.RESET, new DiagnosisSlots(), List.of())
        );
        MealPlanChatServiceImpl service = service(store, extractor, request -> {
            DiagnosisResponse response = new DiagnosisResponse();
            response.setRequestId("diag-1");
            response.setSummary("诊断完成");
            response.setReasons(List.of(new DiagnosisReasonDto()));
            return response;
        });

        service.chat(request("session-1", "查 C10001 今天午餐"));
        AgentChatResponse response = service.chat(request("session-1", "清空会话"));
        MealPlanChatSession saved = store.getOrCreate("session-1");

        assertEquals(ChatStatus.RESET, response.getStatus());
        assertEquals(DiagnosisConversationState.RESET, response.getConversationStage());
        assertEquals(List.of(MissingSlot.CUSTOMER), response.getMissingSlots());
        assertNull(saved.getSlots().getCustomerCode());
        assertNull(saved.getConversationState().getLastDiagnosisResult());
        assertEquals(1, saved.getConversationState().getRecentTurns().size());
    }

    @Test
    void shouldAnswerCustomerMealSummaryWithoutAskingDateAndMealType() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY,
            slots(null, "B3303", null, null), List.of()));
        MealPlanChatServiceImpl service = service(store, extractor, request -> new DiagnosisResponse(),
            new StubDiagnosisToolDataClient() {
                @Override
                public Map<String, Object> getCustomerMealSummary(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightMealRequest request) {
                    return Map.of(
                        "present", true,
                        "customerCode", "B3303",
                        "customerName", "张三",
                        "activeOrderCount", 2,
                        "remainingBreakfast", 3,
                        "remainingLunchDinner", 18,
                        "totalRemaining", 21,
                        "verifiedBreakfast", 7,
                        "verifiedLunch", 12,
                        "verifiedDinner", 10
                    );
                }
            });

        AgentChatResponse response = service.chat(request("session-1", "B3303 这个客户还剩多少餐数"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("CUSTOMER_MEAL_SUMMARY", response.getResponseType());
        assertEquals(List.of(), response.getMissingSlots());
        assertTrue(response.getAssistantMessage().contains("合计剩余 21 餐"));
    }

    @Test
    void shouldAskOnlyForCustomerWhenInsightSlotsMissing() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY, new DiagnosisSlots(), List.of()));
        MealPlanChatServiceImpl service = service(store, extractor, request -> new DiagnosisResponse());

        AgentChatResponse response = service.chat(request("session-1", "还剩多少餐"));

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals(List.of(MissingSlot.CUSTOMER), response.getMissingSlots());
        assertEquals("SLOT_REQUIRED", response.getResponseType());
        assertEquals("请提供客户编号，例如：C10001。", response.getAssistantMessage());
    }

    @Test
    void shouldPassActiveOrderFilterForCustomerOrderQuery() {
        InMemoryMealPlanChatSessionStore store = store();
        DiagnosisSlots slots = slots(null, "B3303", null, null);
        slots.setOrderStatus(1);
        StubExtractor extractor = new StubExtractor(result(ChatIntent.CUSTOMER_ORDER_QUERY, slots, List.of()));
        AtomicReference<Integer> capturedOrderStatus = new AtomicReference<>();
        MealPlanChatServiceImpl service = service(store, extractor, request -> new DiagnosisResponse(),
            new StubDiagnosisToolDataClient() {
                @Override
                public Map<String, Object> getCustomerOrderSummary(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightOrderRequest request) {
                    capturedOrderStatus.set(request.getOrderStatus());
                    return Map.of("present", true, "customerCode", "B3303", "orders", List.of(Map.of("status", 1)));
                }
            });

        AgentChatResponse response = service.chat(request("session-1", "B3303 有哪些进行中订单"));

        assertEquals(Integer.valueOf(1), capturedOrderStatus.get());
        assertEquals("CUSTOMER_ORDER_SUMMARY", response.getResponseType());
    }

    private MealPlanChatServiceImpl service(InMemoryMealPlanChatSessionStore store,
                                            MealPlanChatExtractor extractor,
                                            MealPlanDiagnosisService diagnosisService) {
        return service(store, extractor, diagnosisService, new StubDiagnosisToolDataClient());
    }

    private MealPlanChatServiceImpl service(InMemoryMealPlanChatSessionStore store,
                                            MealPlanChatExtractor extractor,
                                            MealPlanDiagnosisService diagnosisService,
                                            DiagnosisToolDataClient dataClient) {
        return new MealPlanChatServiceImpl(store, extractor, diagnosisService, new MealPlanFollowUpServiceImpl(), dataClient);
    }

    private InMemoryMealPlanChatSessionStore store() {
        return new InMemoryMealPlanChatSessionStore(
            Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneId.of("UTC")),
            Duration.ofMinutes(30)
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

    private static class StubDiagnosisToolDataClient implements DiagnosisToolDataClient {
        @Override
        public Map<String, Object> getCustomerProfile(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest request) {
            return Map.of();
        }

        @Override
        public List<Map<String, Object>> listCustomerOrders(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest request) {
            return List.of();
        }

        @Override
        public Map<String, Object> getMealPlan(me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest request) {
            return Map.of();
        }

        @Override
        public List<Map<String, Object>> getCandidateDishStats(me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest request) {
            return List.of();
        }

        @Override
        public Map<String, Object> getCustomerExcludeDates(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest request) {
            return Map.of();
        }

        @Override
        public Map<String, Object> getOrderMealBalance(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest request) {
            return Map.of();
        }

        @Override
        public Map<String, Object> getPackageSpec(me.zhengjie.agent.domain.dto.DiagnosisToolPackageSpecRequest request) {
            return Map.of();
        }

        @Override
        public List<Map<String, Object>> getDishCandidateDetail(me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest request) {
            return List.of();
        }

        @Override
        public List<Map<String, Object>> listVerificationLogs(me.zhengjie.agent.domain.dto.DiagnosisToolVerificationLogsRequest request) {
            return List.of();
        }

        @Override
        public List<Map<String, Object>> listMealRefunds(me.zhengjie.agent.domain.dto.DiagnosisToolMealRefundsRequest request) {
            return List.of();
        }

        @Override
        public Map<String, Object> getMealPlanGenerationSnapshot(me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest request) {
            return Map.of();
        }

        @Override
        public Map<String, Object> getCustomerMealSummary(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightMealRequest request) {
            return Map.of();
        }

        @Override
        public Map<String, Object> getCustomerVerificationSummary(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightVerificationRequest request) {
            return Map.of();
        }

        @Override
        public Map<String, Object> getCustomerOrderSummary(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightOrderRequest request) {
            return Map.of();
        }
    }
}
