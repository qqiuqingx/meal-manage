package me.zhengjie.agent.chat;

import me.zhengjie.agent.client.DiagnosisToolDataClient;
import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightMealRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightOrderRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerInsightVerificationRequest;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天编排服务。
 */
@Service
public class MealPlanChatServiceImpl implements MealPlanChatService {

    private static final Logger log = LoggerFactory.getLogger(MealPlanChatServiceImpl.class);
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String CUSTOMER_ID_KEY = "customerId";
    private static final String CUSTOMER_CODE_KEY = "customerCode";
    private static final String RECORD_DATE_KEY = "recordDate";
    private static final String MEAL_TYPE_KEY = "mealType";
    private static final String STAGE_KEY = "stage";
    private static final String FALLBACK_KEY = "fallback";
    private static final String FALLBACK_REASON_KEY = "fallbackReason";

    private static final List<String> CUSTOMER_INSIGHT_QUICK_REPLIES = List.of("还剩多少餐", "核销了多少", "有哪些订单", "重新排查", "清空会话");

    private final MealPlanChatSessionStore sessionStore;
    private final MealPlanChatExtractor extractor;
    private final MealPlanDiagnosisService diagnosisService;
    private final MealPlanFollowUpService followUpService;
    private final DiagnosisToolDataClient dataClient;

    public MealPlanChatServiceImpl(MealPlanChatSessionStore sessionStore,
                                   MealPlanChatExtractor extractor,
                                   MealPlanDiagnosisService diagnosisService,
                                   MealPlanFollowUpService followUpService,
                                   DiagnosisToolDataClient dataClient) {
        this.sessionStore = sessionStore;
        this.extractor = extractor;
        this.diagnosisService = diagnosisService;
        this.followUpService = followUpService;
        this.dataClient = dataClient;
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        MealPlanChatSession session = sessionStore.getOrCreate(request.getSessionId());
        long start = System.currentTimeMillis();
        ChatExtractionResult extraction = extractor.extract(request.getMessage(), session.getSlots());
        if (extraction.getIntent() == ChatIntent.RESET) {
            MealPlanChatSession reset = sessionStore.reset(session.getSessionId());
            reset.getConversationState().setStage(DiagnosisConversationState.RESET);
            AgentChatResponse response = response(
                reset,
                ChatStatus.RESET,
                "会话已清空，请重新提供客户、日期和餐次。",
                null,
                missingSlots(reset.getSlots(), extraction.getIntent()),
                quickRepliesFor(missingSlots(reset.getSlots(), extraction.getIntent())),
                "SLOT_REQUIRED"
            );
            rememberAssistantTurn(reset, response, extraction, null);
            sessionStore.save(reset);
            logChat(reset, extraction, false, start);
            return response;
        }

        session.setSlots(mergeSlots(copy(session.getSlots()), extraction.getSlots()));
        rememberUserTurn(session, request.getMessage(), extraction);

        ChatIntent intent = extraction.getIntent();

        if (intent == ChatIntent.OUT_OF_SCOPE) {
            session.getConversationState().setStage(resolveStage(session.getSlots(), false));
            AgentChatResponse response = response(
                session,
                ChatStatus.ANSWERED,
                "我目前只能处理排餐诊断和客户信息查询（餐数余额、核销统计、订单列表），请提供客户编号。",
                null,
                missingSlots(session.getSlots(), intent),
                List.of("重新排查", "清空会话"),
                "SLOT_REQUIRED"
            );
            rememberAssistantTurn(session, response, extraction, null);
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response;
        }

        if (intent == ChatIntent.RETRY) {
            session.getConversationState().clearLastDiagnosisResult();
        }

        // ==================== 客户信息查询 ====================
        if (isCustomerInsightIntent(intent)) {
            // 客户信息查询只要求客户编号
            List<MissingSlot> missing = missingSlotsForInsight(session.getSlots());
            if (!missing.isEmpty()) {
                session.getConversationState().setStage(DiagnosisConversationState.COLLECTING_SLOTS);
                AgentChatResponse response = response(
                    session,
                    ChatStatus.NEED_MORE_INFO,
                    "请提供客户编号，例如：C10001。",
                    null,
                    missing,
                    List.of("客户编号 C10001", "客户ID 1001", "清空会话"),
                    "SLOT_REQUIRED"
                );
                rememberAssistantTurn(session, response, extraction, null);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }

            session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSING);
            AgentChatResponse insightResponse = handleCustomerInsight(intent, session, extraction);
            session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
            sessionStore.save(session);
            logChat(session, extraction, true, start);
            return insightResponse;
        }

