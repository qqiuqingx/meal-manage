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
import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.client.dto.DishCandidatePreviewResponse;
import me.zhengjie.agent.query.AgentQueryPlanValidationError;
import me.zhengjie.agent.query.AgentQueryPlanValidationResult;
import me.zhengjie.agent.query.AgentQueryPlanValidator;
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
            @Override public Map<String, Object> listScheduledDishes(String recordDate, String mealType) {
                capturedDate.set(recordDate);
                assertNull(mealType);
                return Map.of("items", List.of(Map.of("dishName", "番茄炒蛋")));
            }
        };
        MealPlanChatServiceImpl service = new MealPlanChatServiceImpl(store, extractor, request -> new DiagnosisResponse(),
            new MealPlanFollowUpServiceImpl(), new StubDiagnosisToolDataClient(), client);

        AgentChatResponse response = service.chat(request("session-scheduled-menu", "今天的菜单是什么"));

        assertEquals(ChatStatus.ANSWERED, response.getStatus());
        assertEquals("BUSINESS_QUERY_SCHEDULED_MENU", response.getResponseType());
        assertEquals("2026-05-22", capturedDate.get());
        assertEquals(List.of(), response.getMissingSlots());
        assertTrue(response.getAssistantMessage().contains("番茄炒蛋"));
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
        assertTrue(response.getAssistantMessage().contains("不能给出完整结论"));
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
