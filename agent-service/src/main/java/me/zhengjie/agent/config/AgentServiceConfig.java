package me.zhengjie.agent.config;

import me.zhengjie.agent.prompt.DiagnosisPromptBuilder;
import me.zhengjie.agent.validator.DiagnosisResultValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentServiceConfig {

    @Bean
    public DiagnosisPromptBuilder diagnosisPromptBuilder() {
        return new DiagnosisPromptBuilder();
    }

    @Bean
    public DiagnosisResultValidator diagnosisResultValidator() {
        return new DiagnosisResultValidator();
    }
}
