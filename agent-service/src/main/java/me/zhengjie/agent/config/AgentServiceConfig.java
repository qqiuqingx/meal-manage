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
import org.springframework.beans.factory.annotation.Value;
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
     * 业务语义默认交给模型输出受控 JSON；模型未配置、关闭、低置信度或结果不安全时回退规则。
     *
     * @param builderProvider 可选的 Spring AI 客户端构造器
     * @param objectMapper 统一 JSON 枚举反序列化器
     * @return 不会产生自由 SQL 或工具名的业务问题分析器
     */
    @Bean
    public BusinessQuestionAnalyzer businessQuestionAnalyzer(ObjectProvider<ChatClient.Builder> builderProvider,
                                                             ObjectMapper objectMapper,
                                                             @Value("${agent.chat.semantic-analysis.enabled:true}") boolean semanticAnalysisEnabled,
                                                             @Value("${agent.chat.semantic-analysis.confidence-threshold:0.80}") double confidenceThreshold) {
        RuleBasedBusinessQuestionAnalyzer ruleAnalyzer = new RuleBasedBusinessQuestionAnalyzer();
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        return !semanticAnalysisEnabled || builder == null ? ruleAnalyzer : new HybridBusinessQuestionAnalyzer(ruleAnalyzer,
            new LlmBusinessQuestionAnalyzer(builder, objectMapper), confidenceThreshold);
    }

    /** 创建从受控问题分析生成 QueryPlan 2.0 的规划器。 */
    @Bean
    public BusinessQueryPlanningService businessQueryPlanningService() {
        return new BusinessQueryPlanningService();
    }
}
