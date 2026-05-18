package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.prompt.DiagnosisPromptBuilder;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.validator.DiagnosisResultValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI 实现的诊断客户端。
 */
@Component
@ConditionalOnProperty(prefix = "agent.ai", name = "enabled", havingValue = "true")
public class SpringAiDiagnosisAiClient implements DiagnosisAiClient {

    private final ChatClient chatClient;
    private final DiagnosisPromptBuilder promptBuilder;
    private final DiagnosisResultValidator resultValidator;

    public SpringAiDiagnosisAiClient(ChatClient.Builder chatClientBuilder,
                                     DiagnosisPromptBuilder promptBuilder,
                                     DiagnosisResultValidator resultValidator) {
        this.chatClient = chatClientBuilder.build();
        this.promptBuilder = promptBuilder;
        this.resultValidator = resultValidator;
    }

    @Override
    public DiagnosisResponse diagnose(DiagnosisContextDto context, RuleRegistry ruleRegistry) {
        try {
            String prompt = promptBuilder.build(context, ruleRegistry);
            DiagnosisResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(DiagnosisResponse.class);
            DiagnosisResponse validated = resultValidator.validateOrFallback(response, context, ruleRegistry.getVersionDigest());
            if (!validated.isFallback()) {
                validated.setModelName("spring-ai-chat-client");
            }
            return validated;
        } catch (RuntimeException ex) {
            return resultValidator.validateOrFallback(null, context, ruleRegistry.getVersionDigest());
        }
    }
}