        // ==================== 排餐诊断 ====================
        List<MissingSlot> missingSlots = missingSlots(session.getSlots(), intent);
        if (!missingSlots.isEmpty()) {
            session.getConversationState().setStage(DiagnosisConversationState.COLLECTING_SLOTS);
            AgentChatResponse response = response(
                session,
                ChatStatus.NEED_MORE_INFO,
                questionFor(missingSlots),
                null,
                missingSlots,
                quickRepliesFor(missingSlots),
                "SLOT_REQUIRED"
            );
            rememberAssistantTurn(session, response, extraction, null);
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response;
        }

        List<MissingSlot> ambiguousSlots = extraction.getAmbiguousSlots();
        if (!ambiguousSlots.isEmpty()) {
            session.getConversationState().setStage(DiagnosisConversationState.COLLECTING_SLOTS);
            AgentChatResponse response = response(
                session,
                ChatStatus.NEED_MORE_INFO,
                confirmationQuestionFor(ambiguousSlots),
                null,
                List.of(),
                quickRepliesForAmbiguous(ambiguousSlots),
                "SLOT_REQUIRED"
            );
            rememberAssistantTurn(session, response, extraction, null);
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response;
        }

        if (intent == ChatIntent.FOLLOW_UP && session.getLastDiagnosisResult() != null) {
            session.getConversationState().setStage(DiagnosisConversationState.FOLLOWING_UP);
            AgentChatResponse response = response(
                session,
                ChatStatus.ANSWERED,
                followUpService.buildFollowUpReply(request.getMessage(), session.getLastDiagnosisResult()),
                session.getLastDiagnosisResult(),
                List.of(),
                List.of("重新排查", "换成晚餐", "清空会话"),
                "MEAL_PLAN_DIAGNOSIS"
            );
            rememberAssistantTurn(session, response, extraction, session.getLastDiagnosisResult());
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response;
        }

