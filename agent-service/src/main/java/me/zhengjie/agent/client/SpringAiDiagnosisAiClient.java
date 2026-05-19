package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.observability.DiagnosisToolCallLoggingAdvisor;
import me.zhengjie.agent.prompt.DiagnosisPromptBuilder;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.tool.AgentToolRegistry;
import me.zhengjie.agent.validator.DiagnosisResultValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI 实现的诊断客户端。
 */
@Component
@ConditionalOnProperty(prefix = "agent.ai", name = "enabled", havingValue = "true")
public class SpringAiDiagnosisAiClient implements DiagnosisAiClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiDiagnosisAiClient.class);
    private static final String REQUEST_ID_KEY = "requestId";

    private final ChatClient chatClient;
    private final DiagnosisPromptBuilder promptBuilder;
    private final DiagnosisResultValidator resultValidator;
    private final AgentToolRegistry agentToolRegistry;
    private final DiagnosisToolCallLoggingAdvisor toolCallLoggingAdvisor;
    private final boolean toolModeEnabled;

    public SpringAiDiagnosisAiClient(ChatClient.Builder chatClientBuilder,
                                     DiagnosisPromptBuilder promptBuilder,
                                     DiagnosisResultValidator resultValidator,
                                     AgentToolRegistry agentToolRegistry,
                                     DiagnosisToolCallLoggingAdvisor toolCallLoggingAdvisor,
                                     @Value("${agent.diagnosis.tool-mode-enabled:true}") boolean toolModeEnabled) {
        this.chatClient = chatClientBuilder.build();
        this.promptBuilder = promptBuilder;
        this.resultValidator = resultValidator;
        this.agentToolRegistry = agentToolRegistry;
        this.toolCallLoggingAdvisor = toolCallLoggingAdvisor;
        this.toolModeEnabled = toolModeEnabled;
    }

    @Override
    public DiagnosisResponse diagnose(DiagnosisContextDto context, RuleRegistry ruleRegistry) {
        long start = System.currentTimeMillis();
        try {
            String prompt = toolModeEnabled
                ? promptBuilder.buildToolPrompt(context, ruleRegistry)
                : promptBuilder.buildLegacyPrompt(context, ruleRegistry);
            log.info("spring ai diagnose start requestId={} customerId={} recordDate={} mealType={} toolModeEnabled={} promptChars={} ruleCount={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                toolModeEnabled, prompt.length(), ruleRegistry.getRules() == null ? 0 : ruleRegistry.getRules().size());
            ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();
            if (toolModeEnabled) {
                requestSpec = requestSpec
                    .advisors(ToolCallAdvisor.builder().build(), toolCallLoggingAdvisor)
                    .tools(agentToolRegistry);
            } else {
                requestSpec = requestSpec.advisors(toolCallLoggingAdvisor);
            }
            DiagnosisResponse response = requestSpec.user(prompt)
                .call()
                .entity(DiagnosisResponse.class);
            DiagnosisResponse validated = resultValidator.validateOrFallback(response, context, ruleRegistry.getVersionDigest());
            if (!validated.isFallback()) {
                validated.setModelName("spring-ai-chat-client");
            }
            log.info("spring ai diagnose completed requestId={} customerId={} recordDate={} mealType={} fallback={} reasonCount={} costMs={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                validated.isFallback(), validated.getReasons() == null ? 0 : validated.getReasons().size(),
                System.currentTimeMillis() - start);
            return validated;
        } catch (RuntimeException ex) {
            log.warn("spring ai diagnose failed, fallback to validator result requestId={} customerId={} recordDate={} mealType={} costMs={} errorType={} errorMessage={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return resultValidator.validateOrFallback(null, context, ruleRegistry.getVersionDigest());
        }
    }
}
