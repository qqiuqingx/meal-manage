package me.zhengjie.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.prompt.DiagnosisPromptBuilder;
import me.zhengjie.agent.prompt.DiagnosisPromptPolicyLoader;
import me.zhengjie.agent.validator.DiagnosisResultValidator;
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
}
