package me.zhengjie.agent.chat;

import me.zhengjie.agent.analysis.LegacyBusinessQuestionAnalysisFactory;
import me.zhengjie.agent.analysis.ContextReferenceResolver;
import me.zhengjie.agent.analysis.ConversationUnderstandingService;
import me.zhengjie.agent.analysis.ConversationUnderstandingValidator;
import me.zhengjie.agent.query.MultiIntentPlanningService;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.query.client.BusinessQueryDataClient;
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
import me.zhengjie.agent.query.domain.PendingBusinessQueryContext;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentMetricDefinition;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.BusinessResponseTypeCatalog;
import me.zhengjie.agent.query.presentation.BusinessPresentationResult;
import me.zhengjie.agent.analysis.domain.BusinessInteractionMode;
import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.analysis.domain.MealScope;
import me.zhengjie.agent.query.tool.AgentBusinessToolExecutor.ToolExecutionResult;
import me.zhengjie.agent.tool.ToolCatalog;
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
import me.zhengjie.agent.application.conversation.BusinessConversationResultPipeline;
import me.zhengjie.agent.application.conversation.BusinessConversationUnderstandingPipeline;
import me.zhengjie.agent.application.conversation.ChatCommand;
import me.zhengjie.agent.application.conversation.ChatResult;
import me.zhengjie.agent.application.conversation.ConversationExecutionContext;
import me.zhengjie.agent.application.conversation.ConversationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 默认聊天能力处理器。
 *
 * <p>该类承载迁移期间保持兼容的完整业务行为，但不再作为控制器入口。
 * 控制器统一经 {@link MealPlanChatServiceImpl} 和应用层 Coordinator 路由到本处理器。</p>
 */
