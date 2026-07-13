package me.zhengjie.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.analysis.BusinessQuestionAnalyzer;
import me.zhengjie.agent.analysis.HybridBusinessQuestionAnalyzer;
import me.zhengjie.agent.analysis.LlmBusinessQuestionAnalyzer;
import me.zhengjie.agent.analysis.RuleBasedBusinessQuestionAnalyzer;
import me.zhengjie.agent.prompt.DiagnosisPromptBuilder;
import me.zhengjie.agent.prompt.DiagnosisPromptPolicyLoader;
import me.zhengjie.agent.query.BusinessQueryPlanningService;
import me.zhengjie.agent.validator.DiagnosisResultValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentServiceConfig {

    /**
     * 让提示词构建器复用统一 ObjectMapper，避免 legacy 模式下的上下文 JSON 序列化口径漂移。
     */
    @Bean
    public DiagnosisPromptBuilder diagnosisPromptBuilder(ObjectMapper objectMapper,
                                                         DiagnosisPromptPolicyLoader policyLoader) {
        return new DiagnosisPromptBuilder(objectMapper, policyLoader);
    }

    @Bean
    public DiagnosisPromptPolicyLoader diagnosisPromptPolicyLoader() {
        return new DiagnosisPromptPolicyLoader();
    }

    @Bean
    public DiagnosisResultValidator diagnosisResultValidator() {
        return new DiagnosisResultValidator();
    }

    /**
     * 业务问题分析以规则为主；仅在模型客户端已配置时启用低置信度问题的受控 JSON 补充分析。
     *
     * @param builderProvider 可选的 Spring AI 客户端构造器
     * @param objectMapper 统一 JSON 枚举反序列化器
     * @return 不会产生自由 SQL 或工具名的业务问题分析器
     */
    @Bean
    public BusinessQuestionAnalyzer businessQuestionAnalyzer(ObjectProvider<ChatClient.Builder> builderProvider,
                                                             ObjectMapper objectMapper) {
        RuleBasedBusinessQuestionAnalyzer ruleAnalyzer = new RuleBasedBusinessQuestionAnalyzer();
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        return builder == null ? ruleAnalyzer : new HybridBusinessQuestionAnalyzer(ruleAnalyzer,
            new LlmBusinessQuestionAnalyzer(builder, objectMapper));
    }

    /** 创建从受控问题分析生成 QueryPlan 2.0 的规划器。 */
    @Bean
    public BusinessQueryPlanningService businessQueryPlanningService() {
        return new BusinessQueryPlanningService();
    }
}
