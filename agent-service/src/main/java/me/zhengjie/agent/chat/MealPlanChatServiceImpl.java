package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
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

    private final MealPlanChatSessionStore sessionStore;
    private final MealPlanChatExtractor extractor;
    private final MealPlanDiagnosisService diagnosisService;
    private final MealPlanFollowUpService followUpService;

    public MealPlanChatServiceImpl(MealPlanChatSessionStore sessionStore,
                                   MealPlanChatExtractor extractor,
                                   MealPlanDiagnosisService diagnosisService,
                                   MealPlanFollowUpService followUpService) {
        this.sessionStore = sessionStore;
        this.extractor = extractor;
        this.diagnosisService = diagnosisService;
        this.followUpService = followUpService;
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
                missingSlots(reset.getSlots()),
                quickRepliesFor(missingSlots(reset.getSlots()))
            );
            rememberAssistantTurn(reset, response, extraction, null);
            sessionStore.save(reset);
            logChat(reset, extraction, false, start);
            return response;
        }

        session.setSlots(mergeSlots(copy(session.getSlots()), extraction.getSlots()));
        rememberUserTurn(session, request.getMessage(), extraction);

        if (extraction.getIntent() == ChatIntent.OUT_OF_SCOPE) {
            session.getConversationState().setStage(resolveStage(session.getSlots(), false));
            AgentChatResponse response = response(
                session,
                ChatStatus.ANSWERED,
                "我目前只能处理排餐诊断，请提供客户、日期和餐次。",
                null,
                missingSlots(session.getSlots()),
                List.of("重新排查", "清空会话")
            );
            rememberAssistantTurn(session, response, extraction, null);
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response;
        }

        if (extraction.getIntent() == ChatIntent.RETRY) {
            session.getConversationState().clearLastDiagnosisResult();
        }

        List<MissingSlot> missingSlots = missingSlots(session.getSlots());
        if (!missingSlots.isEmpty()) {
            session.getConversationState().setStage(DiagnosisConversationState.COLLECTING_SLOTS);
            AgentChatResponse response = response(
                session,
                ChatStatus.NEED_MORE_INFO,
                questionFor(missingSlots),
                null,
                missingSlots,
                quickRepliesFor(missingSlots)
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
                quickRepliesForAmbiguous(ambiguousSlots)
            );
            rememberAssistantTurn(session, response, extraction, null);
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response;
        }

        if (extraction.getIntent() == ChatIntent.FOLLOW_UP && session.getLastDiagnosisResult() != null) {
            session.getConversationState().setStage(DiagnosisConversationState.FOLLOWING_UP);
            AgentChatResponse response = response(
                session,
                ChatStatus.ANSWERED,
                followUpService.buildFollowUpReply(request.getMessage(), session.getLastDiagnosisResult()),
                session.getLastDiagnosisResult(),
                List.of(),
                List.of("重新排查", "换成晚餐", "清空会话")
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
            List.of("为什么候选菜为空", "换成晚餐", "重新排查", "清空会话")
        );
        rememberAssistantTurn(session, response, extraction, diagnosisResult);
        sessionStore.save(session);
        logChat(session, extraction, true, start);
        return response;
    }

    /**
     * 将会话中的槽位转换为诊断请求，避免聊天层直接暴露内部会话对象。
     *
     * @param slots 当前已识别槽位
     * @return 诊断服务请求对象
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
     *
     * @param session 当前会话
     * @param status 本次响应状态
     * @param message 助手回复文案
     * @param diagnosisResult 诊断结果
     * @param missingSlots 当前仍缺失的槽位
     * @param quickReplies 当前建议快捷回复
     * @return 标准聊天响应
     */
    private AgentChatResponse response(MealPlanChatSession session,
                                       ChatStatus status,
                                       String message,
                                       DiagnosisResponse diagnosisResult,
                                       List<MissingSlot> missingSlots,
                                       List<String> quickReplies) {
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
        return response;
    }

    /**
     * 识别当前还缺失的关键诊断槽位。
     *
     * @param slots 当前槽位
     * @return 缺失槽位列表
     */
    private List<MissingSlot> missingSlots(DiagnosisSlots slots) {
        List<MissingSlot> missing = new ArrayList<>();
        if (slots.getCustomerId() == null && isNotBlank(slots.getCustomerCode()) == false) {
            missing.add(MissingSlot.CUSTOMER);
        }
        if (isNotBlank(slots.getRecordDate()) == false) {
            missing.add(MissingSlot.RECORD_DATE);
        }
        if (isNotBlank(slots.getMealType()) == false) {
            missing.add(MissingSlot.MEAL_TYPE);
        }
        return missing;
    }

    /**
     * 为当前缺失槽位生成单一主问题，避免一次追问多个信息点。
     *
     * @param missingSlots 当前缺失槽位
     * @return 助手追问文案
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
     *
     * @param missingSlots 当前缺失槽位
     * @return 快捷回复文案列表
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
     *
     * @param ambiguousSlots 待确认槽位
     * @return 确认问题文案
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
     *
     * @param ambiguousSlots 待确认槽位
     * @return 快捷回复列表
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
     *
     * @param session 当前会话
     * @param message 用户原始消息
     * @param extraction 当前轮次抽取结果
     */
    private void rememberUserTurn(MealPlanChatSession session, String message, ChatExtractionResult extraction) {
        session.getConversationState().addTurn(
            DiagnosisConversationTurn.userTurn(message, copy(session.getSlots()), extraction.getIntent().name())
        );
    }

    /**
     * 记录助手回复，保留当前槽位快照和关联诊断请求标识。
     *
     * @param session 当前会话
     * @param response 助手响应
     * @param extraction 当前轮次抽取结果
     * @param diagnosisResult 本轮诊断结果
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
     *
     * @param source 原始槽位
     * @return 可独立修改的槽位副本
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
        target.setSlotConfidence(copyMap(source.getSlotConfidence()));
        target.setSlotSource(copyMap(source.getSlotSource()));
        return target;
    }

    /**
     * 合并本轮提取槽位和会话既有槽位，支持只改日期、餐次或客户的局部覆盖。
     *
     * @param target 会话既有槽位副本
     * @param source 本轮提取到的槽位
     * @return 合并后的槽位
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
     *
     * @param source 原始映射
     * @return 可独立修改的映射副本
     */
    private Map<String, String> copyMap(Map<String, String> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    /**
     * 根据当前槽位和诊断结果判断会话所处阶段。
     *
     * @param slots 当前槽位
     * @param diagnosed 是否已有可复用诊断结果
     * @return 会话阶段
     */
    private String resolveStage(DiagnosisSlots slots, boolean diagnosed) {
        if (diagnosed) {
            return DiagnosisConversationState.DIAGNOSED;
        }
        return missingSlots(slots).isEmpty()
            ? DiagnosisConversationState.READY_TO_DIAGNOSE
            : DiagnosisConversationState.COLLECTING_SLOTS;
    }

    /**
     * 生成诊断完成提示，明确返回原因条数并提示人工确认。
     *
     * @param diagnosisResult 本轮诊断结果
     * @return 完成态提示文案
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
     *
     * @param session 当前会话
     * @param extraction 当前轮次抽取结果
     * @param diagnosisTriggered 本轮是否触发诊断
     * @param start 本轮开始时间
     */
    private void logChat(MealPlanChatSession session, ChatExtractionResult extraction, boolean diagnosisTriggered, long start) {
        List<MissingSlot> missing = missingSlots(session.getSlots());
        putChatMdc(session);
        log.info("聊天诊断阶段 requestId={} sessionId={} intent={} conversationStage={} slots.customerId={} slots.customerCode={} slots.recordDate={} slots.mealType={} missingSlots={} diagnosisTriggered={} costMs={}",
            MDC.get(REQUEST_ID_KEY), session.getSessionId(), extraction.getIntent(), session.getConversationState().getStage(),
            session.getSlots().getCustomerId(), session.getSlots().getCustomerCode(), session.getSlots().getRecordDate(), session.getSlots().getMealType(),
            missing, diagnosisTriggered, System.currentTimeMillis() - start);
    }

    /**
     * 写入聊天链路 MDC 字段，让日志格式能稳定携带会话、槽位和兜底状态。
     *
     * @param session 当前会话
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