        session.getConversationState().setStage(DiagnosisConversationState.READY_TO_DIAGNOSE);
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSING);
        DiagnosisResponse diagnosisResult = diagnosisService.diagnose(toDiagnosisRequest(session.getSlots()));
        session.getConversationState().addDiagnosisResult(diagnosisResult);
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
        AgentChatResponse response = response(
            session,
            ChatStatus.ANSWERED,
            diagnosisMessage(diagnosisResult),
            diagnosisResult,
            List.of(),
            List.of("为什么候选菜为空", "换成晚餐", "重新排查", "清空会话"),
            "MEAL_PLAN_DIAGNOSIS"
        );
        rememberAssistantTurn(session, response, extraction, diagnosisResult);
        sessionStore.save(session);
        logChat(session, extraction, true, start);
        return response;
    }

    // ========== 客户信息查询处理 ==========

    /**
     * 判断是否为客户信息查询意图
     */
    private boolean isCustomerInsightIntent(ChatIntent intent) {
        return intent == ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY
            || intent == ChatIntent.CUSTOMER_VERIFICATION_QUERY
            || intent == ChatIntent.CUSTOMER_ORDER_QUERY;
    }

    /**
     * 客户信息查询的缺失槽位（只要求客户编号）
     */
    private List<MissingSlot> missingSlotsForInsight(DiagnosisSlots slots) {
        List<MissingSlot> missing = new ArrayList<>();
        if (slots.getCustomerId() == null && isNotBlank(slots.getCustomerCode()) == false) {
            missing.add(MissingSlot.CUSTOMER);
        }
        return missing;
    }

    /**
     * 处理客户信息查询并构建响应
     */
    private AgentChatResponse handleCustomerInsight(ChatIntent intent, MealPlanChatSession session, ChatExtractionResult extraction) {
        DiagnosisSlots slots = session.getSlots();
        String customerCode = slots.getCustomerCode();
        Long customerId = slots.getCustomerId();

        switch (intent) {
            case CUSTOMER_MEAL_BALANCE_QUERY: {
                DiagnosisToolCustomerInsightMealRequest req = new DiagnosisToolCustomerInsightMealRequest();
                req.setCustomerId(customerId);
                req.setCustomerCode(customerCode);
                req.setMealType(slots.getMealType());
                Map<String, Object> result = dataClient.getCustomerMealSummary(req);
                String message = buildMealBalanceMessage(result);
                return insightResponse(session, "CUSTOMER_MEAL_SUMMARY", result, message, CUSTOMER_INSIGHT_QUICK_REPLIES);
            }
            case CUSTOMER_VERIFICATION_QUERY: {
                DiagnosisToolCustomerInsightVerificationRequest req = new DiagnosisToolCustomerInsightVerificationRequest();
                req.setCustomerId(customerId);
                req.setCustomerCode(customerCode);
                req.setMealType(slots.getMealType());
                req.setRecentLimit(10);
                Map<String, Object> result = dataClient.getCustomerVerificationSummary(req);
                String message = buildVerificationMessage(result);
                return insightResponse(session, "CUSTOMER_VERIFICATION_SUMMARY", result, message, CUSTOMER_INSIGHT_QUICK_REPLIES);
            }
            case CUSTOMER_ORDER_QUERY: {
                DiagnosisToolCustomerInsightOrderRequest req = new DiagnosisToolCustomerInsightOrderRequest();
                req.setCustomerId(customerId);
                req.setCustomerCode(customerCode);
                req.setOrderStatus(slots.getOrderStatus());
                Map<String, Object> result = dataClient.getCustomerOrderSummary(req);
                String message = buildOrderMessage(result);
                return insightResponse(session, "CUSTOMER_ORDER_SUMMARY", result, message, CUSTOMER_INSIGHT_QUICK_REPLIES);
            }
            default:
                throw new IllegalStateException("Unexpected insight intent: " + intent);
        }
    }

    /**
     * 构建客户信息查询响应
     */
    private AgentChatResponse insightResponse(MealPlanChatSession session, String responseType,
                                              Map<String, Object> insightResult, String message,
                                              List<String> quickReplies) {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(session.getSessionId());
        response.setStatus(ChatStatus.ANSWERED);
        response.setAssistantMessage(message);
        response.setSlots(copy(session.getSlots()));
        response.setSlotConfidence(copyMap(session.getSlots().getSlotConfidence()));
        response.setMissingSlots(List.of());
        response.setDiagnosisResult(null);
        response.setQuickReplies(quickReplies);
        response.setConversationStage(session.getConversationState().getStage());
        response.setResponseType(responseType);
        response.setInsightResult(insightResult != null ? insightResult : Map.of());
        return response;
    }

    @SuppressWarnings("unchecked")
    private String buildMealBalanceMessage(Map<String, Object> result) {
        if (result == null) return "查询异常，请稍后重试。";
        Boolean present = (Boolean) result.get("present");
        if (present != null && !present) {
            String code = (String) result.get("customerCode");
            return String.format("未找到客户编号 %s，请确认编号是否正确。", code != null ? code : "");
        }
        String customerCode = (String) result.get("customerCode");
        String customerName = (String) result.get("customerName");
        Integer activeOrderCount = (Integer) result.get("activeOrderCount");
        Integer remainingBreakfast = (Integer) result.get("remainingBreakfast");
        Integer remainingLunchDinner = (Integer) result.get("remainingLunchDinner");
        Integer totalRemaining = (Integer) result.get("totalRemaining");
        Integer verifiedBreakfast = (Integer) result.get("verifiedBreakfast");
        Integer verifiedLunch = (Integer) result.get("verifiedLunch");
        Integer verifiedDinner = (Integer) result.get("verifiedDinner");

        if (activeOrderCount == null || activeOrderCount == 0) {
            return String.format("%s 当前没有有效进行中订单，因此没有可继续核销的剩余餐数。历史订单和核销记录已列在下方供核对。", customerCode);
        }
        return String.format(
            "%s（%s）当前有效订单共 %d 笔。剩余早餐 %d 餐，剩余午晚餐 %d 餐，合计剩余 %d 餐。已核销早餐 %d 餐，午餐 %d 餐，晚餐 %d 餐。数据按未删除核销日志实时汇总。",
            customerCode, customerName != null ? customerName : "",
            activeOrderCount,
            remainingBreakfast != null ? remainingBreakfast : 0,
            remainingLunchDinner != null ? remainingLunchDinner : 0,
            totalRemaining != null ? totalRemaining : 0,
            verifiedBreakfast != null ? verifiedBreakfast : 0,
            verifiedLunch != null ? verifiedLunch : 0,
            verifiedDinner != null ? verifiedDinner : 0
        );
    }

    @SuppressWarnings("unchecked")
    private String buildVerificationMessage(Map<String, Object> result) {
        if (result == null) return "查询异常，请稍后重试。";
        Boolean present = (Boolean) result.get("present");
        if (present != null && !present) {
            String code = (String) result.get("customerCode");
            return String.format("未找到客户编号 %s，请确认编号是否正确。", code != null ? code : "");
        }
        String customerCode = (String) result.get("customerCode");
        Integer totalVerified = (Integer) result.get("totalVerified");
        Integer totalBf = (Integer) result.get("totalVerifiedBreakfast");
        Integer totalLunch = (Integer) result.get("totalVerifiedLunch");
        Integer totalDinner = (Integer) result.get("totalVerifiedDinner");
        List<Map<String, Object>> recent = (List<Map<String, Object>>) result.get("recentVerifications");
        int recentCount = recent != null ? recent.size() : 0;
        return String.format(
            "%s 累计已核销 %d 餐，其中早餐 %d 餐、午餐 %d 餐、晚餐 %d 餐。最近 %d 条核销记录已列在下方。",
            customerCode, totalVerified != null ? totalVerified : 0,
            totalBf != null ? totalBf : 0, totalLunch != null ? totalLunch : 0,
            totalDinner != null ? totalDinner : 0, recentCount
        );
    }

    @SuppressWarnings("unchecked")
    private String buildOrderMessage(Map<String, Object> result) {
        if (result == null) return "查询异常，请稍后重试。";
        Boolean present = (Boolean) result.get("present");
        if (present != null && !present) {
            String code = (String) result.get("customerCode");
            return String.format("未找到客户编号 %s，请确认编号是否正确。", code != null ? code : "");
        }
        String customerCode = (String) result.get("customerCode");
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.get("orders");
        int orderCount = orders != null ? orders.size() : 0;
        long activeCount = orders != null ? orders.stream()
            .filter(o -> o.get("status") != null && o.get("status").equals(1)).count() : 0;
        return String.format("%s 共有 %d 笔订单，其中进行中 %d 笔。订单明细已列在下方。", customerCode, orderCount, activeCount);
    }

    // ========== 原有方法 ==========

    /**
     * 将会话中的槽位转换为诊断请求，避免聊天层直接暴露内部会话对象。
     */
    private DiagnosisRequest toDiagnosisRequest(DiagnosisSlots slots) {
        DiagnosisRequest request = new DiagnosisRequest();
        request.setCustomerId(slots.getCustomerId());
        request.setCustomerCode(slots.getCustomerCode());
        request.setRecordDate(slots.getRecordDate());
        request.setMealType(slots.getMealType());
        return request;
    }

    /**
     * 组装统一聊天响应，确保每次都回传当前槽位、缺失项和会话阶段。
     */
    private AgentChatResponse response(MealPlanChatSession session,
                                       ChatStatus status,
                                       String message,
                                       DiagnosisResponse diagnosisResult,
                                       List<MissingSlot> missingSlots,
                                       List<String> quickReplies,
                                       String responseType) {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(session.getSessionId());
        response.setStatus(status);
        response.setAssistantMessage(message);
        response.setSlots(copy(session.getSlots()));
        response.setSlotConfidence(copyMap(session.getSlots().getSlotConfidence()));
        response.setMissingSlots(new ArrayList<>(missingSlots));
        response.setDiagnosisResult(diagnosisResult);
        response.setQuickReplies(quickReplies);
        response.setConversationStage(session.getConversationState().getStage());
        response.setResponseType(responseType);
        return response;
    }

    /**
     * 识别当前还缺失的关键诊断槽位。
     * 对于客户信息查询，只要求客户编号。
     */
    private List<MissingSlot> missingSlots(DiagnosisSlots slots, ChatIntent intent) {
        List<MissingSlot> missing = new ArrayList<>();
        if (slots.getCustomerId() == null && isNotBlank(slots.getCustomerCode()) == false) {
            missing.add(MissingSlot.CUSTOMER);
        }
        // 只有排餐诊断要求日期和餐次
        if (intent == ChatIntent.DIAGNOSE || intent == ChatIntent.FOLLOW_UP || intent == ChatIntent.RETRY) {
            if (isNotBlank(slots.getRecordDate()) == false) {
                missing.add(MissingSlot.RECORD_DATE);
            }
            if (isNotBlank(slots.getMealType()) == false) {
                missing.add(MissingSlot.MEAL_TYPE);
            }
        }
        return missing;
    }

    /**
     * 为当前缺失槽位生成单一主问题，避免一次追问多个信息点。
     */
    private String questionFor(List<MissingSlot> missingSlots) {
        if (missingSlots.contains(MissingSlot.CUSTOMER)) {
            return "请提供客户 ID 或客户编号。";
        }
        if (missingSlots.contains(MissingSlot.RECORD_DATE)) {
            return "请补充要排查的日期，例如今天、明天或 2026-05-22。";
        }
        return "请补充餐次：早餐、午餐还是晚餐？";
    }

    /**
     * 为当前缺失槽位提供快捷回复，优先给出当前问题所需的最小选项集。
     */
    private List<String> quickRepliesFor(List<MissingSlot> missingSlots) {
        if (missingSlots.contains(MissingSlot.CUSTOMER)) {
            return List.of("客户编号 C10001", "客户ID 1001", "清空会话");
        }
        if (missingSlots.contains(MissingSlot.RECORD_DATE)) {
            return List.of("今天", "明天", "后天");
        }
        if (missingSlots.contains(MissingSlot.MEAL_TYPE)) {
            return List.of("早餐", "午餐", "晚餐");
        }
        return List.of("重新排查", "清空会话");
    }

    /**
     * 为低置信度槽位生成确认问题，优先确认最影响诊断结果的槽位。
     */
    private String confirmationQuestionFor(List<MissingSlot> ambiguousSlots) {
        if (ambiguousSlots.contains(MissingSlot.CUSTOMER)) {
            return "请确认客户标识，回复客户ID或客户编号。";
        }
        if (ambiguousSlots.contains(MissingSlot.RECORD_DATE)) {
            return "请确认排查日期，例如今天、明天或 2026-05-22。";
        }
        return "请确认餐次：早餐、午餐还是晚餐？";
    }

    /**
     * 为低置信度槽位提供确认用快捷回复。
     */
    private List<String> quickRepliesForAmbiguous(List<MissingSlot> ambiguousSlots) {
        if (ambiguousSlots.contains(MissingSlot.CUSTOMER)) {
            return List.of("客户编号 C10001", "客户ID 1001", "清空会话");
        }
        if (ambiguousSlots.contains(MissingSlot.RECORD_DATE)) {
            return List.of("今天", "明天", "后天");
        }
        return List.of("早餐", "午餐", "晚餐");
    }

    /**
     * 记录用户输入，供后续追问和会话回放复用。
     */
    private void rememberUserTurn(MealPlanChatSession session, String message, ChatExtractionResult extraction) {
        session.getConversationState().addTurn(
            DiagnosisConversationTurn.userTurn(message, copy(session.getSlots()), extraction.getIntent().name())
        );
    }

    /**
     * 记录助手回复，保留当前槽位快照和关联诊断请求标识。
     */
    private void rememberAssistantTurn(MealPlanChatSession session,
                                       AgentChatResponse response,
                                       ChatExtractionResult extraction,
                                       DiagnosisResponse diagnosisResult) {
        String diagnosisRequestId = diagnosisResult == null ? null : diagnosisResult.getRequestId();
        session.getConversationState().addTurn(
            DiagnosisConversationTurn.assistantTurn(
                response.getAssistantMessage(),
                copy(session.getSlots()),
                extraction.getIntent().name(),
                diagnosisRequestId
            )
        );
    }

    /**
     * 复制槽位对象，避免会话中的可变对象泄漏到响应和历史记录。
     */
    private DiagnosisSlots copy(DiagnosisSlots source) {
        DiagnosisSlots target = new DiagnosisSlots();
        if (source == null) {
            return target;
        }
        target.setCustomerId(source.getCustomerId());
        target.setCustomerCode(source.getCustomerCode());
        target.setRecordDate(source.getRecordDate());
        target.setMealType(source.getMealType());
        target.setOrderStatus(source.getOrderStatus());
        target.setSlotConfidence(copyMap(source.getSlotConfidence()));
        target.setSlotSource(copyMap(source.getSlotSource()));
        return target;
    }

    /**
     * 合并本轮提取槽位和会话既有槽位，支持只改日期、餐次或客户的局部覆盖。
     */
    private DiagnosisSlots mergeSlots(DiagnosisSlots target, DiagnosisSlots source) {
        if (target == null) {
            target = new DiagnosisSlots();
        }
        if (source == null) {
            return target;
        }
        if (source.getCustomerId() != null) {
            target.setCustomerId(source.getCustomerId());
        }
        if (isNotBlank(source.getCustomerCode())) {
            target.setCustomerCode(source.getCustomerCode());
        }
        if (isNotBlank(source.getRecordDate())) {
            target.setRecordDate(source.getRecordDate());
        }
        if (isNotBlank(source.getMealType())) {
            target.setMealType(source.getMealType());
        }
        if (source.getOrderStatus() != null) {
            target.setOrderStatus(source.getOrderStatus());
        }
        if (source.getSlotConfidence() != null && !source.getSlotConfidence().isEmpty()) {
            target.setSlotConfidence(copyMap(source.getSlotConfidence()));
        }
        if (source.getSlotSource() != null && !source.getSlotSource().isEmpty()) {
            target.setSlotSource(copyMap(source.getSlotSource()));
        }
        return target;
    }

    /**
     * 复制槽位置信度和来源映射，避免响应对象反向修改会话状态。
     */
    private Map<String, String> copyMap(Map<String, String> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    /**
     * 根据当前槽位和诊断结果判断会话所处阶段。
     */
    private String resolveStage(DiagnosisSlots slots, boolean diagnosed) {
        if (diagnosed) {
            return DiagnosisConversationState.DIAGNOSED;
        }
        return missingSlots(slots, ChatIntent.DIAGNOSE).isEmpty()
            ? DiagnosisConversationState.READY_TO_DIAGNOSE
            : DiagnosisConversationState.COLLECTING_SLOTS;
    }

    /**
     * 生成诊断完成提示，明确返回原因条数并提示人工确认。
     */
    private String diagnosisMessage(DiagnosisResponse diagnosisResult) {
        int reasonCount = diagnosisResult == null || diagnosisResult.getReasons() == null ? 0 : diagnosisResult.getReasons().size();
        return "已完成诊断，发现 " + reasonCount + " 个可能原因，请结合证据人工确认。";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 输出聊天阶段日志，记录当前意图、槽位、缺失项和阶段流转。
     */
    private void logChat(MealPlanChatSession session, ChatExtractionResult extraction, boolean diagnosisTriggered, long start) {
        List<MissingSlot> missing = missingSlots(session.getSlots(), extraction.getIntent());
        putChatMdc(session);
        log.info("聊天诊断阶段 requestId={} sessionId={} intent={} conversationStage={} slots.customerId={} slots.customerCode={} slots.recordDate={} slots.mealType={} missingSlots={} diagnosisTriggered={} costMs={}",
            MDC.get(REQUEST_ID_KEY), session.getSessionId(), extraction.getIntent(), session.getConversationState().getStage(),
            session.getSlots().getCustomerId(), session.getSlots().getCustomerCode(), session.getSlots().getRecordDate(), session.getSlots().getMealType(),
            missing, diagnosisTriggered, System.currentTimeMillis() - start);
    }

    /**
     * 写入聊天链路 MDC 字段，让日志格式能稳定携带会话、槽位和兜底状态。
     */
    private void putChatMdc(MealPlanChatSession session) {
        MDC.put(SESSION_ID_KEY, session.getSessionId());
        MDC.put(CUSTOMER_ID_KEY, session.getSlots().getCustomerId() == null ? "" : String.valueOf(session.getSlots().getCustomerId()));
        MDC.put(CUSTOMER_CODE_KEY, safe(session.getSlots().getCustomerCode()));
        MDC.put(RECORD_DATE_KEY, safe(session.getSlots().getRecordDate()));
        MDC.put(MEAL_TYPE_KEY, safe(session.getSlots().getMealType()));
        MDC.put(STAGE_KEY, safe(session.getConversationState().getStage()));
        MDC.put(FALLBACK_KEY, String.valueOf(session.getLastDiagnosisResult() != null && session.getLastDiagnosisResult().isFallback()));
        MDC.put(FALLBACK_REASON_KEY, session.getLastDiagnosisResult() == null ? "" : safe(session.getLastDiagnosisResult().getFallbackReason()));
    }
}
