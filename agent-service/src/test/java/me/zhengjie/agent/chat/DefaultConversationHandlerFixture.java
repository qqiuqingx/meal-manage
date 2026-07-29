package me.zhengjie.agent.chat;

import me.zhengjie.agent.analysis.BusinessQuestionAnalyzer;
import me.zhengjie.agent.analysis.BusinessTemporalResolver;
import me.zhengjie.agent.analysis.ContextReferenceResolver;
import me.zhengjie.agent.analysis.RuleBasedBusinessQuestionAnalyzer;
import me.zhengjie.agent.application.conversation.BusinessConversationUnderstandingPipeline;
import me.zhengjie.agent.application.conversation.BusinessConversationResultPipeline;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.config.BusinessTimeProperties;
import me.zhengjie.agent.query.AgentQueryPlanValidator;
import me.zhengjie.agent.query.BusinessAnswerValidator;
import me.zhengjie.agent.query.BusinessQueryChatService;
import me.zhengjie.agent.query.BusinessQueryPlanningService;
import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.service.MealPlanDiagnosisService;

import java.time.Clock;
import java.time.ZoneId;

/** 统一组装默认聊天处理器的测试依赖，测试不再依赖生产兼容构造器。 */
final class DefaultConversationHandlerFixture {

    private DefaultConversationHandlerFixture() { }

    /** 使用最小依赖创建处理器。 */
    static DefaultConversationHandler create(MealPlanChatSessionStore sessionStore,
                                             MealPlanChatExtractor extractor,
                                             MealPlanDiagnosisService diagnosisService,
                                             MealPlanFollowUpService followUpService) {
        return create(sessionStore, extractor, diagnosisService, followUpService, null);
    }

    /** 增加受控业务查询客户端创建处理器。 */
    static DefaultConversationHandler create(MealPlanChatSessionStore sessionStore,
                                             MealPlanChatExtractor extractor,
                                             MealPlanDiagnosisService diagnosisService,
                                             MealPlanFollowUpService followUpService,
                                             BusinessQueryDataClient businessQueryDataClient) {
        return create(sessionStore, extractor, diagnosisService, followUpService,
            businessQueryDataClient, new AgentQueryPlanValidator());
    }

    /** 增加 QueryPlan 校验器创建处理器。 */
    static DefaultConversationHandler create(MealPlanChatSessionStore sessionStore,
                                             MealPlanChatExtractor extractor,
                                             MealPlanDiagnosisService diagnosisService,
                                             MealPlanFollowUpService followUpService,
                                             BusinessQueryDataClient businessQueryDataClient,
                                             AgentQueryPlanValidator queryPlanValidator) {
        return create(sessionStore, extractor, diagnosisService, followUpService,
            businessQueryDataClient, queryPlanValidator, new BusinessAnswerValidator());
    }

    /** 增加回答安全校验器创建处理器。 */
    static DefaultConversationHandler create(MealPlanChatSessionStore sessionStore,
                                             MealPlanChatExtractor extractor,
                                             MealPlanDiagnosisService diagnosisService,
                                             MealPlanFollowUpService followUpService,
                                             BusinessQueryDataClient businessQueryDataClient,
                                             AgentQueryPlanValidator queryPlanValidator,
                                             BusinessAnswerValidator businessAnswerValidator) {
        return create(sessionStore, extractor, diagnosisService, followUpService,
            businessQueryDataClient, queryPlanValidator, businessAnswerValidator,
            new RuleBasedBusinessQuestionAnalyzer(), new BusinessQueryPlanningService());
    }

    /** 增加业务语义分析器和规划器创建处理器。 */
    static DefaultConversationHandler create(MealPlanChatSessionStore sessionStore,
                                             MealPlanChatExtractor extractor,
                                             MealPlanDiagnosisService diagnosisService,
                                             MealPlanFollowUpService followUpService,
                                             BusinessQueryDataClient businessQueryDataClient,
                                             AgentQueryPlanValidator queryPlanValidator,
                                             BusinessAnswerValidator businessAnswerValidator,
                                             BusinessQuestionAnalyzer businessQuestionAnalyzer,
                                             BusinessQueryPlanningService planningService) {
        return create(sessionStore, extractor, diagnosisService, followUpService,
            businessQueryDataClient, queryPlanValidator, businessAnswerValidator,
            businessQuestionAnalyzer, planningService, defaultTemporalResolver(), 30);
    }

    /** 使用完整显式依赖创建处理器，供固定时钟和 TTL 场景复用。 */
    static DefaultConversationHandler create(MealPlanChatSessionStore sessionStore,
                                             MealPlanChatExtractor extractor,
                                             MealPlanDiagnosisService diagnosisService,
                                             MealPlanFollowUpService followUpService,
                                             BusinessQueryDataClient businessQueryDataClient,
                                             AgentQueryPlanValidator queryPlanValidator,
                                             BusinessAnswerValidator businessAnswerValidator,
                                             BusinessQuestionAnalyzer businessQuestionAnalyzer,
                                             BusinessQueryPlanningService planningService,
                                             BusinessTemporalResolver temporalResolver,
                                             int pendingContextTtlMinutes) {
        AgentProperties properties = new AgentProperties();
        properties.getChat().getBusinessSemantic()
            .setPendingContextTtlMinutes(pendingContextTtlMinutes);
        return new DefaultConversationHandler(
            sessionStore,
            extractor,
            diagnosisService,
            followUpService,
            businessQueryDataClient,
            new BusinessQueryChatService(
                businessQueryDataClient, queryPlanValidator, businessAnswerValidator),
            planningService,
            new ContextReferenceResolver(),
            new BusinessConversationUnderstandingPipeline(
                businessQuestionAnalyzer, temporalResolver, properties),
            new BusinessConversationResultPipeline(),
            properties,
            new ConversationStateSupport(),
            new BusinessQueryIntentPolicy());
    }

    /** 创建使用上海时区系统时钟的默认时间解析器。 */
    private static BusinessTemporalResolver defaultTemporalResolver() {
        BusinessTimeProperties properties = new BusinessTimeProperties();
        return new BusinessTemporalResolver(
            Clock.system(ZoneId.of("Asia/Shanghai")), properties);
    }
}
