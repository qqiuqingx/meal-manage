package me.zhengjie.agent.chat;

import me.zhengjie.agent.analysis.BusinessQuestionAnalyzer;
import me.zhengjie.agent.analysis.BusinessTemporalResolver;
import me.zhengjie.agent.analysis.HybridBusinessQuestionAnalyzer;
import me.zhengjie.agent.analysis.RuleBasedBusinessQuestionAnalyzer;
import me.zhengjie.agent.config.BusinessTimeProperties;
import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.analysis.domain.BusinessInteractionMode;
import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.analysis.domain.MealScope;
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
import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.client.dto.DishCandidatePreviewResponse;
import me.zhengjie.agent.query.AgentQueryPlanValidationError;
import me.zhengjie.agent.query.AgentQueryPlanValidationResult;
import me.zhengjie.agent.query.AgentQueryPlanValidator;
import me.zhengjie.agent.query.BusinessAnswerValidator;
import me.zhengjie.agent.query.BusinessQueryPlanningService;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals("请提供要查询的客户姓名或编号，例如“客户 张三”或“客户编号 B3303”。", response.getAssistantMessage());
        assertEquals(DiagnosisConversationState.COLLECTING_SLOTS, response.getConversationStage());
        assertEquals(List.of(MissingSlot.CUSTOMER), response.getMissingSlots());
        assertEquals(List.of("客户 张三", "客户编号 B3303", "清空会话"), response.getQuickReplies());
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

    /** 顶层业务查询必须使用受控兼容意图进入原业务分支，不得回退为排餐诊断。 */
    @Test
    void shouldRouteTopLevelBusinessQueryThroughCompatibilityIntent() {
        InMemoryMealPlanChatSessionStore store = store();
        ChatExtractionResult extraction = result(ChatIntent.BUSINESS_QUERY, new DiagnosisSlots(), List.of());
        extraction.setRuleIntent(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY.name());
        StubExtractor extractor = new StubExtractor(extraction);
        MealPlanChatServiceImpl service = service(store, extractor, request -> new DiagnosisResponse());

        AgentChatResponse response = service.chat(request("session-business", "还剩多少餐"));

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals(List.of(MissingSlot.CUSTOMER), response.getMissingSlots());
        assertEquals("请提供客户编号，例如：C10001。", response.getAssistantMessage());
        assertEquals("SLOT_REQUIRED", response.getResponseType());
    }

    /** 无法映射到登记兼容意图的顶层查询只能澄清，不得触发诊断或自由工具调用。 */
    @Test
    void shouldClarifyUnmappedTopLevelBusinessQuery() {
        InMemoryMealPlanChatSessionStore store = store();
        ChatExtractionResult extraction = result(ChatIntent.BUSINESS_QUERY, new DiagnosisSlots(), List.of());
        extraction.setRuleIntent("UNKNOWN_QUERY");
        StubExtractor extractor = new StubExtractor(extraction);
        AtomicInteger diagnosisCalls = new AtomicInteger();
        MealPlanChatServiceImpl service = service(store, extractor, request -> { diagnosisCalls.incrementAndGet(); return new DiagnosisResponse(); });

        AgentChatResponse response = service.chat(request("session-business-unknown", "帮我看看"));

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals("BUSINESS_QUERY_CLARIFICATION", response.getResponseType());
        assertEquals(0, diagnosisCalls.get());
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

    @Test
    void shouldUseContextCustomerForTotalMealQuestionWithoutAskingDate() {
        InMemoryMealPlanChatSessionStore store = store();
        MealPlanChatExtractor extractor = new RuleBasedMealPlanChatExtractor(
            Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
        AtomicReference<String> capturedCustomerCode = new AtomicReference<>();
        MealPlanChatServiceImpl service = service(store, extractor, request -> new DiagnosisResponse(),
            new StubDiagnosisToolDataClient() {
                @Override
                public Map<String, Object> getCustomerVerificationSummary(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightVerificationRequest request) {
                    return Map.of(
                        "present", true,
                        "customerCode", "B2201",
                        "totalVerified", 8,
                        "totalVerifiedBreakfast", 1,
                        "totalVerifiedLunch", 4,
                        "totalVerifiedDinner", 3,
                        "recentVerifications", List.of()
                    );
                }

                @Override
                public Map<String, Object> getCustomerMealSummary(me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightMealRequest request) {
                    capturedCustomerCode.set(request.getCustomerCode());
                    return Map.of(
                        "present", true,
                        "customerCode", "B2201",
                        "customerName", "李四",
                        "activeOrderCount", 1,
                        "remainingBreakfast", 2,
                        "remainingLunchDinner", 10,
                        "totalRemaining", 12,
                        "verifiedBreakfast", 1,
                        "verifiedLunch", 4,
                        "verifiedDinner", 3
                    );
                }
            });

        AgentChatResponse first = service.chat(request("session-1", "B2201 看下这个客户核销了多少餐"));
        AgentChatResponse second = service.chat(request("session-1", "他一共多少餐？"));

        assertEquals("CUSTOMER_VERIFICATION_SUMMARY", first.getResponseType());
        assertEquals(ChatStatus.ANSWERED, second.getStatus());
        assertEquals("CUSTOMER_MEAL_SUMMARY", second.getResponseType());
        assertEquals("B2201", capturedCustomerCode.get());
        assertEquals(List.of(), second.getMissingSlots());
        assertTrue(second.getAssistantMessage().contains("当前有效订单总餐数 20 餐"));
    }

    @Test
    void shouldCreateTraceableFactsForBusinessOrderQuery() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.CUSTOMER_ORDER_QUERY,
            slots(3303L, "B3303", null, null), List.of()));
        BusinessQueryDataClient businessClient = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) {
                return Map.of("total", 2, "items", List.of());
            }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), businessClient);

        AgentChatResponse response = service.chat(request("session-1", "B3303 有哪些订单"));

        assertEquals("BUSINESS_QUERY_ORDER", response.getResponseType());
        assertEquals(2, response.getFacts().size());
        assertEquals("F1", response.getFacts().get(0).getFactId());
        assertEquals("订单数量", response.getFacts().get(0).getLabel());
        assertEquals(2, ((Number) response.getFacts().get(0).getValue()).intValue());
        assertEquals("ORDER_LIST", response.getFacts().get(0).getSourceType());
        assertEquals("当前展示记录数", response.getFacts().get(1).getLabel());
        assertEquals(0, ((Number) response.getFacts().get(1).getValue()).intValue());
    }

    @Test
    void shouldQueryScheduledMenuWithoutCustomerWhenDatePresent() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.SCHEDULED_MENU_QUERY,
            slots(null, null, "2026-05-22", null), List.of()));
        AtomicReference<String> capturedDate = new AtomicReference<>();
        AtomicReference<List<String>> capturedMealTypes = new AtomicReference<>();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
            @Override public Map<String, Object> listScheduledDishes(String recordDate, List<String> mealTypes) {
                capturedDate.set(recordDate);
                capturedMealTypes.set(mealTypes);
                return Map.of("recordDate", recordDate, "total", 1, "groups", List.of(
                    Map.of("mealTypeCode", "LUNCH", "mealTypeName", "午餐", "total", 1,
                        "items", List.of(Map.of("dishName", "番茄炒蛋"))),
                    Map.of("mealTypeCode", "DINNER", "mealTypeName", "晚餐", "total", 0, "items", List.of())));
            }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-scheduled-menu", "今天的菜单是什么"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_SCHEDULED_MENU", response.getResponseType());
        assertEquals("2026-05-22", capturedDate.get());
        assertEquals(List.of("LUNCH", "DINNER"), capturedMealTypes.get());
        assertEquals(List.of(), response.getMissingSlots());
        assertTrue(response.getAssistantMessage().contains("番茄炒蛋"));
        assertEquals("排期菜品数", response.getFacts().get(0).getLabel());
        assertEquals("SCHEDULED_DISH_LIST", response.getFacts().get(0).getSourceType());
    }

    @Test
    void shouldNotRepeatAnUnchangedScheduledMenuPlanDuringCorrection() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(
            result(ChatIntent.BUSINESS_QUERY, slots(null, null, "2026-07-13", null), List.of()),
            result(ChatIntent.BUSINESS_QUERY, slots(null, null, "2026-07-13", null), List.of())
        );
        AtomicInteger calls = new AtomicInteger();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
            @Override public Map<String, Object> listScheduledDishes(String recordDate, List<String> mealTypes) {
                calls.incrementAndGet();
                return Map.of("recordDate", recordDate, "total", 1, "groups", List.of(
                    Map.of("mealTypeCode", "LUNCH", "mealTypeName", "午餐", "total", 1,
                        "items", List.of(Map.of("dishName", "番茄炒蛋", "dishTypeCode", "MAIN"))),
                    Map.of("mealTypeCode", "DINNER", "mealTypeName", "晚餐", "total", 0, "items", List.of())));
            }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse first = service.chat(request("session-menu-correction", "今天菜单"));
        AgentChatResponse correction = service.chat(request("session-menu-correction", "怎么全是米饭"));

        assertEquals(ChatStatus.ANSWERED, first.getStatus());
        assertEquals(1, calls.get());
        assertEquals(ChatStatus.NEED_MORE_INFO, correction.getStatus());
        assertEquals("BUSINESS_QUERY_CORRECTION_CLARIFICATION", correction.getResponseType());
        assertTrue(correction.getAssistantMessage().contains("相同的日期和餐次口径"));
    }

    @Test
    void shouldReplanLegacySingleMenuContextWhenUserReportsRiceOnlyResult() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(
            result(ChatIntent.SCHEDULED_MENU_QUERY, slots(null, null, "2026-07-13", null), List.of()),
            result(ChatIntent.BUSINESS_QUERY, slots(null, null, "2026-07-13", null), List.of())
        );
        AtomicInteger calls = new AtomicInteger();
        BusinessQueryDataClient client = scheduledMenuClient(calls);
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        service.chat(request("session-menu-replan", "今天菜单"));
        AgentChatResponse correction = service.chat(request("session-menu-replan", "菜单查得不对，怎么全是米饭"));

        assertEquals(2, calls.get());
        assertEquals(ChatStatus.ANSWERED, correction.getStatus());
        assertTrue(correction.getAssistantMessage().contains("已重新规划查询口径"));
    }

    /** 构造返回午餐、晚餐分组的公共菜单客户端，避免测试依赖主系统网络服务。 */
    private BusinessQueryDataClient scheduledMenuClient(AtomicInteger calls) {
        return new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
            @Override public Map<String, Object> listScheduledDishes(String recordDate, List<String> mealTypes) {
                calls.incrementAndGet();
                return Map.of("recordDate", recordDate, "total", 1, "groups", List.of(
                    Map.of("mealTypeCode", "LUNCH", "mealTypeName", "午餐", "total", 1,
                        "items", List.of(Map.of("dishName", "番茄炒蛋", "dishTypeCode", "MAIN"))),
                    Map.of("mealTypeCode", "DINNER", "mealTypeName", "晚餐", "total", 0, "items", List.of())));
            }
        };
    }

    @Test
    void shouldQueryCustomerDishCandidatesWithoutCreatingMealPlan() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.DISH_CANDIDATE_QUERY,
            slots(3303L, "B3303", "2026-05-22", "LUNCH"), List.of()));
        java.util.concurrent.atomic.AtomicInteger candidateCalls = new java.util.concurrent.atomic.AtomicInteger();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { throw new AssertionError("候选预览不能查询或生成排餐记录"); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
            @Override public DishCandidatePreviewResponse previewDishCandidates(Long customerId, String recordDate, String mealType) {
                candidateCalls.incrementAndGet();
                assertEquals(3303L, customerId); assertEquals("2026-05-22", recordDate); assertEquals("LUNCH", mealType);
                DishCandidatePreviewResponse response = new DishCandidatePreviewResponse();
                response.setPresent(true); response.setCustomerId(3303L); response.setCustomerCode("B3303");
                response.setTotalCandidateCount(4); response.setAvailableCandidateCount(2); response.setFilteredCandidateCount(2);
                return response;
            }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-1", "B3303 今天午餐有哪些菜可以吃"));

        assertEquals("BUSINESS_QUERY_DISH_CANDIDATES", response.getResponseType());
        assertEquals(1, candidateCalls.get());
        assertTrue(response.getAssistantMessage().contains("2 个当前可用"));
        assertEquals("DISH", response.getQueryPlan().getDomain().name());
    }

    /** 成功查询到唯一排餐记录后，应把稳定记录 ID 回写到会话焦点供后续追问使用。 */
    @Test
    void shouldPersistMealPlanRecordFocusFromBusinessQueryResult() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.MEAL_PLAN_QUERY,
            slots(3303L, "B3303", "2026-05-22", "LUNCH"), List.of()));
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) {
                return Map.of("total", 1, "items", List.of(Map.of("customerMealPlanId", 9001L,
                    "customerId", customerId, "recordDate", recordDate, "mealTypeCode", mealType, "dishes", List.of())));
            }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-focus", "B3303 今天午餐排了吗"));

        assertEquals(9001L, response.getSlots().getMealPlanRecordId());
        assertEquals(9001L, store.getOrCreate("session-focus").getSlots().getMealPlanRecordId());
    }

    /** 仅提供客户排餐记录 ID 时，应跳过客户概览并直达受控排餐详情工具。 */
    @Test
    void shouldQueryMealPlanDirectlyByCustomerMealPlanId() {
        InMemoryMealPlanChatSessionStore store = store();
        DiagnosisSlots input = slots(null, null, null, null);
        input.setMealPlanRecordId(9001L);
        StubExtractor extractor = new StubExtractor(result(ChatIntent.MEAL_PLAN_QUERY, input, List.of()));
        AtomicInteger overviewCalls = new AtomicInteger();
        AtomicReference<Long> recordId = new AtomicReference<>();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { overviewCalls.incrementAndGet(); return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { throw new AssertionError("must use detail overload"); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType, Long customerMealPlanId) {
                recordId.set(customerMealPlanId);
                return Map.of("total", 1, "items", List.of(Map.of("customerMealPlanId", customerMealPlanId, "dishes", List.of())));
            }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-direct-plan", "排餐记录ID 9001 吃什么"));

        assertEquals("BUSINESS_QUERY_MEAL_PLAN", response.getResponseType());
        assertEquals(0, overviewCalls.get());
        assertEquals(9001L, recordId.get());
    }

    /** 日期范围问法必须将受控起止日期透传到退餐只读工具。 */
    @Test
    void shouldPassDateRangeToRefundTool() {
        InMemoryMealPlanChatSessionStore store = store();
        DiagnosisSlots input = slots(3303L, "B3303", null, null);
        input.setStartDate("2026-05-01");
        input.setEndDate("2026-05-22");
        StubExtractor extractor = new StubExtractor(result(ChatIntent.CUSTOMER_REFUND_QUERY, input, List.of()));
        AtomicReference<String> startDate = new AtomicReference<>();
        AtomicReference<String> endDate = new AtomicReference<>();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of("customerId", customerId); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { throw new AssertionError("must use range overload"); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit, String rangeStart, String rangeEnd) {
                startDate.set(rangeStart); endDate.set(rangeEnd); return Map.of("total", 0, "items", List.of());
            }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-range", "B3303 本月退过餐吗"));

        assertEquals("BUSINESS_QUERY_REFUND", response.getResponseType());
        assertEquals("2026-05-01", startDate.get());
        assertEquals("2026-05-22", endDate.get());
    }

    /** 运营统计必须由受控问题分析生成 QueryPlan 2.0 后执行，而非回退到旧字符串计划。 */
    @Test
    void shouldExecuteOperationStatisticsWithAnalyzedQueryPlan() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.OPERATION_STATISTICS_QUERY,
            slots(null, null, "2026-05-22", "LUNCH"), List.of()));
        AtomicReference<String> recordDate = new AtomicReference<>();
        AtomicReference<String> mealType = new AtomicReference<>();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
            @Override public Map<String, Object> dailyCustomerWorkload(String date, String type) {
                recordDate.set(date); mealType.set(type);
                return Map.of("recordDate", date, "mealType", type, "unverifiedCustomerCount", 3,
                    "metricDefinitionId", "AGENT_DAILY_UNVERIFIED_CUSTOMER_V1");
            }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-operation", "今天午餐待核销客户有多少"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("2026-05-22", recordDate.get());
        assertEquals("LUNCH", mealType.get());
        assertEquals("2.0", response.getQueryPlan().getVersion());
        assertEquals(me.zhengjie.agent.query.domain.AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT,
            response.getQueryPlan().getMetrics().get(0));
        assertEquals("RULE", response.getQueryPlan().getAnalysisSource());
    }

    @Test
    void shouldAnswerSystemCustomerTotalWithoutDateOrActiveCustomerGuess() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.OPERATION_STATISTICS_QUERY,
            slots(null, null, null, null), List.of()));
        BusinessQueryDataClient client = operationClient((date, mealType) -> Map.of());
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-customer-total", "现在系统中还有多少客户"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_OPERATION_CUSTOMER_TOTAL", response.getResponseType());
        assertEquals(me.zhengjie.agent.query.domain.AgentQueryMetric.CUSTOMER_PROFILE_COUNT,
            response.getQueryPlan().getMetrics().get(0));
        assertEquals(List.of("getCustomerProfileCount"), response.getQueryPlan().getToolNames());
        assertNull(response.getQueryPlan().getFilters().getRecordDate());
        assertTrue(response.getAssistantMessage().contains("客户档案总数为 12 位"));
    }

    /** 目标问题应按业务当天直接执行待排餐统计，不再追问日期或漂移到公共菜单。 */
    @Test
    void shouldExecuteCurrentDayUnscheduledCustomerQuestionWithoutClarification() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.OPERATION_STATISTICS_QUERY,
            slots(null, null, null, null), List.of()));
        AtomicReference<String> recordDate = new AtomicReference<>();
        BusinessQueryDataClient client = operationClient((date, mealType) -> {
            recordDate.set(date);
            return Map.of("recordDate", date, "expectedCustomerCount", 8, "scheduledCustomerCount", 5,
                "unscheduledCustomerCount", 3, "verifiedCustomerCount", 2, "unverifiedCustomerCount", 3,
                "mealPlanFailureCount", 0, "metricDefinitionId", "AGENT_DAILY_CUSTOMER_WORKLOAD_V1");
        });
        BusinessTimeProperties properties = new BusinessTimeProperties();
        BusinessTemporalResolver resolver = new BusinessTemporalResolver(
            Clock.fixed(Instant.parse("2026-07-14T04:00:00Z"), ZoneId.of("UTC")), properties);
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client,
            new AgentQueryPlanValidator(), new BusinessAnswerValidator(), new RuleBasedBusinessQuestionAnalyzer(),
            new BusinessQueryPlanningService(), resolver, 30);

        AgentChatResponse response = service.chat(request("target-session", "现在还有多少客户有餐数没有排餐"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("2026-07-14", recordDate.get());
        assertEquals("BUSINESS_QUERY_OPERATION_UNSCHEDULED", response.getResponseType());
        assertEquals("getDailyCustomerWorkload", response.getQueryPlan().getToolNames().get(0));
        assertTrue(response.getAssistantMessage().contains("待排餐客户数为 3 客户"));
        assertEquals("CURRENT_DAY", response.getSemanticTraceSummary().getTemporalExpression());
        assertNull(response.getPendingBusinessQueryContext());
    }

    /** 客户概览后的代词追问应直接查询全部历史，并明确回答是否曾经排过餐。 */
    @Test
    void shouldAnswerHistoricalMealPlanExistenceForContextCustomer() {
        BusinessTimeProperties properties = new BusinessTimeProperties();
        BusinessTemporalResolver resolver = new BusinessTemporalResolver(
            Clock.fixed(Instant.parse("2026-07-14T04:00:00Z"), ZoneId.of("UTC")), properties);
        AtomicReference<Long> customerId = new AtomicReference<>();
        AtomicReference<String> recordDate = new AtomicReference<>();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long id, String code, String name) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long id, String code) {
                return Map.of("present", true, "customerId", 68L, "customerCode", "B2200", "customerName", "新");
            }
            @Override public Map<String, Object> listOrders(Long id, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long id) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long id, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long id, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long id, String date, String mealType) {
                customerId.set(id); recordDate.set(date);
                return Map.of("total", 3, "items", List.of(Map.of("customerMealPlanId", 11L,
                    "customerId", id, "customerCode", "B2200", "recordDate", "2026-05-28", "mealTypeCode", "DINNER",
                    "generationStatus", "SUCCESS", "dishes", List.of(Map.of("dishId", 1, "dishName", "香菇滑鸡")))));
            }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
        BusinessQuestionAnalyzer model = (question, context) -> {
            BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
            analysis.setSource("LLM"); analysis.setConfidence(0.90D);
            if (question.contains("添加")) {
                analysis.setQueryTarget(BusinessQueryTarget.CUSTOMER);
                analysis.setDomains(List.of(AgentQueryDomain.CUSTOMER));
                analysis.getEntities().setCustomerCode("B2200");
            } else {
                analysis.setQueryTarget(BusinessQueryTarget.CUSTOMER_MEAL_PLAN);
                analysis.setInteractionMode(BusinessInteractionMode.FOLLOW_UP);
                analysis.setMealScope(MealScope.ALL_AVAILABLE);
                analysis.setDomains(List.of(AgentQueryDomain.MEAL_PLAN));
            }
            return analysis;
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store(),
            new StubExtractor(
                result(ChatIntent.BUSINESS_QUERY, slots(null, "B2200", null, null), List.of()),
                result(ChatIntent.BUSINESS_QUERY, slots(null, null, null, null), List.of())),
            request -> new DiagnosisResponse(), new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client,
            new AgentQueryPlanValidator(), new BusinessAnswerValidator(),
            new HybridBusinessQuestionAnalyzer(new RuleBasedBusinessQuestionAnalyzer(), model),
            new BusinessQueryPlanningService(), resolver, 30);

        AgentChatResponse first = service.chat(request("history-session", "B2200 这个客户是什么时候添加的"));
        AgentChatResponse second = service.chat(request("history-session", "他排过餐吗"));

        assertEquals(ChatStatus.ANSWERED, first.getStatus());
        assertEquals(ChatStatus.ANSWERED, second.getStatus());
        assertEquals("BUSINESS_QUERY_MEAL_PLAN", second.getResponseType());
        assertEquals(68L, customerId.get());
        assertNull(recordDate.get());
        assertEquals("listMealPlans", second.getQueryPlan().getToolNames().get(0));
        assertEquals(1, second.getQueryPlan().getFilters().getSize());
        assertTrue(second.getAssistantMessage().contains("曾经排过餐，共 3 条记录"));
        assertEquals("FOLLOW_UP", second.getSemanticTraceSummary().getInteractionMode());
        assertNull(second.getPendingBusinessQueryContext());
    }

    /** 客户编号查询中的模型零值占位符必须先清理，再解析真实客户 ID 后查询历史排餐。 */
    @Test
    void shouldResolveCustomerCodeWhenSemanticEntitiesContainDefaultPlaceholders() {
        AtomicReference<Long> resolvedMealPlanCustomerId = new AtomicReference<>();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long id, String code, String name) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long id, String code) {
                assertNull(id);
                assertEquals("A001", code);
                return Map.of("present", true, "customerId", 101L, "customerCode", "A001", "customerName", "测试客户");
            }
            @Override public Map<String, Object> listOrders(Long id, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long id) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long id, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long id, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long id, String date, String mealType) {
                resolvedMealPlanCustomerId.set(id);
                return Map.of("total", 0, "items", List.of());
            }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
        BusinessQuestionAnalyzer analyzer = (question, context) -> {
            BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
            analysis.setSource("LLM"); analysis.setConfidence(0.95D);
            analysis.setQueryTarget(BusinessQueryTarget.CUSTOMER_MEAL_PLAN);
            analysis.setDomains(List.of(AgentQueryDomain.MEAL_PLAN));
            analysis.setMealScope(MealScope.ALL_AVAILABLE);
            analysis.getEntities().setCustomerId(0L); analysis.getEntities().setCustomerCode("A001");
            analysis.getEntities().setCustomerName(""); analysis.getEntities().setOrderId(0L);
            analysis.getEntities().setOrderCode(""); analysis.getEntities().setMealPlanRecordId(0L);
            analysis.getEntities().setPackageId(0L); analysis.getEntities().setDishId(0L);
            return analysis;
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store(),
            new StubExtractor(result(ChatIntent.BUSINESS_QUERY, slots(null, "A001", null, null), List.of())),
            request -> new DiagnosisResponse(), new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client,
            new AgentQueryPlanValidator(), new BusinessAnswerValidator(), analyzer,
            new BusinessQueryPlanningService(), new BusinessTemporalResolver(Clock.systemUTC(), new BusinessTimeProperties()), 30);

        AgentChatResponse response = service.chat(request("a001-history-session", "A001 这个客户没有参与过排餐吗"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_MEAL_PLAN", response.getResponseType(),
            () -> "warnings=" + response.getWarnings() + ", result=" + response.getInsightResult());
        assertEquals(101L, resolvedMealPlanCustomerId.get());
        assertEquals(101L, response.getQueryPlan().getEntities().getCustomerId());
        assertEquals(1, response.getQueryPlan().getFilters().getSize());
        assertTrue(response.getWarnings().isEmpty());
        assertTrue(response.getAssistantMessage().contains("从未生成过排餐记录"), response::getAssistantMessage);
    }

    /** 无日期活跃客户总数后的名单追问应直接展开余额明细，不要求补日期。 */
    @Test
    void shouldListActiveCustomersWithoutDateForFollowUp() {
        AtomicInteger balanceCalls = new AtomicInteger();
        BusinessQueryDataClient client = new ActiveBalanceClient(operationClient((date, type) -> Map.of()), balanceCalls);
        BusinessQuestionAnalyzer analyzer = (question, context) -> {
            BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
            analysis.setSource("LLM"); analysis.setConfidence(0.95D);
            analysis.setQueryTarget(BusinessQueryTarget.CUSTOMER);
            analysis.setInteractionMode(BusinessInteractionMode.FOLLOW_UP);
            analysis.setDomains(List.of(AgentQueryDomain.OPERATION_STATISTICS));
            analysis.setMetrics(List.of(me.zhengjie.agent.query.domain.AgentQueryMetric.ACTIVE_CUSTOMER_MEAL_BALANCE_DETAIL));
            analysis.setDimensions(List.of(me.zhengjie.agent.query.domain.AgentQueryDimension.CUSTOMER));
            me.zhengjie.agent.analysis.domain.BusinessTemporalIntent temporal = new me.zhengjie.agent.analysis.domain.BusinessTemporalIntent();
            temporal.setExpression(me.zhengjie.agent.analysis.domain.BusinessTemporalExpression.INHERIT_PREVIOUS);
            analysis.setTemporal(temporal);
            return analysis;
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store(),
            new StubExtractor(result(ChatIntent.BUSINESS_QUERY, new DiagnosisSlots(), List.of())),
            request -> new DiagnosisResponse(), new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client,
            new AgentQueryPlanValidator(), new BusinessAnswerValidator(), analyzer,
            new BusinessQueryPlanningService(), new BusinessTemporalResolver(Clock.systemUTC(), new BusinessTimeProperties()), 30);
        AgentChatRequest request = request("active-list-session", "分别有谁呢");
        request.setLastBusinessQueryContext(activeCustomerContext("AGENT_ACTIVE_CUSTOMER_V1"));

        AgentChatResponse response = service.chat(request);

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_ACTIVE_CUSTOMER_BALANCES", response.getResponseType());
        assertEquals(1, balanceCalls.get());
        assertNull(response.getQueryPlan().getFilters().getRecordDate());
        assertEquals("UNSPECIFIED", response.getSemanticTraceSummary().getTemporalExpression());
        assertFalse(response.getAssistantMessage().contains("时间条件无效"));
    }

    /** 同源多指标报表只调用一次每日聚合工具，并为每个指标保留独立事实。 */
    @Test
    void shouldAnswerControlledMultiMetricOperationReport() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.OPERATION_STATISTICS_QUERY,
            slots(null, null, "2026-05-22", "LUNCH"), List.of()));
        AtomicInteger calls = new AtomicInteger();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
            @Override public Map<String, Object> dailyCustomerWorkload(String date, String type) {
                calls.incrementAndGet();
                return Map.of("recordDate", date, "mealType", type, "scheduledCustomerCount", 4,
                    "unverifiedCustomerCount", 2, "metricDefinitionId", "AGENT_DAILY_CUSTOMER_WORKLOAD_V1");
            }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-operation-report", "今天午餐已排餐和待核销客户分别多少"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_OPERATION_REPORT", response.getResponseType());
        assertEquals(1, calls.get());
        assertEquals(2, response.getQueryPlan().getMetrics().size());
        assertEquals(2, response.getFacts().size());
        assertTrue(response.getAssistantMessage().contains("已排餐客户数 4 个"));
        assertTrue(response.getAssistantMessage().contains("待核销客户数 2 个"));
    }

    @Test
    void shouldRejectAmountQueryBeforeCallingDiagnosisService() {
        InMemoryMealPlanChatSessionStore store = store();
        AtomicInteger diagnosisCalls = new AtomicInteger();
        MealPlanChatServiceImpl service = service(store, new RuleBasedMealPlanChatExtractor(), request -> {
            diagnosisCalls.incrementAndGet();
            return new DiagnosisResponse();
        });

        AgentChatResponse response = service.chat(request("session-1", "B3303 订单多少钱"));

        assertEquals("SLOT_REQUIRED", response.getResponseType());
        assertTrue(response.getAssistantMessage().contains("不在本期只读查询范围内"));
        assertEquals(0, diagnosisCalls.get());
    }

    @Test
    void shouldPreserveLlmClarificationInsteadOfGenericBusinessFallback() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.BUSINESS_QUERY,
            slots(null, null, "2026-07-13", null), List.of()));
        BusinessQuestionAnalysis analysis = allergyAnalysis(MealScope.ALL_AVAILABLE);
        analysis.setRequiresClarification(true);
        analysis.setClarificationQuestion("请确认你要查看全天排餐，还是指定某个餐次。");
        BusinessQuestionAnalyzer analyzer = (question, context) -> analysis;
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), null, new AgentQueryPlanValidator(),
            new BusinessAnswerValidator(), analyzer, new BusinessQueryPlanningService());

        AgentChatResponse response = service.chat(request("session-1", "今天排餐的客户 对哪些菜过敏"));

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals("请确认你要查看全天排餐，还是指定某个餐次。", response.getAssistantMessage());
    }

    @Test
    void shouldExecuteAllMealPlanRangeChosenByLlmSemanticAnalysis() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.BUSINESS_QUERY,
            slots(null, null, "2026-07-13", null), List.of()));
        BusinessQuestionAnalyzer analyzer = (question, context) -> allergyAnalysis(MealScope.ALL_AVAILABLE);
        AtomicReference<String> capturedMealType = new AtomicReference<>("NOT_CALLED");
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) {
                capturedMealType.set(mealType);
                return Map.of("total", 1, "page", 1, "size", 50, "truncated", false, "items", List.of(
                    Map.of("customerMealPlanId", 10L, "customerCode", "B3303", "recordDate", recordDate,
                        "mealTypeCode", "LUNCH", "dishes", List.of(Map.of("dishName", "香菇滑鸡",
                            "replaceReason", "ALLERGY", "allergyFiltered", true, "allergyReasons", List.of("鸡肉"))))));
            }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client, new AgentQueryPlanValidator(),
            new BusinessAnswerValidator(), analyzer, new BusinessQueryPlanningService());

        AgentChatResponse response = service.chat(request("session-1", "今天排餐的客户 对哪些菜过敏"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_MEAL_PLAN_ALLERGY", response.getResponseType());
        assertNull(capturedMealType.get());
        assertTrue(response.getAssistantMessage().contains("B3303"));
        assertTrue(response.getAssistantMessage().contains("香菇滑鸡"));
    }

    @Test
    void shouldExecuteCustomerMealPlanDiagnosisChosenByLlmSemanticAnalysis() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.BUSINESS_QUERY,
            slots(null, "B3303", "2026-07-13", "LUNCH"), List.of()));
        BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
        analysis.setSource("LLM"); analysis.setConfidence(0.96D);
        analysis.setQueryTarget(BusinessQueryTarget.MEAL_PLAN_DIAGNOSIS);
        analysis.setDomains(List.of(AgentQueryDomain.MEAL_PLAN));
        analysis.getEntities().setCustomerCode("B3303");
        analysis.getFilters().setRecordDate("2026-07-13"); analysis.getFilters().setMealType("LUNCH");
        BusinessQuestionAnalyzer analyzer = (question, context) -> analysis;
        AtomicReference<DiagnosisRequest> captured = new AtomicReference<>();
        MealPlanDiagnosisService diagnosisService = request -> {
            captured.set(request);
            DiagnosisResponse result = new DiagnosisResponse();
            result.setSummary("客户订单未覆盖目标日期");
            result.setReasons(List.of(new DiagnosisReasonDto()));
            return result;
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, diagnosisService,
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), null, new AgentQueryPlanValidator(),
            new BusinessAnswerValidator(), analyzer, new BusinessQueryPlanningService());

        AgentChatResponse response = service.chat(request("session-1", "B3303 今天午餐为什么没排上"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("MEAL_PLAN_DIAGNOSIS", response.getResponseType());
        assertEquals("B3303", captured.get().getCustomerCode());
        assertEquals("2026-07-13", captured.get().getRecordDate());
        assertEquals("LUNCH", captured.get().getMealType());
        assertNotNull(response.getDiagnosisResult());
    }

    @Test
    void shouldReturnControlledPartialResponseForInvalidQueryPlanBeforeCallingBusinessClient() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.CUSTOMER_ORDER_QUERY,
            slots(3303L, "B3303", null, null), List.of()));
        AtomicInteger businessCalls = new AtomicInteger();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { businessCalls.incrementAndGet(); return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { businessCalls.incrementAndGet(); return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { businessCalls.incrementAndGet(); return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { businessCalls.incrementAndGet(); return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { businessCalls.incrementAndGet(); return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { businessCalls.incrementAndGet(); return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { businessCalls.incrementAndGet(); return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { businessCalls.incrementAndGet(); return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { businessCalls.incrementAndGet(); return Map.of(); }
        };
        AgentQueryPlanValidator rejectingValidator = new AgentQueryPlanValidator() {
            @Override public AgentQueryPlanValidationResult validate(me.zhengjie.agent.query.domain.AgentQueryPlan plan) {
                return new AgentQueryPlanValidationResult(List.of(new AgentQueryPlanValidationError("toolNames", "TOOL_BUDGET_EXCEEDED", "too many")), List.of());
            }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client, rejectingValidator);

        AgentChatResponse response = service.chat(request("session-1", "B3303 有哪些订单"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_ORDER", response.getResponseType());
        assertTrue(response.isPartial());
        assertTrue(response.getWarnings().contains("PLAN_INVALID"));
        assertTrue(response.getAssistantMessage().contains("本次未执行业务查询"));
        assertEquals(0, businessCalls.get());
    }

    @Test
    void shouldReuseBusinessToolResultWithinSingleChatRound() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.MEAL_BALANCE_CHANGE_QUERY,
            slots(3303L, "B3303", null, null), List.of()));
        AtomicInteger overviewCalls = new AtomicInteger();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) {
                overviewCalls.incrementAndGet();
                return Map.of(
                    "present", true,
                    "customerId", 3303L,
                    "customerCode", "B3303",
                    "customerName", "王五",
                    "activeOrderCount", 1,
                    "mealBalance", Map.of("remainingBreakfast", 2, "remainingLunchDinner", 4)
                );
            }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) {
                return Map.of("total", 3, "items", List.of());
            }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) {
                return Map.of("total", 1, "items", List.of());
            }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-1", "B3303 餐数为什么变化"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_CUSTOMER", response.getResponseType());
        assertEquals(1, overviewCalls.get());
        assertTrue(response.isCached());
        assertTrue(response.getAssistantMessage().contains("核销记录 3 条"), response::getAssistantMessage);
    }

    @Test
    void shouldAskUserToChooseWhenCustomerNameMatchesMultipleCandidates() {
        InMemoryMealPlanChatSessionStore store = store();
        DiagnosisSlots nameSlots = slots(null, null, null, null);
        nameSlots.setCustomerName("张三");
        StubExtractor extractor = new StubExtractor(result(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY, nameSlots, List.of()));
        AtomicInteger resolveCalls = new AtomicInteger();
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) {
                resolveCalls.incrementAndGet();
                return Map.of("total", 2, "items", List.of(
                    Map.of("customerId", 1001L, "customerCode", "B1001", "customerName", "张三", "maskedPhone", "138****0001"),
                    Map.of("customerId", 1002L, "customerCode", "B1002", "customerName", "张三", "maskedPhone", "138****0002")
                ));
            }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { throw new AssertionError("should wait for user selection"); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-1", "客户姓名 张三 还剩多少餐"));

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals("BUSINESS_QUERY_CUSTOMER_CANDIDATES", response.getResponseType());
        assertEquals(1, resolveCalls.get());
        assertEquals(2, ((Number) response.getInsightResult().get("total")).intValue());
        assertTrue(response.getAssistantMessage().contains("多个同名客户"));
    }

    /** 新协议必须在已登记活跃客户集合上执行固定余额明细计划，而非回退到单客户追问。 */
    @Test
    void shouldExecuteRegisteredActiveCustomerBalanceFrame() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.BUSINESS_QUERY, new DiagnosisSlots(), List.of()));
        AtomicInteger balanceCalls = new AtomicInteger();
        BusinessQueryDataClient client = operationClient((date, type) -> Map.of());
        client = new ActiveBalanceClient(client, balanceCalls);
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);
        service.configureConversationUnderstanding((message, slots, handles) -> activeCustomerBalanceUnderstanding(),
            new me.zhengjie.agent.analysis.ConversationUnderstandingValidator(), new me.zhengjie.agent.query.MultiIntentPlanningService(), "new");

        AgentChatRequest request = request("active-customer-session", "他们分别还剩多少餐");
        request.setLastBusinessQueryContext(activeCustomerContext("AGENT_ACTIVE_CUSTOMER_V1"));
        AgentChatResponse response = service.chat(request);

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_ACTIVE_CUSTOMER_BALANCES", response.getResponseType());
        assertEquals(1, balanceCalls.get());
        assertEquals("listActiveCustomerMealBalances", response.getQueryPlan().getToolNames().get(0));
        assertEquals(1, response.getResultBlocks().size());
        assertEquals("COMPLETED", response.getResultBlocks().get(0).getStatus());
    }

    /**
     * 线上默认 shadow 模式仍须通过规则语义复用上一轮活跃客户集合，
     * 不能因“还剩多少餐”的自然表达回退为单客户编号追问。
     */
    @Test
    void shouldExpandActiveCustomerBalancesInShadowModeForNaturalFollowUp() {
        InMemoryMealPlanChatSessionStore store = store();
        ChatExtractionResult extraction = result(ChatIntent.BUSINESS_QUERY, new DiagnosisSlots(), List.of());
        extraction.setRuleIntent(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY.name());
        AtomicInteger balanceCalls = new AtomicInteger();
        BusinessQueryDataClient client = new ActiveBalanceClient(operationClient((date, type) -> Map.of()), balanceCalls);
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, new StubExtractor(extraction),
            request -> new DiagnosisResponse(), new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatRequest request = request("active-customer-shadow-session", "他们分别还剩多少餐呢");
        request.setLastBusinessQueryContext(activeCustomerContext("AGENT_ACTIVE_CUSTOMER_V1"));
        AgentChatResponse response = service.chat(request);

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_ACTIVE_CUSTOMER_BALANCES", response.getResponseType());
        assertEquals(1, balanceCalls.get());
        assertTrue(response.getAssistantMessage().contains("各自的早餐、午晚餐和合计剩余餐数"));
    }

    /** 客户档案创建和首次购买时间应直接走客户概览，不再返回业务类别澄清。 */
    @Test
    void shouldAnswerCustomerCreationAndPurchaseTime() {
        InMemoryMealPlanChatSessionStore store = store();
        BusinessQueryDataClient base = operationClient((date, type) -> Map.of());
        BusinessQueryDataClient client = new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long id, String code, String name) { return base.resolveCustomer(id, code, name); }
            @Override public Map<String, Object> customerOverview(Long id, String code) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("present", true); result.put("customerId", 68L); result.put("customerCode", "B2200");
                result.put("customerName", "新客户"); result.put("createTime", "2026-07-01T09:00:00");
                result.put("firstPurchaseTime", "2026-07-02T10:00:00"); result.put("activeOrderCount", 1);
                result.put("mealBalance", Map.of("remainingBreakfast", 0, "remainingLunchDinner", 7));
                return result;
            }
            @Override public Map<String, Object> listOrders(Long id, Integer status, int page, int size) { return base.listOrders(id, status, page, size); }
            @Override public Map<String, Object> orderDetail(Long id, String code, Long customerId) { return base.orderDetail(id, code, customerId); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return base.listVerifications(customerId, orderId, mealType, limit); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return base.listRefunds(customerId, orderId, limit); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return base.listMealPlans(customerId, recordDate, mealType); }
            @Override public Map<String, Object> explainRule(String topic) { return base.explainRule(topic); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return base.listDishes(dishIds); }
        };
        StubExtractor extractor = new StubExtractor(
            result(ChatIntent.BUSINESS_QUERY, slots(null, "B2200", null, null), List.of()),
            result(ChatIntent.BUSINESS_QUERY, new DiagnosisSlots(), List.of()));
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse created = service.chat(request("customer-time-session", "B2200 这个客户是什么时候添加的"));
        AgentChatResponse purchased = service.chat(request("customer-time-session", "什么时候购买的呢"));

        assertEquals("BUSINESS_QUERY_CUSTOMER", created.getResponseType());
        assertTrue(created.getAssistantMessage().contains("客户档案创建于 2026-07-01 09:00:00"));
        assertEquals("BUSINESS_QUERY_CUSTOMER", purchased.getResponseType());
        assertTrue(purchased.getAssistantMessage().contains("首次购买于 2026-07-02 10:00:00"));
    }

    /** 集合定义或指标组合未登记时必须拒绝执行，不得将其伪装为活跃客户明细。 */
    @Test
    void shouldRejectUnregisteredContextCombinationBeforeToolExecution() {
        InMemoryMealPlanChatSessionStore store = store();
        StubExtractor extractor = new StubExtractor(result(ChatIntent.BUSINESS_QUERY, new DiagnosisSlots(), List.of()));
        AtomicInteger balanceCalls = new AtomicInteger();
        BusinessQueryDataClient client = new ActiveBalanceClient(operationClient((date, type) -> Map.of()), balanceCalls);
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);
        service.configureConversationUnderstanding((message, slots, handles) -> activeCustomerBalanceUnderstanding(me.zhengjie.agent.query.domain.AgentQueryMetric.REFUND_COUNT),
            new me.zhengjie.agent.analysis.ConversationUnderstandingValidator(), new me.zhengjie.agent.query.MultiIntentPlanningService(), "new");

        AgentChatRequest request = request("unsupported-combination-session", "他们的退款情况");
        request.setLastBusinessQueryContext(activeCustomerContext("AGENT_ACTIVE_CUSTOMER_V1"));
        AgentChatResponse response = service.chat(request);

        assertEquals(ChatStatus.NEED_MORE_INFO, response.getStatus());
        assertEquals("CAPABILITY_NOT_AVAILABLE", response.getResponseType());
        assertEquals(0, balanceCalls.get());
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

    /** 构造不含客户明细、可被 Resolver 重新绑定的活跃客户集合上下文。 */
    private me.zhengjie.agent.query.domain.LastBusinessQueryContext activeCustomerContext(String definitionId) {
        me.zhengjie.agent.analysis.domain.ConversationContextHandle handle = new me.zhengjie.agent.analysis.domain.ConversationContextHandle();
        handle.setHandleId("ctx-active-customers"); handle.setKind(me.zhengjie.agent.analysis.domain.ContextHandleKind.ENTITY_SET);
        handle.setEntityType(me.zhengjie.agent.analysis.domain.SemanticEntityType.CUSTOMER); handle.setDefinitionId(definitionId);
        handle.setSalience(1D); handle.setExpiresAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusHours(1));
        me.zhengjie.agent.query.domain.LastBusinessQueryContext context = new me.zhengjie.agent.query.domain.LastBusinessQueryContext();
        context.setContextHandles(List.of(handle)); return context;
    }

    /** 构造合法的集合余额语义帧，模型不提供句柄 ID 或工具名。 */
    private me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult activeCustomerBalanceUnderstanding() {
        return activeCustomerBalanceUnderstanding(me.zhengjie.agent.query.domain.AgentQueryMetric.MEAL_BALANCE);
    }

    /** 构造指定指标的集合帧，用于验证未登记组合不能进入工具执行。 */
    private me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult activeCustomerBalanceUnderstanding(
        me.zhengjie.agent.query.domain.AgentQueryMetric metric) {
        me.zhengjie.agent.analysis.domain.SemanticScope scope = new me.zhengjie.agent.analysis.domain.SemanticScope();
        scope.setType(me.zhengjie.agent.analysis.domain.SemanticScope.Type.CONTEXT_REFERENCE);
        scope.setRequiredKind(me.zhengjie.agent.analysis.domain.ContextHandleKind.ENTITY_SET);
        scope.setRequiredEntityType(me.zhengjie.agent.analysis.domain.SemanticEntityType.CUSTOMER);
        me.zhengjie.agent.analysis.domain.SemanticRequestFrame frame = new me.zhengjie.agent.analysis.domain.SemanticRequestFrame();
        frame.setFrameId("balance-frame"); frame.setGoal(me.zhengjie.agent.analysis.domain.SemanticGoal.QUERY);
        frame.setTargetEntity(me.zhengjie.agent.analysis.domain.SemanticEntityType.CUSTOMER); frame.setScope(scope);
        frame.setMeasures(List.of(metric));
        frame.setOperations(List.of(me.zhengjie.agent.analysis.domain.SemanticOperation.PROJECT, me.zhengjie.agent.analysis.domain.SemanticOperation.GROUP));
        frame.setOutputShape(me.zhengjie.agent.analysis.domain.SemanticOutputShape.DETAIL_LIST);
        me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult result = new me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult();
        result.setInteractionMode(me.zhengjie.agent.analysis.domain.BusinessInteractionMode.FOLLOW_UP); result.setFrames(List.of(frame));
        return result;
    }

    /** 仅实现本场景会调用的明细接口，其余能力委托基础客户端。 */
    private static class ActiveBalanceClient implements BusinessQueryDataClient {
        private final BusinessQueryDataClient delegate;
        private final AtomicInteger calls;
        private ActiveBalanceClient(BusinessQueryDataClient delegate, AtomicInteger calls) { this.delegate = delegate; this.calls = calls; }
        @Override public Map<String, Object> resolveCustomer(Long id, String code, String name) { return delegate.resolveCustomer(id, code, name); }
        @Override public Map<String, Object> customerOverview(Long id, String code) { return delegate.customerOverview(id, code); }
        @Override public Map<String, Object> listOrders(Long id, Integer status, int page, int size) { return delegate.listOrders(id, status, page, size); }
        @Override public Map<String, Object> orderDetail(Long id, String code, Long customerId) { return delegate.orderDetail(id, code, customerId); }
        @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return delegate.listVerifications(customerId, orderId, mealType, limit); }
        @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return delegate.listRefunds(customerId, orderId, limit); }
        @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return delegate.listMealPlans(customerId, recordDate, mealType); }
        @Override public Map<String, Object> explainRule(String topic) { return delegate.explainRule(topic); }
        @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return delegate.listDishes(dishIds); }
        @Override public me.zhengjie.agent.query.client.dto.ActiveCustomerBalanceResponse activeCustomerBalances(int page, int size) {
            calls.incrementAndGet();
            me.zhengjie.agent.query.client.dto.ActiveCustomerBalanceResponse response = new me.zhengjie.agent.query.client.dto.ActiveCustomerBalanceResponse();
            response.setMetricDefinitionId("AGENT_ACTIVE_CUSTOMER_V1"); response.setTotal(1L); response.setPage(page); response.setSize(size);
            response.setItems(List.of(Map.of("customerCode", "B3303", "customerNameMasked", "张*", "remainingBreakfast", 2,
                "remainingLunchDinner", 4, "remainingTotal", 6)));
            return response;
        }
    }

    /** 创建仅覆盖运营统计的只读客户端，避免测试绕过真实 QueryPlan 和工具执行器。 */
    private BusinessQueryDataClient operationClient(java.util.function.BiFunction<String, String, Map<String, Object>> workload) {
        return new BusinessQueryDataClient() {
            @Override public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
            @Override public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
            @Override public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
            @Override public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
            @Override public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
            @Override public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
            @Override public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
            @Override public Map<String, Object> explainRule(String topic) { return Map.of(); }
            @Override public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
            @Override public Map<String, Object> dailyCustomerWorkload(String date, String type) { return workload.apply(date, type); }
            @Override public Map<String, Object> customerProfileCount() {
                return Map.of("metricCode", "CUSTOMER_PROFILE_COUNT", "total", 12,
                    "metricDefinitionId", "AGENT_CUSTOMER_PROFILE_COUNT_V1", "truncated", false);
            }
        };
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

    private BusinessQuestionAnalysis allergyAnalysis(MealScope mealScope) {
        BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
        analysis.setSource("LLM"); analysis.setConfidence(0.95D);
        analysis.setQueryTarget(BusinessQueryTarget.MEAL_PLAN_ALLERGY_ANALYSIS);
        analysis.setMealScope(mealScope);
        analysis.setDomains(List.of(AgentQueryDomain.MEAL_PLAN, AgentQueryDomain.CUSTOMER, AgentQueryDomain.DISH));
        analysis.getFilters().setRecordDate("2026-07-13");
        analysis.setSubjects(List.of("MEAL_PLAN", "CUSTOMER", "DISH"));
        analysis.setRelations(List.of("MEAL_PLAN_CUSTOMER", "MEAL_PLAN_DISH"));
        analysis.setRequestedFacts(List.of("CUSTOMER_CODE", "DISH_NAME", "ALLERGY_FILTERED", "ALLERGY_REASONS"));
        analysis.setOperation("FILTER_AND_GROUP"); analysis.setGroupBy(List.of("CUSTOMER_CODE"));
        return analysis;
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
