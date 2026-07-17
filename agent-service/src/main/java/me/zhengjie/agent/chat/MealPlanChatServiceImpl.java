package me.zhengjie.agent.chat;

import me.zhengjie.agent.client.DiagnosisToolDataClient;
import me.zhengjie.agent.analysis.BusinessQuestionAnalyzer;
import me.zhengjie.agent.analysis.LegacyBusinessQuestionAnalysisFactory;
import me.zhengjie.agent.analysis.RuleBasedBusinessQuestionAnalyzer;
import me.zhengjie.agent.analysis.BusinessTemporalResolver;
import me.zhengjie.agent.analysis.ContextReferenceResolver;
import me.zhengjie.agent.analysis.ConversationUnderstandingService;
import me.zhengjie.agent.analysis.ConversationUnderstandingValidator;
import me.zhengjie.agent.query.MultiIntentPlanningService;
import me.zhengjie.agent.config.BusinessTimeProperties;
import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.AgentQueryPlanValidator;
import me.zhengjie.agent.query.BusinessAnswerValidator;
import me.zhengjie.agent.query.BusinessQueryOrchestrator;
import me.zhengjie.agent.query.BusinessQueryChatService;
import me.zhengjie.agent.query.BusinessQueryPlanningService;
import me.zhengjie.agent.query.BusinessResultValidator;
import me.zhengjie.agent.query.BusinessAnswerComposer;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.analysis.domain.ConversationContextHandle;
import me.zhengjie.agent.analysis.domain.ContextHandleKind;
import me.zhengjie.agent.analysis.domain.SemanticEntityType;
import me.zhengjie.agent.analysis.domain.SemanticOperation;
import me.zhengjie.agent.query.domain.PendingBusinessQueryContext;
import me.zhengjie.agent.query.domain.SemanticTraceSummary;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentMetricDefinition;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.analysis.domain.BusinessInteractionMode;
import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.analysis.domain.MealScope;
import me.zhengjie.agent.query.tool.AgentBusinessToolExecutor.ToolExecutionResult;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Clock;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
    private final BusinessQueryDataClient businessQueryDataClient;
    private final BusinessAnswerValidator businessAnswerValidator;
    private final BusinessQueryChatService businessQueryChatService;
    private final BusinessQuestionAnalyzer businessQuestionAnalyzer;
    private final BusinessQueryPlanningService businessQueryPlanningService;
    private final BusinessTemporalResolver businessTemporalResolver;
    private final int pendingContextTtlMinutes;
    private boolean pendingContextEnabled = true;
    private ConversationUnderstandingService conversationUnderstandingService;
    private ConversationUnderstandingValidator conversationUnderstandingValidator;
    private MultiIntentPlanningService multiIntentPlanningService;
    private String conversationUnderstandingMode = "shadow";

    public MealPlanChatServiceImpl(MealPlanChatSessionStore sessionStore,
                                   MealPlanChatExtractor extractor,
                                   MealPlanDiagnosisService diagnosisService,
                                   MealPlanFollowUpService followUpService,
                                   DiagnosisToolDataClient dataClient) {
        this(sessionStore, extractor, diagnosisService, followUpService, dataClient, null);
    }

    /**
     * 构造聊天编排器，并注入独立的受控业务查询客户端。
     */
    public MealPlanChatServiceImpl(MealPlanChatSessionStore sessionStore,
                                   MealPlanChatExtractor extractor,
                                   MealPlanDiagnosisService diagnosisService,
                                   MealPlanFollowUpService followUpService,
                                   DiagnosisToolDataClient dataClient,
                                   BusinessQueryDataClient businessQueryDataClient) {
        this(sessionStore, extractor, diagnosisService, followUpService, dataClient, businessQueryDataClient,
            new AgentQueryPlanValidator(), new BusinessAnswerValidator());
    }

    /**
     * 构造聊天编排器，并注入业务查询计划校验器，所有只读工具均须先通过校验。
     */
    public MealPlanChatServiceImpl(MealPlanChatSessionStore sessionStore,
                                   MealPlanChatExtractor extractor,
                                   MealPlanDiagnosisService diagnosisService,
                                   MealPlanFollowUpService followUpService,
                                   DiagnosisToolDataClient dataClient,
                                   BusinessQueryDataClient businessQueryDataClient,
                                   AgentQueryPlanValidator queryPlanValidator) {
        this(sessionStore, extractor, diagnosisService, followUpService, dataClient, businessQueryDataClient,
            queryPlanValidator, new BusinessAnswerValidator());
    }

    /** 构造聊天编排器，并注入业务回答安全校验器。 */
    public MealPlanChatServiceImpl(MealPlanChatSessionStore sessionStore,
                                   MealPlanChatExtractor extractor,
                                   MealPlanDiagnosisService diagnosisService,
                                   MealPlanFollowUpService followUpService,
                                   DiagnosisToolDataClient dataClient,
                                   BusinessQueryDataClient businessQueryDataClient,
                                   AgentQueryPlanValidator queryPlanValidator,
                                   BusinessAnswerValidator businessAnswerValidator) {
        this(sessionStore, extractor, diagnosisService, followUpService, dataClient, businessQueryDataClient,
            queryPlanValidator, businessAnswerValidator, new RuleBasedBusinessQuestionAnalyzer(), new BusinessQueryPlanningService(),
            defaultTemporalResolver(), 30);
    }

    /**
     * 构造聊天编排器，并注入模型优先、规则兜底的业务问题分析器和 QueryPlan 规划器。
     *
     * @param sessionStore 会话存储
     * @param extractor 基础槽位提取器
     * @param diagnosisService 排餐诊断服务
     * @param followUpService 诊断追问服务
     * @param dataClient 排餐诊断数据客户端
     * @param businessQueryDataClient 受控业务查询客户端
     * @param queryPlanValidator QueryPlan 校验器
     * @param businessAnswerValidator 回答事实安全校验器
     * @param businessQuestionAnalyzer 受控问题分析器
     * @param businessQueryPlanningService QueryPlan 2.0 规划器
     */
    public MealPlanChatServiceImpl(MealPlanChatSessionStore sessionStore,
                                   MealPlanChatExtractor extractor,
                                   MealPlanDiagnosisService diagnosisService,
                                   MealPlanFollowUpService followUpService,
                                   DiagnosisToolDataClient dataClient,
                                   BusinessQueryDataClient businessQueryDataClient,
                                   AgentQueryPlanValidator queryPlanValidator,
                                   BusinessAnswerValidator businessAnswerValidator,
                                   BusinessQuestionAnalyzer businessQuestionAnalyzer,
                                   BusinessQueryPlanningService businessQueryPlanningService) {
        this(sessionStore, extractor, diagnosisService, followUpService, dataClient, businessQueryDataClient,
            queryPlanValidator, businessAnswerValidator, businessQuestionAnalyzer, businessQueryPlanningService,
            defaultTemporalResolver(), 30);
    }

    /**
     * 创建完整聊天编排器；时间解析器和 Pending TTL 均由配置注入，便于固定时钟测试和多实例一致执行。
     */
    @Autowired
    public MealPlanChatServiceImpl(MealPlanChatSessionStore sessionStore,
                                   MealPlanChatExtractor extractor,
                                   MealPlanDiagnosisService diagnosisService,
                                   MealPlanFollowUpService followUpService,
                                   DiagnosisToolDataClient dataClient,
                                   BusinessQueryDataClient businessQueryDataClient,
                                   AgentQueryPlanValidator queryPlanValidator,
                                   BusinessAnswerValidator businessAnswerValidator,
                                   BusinessQuestionAnalyzer businessQuestionAnalyzer,
                                   BusinessQueryPlanningService businessQueryPlanningService,
                                   BusinessTemporalResolver businessTemporalResolver,
                                   @org.springframework.beans.factory.annotation.Value("${agent.chat.business-semantic.pending-context-ttl-minutes:30}") int pendingContextTtlMinutes) {
        this.sessionStore = sessionStore;
        this.extractor = extractor;
        this.diagnosisService = diagnosisService;
        this.followUpService = followUpService;
        this.dataClient = dataClient;
        this.businessQueryDataClient = businessQueryDataClient;
        this.businessAnswerValidator = businessAnswerValidator;
        this.businessQueryChatService = new BusinessQueryChatService(businessQueryDataClient, queryPlanValidator, businessAnswerValidator);
        this.businessQuestionAnalyzer = businessQuestionAnalyzer;
        this.businessQueryPlanningService = businessQueryPlanningService;
        this.businessTemporalResolver = businessTemporalResolver;
        this.pendingContextTtlMinutes = Math.max(1, pendingContextTtlMinutes);
    }

    /**
     * 应用灰度开关；关闭时不恢复或保存 Pending Context，其余时间与 QueryPlan 行为保持不变。
     *
     * @param enabled 是否启用跨轮待补查询上下文
     */
    @Autowired
    public void configurePendingContext(
        @org.springframework.beans.factory.annotation.Value("${agent.chat.business-semantic.pending-context-enabled:true}") boolean enabled) {
        this.pendingContextEnabled = enabled;
    }

    /** 配置可灰度启用的多帧会话理解服务，默认 shadow 模式不改变既有执行路径。 */
    @Autowired
    public void configureConversationUnderstanding(ConversationUnderstandingService service,
                                                   ConversationUnderstandingValidator validator,
                                                   MultiIntentPlanningService planner,
                                                   @org.springframework.beans.factory.annotation.Value("${agent.chat.conversation-understanding.mode:shadow}") String mode) {
        this.conversationUnderstandingService = service; this.conversationUnderstandingValidator = validator;
        this.multiIntentPlanningService = planner; this.conversationUnderstandingMode = mode == null ? "shadow" : mode;
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        MealPlanChatSession session = sessionStore.getOrCreate(request.getSessionId());
        hydrateBusinessContexts(session, request);
        // 主系统持久化上下文仅在当前 agent 实例缺少槽位时补充；本实例中的更新槽位优先保留。
        session.setSlots(mergeSlots(copy(request.getContextSlots()), session.getSlots()));
        long start = System.currentTimeMillis();
        ChatExtractionResult extraction = extractor.extract(
            request.getMessage(), session.getSlots(), session.getConversationState()
        );
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
        BusinessQueryOrchestrator businessQueryOrchestrator = createBusinessQueryOrchestrator();

        if (intent == ChatIntent.BUSINESS_QUERY) {
            AgentChatResponse semanticResponse = handleSemanticBusinessQuery(session, request.getMessage(), businessQueryOrchestrator);
            if (semanticResponse != null) {
                DiagnosisResponse semanticDiagnosis = semanticResponse.getDiagnosisResult();
                rememberAssistantTurn(session, semanticResponse, extraction, semanticDiagnosis);
                sessionStore.save(session);
                logChat(session, extraction, semanticDiagnosis != null, start);
                return semanticResponse;
            }
            ChatIntent compatibilityIntent = compatibilityBusinessIntent(extraction);
            if (compatibilityIntent == null) {
                AgentChatResponse response = response(session, ChatStatus.NEED_MORE_INFO,
                    "请说明想查询客户、订单、排餐、核销、退餐、套餐、菜品或运营统计中的哪类数据。", null,
                    List.of(), List.of("客户订单", "今天待核销客户", "今天菜单"), "BUSINESS_QUERY_CLARIFICATION");
                rememberAssistantTurn(session, response, extraction, null);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }
            intent = compatibilityIntent;
        }

        if (intent == ChatIntent.OUT_OF_SCOPE) {
            session.getConversationState().setStage(resolveStage(session.getSlots(), false));
            String message = isAmountQuery(request.getMessage())
                ? "订单金额、退款金额、优惠金额、已收金额和单价不在本期只读查询范围内，无法查询或返回。"
                : "我目前只能处理排餐诊断和客户信息查询（餐数余额、核销统计、订单列表），请提供客户编号。";
            AgentChatResponse response = response(
                session,
                ChatStatus.ANSWERED,
                message,
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

        if (intent == ChatIntent.BUSINESS_RULE_QUERY) {
            if (businessQueryDataClient == null) {
                return response(session, ChatStatus.ERROR, "业务规则查询服务暂不可用，请稍后重试。", null, List.of(), List.of(), "BUSINESS_QUERY_RULE");
            }
            ToolExecutionResult execution = executeBusinessTool(businessQueryOrchestrator, "BUSINESS_QUERY_RULE",
                session.getSlots(), "explainRule", ruleTopic(request.getMessage()), List.of());
            Map<String, Object> result = execution.result();
            AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_RULE", result,
                composer().businessRule(result), List.of("剩余餐数怎么算", "订单什么时候有效", "清空会话"));
            applyToolExecution(response, execution);
            session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response;
        }

        if (intent == ChatIntent.SCHEDULED_MENU_QUERY) {
            List<MissingSlot> missing = missingSlots(session.getSlots(), intent);
            if (!missing.isEmpty()) return response(session, ChatStatus.NEED_MORE_INFO,
                "请补充菜单日期，例如今天、明天或 2026-07-12。", null, missing, quickRepliesFor(missing), "SLOT_REQUIRED");
            ToolExecutionResult execution = executeBusinessTool(businessQueryOrchestrator, "BUSINESS_QUERY_SCHEDULED_MENU",
                session.getSlots(), "listScheduledDishes", null, List.of());
            AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_SCHEDULED_MENU", execution.result(),
                composer().scheduledMenu(execution.result()), List.of("今天菜单", "明天菜单", "清空会话"));
            applyToolExecution(response, execution);
            captureLastBusinessQueryContext(session, response);
            sessionStore.save(session);
            return response;
        }

        if (intent == ChatIntent.OPERATION_STATISTICS_QUERY) {
            AgentChatResponse response = handleOperationStatistics(session, request.getMessage(), businessQueryOrchestrator);
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response;
        }

        // 事实查询和排餐诊断分离：查询只返回当前记录，不调用诊断模型。
        if (intent == ChatIntent.MEAL_PLAN_QUERY || intent == ChatIntent.DISH_INGREDIENT_QUERY || intent == ChatIntent.DISH_CANDIDATE_QUERY
            || intent == ChatIntent.MEAL_PLAN_UNVERIFIED_QUERY || intent == ChatIntent.MEAL_BALANCE_NO_PLAN_QUERY) {
            List<MissingSlot> missing = missingSlots(session.getSlots(), intent);
            if (!missing.isEmpty()) {
                session.getConversationState().setStage(DiagnosisConversationState.COLLECTING_SLOTS);
                AgentChatResponse response = response(session, ChatStatus.NEED_MORE_INFO,
                    questionFor(missing), null, missing, quickRepliesFor(missing), "SLOT_REQUIRED");
                rememberAssistantTurn(session, response, extraction, null);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }
            if (businessQueryDataClient == null) {
                return response(session, ChatStatus.ERROR, "排餐查询服务暂不可用，请稍后重试。", null, List.of(), List.of(), "BUSINESS_QUERY_MEAL_PLAN");
            }
            Long resolvedCustomerId = session.getSlots().getCustomerId();
            boolean directMealPlanRecord = session.getSlots().getMealPlanRecordId() != null;
            if (resolvedCustomerId == null && !directMealPlanRecord) {
                ToolExecutionResult overviewExecution = executeBusinessTool(businessQueryOrchestrator,
                    "BUSINESS_QUERY_CUSTOMER", session.getSlots(), "customerOverview", null, List.of());
                Map<String, Object> overview = overviewExecution.result();
                Object overviewCustomerId = overview.get("customerId");
                if (overviewCustomerId instanceof Number) resolvedCustomerId = ((Number) overviewCustomerId).longValue();
                if (resolvedCustomerId == null) {
                    AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_CUSTOMER", overview,
                        composer().customerOverview(overview), CUSTOMER_INSIGHT_QUICK_REPLIES);
                    applyToolExecution(response, overviewExecution);
                    return response;
                }
            }
            DiagnosisSlots resolvedSlots = copy(session.getSlots());
            if (resolvedCustomerId != null) resolvedSlots.setCustomerId(resolvedCustomerId);
            if (intent == ChatIntent.DISH_CANDIDATE_QUERY) {
                ToolExecutionResult candidateExecution = executeBusinessTool(businessQueryOrchestrator,
                    "BUSINESS_QUERY_DISH_CANDIDATES", resolvedSlots, "previewDishCandidates", null, List.of());
                AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_DISH_CANDIDATES", candidateExecution.result(),
                    composer().dishCandidates(candidateExecution.result()), CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, candidateExecution);
                session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }
            ToolExecutionResult mealPlanExecution = executeBusinessTool(businessQueryOrchestrator,
                "BUSINESS_QUERY_MEAL_PLAN", resolvedSlots, "listMealPlans", null, List.of());
            Map<String, Object> result = mealPlanExecution.result();
            if (intent == ChatIntent.MEAL_BALANCE_NO_PLAN_QUERY) {
                AgentQueryPlan comboPlan = mealBalanceNoPlanPlan(resolvedSlots);
                ToolExecutionResult overviewExecution = businessQueryOrchestrator.execute(comboPlan,
                    "customerOverview", null, List.of());
                Map<String, Object> overview = overviewExecution.result();
                AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_MEAL_PLAN", result,
                    composer().mealBalanceWithoutPlan(overview, result), CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, mealPlanExecution, overviewExecution);
                return response;
            }
            if (intent == ChatIntent.MEAL_PLAN_UNVERIFIED_QUERY) {
                Map<String, Object> unverified = filterUnverifiedMealPlans(result);
                AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_MEAL_PLAN", unverified,
                    composer().unverifiedMealPlans(unverified), CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, mealPlanExecution);
                session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }
            if (intent == ChatIntent.DISH_INGREDIENT_QUERY) {
                List<Integer> dishIds = extractDishIds(result);
                if (dishIds.isEmpty()) {
                    return insightResponse(session, "BUSINESS_QUERY_DISH", Map.of(), "该客户指定餐次没有可查询配料的排餐菜品。", CUSTOMER_INSIGHT_QUICK_REPLIES);
                }
                ToolExecutionResult dishExecution = executeBusinessTool(businessQueryOrchestrator,
                    "BUSINESS_QUERY_DISH", resolvedSlots, "listDishes", null, dishIds);
                Map<String, Object> dishes = dishExecution.result();
                AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_DISH", dishes,
                    composer().dishIngredients(dishes), CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, mealPlanExecution, dishExecution);
                session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }
            AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_MEAL_PLAN", result,
                composer().mealPlan(result), CUSTOMER_INSIGHT_QUICK_REPLIES);
            applyToolExecution(response, mealPlanExecution);
            session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
            sessionStore.save(session);
            logChat(session, extraction, true, start);
            return response;
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

            AgentChatResponse candidateResponse = resolveCustomerCandidatesIfNeeded(session, businessQueryOrchestrator);
            if (candidateResponse != null) {
                rememberAssistantTurn(session, candidateResponse, extraction, null);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return candidateResponse;
            }

            session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSING);
            AgentChatResponse insightResponse = handleCustomerInsight(intent, session, extraction, businessQueryOrchestrator);
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
            || intent == ChatIntent.CUSTOMER_ORDER_QUERY
            || intent == ChatIntent.CUSTOMER_REFUND_QUERY
            || intent == ChatIntent.CUSTOMER_PACKAGE_QUERY
            || intent == ChatIntent.MEAL_BALANCE_CHANGE_QUERY;
    }

    /**
     * 将顶层业务查询暂时映射到旧版受控分支；该值仅来自抽取器记录的枚举名，不能来自用户输入。
     * 后续领域分支可逐个迁移为 BusinessQuestionAnalysis 与 QueryPlan 的直接执行，不影响顶层协议。
     *
     * @param extraction 当前抽取结果
     * @return 已登记的兼容业务意图，无法映射时返回 null
     */
    private ChatIntent compatibilityBusinessIntent(ChatExtractionResult extraction) {
        if (extraction == null || !isNotBlank(extraction.getRuleIntent())) return null;
        try {
            ChatIntent intent = ChatIntent.valueOf(extraction.getRuleIntent());
            return intent == ChatIntent.BUSINESS_QUERY || intent == ChatIntent.DIAGNOSE || intent == ChatIntent.FOLLOW_UP
                || intent == ChatIntent.RETRY || intent == ChatIntent.RESET || intent == ChatIntent.OUT_OF_SCOPE ? null : intent;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 判断输入是否请求本期明确禁止返回的金额信息。
     *
     * @param message 客服原始问题
     * @return 命中金额查询边界时返回 true
     */
    private boolean isAmountQuery(String message) {
        if (message == null) return false;
        return message.contains("订单金额") || message.contains("退款金额") || message.contains("优惠金额")
            || message.contains("已收金额") || message.contains("单价") || message.contains("多少钱") || message.contains("价格");
    }

    /**
     * 客户信息查询的缺失槽位（只要求客户编号）
     */
    private List<MissingSlot> missingSlotsForInsight(DiagnosisSlots slots) {
        List<MissingSlot> missing = new ArrayList<>();
        if (slots.getCustomerId() == null && isNotBlank(slots.getCustomerCode()) == false
            && isNotBlank(slots.getCustomerName()) == false && slots.getOrderId() == null && isNotBlank(slots.getOrderCode()) == false) {
            missing.add(MissingSlot.CUSTOMER);
        }
        return missing;
    }

    /**
     * 仅有客户姓名时先解析候选；多候选必须返回给客服选择，不能自动挑选。
     *
     * @param session 当前聊天会话
     * @param orchestrator 本轮业务查询编排器
     * @return 需要直接返回的候选响应；无需候选确认时返回 null
     */
    private AgentChatResponse resolveCustomerCandidatesIfNeeded(MealPlanChatSession session,
                                                                BusinessQueryOrchestrator orchestrator) {
        DiagnosisSlots slots = session.getSlots();
        if (slots.getCustomerId() != null || isNotBlank(slots.getCustomerCode()) || !isNotBlank(slots.getCustomerName())) {
            return null;
        }
        ToolExecutionResult execution = executeBusinessTool(orchestrator, "BUSINESS_QUERY_CUSTOMER_CANDIDATES",
            slots, "resolveCustomer", null, List.of());
        Map<String, Object> result = execution.result();
        long total = resultCount(result.get("total"));
        List<?> items = result.get("items") instanceof List ? (List<?>) result.get("items") : List.of();
        if (total == 1 && !items.isEmpty() && items.get(0) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> candidate = (Map<String, Object>) items.get(0);
            Object id = candidate.get("customerId");
            if (id instanceof Number) slots.setCustomerId(((Number) id).longValue());
            Object code = candidate.get("customerCode");
            if (code != null) slots.setCustomerCode(String.valueOf(code));
            return null;
        }
        String message = items.isEmpty()
            ? "未找到匹配该姓名的客户，请改用客户编号或客户ID。"
            : "找到多个同名客户，请选择一个客户后继续查询。";
        AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_CUSTOMER_CANDIDATES", result,
            message, List.of("客户编号 C10001", "客户ID 1001", "清空会话"));
        response.setStatus(ChatStatus.NEED_MORE_INFO);
        response.setMissingSlots(List.of(MissingSlot.CUSTOMER));
        applyToolExecution(response, execution);
        return response;
    }

    /** 将受控列表结果的 total 统一为数量，兼容 JSON 反序列化后的 Number 类型。 */
    private long resultCount(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    /**
     * 为当前聊天请求创建业务查询编排器；业务客户端未注入时返回 null，保留旧诊断工具兜底路径。
     *
     * @return 当前请求专用编排器，或 null
     */
    private BusinessQueryOrchestrator createBusinessQueryOrchestrator() {
        return businessQueryChatService.createOrchestrator();
    }

    /**
     * 通过单轮编排器执行指定业务工具，所有调用都会先经过 QueryPlan 校验和工具白名单校验。
     *
     * @param orchestrator 当前请求编排器
     * @param responseType 固定业务响应类型
     * @param slots 当前查询槽位
     * @param toolName 待执行的登记工具名
     * @param ruleTopic 规则主题，仅 explainRule 使用
     * @param dishIds 菜品 ID 列表，仅 listDishes 使用
     * @return 受控工具执行结果
     */
    private ToolExecutionResult executeBusinessTool(BusinessQueryOrchestrator orchestrator, String responseType,
                                                    DiagnosisSlots slots, String toolName, String ruleTopic,
                                                    List<Integer> dishIds) {
        return businessQueryChatService.execute(orchestrator, responseType, slots, toolName, ruleTopic, dishIds);
    }

    /** 执行由问题分析器和规划器产生的 QueryPlan，避免重新按响应类型规划。 */
    private ToolExecutionResult executeBusinessTool(BusinessQueryOrchestrator orchestrator, AgentQueryPlan queryPlan,
                                                    String toolName, String ruleTopic, List<Integer> dishIds) {
        return businessQueryChatService.execute(orchestrator, queryPlan, toolName, ruleTopic, dishIds);
    }

    /** 为旧意图生成兼容分析结果并立刻规划为统一 QueryPlan。 */
    private AgentQueryPlan legacyBusinessQueryPlan(ChatIntent intent, DiagnosisSlots slots) {
        return businessQueryPlanningService.plan(LegacyBusinessQuestionAnalysisFactory.fromIntent(intent, slots));
    }

    /**
     * 执行跨客户运营统计。关键口径不明确时只追问，不执行任何统计工具。
     *
     * @param session 当前受控会话
     * @param message 用户原始问题
     * @param orchestrator 本轮工具编排器
     * @return 聚合统计或受控澄清响应
     */
    private AgentChatResponse handleOperationStatistics(MealPlanChatSession session, String message,
                                                        BusinessQueryOrchestrator orchestrator) {
        BusinessQuestionAnalysis analysis = businessQuestionAnalyzer.analyze(message, session.getSlots());
        analysis = resolveTemporalAnalysis(analysis, session, false);
        return executeOperationAnalysis(session, message, orchestrator, analysis, false);
    }

    /** 执行已经完成时间落地的运营统计语义，供新问题和 Pending Context 续接共用。 */
    private AgentChatResponse executeOperationAnalysis(MealPlanChatSession session, String message,
                                                       BusinessQueryOrchestrator orchestrator,
                                                       BusinessQuestionAnalysis analysis,
                                                       boolean pendingReused) {
        if (analysis == null || analysis.isRequiresClarification()) {
            if (analysis != null) savePendingContext(session, analysis, missingFields(analysis), message);
            String clarification = analysis == null || !isNotBlank(analysis.getClarificationQuestion())
                ? "你想查今天待排餐、待配送还是待核销的客户数？" : analysis.getClarificationQuestion();
            return response(session, ChatStatus.NEED_MORE_INFO, clarification, null,
                List.of(), List.of("待排餐", "待核销"), "BUSINESS_QUERY_OPERATION_CLARIFICATION");
        }
        if (businessQueryDataClient == null) {
            return response(session, ChatStatus.ERROR, "运营统计查询服务暂不可用，请稍后重试。", null,
                List.of(), List.of(), "BUSINESS_QUERY_OPERATION");
        }
        AgentQueryPlan queryPlan = businessQueryPlanningService.plan(analysis);
        if (queryPlan == null) {
            return response(session, ChatStatus.NEED_MORE_INFO, "请说明想查询的运营指标和统计条件。", null,
                List.of(), List.of("今天待核销客户", "今天待排餐客户"), "BUSINESS_QUERY_OPERATION_CLARIFICATION");
        }
        boolean report = queryPlan.getMetrics() != null && queryPlan.getMetrics().size() > 1;
        if (requiresRecordDate(queryPlan) && (queryPlan.getFilters() == null || !isNotBlank(queryPlan.getFilters().getRecordDate()))) {
            savePendingContext(session, analysis, List.of("recordDate"), message);
            return response(session, ChatStatus.NEED_MORE_INFO, "请补充统计日期，例如今天、明天或 2026-07-13。", null,
                List.of(MissingSlot.RECORD_DATE), List.of("今天", "明天"), "BUSINESS_QUERY_OPERATION_CLARIFICATION");
        }
        AgentQueryMetric primaryMetric = queryPlan.getMetrics().get(0);
        AgentMetricDefinition metricDefinition = AgentMetricCatalog.definition(primaryMetric);
        if (metricDefinition == null) return response(session, ChatStatus.ERROR, "运营指标未登记，已停止执行。", null,
            List.of(), List.of(), "BUSINESS_QUERY_OPERATION");
        String responseType = report ? "BUSINESS_QUERY_OPERATION_REPORT" : metricDefinition.getResponseType();
        String tool = queryPlan.getToolNames().get(0);
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator, queryPlan, tool, null, List.of());
        Map<String, Object> result = report ? operationReportResult(execution.result(), queryPlan) : execution.result();
        String answer = report ? composer().operationReport(result, queryPlan.getMetrics())
            : composer().operationStatistics(result, primaryMetric);
        session.getConversationState().setPendingBusinessQueryContext(null);
        applyResolvedFiltersToSlots(session, queryPlan.getFilters());
        AgentChatResponse response = insightResponse(session, responseType, result, answer,
            List.of("今天待核销客户", "今天已排餐客户", "活跃客户"));
        response.setQueryPlan(queryPlan);
        applyToolExecution(response, execution);
        response.setSemanticTraceSummary(semanticTrace(analysis, pendingReused));
        captureLastBusinessQueryContext(session, response);
        return response;
    }

    /**
     * 优先处理已迁移到受控语义协议的业务问题；暂未迁移的目标返回 null 走兼容分支。
     *
     * @param session 当前会话
     * @param message 用户原始问题
     * @param orchestrator 本轮只读工具编排器
     * @return 已处理的响应；目标尚未迁移时返回 null
     */
    private AgentChatResponse handleSemanticBusinessQuery(MealPlanChatSession session, String message,
                                                          BusinessQueryOrchestrator orchestrator) {
        AgentChatResponse multiFrameResponse = handleMultiFrameUnderstanding(session, message, orchestrator);
        if (multiFrameResponse != null) return multiFrameResponse;
        BusinessQuestionAnalysis analysis = resolvePendingAnalysis(session, message);
        boolean pendingReused = analysis != null;
        if (analysis == null) {
            if (session.getConversationState().getPendingBusinessQueryContext() != null && !isPureSlotReply(message)) {
                session.getConversationState().setPendingBusinessQueryContext(null);
            }
            analysis = businessQuestionAnalyzer.analyze(message, session.getSlots(),
                session.getConversationState().getLastBusinessQueryContext());
            analysis = resolveTemporalAnalysis(analysis, session, false);
        }
        if (analysis == null) return null;
        AgentChatResponse contextFollowUp = handleActiveCustomerBalanceFollowUp(session, analysis, orchestrator);
        if (contextFollowUp != null) return contextFollowUp;
        if (analysis.isRequiresClarification()) {
            // 未识别出任何领域时保留既有细粒度意图兼容入口；已识别领域的歧义统一由语义层追问。
            if (analysis.getDomains() == null || analysis.getDomains().isEmpty()) return null;
            savePendingContext(session, analysis, missingFields(analysis), message);
            AgentChatResponse clarification = response(session, ChatStatus.NEED_MORE_INFO,
                isNotBlank(analysis.getClarificationQuestion()) ? analysis.getClarificationQuestion() : "请补充需要查询的业务对象或条件。",
                null, List.of(), List.of("今天菜单", "客户订单"), "BUSINESS_QUERY_CLARIFICATION");
            clarification.setSemanticTraceSummary(semanticTrace(analysis, pendingReused));
            return clarification;
        }
        if (analysis.getDomains() != null && (analysis.getDomains().contains(me.zhengjie.agent.query.domain.AgentQueryDomain.OPERATION_STATISTICS)
            || analysis.getDomains().contains(me.zhengjie.agent.query.domain.AgentQueryDomain.NATURAL_LANGUAGE_REPORT))) {
            return executeOperationAnalysis(session, message, orchestrator, analysis, pendingReused);
        }
        if (analysis.getQueryTarget() == BusinessQueryTarget.MEAL_PLAN_DIAGNOSIS) {
            return handleSemanticMealPlanDiagnosis(session, analysis);
        }
        if (analysis.getQueryTarget() == BusinessQueryTarget.MEAL_PLAN_ALLERGY_ANALYSIS) {
            return handleMealPlanAllergyAnalysis(session, analysis, orchestrator);
        }
        if (analysis.getQueryTarget() == BusinessQueryTarget.CUSTOMER_MEAL_PLAN) {
            return handleCustomerMealPlanQuery(session, analysis, orchestrator, pendingReused);
        }
        if (analysis.getQueryTarget() == BusinessQueryTarget.CUSTOMER
            || analysis.getDomains() != null && analysis.getDomains().contains(AgentQueryDomain.CUSTOMER)) {
            return handleCustomerOverviewQuery(session, analysis, orchestrator, pendingReused);
        }
        if (analysis.getQueryTarget() != BusinessQueryTarget.SCHEDULED_MENU) return null;
        if (!isNotBlank(analysis.getFilters().getRecordDate())) {
            return response(session, ChatStatus.NEED_MORE_INFO, "请补充菜单日期，例如今天、明天或 2026-07-13。", null,
                List.of(MissingSlot.RECORD_DATE), List.of("今天", "明天"), "BUSINESS_QUERY_SCHEDULED_MENU");
        }
        if (businessQueryDataClient == null) {
            return response(session, ChatStatus.ERROR, "公共菜单查询服务暂不可用，请稍后重试。", null,
                List.of(), List.of(), "BUSINESS_QUERY_SCHEDULED_MENU");
        }
        AgentQueryPlan queryPlan = businessQueryPlanningService.plan(analysis);
        if (queryPlan == null) {
            savePendingContext(session, analysis, List.of("recordDate", "mealType"), message);
            return response(session, ChatStatus.NEED_MORE_INFO, "公共菜单仅支持查询午餐或晚餐，请确认需要的餐次。", null,
                List.of(), List.of("今天午餐菜单", "今天晚餐菜单"), "BUSINESS_QUERY_SCHEDULED_MENU");
        }
        LastBusinessQueryContext previous = session.getConversationState().getLastBusinessQueryContext();
        String fingerprint = queryPlanFingerprint(queryPlan);
        if (analysis.getInteractionMode() == BusinessInteractionMode.CORRECTION && previous != null
            && fingerprint.equals(previous.getQueryPlanFingerprint())) {
            return response(session, ChatStatus.NEED_MORE_INFO,
                "上一次已按相同的日期和餐次口径查询。请说明你想查看公共菜单、某位客户实际排餐，还是客户候选菜。",
                null, List.of(), List.of("今天公共菜单", "B3303 今天吃什么", "B3303 今天有哪些候选菜"),
                "BUSINESS_QUERY_CORRECTION_CLARIFICATION");
        }
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator, queryPlan, "listScheduledDishes", null, List.of());
        Map<String, Object> result = execution.result();
        String answer = composer().scheduledMenu(result);
        if (analysis.getInteractionMode() == BusinessInteractionMode.CORRECTION) {
            answer = "已重新规划查询口径：按指定日期的公共排期菜单分别查询午餐和晚餐。" + answer;
        }
        AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_SCHEDULED_MENU", result, answer,
            List.of("今天午餐菜单", "今天晚餐菜单", "清空会话"));
        response.setQueryPlan(queryPlan);
        response.setSemanticTraceSummary(semanticTrace(analysis, pendingReused));
        session.getConversationState().setPendingBusinessQueryContext(null);
        applyToolExecution(response, execution);
        applyResultValidation(response, queryPlan, result);
        captureLastBusinessQueryContext(session, response);
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
        return response;
    }

    /** 执行已登记的多帧能力；shadow 只完成理解而不调用任何新增业务工具。 */
    private AgentChatResponse handleMultiFrameUnderstanding(MealPlanChatSession session, String message, BusinessQueryOrchestrator orchestrator) {
        if (conversationUnderstandingService == null || multiIntentPlanningService == null || !"new".equalsIgnoreCase(conversationUnderstandingMode)) return null;
        LastBusinessQueryContext last = session.getConversationState().getLastBusinessQueryContext();
        me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult understanding = conversationUnderstandingService.understand(message, session.getSlots(), last == null ? List.of() : last.getContextHandles());
        if (understanding == null || understanding.isRequiresClarification() || understanding.getFrames().isEmpty()) return null;
        ContextReferenceResolver resolver = new ContextReferenceResolver();
        for (me.zhengjie.agent.analysis.domain.SemanticRequestFrame frame : understanding.getFrames()) {
            ContextReferenceResolver.Resolution resolution = resolver.resolve(frame, last == null ? List.of() : last.getContextHandles(), OffsetDateTime.now(ZoneOffset.ofHours(8)));
            if (resolution.status() == ContextReferenceResolver.Status.MISSING || resolution.status() == ContextReferenceResolver.Status.AMBIGUOUS) return response(session, ChatStatus.NEED_MORE_INFO,
                resolution.status() == ContextReferenceResolver.Status.MISSING ? "你指的是哪些客户？" : "当前存在多个可引用对象，请说明要查看哪一批。", null, List.of(), List.of(), resolution.status() == ContextReferenceResolver.Status.MISSING ? "CONTEXT_REFERENCE_MISSING" : "CONTEXT_REFERENCE_AMBIGUOUS");
        }
        if (conversationUnderstandingValidator.validate(understanding) != null) return null;
        List<AgentQueryPlan> plans = multiIntentPlanningService.plan(understanding);
        if (plans.size() != 1 || businessQueryDataClient == null) return null;
        AgentQueryPlan plan = plans.get(0);
        me.zhengjie.agent.query.domain.ConversationTask task = new me.zhengjie.agent.query.domain.ConversationTask();
        task.setTaskId("task-" + java.util.UUID.randomUUID()); task.setStatus(me.zhengjie.agent.query.domain.ConversationTaskStatus.ACTIVE);
        task.setUnderstanding(understanding); task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
        session.getConversationState().getTaskStack().add(task);
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator, plan, plan.getToolNames().get(0), null, List.of());
        task.setStatus(execution.partial() ? me.zhengjie.agent.query.domain.ConversationTaskStatus.FAILED
            : me.zhengjie.agent.query.domain.ConversationTaskStatus.COMPLETED);
        task.setFailureCode(execution.partial() && execution.warnings() != null && !execution.warnings().isEmpty() ? execution.warnings().get(0) : null);
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
        Map<String, Object> result = execution.result();
        AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_ACTIVE_CUSTOMER_BALANCES", result,
            "已按上一轮授权客户集合展示餐数余额明细。", List.of("活跃客户数", "清空会话"));
        response.setQueryPlan(plan); applyToolExecution(response, execution);
        response.setActiveTaskStack(session.getConversationState().getTaskStack());
        return response;
    }

    /**
     * 将已识别的餐数余额语义与服务端登记的客户集合句柄组合为固定明细计划。
     * 该方法只检查结构化语义和句柄类型，绝不按代词或量词文本直接选择集合或工具。
     */
    private AgentChatResponse handleActiveCustomerBalanceFollowUp(MealPlanChatSession session,
                                                                   BusinessQuestionAnalysis analysis,
                                                                   BusinessQueryOrchestrator orchestrator) {
        if (analysis == null || analysis.getMetrics() == null || !analysis.getMetrics().contains(AgentQueryMetric.MEAL_BALANCE)
            || analysis.getEntities() == null || analysis.getEntities().getCustomerId() != null || isNotBlank(analysis.getEntities().getCustomerCode())) return null;
        LastBusinessQueryContext context = session.getConversationState().getLastBusinessQueryContext();
        me.zhengjie.agent.analysis.domain.SemanticRequestFrame frame = new me.zhengjie.agent.analysis.domain.SemanticRequestFrame();
        me.zhengjie.agent.analysis.domain.SemanticScope scope = new me.zhengjie.agent.analysis.domain.SemanticScope();
        scope.setType(me.zhengjie.agent.analysis.domain.SemanticScope.Type.CONTEXT_REFERENCE);
        scope.setRequiredKind(ContextHandleKind.ENTITY_SET); scope.setRequiredEntityType(SemanticEntityType.CUSTOMER);
        frame.setScope(scope);
        ContextReferenceResolver.Resolution resolution = new ContextReferenceResolver().resolve(frame,
            context == null ? List.of() : context.getContextHandles(), OffsetDateTime.now(ZoneOffset.ofHours(8)));
        if (resolution.status() == ContextReferenceResolver.Status.MISSING) {
            return response(session, ChatStatus.NEED_MORE_INFO, "你指的是哪些客户？请先查询一个客户集合，或提供客户编号。", null,
                List.of(), List.of("现在活跃客户有多少", "B3303 还剩多少餐"), "CONTEXT_REFERENCE_MISSING");
        }
        if (resolution.status() == ContextReferenceResolver.Status.AMBIGUOUS) {
            return response(session, ChatStatus.NEED_MORE_INFO, "当前存在多个可引用的客户集合，请说明要查看哪一批客户。", null,
                List.of(), List.of(), "CONTEXT_REFERENCE_AMBIGUOUS");
        }
        if (resolution.status() != ContextReferenceResolver.Status.RESOLVED || !"AGENT_ACTIVE_CUSTOMER_V1".equals(resolution.handle().getDefinitionId())) return null;
        if (businessQueryDataClient == null) return response(session, ChatStatus.ERROR, "客户余额明细查询服务暂不可用，请稍后重试。", null,
            List.of(), List.of(), "CAPABILITY_NOT_AVAILABLE");
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V2); plan.setDomain(AgentQueryDomain.OPERATION_STATISTICS);
        plan.setAction(AgentQueryAction.BREAKDOWN); plan.setMetrics(List.of(AgentQueryMetric.ACTIVE_CUSTOMER_MEAL_BALANCE_DETAIL));
        plan.setDimensions(List.of(AgentQueryDimension.CUSTOMER)); plan.setLimit(50); plan.getFilters().setPage(1); plan.getFilters().setSize(50);
        plan.setToolNames(List.of("listActiveCustomerMealBalances")); plan.setMetricVersion(AgentMetricCatalog.VERSION); plan.setTimezone("Asia/Shanghai"); plan.setAnalysisSource(analysis.getSource()); plan.setAnalysisConfidence(analysis.getConfidence());
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator, plan, "listActiveCustomerMealBalances", null, List.of());
        Map<String, Object> result = execution.result();
        String answer = composer().activeCustomerBalances(result);
        AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_ACTIVE_CUSTOMER_BALANCES", result, answer, List.of("活跃客户数", "清空会话"));
        response.setQueryPlan(plan); applyToolExecution(response, execution); response.setSemanticTraceSummary(semanticTrace(analysis, false));
        return response;
    }

    /**
     * 执行客户排餐语义计划。客户编号先解析为内部 ID，成功后清除 Pending 并记录本轮语义追踪。
     *
     * @param session 当前会话
     * @param analysis 已补齐日期的受控客户排餐语义
     * @param orchestrator 本轮只读工具编排器
     * @param pendingReused 是否来自上一轮待补上下文
     * @return 客户解析结果、受控错误或排餐查询响应
     */
    private AgentChatResponse handleCustomerMealPlanQuery(MealPlanChatSession session,
                                                          BusinessQuestionAnalysis analysis,
                                                          BusinessQueryOrchestrator orchestrator,
                                                          boolean pendingReused) {
        if (businessQueryDataClient == null) {
            return response(session, ChatStatus.ERROR, "排餐查询服务暂不可用，请稍后重试。", null,
                List.of(), List.of(), "BUSINESS_QUERY_MEAL_PLAN");
        }
        syncSemanticCustomerToSlots(session.getSlots(), analysis);
        AgentQueryPlan queryPlan = businessQueryPlanningService.plan(analysis);
        if (queryPlan == null || !isNotBlank(analysis.getFilters().getRecordDate())) {
            savePendingContext(session, analysis, List.of("recordDate"), "");
            return response(session, ChatStatus.NEED_MORE_INFO, "请确认要查询的排餐日期，例如今天、明天或 2026-07-14。",
                null, List.of(MissingSlot.RECORD_DATE), List.of("今天", "明天"), "BUSINESS_QUERY_CLARIFICATION");
        }
        if (queryPlan.getEntities().getCustomerId() == null) {
            ResolvedCustomer resolved = resolveCustomerForBusinessQuery(orchestrator, session);
            if (resolved.response() != null) {
                session.getConversationState().setPendingBusinessQueryContext(null);
                return resolved.response();
            }
            session.getSlots().setCustomerId(resolved.customerId());
            queryPlan.getEntities().setCustomerId(resolved.customerId());
        }
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator, queryPlan, "listMealPlans", null, List.of());
        Map<String, Object> result = execution.result();
        session.getConversationState().setPendingBusinessQueryContext(null);
        applyResolvedFiltersToSlots(session, queryPlan.getFilters());
        AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_MEAL_PLAN", result,
            composer().mealPlan(result), CUSTOMER_INSIGHT_QUICK_REPLIES);
        response.setQueryPlan(queryPlan);
        response.setSemanticTraceSummary(semanticTrace(analysis, pendingReused));
        applyToolExecution(response, execution);
        applyResultValidation(response, queryPlan, result);
        captureLastBusinessQueryContext(session, response);
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
        return response;
    }

    /** 将模型或 Pending 中的客户实体补到确定性槽位，供统一客户解析工具使用。 */
    private void syncSemanticCustomerToSlots(DiagnosisSlots slots, BusinessQuestionAnalysis analysis) {
        if (slots == null || analysis == null || analysis.getEntities() == null) return;
        if (slots.getCustomerId() == null) slots.setCustomerId(analysis.getEntities().getCustomerId());
        if (!isNotBlank(slots.getCustomerCode())) slots.setCustomerCode(analysis.getEntities().getCustomerCode());
        if (!isNotBlank(slots.getCustomerName())) slots.setCustomerName(analysis.getEntities().getCustomerName());
    }

    /**
     * 执行单客户综合概览查询，包含档案创建时间和首笔订单购买时间。
     *
     * @param session 当前会话
     * @param analysis 已验证的客户查询语义
     * @param orchestrator 受控只读工具编排器
     * @param pendingReused 是否从待补上下文恢复
     * @return 客户概览或客户槽位追问
     */
    private AgentChatResponse handleCustomerOverviewQuery(MealPlanChatSession session,
                                                           BusinessQuestionAnalysis analysis,
                                                           BusinessQueryOrchestrator orchestrator,
                                                           boolean pendingReused) {
        if (businessQueryDataClient == null) {
            return response(session, ChatStatus.ERROR, "客户信息查询服务暂不可用，请稍后重试。", null,
                List.of(), List.of(), "BUSINESS_QUERY_CUSTOMER");
        }
        syncSemanticCustomerToSlots(session.getSlots(), analysis);
        if (session.getSlots().getCustomerId() == null && !isNotBlank(session.getSlots().getCustomerCode())
            && !isNotBlank(session.getSlots().getCustomerName())) {
            return response(session, ChatStatus.NEED_MORE_INFO, "请提供客户编号，例如：B2200。", null,
                List.of(MissingSlot.CUSTOMER), List.of("客户编号 B2200"), "SLOT_REQUIRED");
        }
        AgentQueryPlan queryPlan = businessQueryPlanningService.plan(analysis);
        if (queryPlan == null) return null;
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator, queryPlan, "customerOverview", null, List.of());
        Map<String, Object> result = execution.result();
        session.getConversationState().setPendingBusinessQueryContext(null);
        AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_CUSTOMER", result,
            composer().customerOverview(result), CUSTOMER_INSIGHT_QUICK_REPLIES);
        response.setQueryPlan(queryPlan);
        response.setSemanticTraceSummary(semanticTrace(analysis, pendingReused));
        applyToolExecution(response, execution);
        applyResultValidation(response, queryPlan, result);
        captureLastBusinessQueryContext(session, response);
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
        return response;
    }

    /**
     * 执行由受控语义明确选择的单客户排餐诊断；实体和过滤条件缺失时只追问，不启动诊断模型。
     *
     * @param session 当前会话及确定性槽位
     * @param analysis LLM 输出并通过结构校验的诊断语义
     * @return 槽位追问或现有诊断服务的受控响应
     */
    private AgentChatResponse handleSemanticMealPlanDiagnosis(MealPlanChatSession session, BusinessQuestionAnalysis analysis) {
        fillMissingSemanticDiagnosisSlots(session.getSlots(), analysis);
        List<MissingSlot> missing = missingSlots(session.getSlots(), ChatIntent.DIAGNOSE);
        if (!missing.isEmpty()) {
            session.getConversationState().setStage(DiagnosisConversationState.COLLECTING_SLOTS);
            return response(session, ChatStatus.NEED_MORE_INFO, questionFor(missing), null, missing,
                quickRepliesFor(missing), "SLOT_REQUIRED");
        }
        session.getConversationState().setStage(DiagnosisConversationState.READY_TO_DIAGNOSE);
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSING);
        DiagnosisResponse diagnosisResult = diagnosisService.diagnose(toDiagnosisRequest(session.getSlots()));
        session.getConversationState().addDiagnosisResult(diagnosisResult);
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
        return response(session, ChatStatus.ANSWERED, diagnosisMessage(diagnosisResult), diagnosisResult, List.of(),
            List.of("为什么候选菜为空", "换成晚餐", "重新排查", "清空会话"), "MEAL_PLAN_DIAGNOSIS");
    }

    /** 仅用语义分析结果补齐确定性抽取未获得的诊断槽位，显式输入和会话槽位保持优先。 */
    private void fillMissingSemanticDiagnosisSlots(DiagnosisSlots slots, BusinessQuestionAnalysis analysis) {
        if (slots == null || analysis == null) return;
        if (analysis.getEntities() != null) {
            boolean hasDeterministicCustomer = slots.getCustomerId() != null || isNotBlank(slots.getCustomerCode())
                || isNotBlank(slots.getCustomerName());
            if (!hasDeterministicCustomer) {
                slots.setCustomerId(analysis.getEntities().getCustomerId());
                slots.setCustomerCode(analysis.getEntities().getCustomerCode());
                slots.setCustomerName(analysis.getEntities().getCustomerName());
            }
        }
        if (analysis.getFilters() != null) {
            if (!isNotBlank(slots.getRecordDate())) slots.setRecordDate(analysis.getFilters().getRecordDate());
            if (!isNotBlank(slots.getMealType())) slots.setMealType(analysis.getFilters().getMealType());
        }
    }

    /**
     * 执行跨客户排餐过敏事实分析。只将 replaceReason=ALLERGY 且 isAllergyFiltered=true 的菜品作为结论，
     * 客户主动排除和其他换菜原因均不参与，避免错误表述为过敏。
     */
    @SuppressWarnings("unchecked")
    private AgentChatResponse handleMealPlanAllergyAnalysis(MealPlanChatSession session, BusinessQuestionAnalysis analysis,
                                                            BusinessQueryOrchestrator orchestrator) {
        if (businessQueryDataClient == null) {
            return response(session, ChatStatus.ERROR, "排餐查询服务暂不可用，请稍后重试。", null,
                List.of(), List.of(), "BUSINESS_QUERY_MEAL_PLAN_ALLERGY");
        }
        AgentQueryPlan queryPlan = businessQueryPlanningService.plan(analysis);
        if (queryPlan == null) {
            return response(session, ChatStatus.NEED_MORE_INFO, "请补充要查询的排餐日期和餐次，例如今天午餐。", null,
                List.of(MissingSlot.RECORD_DATE, MissingSlot.MEAL_TYPE), List.of("今天午餐", "今天晚餐"), "BUSINESS_QUERY_MEAL_PLAN_ALLERGY");
        }
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator, queryPlan, "listMealPlans", null, List.of());
        Map<String, Object> result = allergyFilteredMealPlans(execution.result());
        AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_MEAL_PLAN_ALLERGY", result,
            composer().mealPlanAllergy(result), List.of("今天午餐排餐客户对哪些菜过敏", "今天晚餐排餐客户对哪些菜过敏", "清空会话"));
        response.setQueryPlan(queryPlan);
        applyToolExecution(response, execution);
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
        return response;
    }

    /** 将范围排餐结果转换为仅含实际过敏过滤菜品的客户分组结果，并保留完整性元数据。 */
    private Map<String, Object> allergyFilteredMealPlans(Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> selected = new ArrayList<>();
        long totalCount = raw != null && raw.get("total") instanceof Number ? ((Number) raw.get("total")).longValue() : 0L;
        long scanned = 0L;
        if (raw != null && raw.get("items") instanceof List) {
            scanned = ((List<?>) raw.get("items")).size();
            for (Object planValue : (List<?>) raw.get("items")) {
                if (!(planValue instanceof Map)) continue;
                Map<String, Object> plan = (Map<String, Object>) planValue;
                if (!(plan.get("dishes") instanceof List)) continue;
                List<Map<String, Object>> dishes = new ArrayList<>();
                for (Object dishValue : (List<?>) plan.get("dishes")) {
                    if (!(dishValue instanceof Map)) continue;
                    Map<String, Object> dish = (Map<String, Object>) dishValue;
                    if (Boolean.TRUE.equals(dish.get("allergyFiltered")) && "ALLERGY".equals(dish.get("replaceReason"))) dishes.add(dish);
                }
                if (!dishes.isEmpty()) {
                    Map<String, Object> customer = new LinkedHashMap<>();
                    customer.put("customerCode", plan.get("customerCode")); customer.put("customerMealPlanId", plan.get("customerMealPlanId"));
                    customer.put("recordDate", plan.get("recordDate")); customer.put("mealTypeCode", plan.get("mealTypeCode")); customer.put("dishes", dishes);
                    selected.add(customer);
                }
            }
        }
        result.put("items", selected); result.put("total", selected.size()); result.put("scannedCount", scanned);
        result.put("totalCount", totalCount); result.put("page", raw == null ? 1 : raw.getOrDefault("page", 1));
        result.put("size", raw == null ? 0 : raw.getOrDefault("size", 0));
        result.put("queriedAt", raw == null ? null : raw.get("queriedAt"));
        result.put("truncated", raw != null && Boolean.TRUE.equals(raw.get("truncated")));
        return result;
    }

    /** 将菜单结果的领域合理性告警合并到响应，异常时不再声称它是完整菜单。 */
    private void applyResultValidation(AgentChatResponse response, AgentQueryPlan queryPlan, Map<String, Object> result) {
        List<String> codes = new BusinessResultValidator().validate(response.getResponseType(), queryPlan, result);
        if (codes.isEmpty()) return;
        List<String> warnings = new ArrayList<>(response.getWarnings() == null ? List.of() : response.getWarnings());
        codes.forEach(code -> { if (!warnings.contains(code)) warnings.add(code); });
        response.setWarnings(warnings);
        response.setPartial(true);
        if (codes.contains("MENU_RESULT_IMPLAUSIBLE")) {
            response.setAssistantMessage("查询结果仅包含米饭类型菜品，不能确认其为完整公共菜单；请核对排期配置或指定客户实际排餐。"
                + " 数据依据：[F1]");
        }
    }

    /** 判断统计计划是否包含必须按单日计算的每日工作量指标。 */
    private boolean requiresRecordDate(AgentQueryPlan queryPlan) {
        if (queryPlan == null || queryPlan.getMetrics() == null) return false;
        for (me.zhengjie.agent.query.domain.AgentQueryMetric metric : queryPlan.getMetrics()) {
            if (metric == me.zhengjie.agent.query.domain.AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT
                || metric == me.zhengjie.agent.query.domain.AgentQueryMetric.DAILY_VERIFIED_CUSTOMER_COUNT
                || metric == me.zhengjie.agent.query.domain.AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT
                || metric == me.zhengjie.agent.query.domain.AgentQueryMetric.DAILY_EXPECTED_CUSTOMER_COUNT
                || metric == me.zhengjie.agent.query.domain.AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT
                || metric == me.zhengjie.agent.query.domain.AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT) return true;
        }
        return false;
    }

    /** 将已登记报表指标写入受控展示结果，供事实工厂逐项映射，不能携带自由字段。 */
    private Map<String, Object> operationReportResult(Map<String, Object> source, AgentQueryPlan queryPlan) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source != null) result.putAll(source);
        result.put("reportMetrics", queryPlan.getMetrics().stream().map(Enum::name).collect(java.util.stream.Collectors.toList()));
        return result;
    }

    /**
     * 将一个或多个工具执行状态合并进响应，暴露缓存命中、部分失败和受控告警。
     *
     * @param response 待补充元信息的聊天响应
     * @param executions 本轮相关工具执行结果
     */
    private void applyToolExecution(AgentChatResponse response, ToolExecutionResult... executions) {
        businessQueryChatService.applyToolExecution(response, executions);
    }

    /**
     * 客户编号场景先通过客户概览解析客户 ID，解析失败时直接返回概览响应。
     *
     * @param orchestrator 当前请求编排器
     * @param session 当前聊天会话
     * @return 已解析客户 ID，或需要立即返回的响应
     */
    private ResolvedCustomer resolveCustomerForBusinessQuery(BusinessQueryOrchestrator orchestrator,
                                                             MealPlanChatSession session) {
        DiagnosisSlots slots = session.getSlots();
        if (slots.getCustomerId() != null) return new ResolvedCustomer(slots.getCustomerId(), null);
        ToolExecutionResult overviewExecution = executeBusinessTool(orchestrator, "BUSINESS_QUERY_CUSTOMER",
            slots, "customerOverview", null, List.of());
        Map<String, Object> overview = overviewExecution.result();
        Object overviewCustomerId = overview.get("customerId");
        if (overviewCustomerId instanceof Number) return new ResolvedCustomer(((Number) overviewCustomerId).longValue(), null);
        AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_CUSTOMER", overview,
            composer().customerOverview(overview), CUSTOMER_INSIGHT_QUICK_REPLIES);
        applyToolExecution(response, overviewExecution);
        return new ResolvedCustomer(null, response);
    }

    /**
     * 将单条详情结果包装为列表响应，复用订单列表的卡片和事实构造逻辑。
     *
     * @param detail 订单详情结果
     * @return 包含 total/items 的列表形态结果
     */
    private Map<String, Object> singleItemResult(Map<String, Object> detail) {
        return Map.of("total", detail == null || detail.isEmpty() ? 0 : 1,
            "items", detail == null || detail.isEmpty() ? List.of() : List.of(detail));
    }

    /** 客户 ID 解析结果。 */
    private record ResolvedCustomer(Long customerId, AgentChatResponse response) {}

    /**
     * 处理客户信息查询并构建响应，业务查询优先通过单轮编排器执行，统一工具白名单、预算和缓存。
     *
     * @param intent 已识别的客户查询意图
     * @param session 当前聊天会话
     * @param extraction 本轮抽取结果
     * @param orchestrator 本轮业务查询编排器；业务客户端不可用时为空
     * @return 客户信息查询响应
     */
    private AgentChatResponse handleCustomerInsight(ChatIntent intent, MealPlanChatSession session,
                                                    ChatExtractionResult extraction,
                                                    BusinessQueryOrchestrator orchestrator) {
        DiagnosisSlots slots = session.getSlots();
        String customerCode = slots.getCustomerCode();
        Long customerId = slots.getCustomerId();

        switch (intent) {
            case CUSTOMER_MEAL_BALANCE_QUERY: {
                if (businessQueryDataClient != null) {
                    AgentQueryPlan queryPlan = legacyBusinessQueryPlan(intent, slots);
                    ToolExecutionResult execution = executeBusinessTool(orchestrator, queryPlan,
                        "customerOverview", null, List.of());
                    AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_CUSTOMER", execution.result(),
                        composer().customerOverview(execution.result()), CUSTOMER_INSIGHT_QUICK_REPLIES);
                    response.setQueryPlan(queryPlan);
                    applyToolExecution(response, execution);
                    return response;
                }
                DiagnosisToolCustomerInsightMealRequest req = new DiagnosisToolCustomerInsightMealRequest();
                req.setCustomerId(customerId);
                req.setCustomerCode(customerCode);
                req.setMealType(slots.getMealType());
                Map<String, Object> result = dataClient.getCustomerMealSummary(req);
                String message = buildMealBalanceMessage(result);
                return insightResponse(session, "CUSTOMER_MEAL_SUMMARY", result, message, CUSTOMER_INSIGHT_QUICK_REPLIES);
            }
            case CUSTOMER_VERIFICATION_QUERY: {
                if (businessQueryDataClient != null) {
                    ResolvedCustomer resolved = resolveCustomerForBusinessQuery(orchestrator, session);
                    if (resolved.response != null) return resolved.response;
                    DiagnosisSlots resolvedSlots = copy(slots);
                    resolvedSlots.setCustomerId(resolved.customerId);
                    AgentQueryPlan queryPlan = legacyBusinessQueryPlan(intent, resolvedSlots);
                    ToolExecutionResult execution = executeBusinessTool(orchestrator, queryPlan,
                        "listVerifications", null, List.of());
                    AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_VERIFICATION", execution.result(),
                        composer().verificationList(execution.result()), CUSTOMER_INSIGHT_QUICK_REPLIES);
                    response.setQueryPlan(queryPlan);
                    applyToolExecution(response, execution);
                    return response;
                }
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
                if (businessQueryDataClient != null) {
                    if (slots.getOrderId() != null || isNotBlank(slots.getOrderCode())) {
                        AgentQueryPlan queryPlan = legacyBusinessQueryPlan(intent, slots);
                        ToolExecutionResult execution = executeBusinessTool(orchestrator, queryPlan,
                            "orderDetail", null, List.of());
                        Map<String, Object> result = singleItemResult(execution.result());
                        AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_ORDER", result,
                            composer().orderList(result), CUSTOMER_INSIGHT_QUICK_REPLIES);
                        response.setQueryPlan(queryPlan);
                        applyToolExecution(response, execution);
                        return response;
                    }
                    ResolvedCustomer resolved = resolveCustomerForBusinessQuery(orchestrator, session);
                    if (resolved.response != null) return resolved.response;
                    DiagnosisSlots resolvedSlots = copy(slots);
                    resolvedSlots.setCustomerId(resolved.customerId);
                    AgentQueryPlan queryPlan = legacyBusinessQueryPlan(intent, resolvedSlots);
                    ToolExecutionResult execution = executeBusinessTool(orchestrator, queryPlan,
                        "listOrders", null, List.of());
                    AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_ORDER", execution.result(),
                        composer().orderList(execution.result()), CUSTOMER_INSIGHT_QUICK_REPLIES);
                    response.setQueryPlan(queryPlan);
                    applyToolExecution(response, execution);
                    return response;
                }
                DiagnosisToolCustomerInsightOrderRequest req = new DiagnosisToolCustomerInsightOrderRequest();
                req.setCustomerId(customerId);
                req.setCustomerCode(customerCode);
                req.setOrderStatus(slots.getOrderStatus());
                Map<String, Object> result = dataClient.getCustomerOrderSummary(req);
                String message = buildOrderMessage(result);
                return insightResponse(session, "CUSTOMER_ORDER_SUMMARY", result, message, CUSTOMER_INSIGHT_QUICK_REPLIES);
            }
            case CUSTOMER_REFUND_QUERY: {
                if (businessQueryDataClient != null) {
                    ResolvedCustomer resolved = resolveCustomerForBusinessQuery(orchestrator, session);
                    if (resolved.response != null) return resolved.response;
                    DiagnosisSlots resolvedSlots = copy(slots);
                    resolvedSlots.setCustomerId(resolved.customerId);
                    AgentQueryPlan queryPlan = legacyBusinessQueryPlan(intent, resolvedSlots);
                    ToolExecutionResult execution = executeBusinessTool(orchestrator, queryPlan,
                        "listRefunds", null, List.of());
                    AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_REFUND", execution.result(),
                        composer().refundList(execution.result()), CUSTOMER_INSIGHT_QUICK_REPLIES);
                    response.setQueryPlan(queryPlan);
                    applyToolExecution(response, execution);
                    return response;
                }
                return insightResponse(session, "BUSINESS_QUERY_REFUND", Map.of(), "退餐查询服务暂不可用，请稍后重试。", CUSTOMER_INSIGHT_QUICK_REPLIES);
            }
            case CUSTOMER_PACKAGE_QUERY: {
                if (businessQueryDataClient == null) {
                    return insightResponse(session, "BUSINESS_QUERY_CUSTOMER", Map.of(), "套餐查询服务暂不可用，请稍后重试。", CUSTOMER_INSIGHT_QUICK_REPLIES);
                }
                ToolExecutionResult overviewExecution = executeBusinessTool(orchestrator, "BUSINESS_QUERY_CUSTOMER",
                    slots, "customerOverview", null, List.of());
                Map<String, Object> overview = overviewExecution.result();
                List<Long> packageIds = extractParentPackageIds(overview);
                if (packageIds.isEmpty()) {
                    AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_CUSTOMER", overview,
                        composer().customerPackages(overview), CUSTOMER_INSIGHT_QUICK_REPLIES);
                    applyToolExecution(response, overviewExecution);
                    return response;
                }
                List<Map<String, Object>> details = new ArrayList<>();
                List<ToolExecutionResult> executions = new ArrayList<>();
                executions.add(overviewExecution);
                for (Long packageId : packageIds) {
                    AgentQueryPlan packagePlan = packageDetailPlan(slots, packageId);
                    ToolExecutionResult execution = orchestrator == null ? ToolExecutionResult.failure("BUSINESS_QUERY_CLIENT_UNAVAILABLE")
                        : orchestrator.execute(packagePlan, "packageDetail", null, List.of());
                    executions.add(execution);
                    if (!execution.partial() && !execution.result().isEmpty()) details.add(execution.result());
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("total", details.size());
                result.put("items", details);
                result.put("truncated", isPackageListTruncated(overview));
                AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_PACKAGE", result,
                    composer().packageDetails(result), CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, executions.toArray(new ToolExecutionResult[0]));
                return response;
            }
            case MEAL_BALANCE_CHANGE_QUERY: {
                if (businessQueryDataClient == null) return insightResponse(session, "BUSINESS_QUERY_CUSTOMER", Map.of(), "餐数变化查询服务暂不可用，请稍后重试。", CUSTOMER_INSIGHT_QUICK_REPLIES);
                ToolExecutionResult overviewExecution = executeBusinessTool(orchestrator, "BUSINESS_QUERY_CUSTOMER",
                    slots, "customerOverview", null, List.of());
                Map<String, Object> overview = overviewExecution.result();
                Long resolvedCustomerId = overview.get("customerId") instanceof Number ? ((Number) overview.get("customerId")).longValue() : customerId;
                if (resolvedCustomerId == null) {
                    AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_CUSTOMER", overview,
                        composer().customerOverview(overview), CUSTOMER_INSIGHT_QUICK_REPLIES);
                    applyToolExecution(response, overviewExecution);
                    return response;
                }
                DiagnosisSlots resolvedSlots = copy(slots);
                resolvedSlots.setCustomerId(resolvedCustomerId);
                ToolExecutionResult verificationExecution = executeBusinessTool(orchestrator, "BUSINESS_QUERY_VERIFICATION",
                    resolvedSlots, "listVerifications", null, List.of());
                ToolExecutionResult refundExecution = executeBusinessTool(orchestrator, "BUSINESS_QUERY_REFUND",
                    resolvedSlots, "listRefunds", null, List.of());
                ToolExecutionResult cachedOverviewExecution = executeBusinessTool(orchestrator, "BUSINESS_QUERY_CUSTOMER",
                    slots, "customerOverview", null, List.of());
                Map<String, Object> changeResult = new LinkedHashMap<>(overview);
                changeResult.put("verificationRecordCount", verificationExecution.result().getOrDefault("total", 0));
                changeResult.put("refundRecordCount", refundExecution.result().getOrDefault("total", 0));
                AgentChatResponse response = insightResponse(session, "BUSINESS_QUERY_CUSTOMER", changeResult,
                    composer().mealBalanceChange(overview, verificationExecution.result(), refundExecution.result()), CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, overviewExecution, verificationExecution, refundExecution, cachedOverviewExecution);
                return response;
            }
            default:
                throw new IllegalStateException("Unexpected insight intent: " + intent);
        }
    }

    /** 返回本轮业务查询使用的统一固定话术组装器。 */
    private BusinessAnswerComposer composer() { return businessQueryChatService.responseFactory().answerComposer(); }

    /** 从客户概览的受控套餐摘要提取最多五个父套餐标识，拒绝用户自由传入套餐 ID。 */
    @SuppressWarnings("unchecked")
    private List<Long> extractParentPackageIds(Map<String, Object> overview) {
        if (overview == null || !(overview.get("packages") instanceof List)) return List.of();
        return ((List<Map<String, Object>>) overview.get("packages")).stream()
            .map(item -> item.get("parentPackageId")).filter(Number.class::isInstance).map(Number.class::cast)
            .map(Number::longValue).distinct().limit(5).collect(java.util.stream.Collectors.toList());
    }

    /** 判断客户套餐摘要是否超出本轮套餐规格查询安全上限。 */
    @SuppressWarnings("unchecked")
    private boolean isPackageListTruncated(Map<String, Object> overview) {
        return overview != null && overview.get("packages") instanceof List && ((List<?>) overview.get("packages")).size() > 5;
    }

    /** 构造仅从客户套餐摘要派生套餐 ID 的受控规格查询计划。 */
    private AgentQueryPlan packageDetailPlan(DiagnosisSlots slots, Long packageId) {
        AgentQueryPlan plan = buildQueryPlan("BUSINESS_QUERY_PACKAGE", slots);
        plan.getEntities().setPackageId(packageId);
        return plan;
    }

    @SuppressWarnings("unchecked")
    private List<Integer> extractDishIds(Map<String, Object> mealPlanResult) {
        if (mealPlanResult == null || !(mealPlanResult.get("items") instanceof List)) return List.of();
        return ((List<Map<String, Object>>) mealPlanResult.get("items")).stream()
            .flatMap(plan -> plan.get("dishes") instanceof List ? ((List<Map<String, Object>>) plan.get("dishes")).stream() : java.util.stream.Stream.empty())
            .map(item -> item.get("dishId")).filter(Number.class::isInstance).map(Number.class::cast)
            .map(Number::intValue).distinct().limit(20).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 将受控规则问法映射到主系统白名单主题，避免把用户原文作为任意规则标识传递。
     *
     * @param message 客服问题
     * @return 主系统认可的规则主题
     */
    private String ruleTopic(String message) {
        String text = message == null ? "" : message;
        if (text.contains("订单") && (text.contains("有效") || text.contains("什么时候"))) return "ORDER_EFFECTIVE";
        if (text.contains("排餐模式") || text.contains("餐次匹配") || text.contains("不能排")) return "MEAL_PLAN_MATCH";
        if (text.contains("过敏") || text.contains("忌口") || text.contains("排除日期") || text.contains("菜") && text.contains("过滤")) return "DIETARY_FILTER";
        if (text.contains("退餐") || text.contains("核销") && (text.contains("影响") || text.contains("规则") || text.contains("餐数"))) return "VERIFICATION_REFUND_EFFECT";
        return "MEAL_BALANCE";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> filterUnverifiedMealPlans(Map<String, Object> result) {
        if (result == null || !(result.get("items") instanceof List)) return Map.of("total", 0, "items", List.of());
        List<Map<String, Object>> items = ((List<Map<String, Object>>) result.get("items")).stream()
            .filter(item -> !Boolean.TRUE.equals(item.get("verified"))).collect(java.util.stream.Collectors.toList());
        return Map.of("total", items.size(), "items", items, "truncated", Boolean.TRUE.equals(result.get("truncated")));
    }

    /**
     * 构建客户信息查询响应
     */
    private AgentChatResponse insightResponse(MealPlanChatSession session, String responseType,
                                              Map<String, Object> insightResult, String message,
                                              List<String> quickReplies) {
        captureBusinessFocus(session, responseType, insightResult);
        AgentChatResponse response = businessQueryChatService.responseFactory().create(session.getSessionId(), copy(session.getSlots()),
            copyMap(session.getSlots().getSlotConfidence()), session.getConversationState().getStage(),
            responseType, insightResult, message, quickReplies);
        captureLastBusinessQueryContext(session, response);
        response.setActiveTaskStack(session.getConversationState().getTaskStack());
        return response;
    }

    /** 从已脱敏的聊天响应提取下一轮重新规划所需摘要，禁止保存工具原始响应和客户敏感字段。 */
    @SuppressWarnings("unchecked")
    private void captureLastBusinessQueryContext(MealPlanChatSession session, AgentChatResponse response) {
        if (session == null || response == null || response.getQueryPlan() == null || !isBusinessResponse(response.getResponseType())) return;
        LastBusinessQueryContext context = new LastBusinessQueryContext();
        context.setResponseType(response.getResponseType());
        context.setQueryTarget(queryTarget(response.getResponseType()));
        context.setDomain(response.getQueryPlan().getDomain() == null ? null : response.getQueryPlan().getDomain().name());
        context.setQueryPlanFingerprint(queryPlanFingerprint(response.getQueryPlan()));
        context.setRecordDate(response.getQueryPlan().getFilters() == null ? null : response.getQueryPlan().getFilters().getRecordDate());
        context.setStartDate(response.getQueryPlan().getFilters() == null ? null : response.getQueryPlan().getFilters().getStartDate());
        context.setEndDate(response.getQueryPlan().getFilters() == null ? null : response.getQueryPlan().getFilters().getEndDate());
        context.setMetric(response.getQueryPlan().getMetrics() == null || response.getQueryPlan().getMetrics().isEmpty()
            ? null : response.getQueryPlan().getMetrics().get(0).name());
        context.setMealScope(response.getQueryPlan().getMealScope());
        context.setAssistantSummary(limitText(response.getAssistantMessage(), 160));
        context.setQueriedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
        Map<String, Object> shape = new LinkedHashMap<>();
        Map<String, Object> result = response.getInsightResult();
        if (result != null && result.get("total") instanceof Number) shape.put("total", result.get("total"));
        if ("BUSINESS_QUERY_OPERATION_ACTIVE".equals(response.getResponseType()) && result != null && result.get("total") instanceof Number) {
            ConversationContextHandle handle = new ConversationContextHandle();
            handle.setHandleId("ctx-" + java.util.UUID.randomUUID()); handle.setKind(ContextHandleKind.ENTITY_SET);
            handle.setEntityType(SemanticEntityType.CUSTOMER); handle.setDefinitionId("AGENT_ACTIVE_CUSTOMER_V1");
            handle.setCardinality(((Number) result.get("total")).intValue());
            handle.setSafeDescriptor(Map.of("metric", "ACTIVE_CUSTOMER_COUNT"));
            handle.setAllowedOperations(List.of(SemanticOperation.COUNT, SemanticOperation.PROJECT, SemanticOperation.GROUP, SemanticOperation.FILTER));
            handle.setSalience(1D); handle.setCreatedAt(context.getQueriedAt()); handle.setExpiresAt(context.getQueriedAt().plusMinutes(30));
            context.setContextHandles(List.of(handle));
        }
        if ("BUSINESS_QUERY_SCHEDULED_MENU".equals(response.getResponseType()) && result != null && result.get("groups") instanceof List) {
            Map<String, Integer> mealTypes = new LinkedHashMap<>();
            Map<String, Integer> dishTypes = new LinkedHashMap<>();
            for (Object groupValue : (List<?>) result.get("groups")) {
                if (!(groupValue instanceof Map)) continue;
                Map<String, Object> group = (Map<String, Object>) groupValue;
                String mealType = String.valueOf(group.get("mealTypeCode"));
                Object total = group.get("total");
                mealTypes.put(mealType, total instanceof Number ? ((Number) total).intValue() : 0);
                Object items = group.get("items");
                if (items instanceof List) for (Object itemValue : (List<?>) items) {
                    if (itemValue instanceof Map) {
                        String dishType = String.valueOf(((Map<?, ?>) itemValue).get("dishTypeCode"));
                        dishTypes.put(dishType, dishTypes.getOrDefault(dishType, 0) + 1);
                    }
                }
            }
            shape.put("mealTypes", mealTypes);
            shape.put("dishTypeDistribution", dishTypes);
            shape.put("warnings", response.getWarnings() == null ? List.of() : new ArrayList<>(response.getWarnings()));
        }
        context.setResultShape(shape);
        session.getConversationState().setLastBusinessQueryContext(context);
        response.setLastBusinessQueryContext(context);
    }

    /** 计算不可逆的受控查询计划指纹，用于阻止纠错轮重复执行同一计划。 */
    private String queryPlanFingerprint(AgentQueryPlan plan) {
        if (plan == null) return "";
        String material = String.valueOf(plan.getDomain()) + "|" + plan.getAction() + "|" + plan.getMealScope() + "|"
            + (plan.getFilters() == null ? "" : plan.getFilters().getRecordDate()) + "|"
            + (plan.getFilters() == null ? "" : plan.getFilters().getMealType()) + "|" + plan.getToolNames();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder("sha256:");
            for (byte value : digest) encoded.append(String.format("%02x", value));
            return encoded.toString();
        } catch (Exception ignored) {
            return "sha256:unavailable";
        }
    }

    /** 将响应类型映射为受控查询目标，未登记类型不保存为可纠错上下文。 */
    private BusinessQueryTarget queryTarget(String responseType) {
        if ("BUSINESS_QUERY_SCHEDULED_MENU".equals(responseType)) return BusinessQueryTarget.SCHEDULED_MENU;
        if ("BUSINESS_QUERY_MEAL_PLAN".equals(responseType)) return BusinessQueryTarget.CUSTOMER_MEAL_PLAN;
        if ("BUSINESS_QUERY_DISH_CANDIDATES".equals(responseType)) return BusinessQueryTarget.DISH_CANDIDATES;
        if ("BUSINESS_QUERY_ORDER".equals(responseType)) return BusinessQueryTarget.ORDER;
        if ("BUSINESS_QUERY_VERIFICATION".equals(responseType)) return BusinessQueryTarget.VERIFICATION;
        if ("BUSINESS_QUERY_REFUND".equals(responseType)) return BusinessQueryTarget.REFUND;
        return BusinessQueryTarget.CUSTOMER;
    }

    private boolean isBusinessResponse(String responseType) {
        return responseType != null && responseType.startsWith("BUSINESS_QUERY");
    }

    /** 截断仅用于受控上下文的助手摘要，避免历史话术无限增长。 */
    private String limitText(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private AgentQueryPlan buildQueryPlan(String responseType, DiagnosisSlots slots) {
        return businessQueryChatService.plan(responseType, slots);
    }

    /**
     * 将受控查询成功返回的稳定对象标识回写到会话，供刷新、多实例和后续指代查询复用。
     *
     * @param session 当前会话
     * @param responseType 受控查询响应类型
     * @param result 已脱敏的查询结果
     */
    @SuppressWarnings("unchecked")
    private void captureBusinessFocus(MealPlanChatSession session, String responseType, Map<String, Object> result) {
        if (session == null || result == null || !Boolean.TRUE.equals(result.getOrDefault("present", true))) return;
        DiagnosisSlots slots = session.getSlots();
        if (result.get("customerId") instanceof Number) slots.setCustomerId(((Number) result.get("customerId")).longValue());
        if (result.get("customerCode") != null) slots.setCustomerCode(String.valueOf(result.get("customerCode")));
        if (!(result.get("items") instanceof List) || ((List<?>) result.get("items")).size() != 1) return;
        Object value = ((List<?>) result.get("items")).get(0);
        if (!(value instanceof Map)) return;
        Map<String, Object> item = (Map<String, Object>) value;
        if ("BUSINESS_QUERY_ORDER".equals(responseType)) {
            if (item.get("orderId") instanceof Number) slots.setOrderId(((Number) item.get("orderId")).longValue());
            if (item.get("orderCode") != null) slots.setOrderCode(String.valueOf(item.get("orderCode")));
        }
        if ("BUSINESS_QUERY_MEAL_PLAN".equals(responseType) && item.get("customerMealPlanId") instanceof Number) {
            slots.setMealPlanRecordId(((Number) item.get("customerMealPlanId")).longValue());
        }
    }

    /**
     * 构建“有餐未排”组合查询计划，计划内显式登记排餐查询和客户概览两个工具。
     *
     * @param slots 已解析会话槽位
     * @return 可交给编排器执行的组合 QueryPlan
     */
    private AgentQueryPlan mealBalanceNoPlanPlan(DiagnosisSlots slots) {
        AgentQueryPlan plan = buildQueryPlan("BUSINESS_QUERY_MEAL_PLAN", slots);
        plan.setToolNames(List.of("listMealPlans", "customerOverview"));
        return plan;
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
        int totalMealCount = (totalRemaining != null ? totalRemaining : 0)
            + (verifiedBreakfast != null ? verifiedBreakfast : 0)
            + (verifiedLunch != null ? verifiedLunch : 0)
            + (verifiedDinner != null ? verifiedDinner : 0);

        if (activeOrderCount == null || activeOrderCount == 0) {
            return String.format("%s 当前没有有效进行中订单，因此没有可继续核销的剩余餐数。历史订单和核销记录已列在下方供核对。", customerCode);
        }
        return String.format(
            "%s（%s）当前有效订单共 %d 笔，当前有效订单总餐数 %d 餐。剩余早餐 %d 餐，剩余午晚餐 %d 餐，合计剩余 %d 餐。已核销早餐 %d 餐，午餐 %d 餐，晚餐 %d 餐。数据按未删除核销日志实时汇总。",
            customerCode, customerName != null ? customerName : "",
            activeOrderCount, totalMealCount,
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
        response.setPendingBusinessQueryContext(session.getConversationState().getPendingBusinessQueryContext());
        response.setLastBusinessQueryContext(session.getConversationState().getLastBusinessQueryContext());
        response.setActiveTaskStack(session.getConversationState().getTaskStack());
        return response;
    }

    /** 主系统持久化上下文是跨实例真相源；每轮请求先恢复到本地会话状态。 */
    private void hydrateBusinessContexts(MealPlanChatSession session, AgentChatRequest request) {
        if (session == null || request == null) return;
        if (request.getPendingBusinessQueryContext() != null) {
            session.getConversationState().setPendingBusinessQueryContext(request.getPendingBusinessQueryContext());
        }
        if (request.getLastBusinessQueryContext() != null) {
            session.getConversationState().setLastBusinessQueryContext(request.getLastBusinessQueryContext());
        }
        if (request.getActiveTaskStack() != null) session.getConversationState().setTaskStack(request.getActiveTaskStack());
    }

    /**
     * 纯槽位回复优先复用待执行语义；过期或仍缺条件时更新 Pending，不调用模型重新选择领域。
     */
    private BusinessQuestionAnalysis resolvePendingAnalysis(MealPlanChatSession session, String message) {
        if (!pendingContextEnabled) {
            session.getConversationState().setPendingBusinessQueryContext(null);
            return null;
        }
        PendingBusinessQueryContext pending = session.getConversationState().getPendingBusinessQueryContext();
        if (pending == null || !isPureSlotReply(message)) return null;
        OffsetDateTime now = OffsetDateTime.now(businessTemporalResolver.getZoneId());
        if (pending.isExpired(now) || pending.getAnalysis() == null) {
            session.getConversationState().setPendingBusinessQueryContext(null);
            return null;
        }
        BusinessQuestionAnalysis analysis = pending.getAnalysis();
        analysis.setRequiresClarification(false);
        analysis.setClarificationQuestion(null);
        analysis.setAmbiguities(new ArrayList<>());
        analysis = resolveTemporalAnalysis(analysis, session, true);
        List<String> unresolved = unresolvedPendingFields(pending.getMissingFields(), analysis);
        if (!unresolved.isEmpty()) {
            analysis.setRequiresClarification(true);
            analysis.setClarificationQuestion("还需要补充：" + String.join("、", unresolved) + "。");
            pending.setMissingFields(unresolved);
            session.getConversationState().setPendingBusinessQueryContext(pending);
        }
        return analysis;
    }

    /** 校验 Pending 声明的缺失字段是否已由本轮确定性槽位补齐。 */
    private List<String> unresolvedPendingFields(List<String> fields, BusinessQuestionAnalysis analysis) {
        if (fields == null || fields.isEmpty()) return List.of();
        List<String> unresolved = new ArrayList<>();
        for (String field : fields) {
            boolean resolved;
            if ("recordDate".equals(field)) {
                AgentQueryFilters filters = analysis.getFilters();
                resolved = filters != null && (isNotBlank(filters.getRecordDate())
                    || isNotBlank(filters.getStartDate()) && isNotBlank(filters.getEndDate()));
            } else if ("mealType".equals(field)) {
                resolved = analysis.getFilters() != null && isNotBlank(analysis.getFilters().getMealType());
            } else if ("customer".equals(field) || "customerOrOrder".equals(field)) {
                resolved = analysis.getEntities() != null && (analysis.getEntities().getCustomerId() != null
                    || isNotBlank(analysis.getEntities().getCustomerCode()) || isNotBlank(analysis.getEntities().getCustomerName())
                    || "customerOrOrder".equals(field) && (analysis.getEntities().getOrderId() != null || isNotBlank(analysis.getEntities().getOrderCode())));
            } else {
                resolved = false;
            }
            if (!resolved) unresolved.add(field);
        }
        return unresolved;
    }

    /** 仅日期、餐次或编号可直接补 Pending；包含新业务谓词的输入必须重新分析。 */
    private boolean isPureSlotReply(String message) {
        if (message == null) return false;
        String text = message.trim();
        return text.matches("今天|今日|昨天|明天|本周|早餐|午餐|晚餐|\\d{4}-\\d{2}-\\d{2}")
            || text.matches("(?i)[A-Z]\\d{3,}") || text.matches("\\d{1,18}");
    }

    /** 合并确定性槽位后解析相对时间，确保 QueryPlan 只接收 yyyy-MM-dd 日期。 */
    private BusinessQuestionAnalysis resolveTemporalAnalysis(BusinessQuestionAnalysis analysis,
                                                             MealPlanChatSession session,
                                                             boolean pendingReused) {
        if (analysis == null) return null;
        mergeDeterministicSlots(analysis, session.getSlots());
        if (pendingReused) analysis.setSource("PENDING_CONTEXT");
        return businessTemporalResolver.resolve(analysis, session.getConversationState().getPendingBusinessQueryContext(),
            session.getConversationState().getLastBusinessQueryContext());
    }

    /** 本轮明确提取的实体、日期和餐次优先于模型推断。 */
    private void mergeDeterministicSlots(BusinessQuestionAnalysis analysis, DiagnosisSlots slots) {
        if (slots == null) return;
        if (analysis.getEntities() == null) analysis.setEntities(new me.zhengjie.agent.query.domain.AgentEntityReference());
        if (slots.getCustomerId() != null) analysis.getEntities().setCustomerId(slots.getCustomerId());
        if (isNotBlank(slots.getCustomerCode())) analysis.getEntities().setCustomerCode(slots.getCustomerCode());
        if (isNotBlank(slots.getCustomerName())) analysis.getEntities().setCustomerName(slots.getCustomerName());
        if (slots.getOrderId() != null) analysis.getEntities().setOrderId(slots.getOrderId());
        if (isNotBlank(slots.getOrderCode())) analysis.getEntities().setOrderCode(slots.getOrderCode());
        AgentQueryFilters filters = analysis.getFilters() == null ? new AgentQueryFilters() : analysis.getFilters();
        analysis.setFilters(filters);
        if (isNotBlank(slots.getRecordDate())) filters.setRecordDate(slots.getRecordDate());
        if (isNotBlank(slots.getStartDate()) && isNotBlank(slots.getEndDate())) {
            filters.setRecordDate(null); filters.setStartDate(slots.getStartDate()); filters.setEndDate(slots.getEndDate());
        }
        if (isNotBlank(slots.getMealType())) filters.setMealType(slots.getMealType());
    }

    /** 保存待补语义，过期时间按配置业务时区生成，摘要仅包含登记指标或领域。 */
    private void savePendingContext(MealPlanChatSession session, BusinessQuestionAnalysis analysis,
                                    List<String> missingFields, String ignoredOriginalQuestion) {
        if (session == null || analysis == null) return;
        if (!pendingContextEnabled) {
            session.getConversationState().setPendingBusinessQueryContext(null);
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(businessTemporalResolver.getZoneId());
        PendingBusinessQueryContext pending = new PendingBusinessQueryContext();
        pending.setAnalysis(analysis);
        pending.setMissingFields(missingFields == null ? List.of() : new ArrayList<>(missingFields));
        AgentMetricDefinition definition = analysis.getMetrics() == null || analysis.getMetrics().isEmpty()
            ? null : AgentMetricCatalog.definition(analysis.getMetrics().get(0));
        pending.setOriginalQuestionSummary(limitText(definition == null
            ? (analysis.getDomains() == null || analysis.getDomains().isEmpty() ? "受控业务查询" : analysis.getDomains().get(0).name())
            : definition.getSemanticDescription(), 120));
        pending.setSourceRequestId(MDC.get(REQUEST_ID_KEY));
        pending.setCreatedAt(now);
        pending.setExpiresAt(now.plusMinutes(pendingContextTtlMinutes));
        session.getConversationState().setPendingBusinessQueryContext(pending);
    }

    /** 根据受控分析结构生成待补字段，不使用用户原文猜测缺失项。 */
    private List<String> missingFields(BusinessQuestionAnalysis analysis) {
        List<String> missing = new ArrayList<>();
        if (analysis == null) return missing;
        if (analysis.getFilters() == null || !isNotBlank(analysis.getFilters().getRecordDate())
            && !(isNotBlank(analysis.getFilters().getStartDate()) && isNotBlank(analysis.getFilters().getEndDate()))) {
            missing.add("recordDate");
        }
        if (analysis.getAmbiguities() != null) analysis.getAmbiguities().forEach(item -> {
            if (item != null && isNotBlank(item.getField()) && !missing.contains(item.getField())) missing.add(item.getField());
        });
        return missing;
    }

    /** 将解析后的日期同步到响应槽位，供主系统会话字段和下一轮确定性提取复用。 */
    private void applyResolvedFiltersToSlots(MealPlanChatSession session, AgentQueryFilters filters) {
        if (session == null || filters == null) return;
        session.getSlots().setRecordDate(filters.getRecordDate());
        session.getSlots().setStartDate(filters.getStartDate());
        session.getSlots().setEndDate(filters.getEndDate());
        if (isNotBlank(filters.getMealType())) session.getSlots().setMealType(filters.getMealType());
    }

    /** 创建不含原始问题、Prompt 和结果数据的语义追踪摘要。 */
    private SemanticTraceSummary semanticTrace(BusinessQuestionAnalysis analysis, boolean pendingReused) {
        SemanticTraceSummary trace = new SemanticTraceSummary();
        trace.setSemanticSource(analysis == null ? null : analysis.getSource());
        trace.setFallbackReason(analysis == null ? null : analysis.getFallbackReason());
        trace.setSemanticConfidence(analysis == null ? null : analysis.getConfidence());
        trace.setSemanticCatalogVersion(analysis == null || analysis.getSemanticCatalogVersion() == null
            ? AgentMetricCatalog.VERSION : analysis.getSemanticCatalogVersion());
        if (analysis != null && analysis.getTemporal() != null && analysis.getTemporal().getExpression() != null) {
            trace.setTemporalExpression(analysis.getTemporal().getExpression().name());
        }
        if (analysis != null && analysis.getFilters() != null) {
            trace.setResolvedRecordDate(analysis.getFilters().getRecordDate());
            trace.setResolvedStartDate(analysis.getFilters().getStartDate());
            trace.setResolvedEndDate(analysis.getFilters().getEndDate());
        }
        trace.setPendingContextReused(pendingReused);
        trace.setInteractionMode(analysis == null || analysis.getInteractionMode() == null ? null : analysis.getInteractionMode().name());
        double confidence = analysis == null ? 0D : analysis.getConfidence();
        trace.setConfidenceBucket(confidence >= .90D ? "HIGH" : confidence >= .80D ? "MEDIUM" : "LOW");
        return trace;
    }

    private static BusinessTemporalResolver defaultTemporalResolver() {
        BusinessTimeProperties properties = new BusinessTimeProperties();
        return new BusinessTemporalResolver(Clock.system(ZoneId.of("Asia/Shanghai")), properties);
    }

    /**
     * 识别当前还缺失的关键诊断槽位。
     * 公共排期菜单只要求日期；其他客户相关查询至少需要客户标识。
     *
     * @param slots 当前会话已解析的业务槽位
     * @param intent 当前查询意图
     * @return 仍需由客服补充的槽位列表
     */
    private List<MissingSlot> missingSlots(DiagnosisSlots slots, ChatIntent intent) {
        // 公共排期菜单不关联具体客户，只需要查询日期；不能复用客户排餐诊断的必填槽位规则。
        if (intent == ChatIntent.SCHEDULED_MENU_QUERY) {
            return isNotBlank(slots.getRecordDate()) ? List.of() : List.of(MissingSlot.RECORD_DATE);
        }
        List<MissingSlot> missing = new ArrayList<>();
        if (slots.getCustomerId() == null && isNotBlank(slots.getCustomerCode()) == false
            && isNotBlank(slots.getCustomerName()) == false && slots.getMealPlanRecordId() == null) {
            missing.add(MissingSlot.CUSTOMER);
        }
        // 排餐诊断和候选菜预览均必须限定日期与餐次，禁止扫描无界历史。
        if (intent == ChatIntent.DIAGNOSE || intent == ChatIntent.FOLLOW_UP || intent == ChatIntent.RETRY || intent == ChatIntent.DISH_CANDIDATE_QUERY) {
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
            return "请提供要查询的客户姓名或编号，例如“客户 张三”或“客户编号 B3303”。";
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
            return List.of("客户 张三", "客户编号 B3303", "清空会话");
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
        target.setCustomerName(source.getCustomerName());
        target.setRecordDate(source.getRecordDate());
        target.setStartDate(source.getStartDate());
        target.setEndDate(source.getEndDate());
        target.setMealType(source.getMealType());
        target.setOrderStatus(source.getOrderStatus());
        target.setOrderId(source.getOrderId());
        target.setOrderCode(source.getOrderCode());
        target.setMealPlanRecordId(source.getMealPlanRecordId());
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
        if (isNotBlank(source.getCustomerName())) {
            target.setCustomerName(source.getCustomerName());
        }
        if (isNotBlank(source.getRecordDate())) {
            target.setRecordDate(source.getRecordDate());
        }
        if (isNotBlank(source.getStartDate())) target.setStartDate(source.getStartDate());
        if (isNotBlank(source.getEndDate())) target.setEndDate(source.getEndDate());
        if (isNotBlank(source.getMealType())) {
            target.setMealType(source.getMealType());
        }
        if (source.getOrderStatus() != null) {
            target.setOrderStatus(source.getOrderStatus());
        }
        if (source.getOrderId() != null) target.setOrderId(source.getOrderId());
        if (isNotBlank(source.getOrderCode())) target.setOrderCode(source.getOrderCode());
        if (source.getMealPlanRecordId() != null) target.setMealPlanRecordId(source.getMealPlanRecordId());
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
        log.info("聊天诊断阶段 requestId={} sessionId={} intentSource={} ruleIntent={} intent={} intentConfidence={} intentReason={} llmTriggered={} conversationStage={} slots.customerId={} slots.customerCode={} slots.recordDate={} slots.mealType={} missingSlots={} diagnosisTriggered={} costMs={}",
            MDC.get(REQUEST_ID_KEY), session.getSessionId(), extraction.getIntentSource(), extraction.getRuleIntent(), extraction.getIntent(), extraction.getIntentConfidence(), extraction.getIntentReason(), extraction.isLlmTriggered(), session.getConversationState().getStage(),
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