@Component
public class DefaultConversationHandler implements ConversationHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultConversationHandler.class);
    private static final String REQUEST_ID_KEY = "requestId";
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
    private final BusinessQueryDataClient businessQueryDataClient;
    private final BusinessQueryChatService businessQueryChatService;
    private final BusinessQueryPlanningService businessQueryPlanningService;
    private final ContextReferenceResolver contextReferenceResolver;
    private final BusinessConversationUnderstandingPipeline understandingPipeline;
    private final BusinessConversationResultPipeline resultPipeline;
    private final ConversationStateSupport conversationStateSupport;
    private final BusinessQueryIntentPolicy businessQueryIntentPolicy;
    private ConversationUnderstandingService conversationUnderstandingService;
    private ConversationUnderstandingValidator conversationUnderstandingValidator;
    private MultiIntentPlanningService multiIntentPlanningService;
    private String conversationUnderstandingMode = "shadow";

    /**
     * 创建生产聊天处理器，开关和 TTL 统一从强类型配置读取。
     */
    @Autowired
    public DefaultConversationHandler(MealPlanChatSessionStore sessionStore,
                                      MealPlanChatExtractor extractor,
                                      MealPlanDiagnosisService diagnosisService,
                                      MealPlanFollowUpService followUpService,
                                      BusinessQueryDataClient businessQueryDataClient,
                                      BusinessQueryChatService businessQueryChatService,
                                      BusinessQueryPlanningService businessQueryPlanningService,
                                      ContextReferenceResolver contextReferenceResolver,
                                      BusinessConversationUnderstandingPipeline understandingPipeline,
                                      BusinessConversationResultPipeline resultPipeline,
                                      AgentProperties properties,
                                      ConversationStateSupport conversationStateSupport,
                                      BusinessQueryIntentPolicy businessQueryIntentPolicy) {
        this.sessionStore = sessionStore;
        this.extractor = extractor;
        this.diagnosisService = diagnosisService;
        this.followUpService = followUpService;
        this.businessQueryDataClient = businessQueryDataClient;
        this.businessQueryChatService = businessQueryChatService;
        this.businessQueryPlanningService = businessQueryPlanningService;
        this.contextReferenceResolver = contextReferenceResolver;
        this.understandingPipeline = understandingPipeline;
        this.resultPipeline = resultPipeline;
        this.conversationStateSupport = conversationStateSupport;
        this.businessQueryIntentPolicy = businessQueryIntentPolicy;
        this.conversationUnderstandingMode =
            properties.getChat().getConversationUnderstanding().getMode().name();
    }

    /** 配置可灰度启用的多帧会话理解服务，默认 shadow 模式不改变既有执行路径。 */
    @Autowired
    public void configureConversationUnderstanding(ConversationUnderstandingService service,
                                                   ConversationUnderstandingValidator validator,
                                                   MultiIntentPlanningService planner,
                                                   AgentProperties properties) {
        configureConversationUnderstanding(service, validator, planner,
            properties.getChat().getConversationUnderstanding().getMode().name());
    }

    /** 测试用显式灰度模式配置，生产环境统一通过 {@link AgentProperties} 调用。 */
    void configureConversationUnderstanding(ConversationUnderstandingService service,
                                            ConversationUnderstandingValidator validator,
                                            MultiIntentPlanningService planner,
                                            String mode) {
        this.conversationUnderstandingService = service; this.conversationUnderstandingValidator = validator;
        this.multiIntentPlanningService = planner;
        this.conversationUnderstandingMode = mode == null ? "shadow" : mode;
    }

    /** 返回稳定的处理器标识，供路由冲突诊断和领域审计使用。 */
    @Override
    public String handlerId() {
        return "default-conversation";
    }

    /**
     * 默认处理器接管尚未拆成独立能力处理器的兼容聊天命令。
     *
     * @param command 已完成外部契约映射的聊天命令
     * @param context 主系统签发的可信执行上下文
     * @return 当前兼容期始终由本处理器接管
     */
    @Override
    public boolean supports(ChatCommand command, ConversationExecutionContext context) {
        return command != null && command.request() != null;
    }

    /**
     * 执行兼容聊天能力并包装为应用层结果。
     *
     * @param command 聊天命令
     * @param context 可信执行上下文
     * @return 结构化聊天结果
     */
    @Override
    public ChatResult handle(ChatCommand command, ConversationExecutionContext context) {
        return new ChatResult(chat(command.request()));
    }

    /**
     * 执行既有聊天行为。该方法仅供 Handler 入口和行为刻画测试调用。
     *
     * @param request 聊天请求
     * @return 兼容历史字段的结构化响应
     */
    public AgentChatResponse chat(AgentChatRequest request) {
        MealPlanChatSession session = sessionStore.getOrCreate(request.getSessionId());
        conversationStateSupport.hydrateBusinessContexts(session, request);
        // 主系统持久化上下文仅在当前 agent 实例缺少槽位时补充；本实例中的更新槽位优先保留。
        session.setSlots(conversationStateSupport.mergeSlots(
            conversationStateSupport.copy(request.getContextSlots()), session.getSlots()));
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
            conversationStateSupport.rememberAssistantTurn(reset, response, extraction, null);
            sessionStore.save(reset);
            logChat(reset, extraction, false, start);
            return response;
        }

        session.setSlots(conversationStateSupport.mergeSlots(
            conversationStateSupport.copy(session.getSlots()), extraction.getSlots()));
        conversationStateSupport.rememberUserTurn(session, request.getMessage(), extraction);

        ChatIntent intent = extraction.getIntent();
        BusinessQueryOrchestrator businessQueryOrchestrator = createBusinessQueryOrchestrator();

        if (intent == ChatIntent.BUSINESS_QUERY) {
            AgentChatResponse semanticResponse = handleSemanticBusinessQuery(session, request.getMessage(), businessQueryOrchestrator);
            if (semanticResponse != null) {
                DiagnosisResponse semanticDiagnosis = semanticResponse.getDiagnosisResult();
                conversationStateSupport.rememberAssistantTurn(session, semanticResponse, extraction, semanticDiagnosis);
                sessionStore.save(session);
                logChat(session, extraction, semanticDiagnosis != null, start);
                return semanticResponse;
            }
            ChatIntent compatibilityIntent = businessQueryIntentPolicy.compatibilityIntent(extraction);
            if (compatibilityIntent == null) {
                AgentChatResponse response = response(session, ChatStatus.NEED_MORE_INFO,
                    "请说明想查询客户、订单、排餐、核销、退餐、套餐、菜品或运营统计中的哪类数据。", null,
                    List.of(), List.of("客户订单", "今天待核销客户", "今天菜单"), "BUSINESS_QUERY_CLARIFICATION");
                conversationStateSupport.rememberAssistantTurn(session, response, extraction, null);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }
            intent = compatibilityIntent;
        }

        if (intent == ChatIntent.OUT_OF_SCOPE) {
            session.getConversationState().setStage(resolveStage(session.getSlots(), false));
            String message = businessQueryIntentPolicy.isAmountQuery(request.getMessage())
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
            conversationStateSupport.rememberAssistantTurn(session, response, extraction, null);
            sessionStore.save(session);
            logChat(session, extraction, false, start);
            return response;
        }

        if (intent == ChatIntent.RETRY) {
            session.getConversationState().clearLastDiagnosisResult();
        }

        if (intent == ChatIntent.BUSINESS_RULE_QUERY) {
            if (businessQueryDataClient == null) {
                return response(session, ChatStatus.ERROR, "业务规则查询服务暂不可用，请稍后重试。", null, List.of(), List.of(), BusinessResponseTypeCatalog.RULE);
            }
            if (extraction.getRuleIntent() == null || extraction.getRuleIntent().trim().isEmpty()) {
                AgentChatResponse response = response(session, ChatStatus.ANSWERED,
                    "当前问题尚未登记为可解释的业务规则，请按业务文档或人工确认。", null,
                    List.of(), List.of("清空会话"), BusinessResponseTypeCatalog.RULE);
                sessionStore.save(session);
                return response;
            }
            ToolExecutionResult execution = executeBusinessTool(businessQueryOrchestrator, BusinessResponseTypeCatalog.RULE,
                session.getSlots(), ToolCatalog.EXPLAIN_RULE, extraction.getRuleIntent(), List.of());
            Map<String, Object> result = execution.result();
            AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.RULE, result,
                composer().businessRule(presentation(result)),
                List.of("剩余餐数怎么算", "订单什么时候有效", "清空会话"));
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
            ToolExecutionResult execution = executeBusinessTool(businessQueryOrchestrator, BusinessResponseTypeCatalog.SCHEDULED_MENU,
                session.getSlots(), ToolCatalog.LIST_SCHEDULED_DISHES, null, List.of());
            AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.SCHEDULED_MENU, execution.result(),
                composer().scheduledMenu(presentation(execution.result())),
                List.of("今天菜单", "明天菜单", "清空会话"));
            applyToolExecution(response, execution);
            resultPipeline.captureLastBusinessQueryContext(session, response);
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
                conversationStateSupport.rememberAssistantTurn(session, response, extraction, null);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }
            if (businessQueryDataClient == null) {
                return response(session, ChatStatus.ERROR, "排餐查询服务暂不可用，请稍后重试。", null, List.of(), List.of(), BusinessResponseTypeCatalog.MEAL_PLAN);
            }
            Long resolvedCustomerId = session.getSlots().getCustomerId();
            boolean directMealPlanRecord = session.getSlots().getMealPlanRecordId() != null;
            if (resolvedCustomerId == null && !directMealPlanRecord) {
                ToolExecutionResult overviewExecution = executeBusinessTool(businessQueryOrchestrator,
                    BusinessResponseTypeCatalog.CUSTOMER, session.getSlots(), ToolCatalog.CUSTOMER_OVERVIEW,
                    null, List.of());
                Map<String, Object> overview = overviewExecution.result();
                Object overviewCustomerId = overview.get("customerId");
                if (overviewCustomerId instanceof Number) resolvedCustomerId = ((Number) overviewCustomerId).longValue();
                if (resolvedCustomerId == null) {
                    AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.CUSTOMER, overview,
                        composer().customerOverview(presentation(overview)),
                        CUSTOMER_INSIGHT_QUICK_REPLIES);
                    applyToolExecution(response, overviewExecution);
                    return response;
                }
            }
            DiagnosisSlots resolvedSlots = conversationStateSupport.copy(session.getSlots());
            if (resolvedCustomerId != null) resolvedSlots.setCustomerId(resolvedCustomerId);
            if (intent == ChatIntent.DISH_CANDIDATE_QUERY) {
                ToolExecutionResult candidateExecution = executeBusinessTool(businessQueryOrchestrator,
                    BusinessResponseTypeCatalog.DISH_CANDIDATES, resolvedSlots,
                    ToolCatalog.PREVIEW_DISH_CANDIDATES, null, List.of());
                AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.DISH_CANDIDATES, candidateExecution.result(),
                    composer().dishCandidates(presentation(candidateExecution.result())),
                    CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, candidateExecution);
                session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }
            ToolExecutionResult mealPlanExecution = executeBusinessTool(businessQueryOrchestrator,
                BusinessResponseTypeCatalog.MEAL_PLAN, resolvedSlots, ToolCatalog.LIST_MEAL_PLANS,
                null, List.of());
            Map<String, Object> result = mealPlanExecution.result();
            if (intent == ChatIntent.MEAL_BALANCE_NO_PLAN_QUERY) {
                AgentQueryPlan comboPlan = mealBalanceNoPlanPlan(resolvedSlots);
                ToolExecutionResult overviewExecution = businessQueryOrchestrator.execute(comboPlan,
                    ToolCatalog.CUSTOMER_OVERVIEW, null, List.of());
                Map<String, Object> overview = overviewExecution.result();
                AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.MEAL_PLAN, result,
                    composer().mealBalanceWithoutPlan(presentation(overview),
                        presentation(result)), CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, mealPlanExecution, overviewExecution);
                return response;
            }
            if (intent == ChatIntent.MEAL_PLAN_UNVERIFIED_QUERY) {
                Map<String, Object> unverified = filterUnverifiedMealPlans(result);
                AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.MEAL_PLAN, unverified,
                    composer().unverifiedMealPlans(presentation(unverified)),
                    CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, mealPlanExecution);
                session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }
            if (intent == ChatIntent.DISH_INGREDIENT_QUERY) {
                List<Integer> dishIds = extractDishIds(result);
                if (dishIds.isEmpty()) {
                    return insightResponse(session, BusinessResponseTypeCatalog.DISH, Map.of(), "该客户指定餐次没有可查询配料的排餐菜品。", CUSTOMER_INSIGHT_QUICK_REPLIES);
                }
                ToolExecutionResult dishExecution = executeBusinessTool(businessQueryOrchestrator,
                    BusinessResponseTypeCatalog.DISH, resolvedSlots, ToolCatalog.LIST_DISHES,
                    null, dishIds);
                Map<String, Object> dishes = dishExecution.result();
                AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.DISH, dishes,
                    composer().dishIngredients(presentation(dishes)),
                    CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, mealPlanExecution, dishExecution);
                session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }
            boolean historicalMealPlan = !isNotBlank(resolvedSlots.getRecordDate())
                && !isNotBlank(resolvedSlots.getStartDate()) && !isNotBlank(resolvedSlots.getEndDate())
                && resolvedSlots.getMealPlanRecordId() == null;
            AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.MEAL_PLAN, result,
                composer().mealPlan(presentation(result), historicalMealPlan),
                CUSTOMER_INSIGHT_QUICK_REPLIES);
            applyToolExecution(response, mealPlanExecution);
            session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
            sessionStore.save(session);
            logChat(session, extraction, true, start);
            return response;
        }

        // ==================== 客户信息查询 ====================
        if (businessQueryIntentPolicy.isCustomerInsightIntent(intent)) {
            // 客户信息查询只要求客户编号
            List<MissingSlot> missing = businessQueryIntentPolicy.missingSlotsForInsight(session.getSlots());
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
                conversationStateSupport.rememberAssistantTurn(session, response, extraction, null);
                sessionStore.save(session);
                logChat(session, extraction, false, start);
                return response;
            }

            AgentChatResponse candidateResponse = resolveCustomerCandidatesIfNeeded(session, businessQueryOrchestrator);
            if (candidateResponse != null) {
                conversationStateSupport.rememberAssistantTurn(session, candidateResponse, extraction, null);
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
            conversationStateSupport.rememberAssistantTurn(session, response, extraction, null);
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
            conversationStateSupport.rememberAssistantTurn(session, response, extraction, null);
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
            conversationStateSupport.rememberAssistantTurn(session, response, extraction, session.getLastDiagnosisResult());
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
        conversationStateSupport.rememberAssistantTurn(session, response, extraction, diagnosisResult);
        sessionStore.save(session);
        logChat(session, extraction, true, start);
        return response;
    }

    // ========== 客户信息查询处理 ==========

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
        ToolExecutionResult execution = executeBusinessTool(orchestrator, BusinessResponseTypeCatalog.CUSTOMER_CANDIDATES,
            slots, ToolCatalog.RESOLVE_CUSTOMER, null, List.of());
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
        AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.CUSTOMER_CANDIDATES, result,
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
        BusinessQuestionAnalysis analysis = understandingPipeline.understandOperation(session, message);
        return executeOperationAnalysis(session, message, orchestrator, analysis, false);
    }

    /** 执行已经完成时间落地的运营统计语义，供新问题和 Pending Context 续接共用。 */
    private AgentChatResponse executeOperationAnalysis(MealPlanChatSession session, String message,
                                                       BusinessQueryOrchestrator orchestrator,
                                                       BusinessQuestionAnalysis analysis,
        boolean pendingReused) {
        if (analysis == null || analysis.isRequiresClarification()) {
            if (analysis != null) {
                understandingPipeline.savePendingContext(session, analysis,
                    understandingPipeline.missingFields(analysis));
            }
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
            understandingPipeline.savePendingContext(session, analysis, List.of("recordDate"));
            return response(session, ChatStatus.NEED_MORE_INFO, "请补充统计日期，例如今天、明天或 2026-07-13。", null,
                List.of(MissingSlot.RECORD_DATE), List.of("今天", "明天"), "BUSINESS_QUERY_OPERATION_CLARIFICATION");
        }
        AgentQueryMetric primaryMetric = queryPlan.getMetrics().get(0);
        AgentMetricDefinition metricDefinition = AgentMetricCatalog.definition(primaryMetric);
        if (metricDefinition == null) return response(session, ChatStatus.ERROR, "运营指标未登记，已停止执行。", null,
            List.of(), List.of(), "BUSINESS_QUERY_OPERATION");
        String responseType = report ? BusinessResponseTypeCatalog.OPERATION_REPORT
            : metricDefinition.getResponseType();
        String tool = queryPlan.getToolNames().get(0);
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator, queryPlan, tool, null, List.of());
        Map<String, Object> result = report ? operationReportResult(execution.result(), queryPlan) : execution.result();
        String answer = report
            ? composer().operationReport(presentation(result), queryPlan.getMetrics())
            : composer().operationStatistics(presentation(result), primaryMetric);
        understandingPipeline.clearPendingContext(session, true);
        understandingPipeline.applyResolvedFiltersToSlots(session, queryPlan.getFilters());
        AgentChatResponse response = insightResponse(session, responseType, result, answer,
            List.of("今天待核销客户", "今天已排餐客户", "活跃客户"));
        response.setQueryPlan(queryPlan);
        applyToolExecution(response, execution);
        response.setSemanticTraceSummary(understandingPipeline.semanticTrace(analysis, pendingReused));
        resultPipeline.captureLastBusinessQueryContext(session, response);
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
        BusinessConversationUnderstandingPipeline.UnderstandingOutcome understanding =
            understandingPipeline.understand(session, message);
        BusinessQuestionAnalysis analysis = understanding.analysis();
        boolean pendingReused = understanding.pendingReused();
        if (analysis == null) return null;
        AgentChatResponse contextFollowUp = handleActiveCustomerBalanceFollowUp(session, analysis, orchestrator);
        if (contextFollowUp != null) return contextFollowUp;
        if (analysis.isRequiresClarification()) {
            // 未识别出任何领域时保留既有细粒度意图兼容入口；已识别领域的歧义统一由语义层追问。
            if (analysis.getDomains() == null || analysis.getDomains().isEmpty()) return null;
            understandingPipeline.savePendingContext(session, analysis,
                understandingPipeline.missingFields(analysis));
            AgentChatResponse clarification = response(session, ChatStatus.NEED_MORE_INFO,
                isNotBlank(analysis.getClarificationQuestion()) ? analysis.getClarificationQuestion() : "请补充需要查询的业务对象或条件。",
                null, List.of(), List.of("今天菜单", "客户订单"), "BUSINESS_QUERY_CLARIFICATION");
            clarification.setSemanticTraceSummary(
                understandingPipeline.semanticTrace(analysis, pendingReused));
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
            return handleCustomerMealPlanQuery(session, message, analysis, orchestrator, pendingReused);
        }
        if (analysis.getQueryTarget() == BusinessQueryTarget.CUSTOMER
            || analysis.getDomains() != null && analysis.getDomains().contains(AgentQueryDomain.CUSTOMER)) {
            return handleCustomerOverviewQuery(session, analysis, orchestrator, pendingReused);
        }
        if (analysis.getQueryTarget() != BusinessQueryTarget.SCHEDULED_MENU) return null;
        if (!isNotBlank(analysis.getFilters().getRecordDate())) {
            return response(session, ChatStatus.NEED_MORE_INFO, "请补充菜单日期，例如今天、明天或 2026-07-13。", null,
                List.of(MissingSlot.RECORD_DATE), List.of("今天", "明天"), BusinessResponseTypeCatalog.SCHEDULED_MENU);
        }
        if (businessQueryDataClient == null) {
            return response(session, ChatStatus.ERROR, "公共菜单查询服务暂不可用，请稍后重试。", null,
                List.of(), List.of(), BusinessResponseTypeCatalog.SCHEDULED_MENU);
        }
        AgentQueryPlan queryPlan = businessQueryPlanningService.plan(analysis);
        if (queryPlan == null) {
            understandingPipeline.savePendingContext(session, analysis,
                List.of("recordDate", "mealType"));
            return response(session, ChatStatus.NEED_MORE_INFO, "公共菜单仅支持查询午餐或晚餐，请确认需要的餐次。", null,
                List.of(), List.of("今天午餐菜单", "今天晚餐菜单"), BusinessResponseTypeCatalog.SCHEDULED_MENU);
        }
        LastBusinessQueryContext previous = session.getConversationState().getLastBusinessQueryContext();
        String fingerprint = resultPipeline.queryPlanFingerprint(queryPlan);
        if (analysis.getInteractionMode() == BusinessInteractionMode.CORRECTION && previous != null
            && fingerprint.equals(previous.getQueryPlanFingerprint())) {
            return response(session, ChatStatus.NEED_MORE_INFO,
                "上一次已按相同的日期和餐次口径查询。请说明你想查看公共菜单、某位客户实际排餐，还是客户候选菜。",
                null, List.of(), List.of("今天公共菜单", "B3303 今天吃什么", "B3303 今天有哪些候选菜"),
                "BUSINESS_QUERY_CORRECTION_CLARIFICATION");
        }
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator,
            queryPlan, ToolCatalog.LIST_SCHEDULED_DISHES, null, List.of());
        Map<String, Object> result = execution.result();
        String answer = composer().scheduledMenu(presentation(result));
        if (analysis.getInteractionMode() == BusinessInteractionMode.CORRECTION) {
            answer = "已重新规划查询口径：按指定日期的公共排期菜单分别查询午餐和晚餐。" + answer;
        }
        AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.SCHEDULED_MENU, result, answer,
            List.of("今天午餐菜单", "今天晚餐菜单", "清空会话"));
        response.setQueryPlan(queryPlan);
        response.setSemanticTraceSummary(
            understandingPipeline.semanticTrace(analysis, pendingReused));
        understandingPipeline.clearPendingContext(session, true);
        applyToolExecution(response, execution);
        applyResultValidation(response, queryPlan, result);
        resultPipeline.captureLastBusinessQueryContext(session, response);
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
        return response;
    }

    /** 执行已登记的多帧能力；shadow 只完成理解而不调用任何新增业务工具。 */
    private AgentChatResponse handleMultiFrameUnderstanding(MealPlanChatSession session, String message, BusinessQueryOrchestrator orchestrator) {
        if (conversationUnderstandingService == null || multiIntentPlanningService == null || !"new".equalsIgnoreCase(conversationUnderstandingMode)) return null;
        LastBusinessQueryContext last = session.getConversationState().getLastBusinessQueryContext();
        me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult understanding = conversationUnderstandingService.understand(message, session.getSlots(), last == null ? List.of() : last.getContextHandles());
        if (understanding == null || understanding.isRequiresClarification() || understanding.getFrames().isEmpty()) return null;
        for (me.zhengjie.agent.analysis.domain.SemanticRequestFrame frame : understanding.getFrames()) {
            ContextReferenceResolver.Resolution resolution = contextReferenceResolver.resolve(
                frame, last == null ? List.of() : last.getContextHandles(),
                OffsetDateTime.now(ZoneOffset.ofHours(8)));
            if (resolution.status() == ContextReferenceResolver.Status.MISSING || resolution.status() == ContextReferenceResolver.Status.AMBIGUOUS) return response(session, ChatStatus.NEED_MORE_INFO,
                resolution.status() == ContextReferenceResolver.Status.MISSING ? "你指的是哪些客户？" : "当前存在多个可引用对象，请说明要查看哪一批。", null, List.of(), List.of(), resolution.status() == ContextReferenceResolver.Status.MISSING ? "CONTEXT_REFERENCE_MISSING" : "CONTEXT_REFERENCE_AMBIGUOUS");
        }
        if (conversationUnderstandingValidator.validate(understanding) != null) return null;
        List<AgentQueryPlan> plans = multiIntentPlanningService.plan(understanding);
        if (plans.isEmpty()) return response(session, ChatStatus.NEED_MORE_INFO,
            "当前客服能力暂不支持该查询组合，请调整查询对象或条件。", null,
            List.of(), List.of(), "CAPABILITY_NOT_AVAILABLE");
        if (plans.size() > 3 || businessQueryDataClient == null) return null;
        session.getConversationState().getTaskStack().suspendActive();
        me.zhengjie.agent.query.domain.ConversationTask task = new me.zhengjie.agent.query.domain.ConversationTask();
        task.setTaskId("task-" + java.util.UUID.randomUUID()); task.setStatus(me.zhengjie.agent.query.domain.ConversationTaskStatus.ACTIVE);
        task.setUnderstanding(understanding); task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
        session.getConversationState().getTaskStack().add(task);
        List<me.zhengjie.agent.query.domain.BusinessQueryResultBlock> blocks = new ArrayList<>();
        ToolExecutionResult firstExecution = null;
        for (int index = 0; index < plans.size(); index++) {
            AgentQueryPlan plan = plans.get(index);
            applySemanticFrameSlots(plan, session.getSlots());
            ToolExecutionResult execution = businessQueryChatService.execute(orchestrator, plan, plan.getToolNames().get(0), null, List.of());
            if (firstExecution == null) firstExecution = execution;
            me.zhengjie.agent.query.domain.BusinessQueryResultBlock block = new me.zhengjie.agent.query.domain.BusinessQueryResultBlock();
            block.setFrameId(understanding.getFrames().get(index).getFrameId()); block.setResponseType(multiFrameResponseType(plan));
            block.setStatus(execution.partial() ? "FAILED" : "COMPLETED"); block.setResult(execution.result()); block.setWarnings(execution.warnings()); blocks.add(block);
        }
        boolean partial = blocks.stream().anyMatch(block -> "FAILED".equals(block.getStatus()));
        task.setStatus(partial ? me.zhengjie.agent.query.domain.ConversationTaskStatus.FAILED : me.zhengjie.agent.query.domain.ConversationTaskStatus.COMPLETED);
        task.setFailureCode(partial ? "FRAME_EXECUTION_PARTIAL" : null);
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
        boolean resumesPreviousTask = understanding.getFrames().stream()
            .anyMatch(frame -> frame.getDependsOnFrameIds() != null && !frame.getDependsOnFrameIds().isEmpty());
        if (resumesPreviousTask) {
            java.util.Optional<PendingBusinessQueryContext> restoredPending = session.getConversationState().getTaskStack().restoreLatestSuspendedPending();
            if (restoredPending.isPresent()) session.getConversationState().setPendingBusinessQueryContext(restoredPending.get());
            else session.getConversationState().getTaskStack().restoreLatestSuspended();
        }
        AgentQueryPlan plan = plans.get(0); Map<String, Object> result = blocks.get(0).getResult();
        String answer = plan.getMetrics().contains(AgentQueryMetric.ACTIVE_CUSTOMER_MEAL_BALANCE_DETAIL)
            ? composer().activeCustomerBalances(presentation(result))
            : "已完成本轮受控业务查询。";
        AgentChatResponse response = insightResponse(session, multiFrameResponseType(plan), result,
            answer, List.of("活跃客户数", "清空会话"));
        response.setQueryPlan(plan); applyToolExecution(response, firstExecution); response.setResultBlocks(blocks);
        response.setActiveTaskStack(session.getConversationState().getTaskStack());
        response.setConversationFocus(conversationFocus(session.getSlots()));
        return response;
    }

    /** 将规则提取的客户/订单槽位安全复制至固定 QueryPlan，模型不能提供实体 ID。 */
    private void applySemanticFrameSlots(AgentQueryPlan plan, DiagnosisSlots slots) {
        if (plan == null || slots == null) return;
        plan.getEntities().setCustomerId(slots.getCustomerId()); plan.getEntities().setCustomerCode(slots.getCustomerCode());
        plan.getEntities().setOrderId(slots.getOrderId()); plan.getEntities().setOrderCode(slots.getOrderCode());
        if (plan.getFilters().getRecordDate() == null) plan.getFilters().setRecordDate(slots.getRecordDate());
        if (plan.getFilters().getMealType() == null) plan.getFilters().setMealType(slots.getMealType());
    }

    /** 将固定计划映射为受控展示类型，不读取用户文本。 */
    private String multiFrameResponseType(AgentQueryPlan plan) {
        if (plan == null || plan.getDomain() == null) return "BUSINESS_QUERY";
        if (plan.getDomain() == AgentQueryDomain.ORDER) return BusinessResponseTypeCatalog.ORDER;
        if (plan.getDomain() == AgentQueryDomain.VERIFICATION) return BusinessResponseTypeCatalog.VERIFICATION;
        if (plan.getDomain() == AgentQueryDomain.REFUND) return BusinessResponseTypeCatalog.REFUND;
        if (plan.getDomain() == AgentQueryDomain.MEAL_PLAN) return BusinessResponseTypeCatalog.MEAL_PLAN;
        return BusinessResponseTypeCatalog.ACTIVE_CUSTOMER_BALANCES;
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
        ContextReferenceResolver.Resolution resolution = contextReferenceResolver.resolve(frame,
            context == null ? List.of() : context.getContextHandles(), OffsetDateTime.now(ZoneOffset.ofHours(8)));
        if (resolution.status() == ContextReferenceResolver.Status.MISSING) {
            // 没有可引用集合时继续走既有单客户槽位补全；集合追问不能改变独立问题的兼容行为。
            return null;
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
        plan.setToolNames(List.of(ToolCatalog.LIST_ACTIVE_CUSTOMER_MEAL_BALANCES));
        plan.setMetricVersion(AgentMetricCatalog.VERSION); plan.setTimezone("Asia/Shanghai");
        plan.setAnalysisSource(analysis.getSource());
        plan.setAnalysisConfidence(analysis.getConfidence());
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator, plan,
            ToolCatalog.LIST_ACTIVE_CUSTOMER_MEAL_BALANCES, null, List.of());
        Map<String, Object> result = execution.result();
        String answer = composer().activeCustomerBalances(presentation(result));
        AgentChatResponse response = insightResponse(session,
            BusinessResponseTypeCatalog.ACTIVE_CUSTOMER_BALANCES, result, answer,
            List.of("活跃客户数", "清空会话"));
        response.setQueryPlan(plan); applyToolExecution(response, execution);
        response.setSemanticTraceSummary(understandingPipeline.semanticTrace(analysis, false));
        return response;
    }

    /**
     * 执行客户排餐语义计划。客户编号先解析为内部 ID，成功后清除 Pending 并记录本轮语义追踪。
     *
     * @param session 当前会话
     * @param message 用户当前问题，仅用于在历史查询中识别存在性问法并缩小分页
     * @param analysis 已验证的受控客户排餐语义；日期为空表示全部历史
     * @param orchestrator 本轮只读工具编排器
     * @param pendingReused 是否来自上一轮待补上下文
     * @return 客户解析结果、受控错误或排餐查询响应
     */
    private AgentChatResponse handleCustomerMealPlanQuery(MealPlanChatSession session,
                                                          String message,
                                                          BusinessQuestionAnalysis analysis,
                                                          BusinessQueryOrchestrator orchestrator,
                                                          boolean pendingReused) {
        if (businessQueryDataClient == null) {
            return response(session, ChatStatus.ERROR, "排餐查询服务暂不可用，请稍后重试。", null,
                List.of(), List.of(), BusinessResponseTypeCatalog.MEAL_PLAN);
        }
        syncSemanticCustomerToSlots(session.getSlots(), analysis);
        AgentQueryPlan queryPlan = businessQueryPlanningService.plan(analysis);
        if (queryPlan == null) {
            return response(session, ChatStatus.NEED_MORE_INFO, "请补充要查询的客户或排餐筛选条件。",
                null, List.of(), List.of("查询客户排餐", "查询历史排餐"), "BUSINESS_QUERY_CLARIFICATION");
        }
        if (queryPlan.getEntities().getCustomerId() == null) {
            ResolvedCustomer resolved = resolveCustomerForBusinessQuery(orchestrator, session);
            if (resolved.response() != null) {
                understandingPipeline.clearPendingContext(session, false);
                return resolved.response();
            }
            session.getSlots().setCustomerId(resolved.customerId());
            queryPlan.getEntities().setCustomerId(resolved.customerId());
        }
        boolean historical = !hasMealPlanDateFilter(queryPlan) && queryPlan.getEntities().getMealPlanRecordId() == null;
        if (historical && isMealPlanExistenceQuestion(message)) {
            queryPlan.getFilters().setPage(1);
            queryPlan.getFilters().setSize(1);
        }
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator,
            queryPlan, ToolCatalog.LIST_MEAL_PLANS, null, List.of());
        Map<String, Object> result = execution.result();
        understandingPipeline.clearPendingContext(session, true);
        understandingPipeline.applyResolvedFiltersToSlots(session, queryPlan.getFilters());
        AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.MEAL_PLAN, result,
            composer().mealPlan(presentation(result), historical),
            CUSTOMER_INSIGHT_QUICK_REPLIES);
        response.setQueryPlan(queryPlan);
        response.setSemanticTraceSummary(
            understandingPipeline.semanticTrace(analysis, pendingReused));
        applyToolExecution(response, execution);
        applyResultValidation(response, queryPlan, result);
        resultPipeline.captureLastBusinessQueryContext(session, response);
        session.getConversationState().setStage(DiagnosisConversationState.DIAGNOSED);
        return response;
    }

    /** 判断排餐计划是否包含任意单日或日期范围过滤。 */
    private boolean hasMealPlanDateFilter(AgentQueryPlan plan) {
        if (plan == null || plan.getFilters() == null) return false;
        return isNotBlank(plan.getFilters().getRecordDate()) || isNotBlank(plan.getFilters().getStartDate())
            || isNotBlank(plan.getFilters().getEndDate());
    }

    /** 识别历史排餐存在性问法，仅用于将已确定的历史查询收窄为一条，不参与业务意图分类。 */
    private boolean isMealPlanExistenceQuestion(String message) {
        return isNotBlank(message) && (message.contains("排过") || message.contains("曾经")
            || message.contains("是否") || message.contains("有没有") || message.contains("排了吗")
            || message.contains("参与过排餐"));
    }

    /** 将模型或 Pending 中的客户实体补到确定性槽位，供统一客户解析工具使用。 */
    private void syncSemanticCustomerToSlots(DiagnosisSlots slots, BusinessQuestionAnalysis analysis) {
        if (slots == null || analysis == null || analysis.getEntities() == null) return;
        Long analyzedCustomerId = analysis.getEntities().getCustomerId();
        if (slots.getCustomerId() == null && analyzedCustomerId != null && analyzedCustomerId > 0) {
            slots.setCustomerId(analyzedCustomerId);
        }
        if (!isNotBlank(slots.getCustomerCode()) && isNotBlank(analysis.getEntities().getCustomerCode())) {
            slots.setCustomerCode(analysis.getEntities().getCustomerCode());
        }
        if (!isNotBlank(slots.getCustomerName()) && isNotBlank(analysis.getEntities().getCustomerName())) {
            slots.setCustomerName(analysis.getEntities().getCustomerName());
        }
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
                List.of(), List.of(), BusinessResponseTypeCatalog.CUSTOMER);
        }
        syncSemanticCustomerToSlots(session.getSlots(), analysis);
        if (session.getSlots().getCustomerId() == null && !isNotBlank(session.getSlots().getCustomerCode())
            && !isNotBlank(session.getSlots().getCustomerName())) {
            return response(session, ChatStatus.NEED_MORE_INFO, "请提供客户编号，例如：B2200。", null,
                List.of(MissingSlot.CUSTOMER), List.of("客户编号 B2200"), "SLOT_REQUIRED");
        }
        AgentQueryPlan queryPlan = businessQueryPlanningService.plan(analysis);
        if (queryPlan == null) return null;
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator,
            queryPlan, ToolCatalog.CUSTOMER_OVERVIEW, null, List.of());
        Map<String, Object> result = execution.result();
        understandingPipeline.clearPendingContext(session, true);
        AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.CUSTOMER, result,
            composer().customerOverview(presentation(result)),
            CUSTOMER_INSIGHT_QUICK_REPLIES);
        response.setQueryPlan(queryPlan);
        response.setSemanticTraceSummary(
            understandingPipeline.semanticTrace(analysis, pendingReused));
        applyToolExecution(response, execution);
        applyResultValidation(response, queryPlan, result);
        resultPipeline.captureLastBusinessQueryContext(session, response);
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
                List.of(), List.of(), BusinessResponseTypeCatalog.MEAL_PLAN_ALLERGY);
        }
        AgentQueryPlan queryPlan = businessQueryPlanningService.plan(analysis);
        if (queryPlan == null) {
            return response(session, ChatStatus.NEED_MORE_INFO, "请补充要查询的排餐日期和餐次，例如今天午餐。", null,
                List.of(MissingSlot.RECORD_DATE, MissingSlot.MEAL_TYPE), List.of("今天午餐", "今天晚餐"), BusinessResponseTypeCatalog.MEAL_PLAN_ALLERGY);
        }
        ToolExecutionResult execution = businessQueryChatService.execute(orchestrator,
            queryPlan, ToolCatalog.LIST_MEAL_PLANS, null, List.of());
        Map<String, Object> result = allergyFilteredMealPlans(execution.result());
        AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.MEAL_PLAN_ALLERGY, result,
            composer().mealPlanAllergy(presentation(result)),
            List.of("今天午餐排餐客户对哪些菜过敏", "今天晚餐排餐客户对哪些菜过敏", "清空会话"));
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
        List<String> codes = new BusinessResultValidator().validate(
            response.getResponseType(), queryPlan, presentation(result));
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
        if (slots.getCustomerId() != null && slots.getCustomerId() > 0) {
            return new ResolvedCustomer(slots.getCustomerId(), null);
        }
        // 兼容已持久化会话或模型 Schema 中的 0 占位符，继续按客户编号/姓名解析真实内部 ID。
        slots.setCustomerId(null);
        ToolExecutionResult overviewExecution = executeBusinessTool(orchestrator, BusinessResponseTypeCatalog.CUSTOMER,
            slots, ToolCatalog.CUSTOMER_OVERVIEW, null, List.of());
        Map<String, Object> overview = overviewExecution.result();
        Object overviewCustomerId = overview.get("customerId");
        if (overviewCustomerId instanceof Number) return new ResolvedCustomer(((Number) overviewCustomerId).longValue(), null);
        AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.CUSTOMER, overview,
            composer().customerOverview(presentation(overview)),
            CUSTOMER_INSIGHT_QUICK_REPLIES);
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
     * @param orchestrator 本轮业务查询编排器
     * @return 客户信息查询响应
     */
    private AgentChatResponse handleCustomerInsight(ChatIntent intent, MealPlanChatSession session,
                                                    ChatExtractionResult extraction,
                                                    BusinessQueryOrchestrator orchestrator) {
        DiagnosisSlots slots = session.getSlots();
        Long customerId = slots.getCustomerId();

        switch (intent) {
            case CUSTOMER_MEAL_BALANCE_QUERY: {
                AgentQueryPlan queryPlan = legacyBusinessQueryPlan(intent, slots);
                ToolExecutionResult execution = executeBusinessTool(orchestrator, queryPlan,
                    ToolCatalog.CUSTOMER_OVERVIEW, null, List.of());
                AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.CUSTOMER, execution.result(),
                    composer().customerOverview(presentation(execution.result())),
                    CUSTOMER_INSIGHT_QUICK_REPLIES);
                response.setQueryPlan(queryPlan);
                applyToolExecution(response, execution);
                return response;
            }
            case CUSTOMER_VERIFICATION_QUERY: {
                ResolvedCustomer resolved = resolveCustomerForBusinessQuery(orchestrator, session);
                if (resolved.response != null) return resolved.response;
                DiagnosisSlots resolvedSlots = conversationStateSupport.copy(slots);
                resolvedSlots.setCustomerId(resolved.customerId);
                AgentQueryPlan queryPlan = legacyBusinessQueryPlan(intent, resolvedSlots);
                ToolExecutionResult execution = executeBusinessTool(orchestrator, queryPlan,
                    ToolCatalog.LIST_VERIFICATIONS, null, List.of());
                AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.VERIFICATION, execution.result(),
                    composer().verificationList(presentation(execution.result())),
                    CUSTOMER_INSIGHT_QUICK_REPLIES);
                response.setQueryPlan(queryPlan);
                applyToolExecution(response, execution);
                return response;
            }
            case CUSTOMER_ORDER_QUERY: {
                if (slots.getOrderId() != null || isNotBlank(slots.getOrderCode())) {
                    AgentQueryPlan queryPlan = legacyBusinessQueryPlan(intent, slots);
                    ToolExecutionResult execution = executeBusinessTool(orchestrator, queryPlan,
                        ToolCatalog.ORDER_DETAIL, null, List.of());
                    Map<String, Object> result = singleItemResult(execution.result());
                    AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.ORDER, result,
                        composer().orderList(presentation(result)),
                        CUSTOMER_INSIGHT_QUICK_REPLIES);
                    response.setQueryPlan(queryPlan);
                    applyToolExecution(response, execution);
                    return response;
                }
                ResolvedCustomer resolved = resolveCustomerForBusinessQuery(orchestrator, session);
                if (resolved.response != null) return resolved.response;
                DiagnosisSlots resolvedSlots = conversationStateSupport.copy(slots);
                resolvedSlots.setCustomerId(resolved.customerId);
                AgentQueryPlan queryPlan = legacyBusinessQueryPlan(intent, resolvedSlots);
                ToolExecutionResult execution = executeBusinessTool(orchestrator, queryPlan,
                    ToolCatalog.LIST_ORDERS, null, List.of());
                AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.ORDER, execution.result(),
                    composer().orderList(presentation(execution.result())),
                    CUSTOMER_INSIGHT_QUICK_REPLIES);
                response.setQueryPlan(queryPlan);
                applyToolExecution(response, execution);
                return response;
            }
            case CUSTOMER_REFUND_QUERY: {
                if (businessQueryDataClient != null) {
                    ResolvedCustomer resolved = resolveCustomerForBusinessQuery(orchestrator, session);
                    if (resolved.response != null) return resolved.response;
                    DiagnosisSlots resolvedSlots = conversationStateSupport.copy(slots);
                    resolvedSlots.setCustomerId(resolved.customerId);
                    AgentQueryPlan queryPlan = legacyBusinessQueryPlan(intent, resolvedSlots);
                    ToolExecutionResult execution = executeBusinessTool(orchestrator, queryPlan,
                        ToolCatalog.LIST_REFUNDS, null, List.of());
                    AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.REFUND, execution.result(),
                        composer().refundList(presentation(execution.result())),
                        CUSTOMER_INSIGHT_QUICK_REPLIES);
                    response.setQueryPlan(queryPlan);
                    applyToolExecution(response, execution);
                    return response;
                }
                return insightResponse(session, BusinessResponseTypeCatalog.REFUND, Map.of(), "退餐查询服务暂不可用，请稍后重试。", CUSTOMER_INSIGHT_QUICK_REPLIES);
            }
            case CUSTOMER_PACKAGE_QUERY: {
                if (businessQueryDataClient == null) {
                    return insightResponse(session, BusinessResponseTypeCatalog.CUSTOMER, Map.of(), "套餐查询服务暂不可用，请稍后重试。", CUSTOMER_INSIGHT_QUICK_REPLIES);
                }
                ToolExecutionResult overviewExecution = executeBusinessTool(orchestrator, BusinessResponseTypeCatalog.CUSTOMER,
                    slots, ToolCatalog.CUSTOMER_OVERVIEW, null, List.of());
                Map<String, Object> overview = overviewExecution.result();
                List<Long> packageIds = extractParentPackageIds(overview);
                if (packageIds.isEmpty()) {
                    AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.CUSTOMER, overview,
                        composer().customerPackages(presentation(overview)),
                        CUSTOMER_INSIGHT_QUICK_REPLIES);
                    applyToolExecution(response, overviewExecution);
                    return response;
                }
                List<Map<String, Object>> details = new ArrayList<>();
                List<ToolExecutionResult> executions = new ArrayList<>();
                executions.add(overviewExecution);
                for (Long packageId : packageIds) {
                    AgentQueryPlan packagePlan = packageDetailPlan(slots, packageId);
                    ToolExecutionResult execution = orchestrator == null ? ToolExecutionResult.failure("BUSINESS_QUERY_CLIENT_UNAVAILABLE")
                        : orchestrator.execute(packagePlan, ToolCatalog.PACKAGE_DETAIL,
                            null, List.of());
                    executions.add(execution);
                    if (!execution.partial() && !execution.result().isEmpty()) details.add(execution.result());
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("total", details.size());
                result.put("items", details);
                result.put("truncated", isPackageListTruncated(overview));
                AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.PACKAGE, result,
                    composer().packageDetails(presentation(result)),
                    CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, executions.toArray(new ToolExecutionResult[0]));
                return response;
            }
            case MEAL_BALANCE_CHANGE_QUERY: {
                if (businessQueryDataClient == null) return insightResponse(session, BusinessResponseTypeCatalog.CUSTOMER, Map.of(), "餐数变化查询服务暂不可用，请稍后重试。", CUSTOMER_INSIGHT_QUICK_REPLIES);
                ToolExecutionResult overviewExecution = executeBusinessTool(orchestrator, BusinessResponseTypeCatalog.CUSTOMER,
                    slots, ToolCatalog.CUSTOMER_OVERVIEW, null, List.of());
                Map<String, Object> overview = overviewExecution.result();
                Long resolvedCustomerId = overview.get("customerId") instanceof Number ? ((Number) overview.get("customerId")).longValue() : customerId;
                if (resolvedCustomerId == null) {
                    AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.CUSTOMER, overview,
                        composer().customerOverview(presentation(overview)),
                        CUSTOMER_INSIGHT_QUICK_REPLIES);
                    applyToolExecution(response, overviewExecution);
                    return response;
                }
                DiagnosisSlots resolvedSlots = conversationStateSupport.copy(slots);
                resolvedSlots.setCustomerId(resolvedCustomerId);
                ToolExecutionResult verificationExecution = executeBusinessTool(orchestrator, BusinessResponseTypeCatalog.VERIFICATION,
                    resolvedSlots, ToolCatalog.LIST_VERIFICATIONS, null, List.of());
                ToolExecutionResult refundExecution = executeBusinessTool(orchestrator, BusinessResponseTypeCatalog.REFUND,
                    resolvedSlots, ToolCatalog.LIST_REFUNDS, null, List.of());
                ToolExecutionResult cachedOverviewExecution = executeBusinessTool(orchestrator, BusinessResponseTypeCatalog.CUSTOMER,
                    slots, ToolCatalog.CUSTOMER_OVERVIEW, null, List.of());
                Map<String, Object> changeResult = new LinkedHashMap<>(overview);
                changeResult.put("verificationRecordCount", verificationExecution.result().getOrDefault("total", 0));
                changeResult.put("refundRecordCount", refundExecution.result().getOrDefault("total", 0));
                AgentChatResponse response = insightResponse(session, BusinessResponseTypeCatalog.CUSTOMER, changeResult,
                    composer().mealBalanceChange(presentation(overview),
                        presentation(verificationExecution.result()),
                        presentation(refundExecution.result())),
                    CUSTOMER_INSIGHT_QUICK_REPLIES);
                applyToolExecution(response, overviewExecution, verificationExecution, refundExecution, cachedOverviewExecution);
                return response;
            }
            default:
                throw new IllegalStateException("Unexpected insight intent: " + intent);
        }
    }

    /** 返回本轮业务查询使用的统一固定话术组装器。 */
    private BusinessAnswerComposer composer() { return businessQueryChatService.responseFactory().answerComposer(); }

    /** 将兼容展示 Map 隔离在 Handler 边界，Presenter 只消费强类型结果。 */
    private BusinessPresentationResult presentation(Map<String, Object> result) {
        return BusinessPresentationResult.fromPresentationMap(result);
    }

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
        AgentQueryPlan plan = buildQueryPlan(BusinessResponseTypeCatalog.PACKAGE, slots);
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
        MDC.put("capabilityId", safe(responseType));
        BusinessPresentationResult presentationResult = presentation(insightResult);
        resultPipeline.captureBusinessFocus(session, responseType, presentationResult);
        AgentChatResponse response = businessQueryChatService.responseFactory().create(session.getSessionId(), conversationStateSupport.copy(session.getSlots()),
            conversationStateSupport.copyMap(session.getSlots().getSlotConfidence()), session.getConversationState().getStage(),
            responseType, presentationResult, message, quickReplies);
        resultPipeline.captureLastBusinessQueryContext(session, response);
        response.setActiveTaskStack(session.getConversationState().getTaskStack());
        response.setConversationFocus(conversationFocus(session.getSlots()));
        return response;
    }

    /** 仅返回已解析的非敏感上下文句柄，禁止把姓名、手机号、地址或金额回传为会话焦点。 */
    private Map<String, String> conversationFocus(DiagnosisSlots slots) {
        if (slots == null) return Map.of();
        Map<String, String> focus = new LinkedHashMap<>();
        if (isNotBlank(slots.getCustomerCode())) focus.put("客户编号", slots.getCustomerCode());
        if (isNotBlank(slots.getOrderCode())) focus.put("订单编号", slots.getOrderCode());
        if (isNotBlank(slots.getRecordDate())) focus.put("日期", slots.getRecordDate());
        if (isNotBlank(slots.getMealType())) focus.put("餐次", slots.getMealType());
        return focus;
    }

    private AgentQueryPlan buildQueryPlan(String responseType, DiagnosisSlots slots) {
        return businessQueryChatService.plan(responseType, slots);
    }


    /**
     * 构建“有餐未排”组合查询计划，计划内显式登记排餐查询和客户概览两个工具。
     *
     * @param slots 已解析会话槽位
     * @return 可交给编排器执行的组合 QueryPlan
     */
    private AgentQueryPlan mealBalanceNoPlanPlan(DiagnosisSlots slots) {
        AgentQueryPlan plan = buildQueryPlan(BusinessResponseTypeCatalog.MEAL_PLAN, slots);
        plan.setToolNames(List.of(ToolCatalog.LIST_MEAL_PLANS,
            ToolCatalog.CUSTOMER_OVERVIEW));
        return plan;
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
        MDC.put("capabilityId", safe(responseType));
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(session.getSessionId());
        response.setStatus(status);
        response.setAssistantMessage(message);
        response.setSlots(conversationStateSupport.copy(session.getSlots()));
        response.setSlotConfidence(conversationStateSupport.copyMap(session.getSlots().getSlotConfidence()));
        response.setMissingSlots(new ArrayList<>(missingSlots));
        response.setDiagnosisResult(diagnosisResult);
        response.setQuickReplies(quickReplies);
        response.setConversationStage(session.getConversationState().getStage());
        response.setResponseType(responseType);
        response.setPendingBusinessQueryContext(session.getConversationState().getPendingBusinessQueryContext());
        response.setLastBusinessQueryContext(session.getConversationState().getLastBusinessQueryContext());
        response.setActiveTaskStack(session.getConversationState().getTaskStack());
        response.setConversationFocus(conversationFocus(session.getSlots()));
        if (diagnosisResult != null) {
            me.zhengjie.agent.domain.dto.AgentResponseValidation validation = new me.zhengjie.agent.domain.dto.AgentResponseValidation();
            validation.setStatus("AI_SUGGESTION");
            validation.setSuggestion(true);
            response.setValidation(validation);
        }
        return response;
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
        log.info("聊天诊断阶段 requestId={} intentSource={} ruleIntent={} intent={} intentConfidence={} llmTriggered={} conversationStage={} slots.recordDate={} slots.mealType={} missingSlots={} diagnosisTriggered={} costMs={}",
            MDC.get(REQUEST_ID_KEY), extraction.getIntentSource(), extraction.getRuleIntent(), extraction.getIntent(), extraction.getIntentConfidence(), extraction.isLlmTriggered(), session.getConversationState().getStage(),
            session.getSlots().getRecordDate(), session.getSlots().getMealType(),
            missing, diagnosisTriggered, System.currentTimeMillis() - start);
    }

    /**
     * 写入聊天链路 MDC 字段，让日志格式能稳定携带会话、槽位和兜底状态。
     */
    private void putChatMdc(MealPlanChatSession session) {
        MDC.put(RECORD_DATE_KEY, safe(session.getSlots().getRecordDate()));
        MDC.put(MEAL_TYPE_KEY, safe(session.getSlots().getMealType()));
        MDC.put(STAGE_KEY, safe(session.getConversationState().getStage()));
        MDC.put(FALLBACK_KEY, String.valueOf(session.getLastDiagnosisResult() != null && session.getLastDiagnosisResult().isFallback()));
        MDC.put(FALLBACK_REASON_KEY, session.getLastDiagnosisResult() == null ? "" : safe(session.getLastDiagnosisResult().getFallbackReason()));
    }
}
