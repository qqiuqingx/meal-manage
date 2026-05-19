package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.prompt.DiagnosisPromptBuilder;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.tool.AgentToolRegistry;
import me.zhengjie.agent.validator.DiagnosisResultValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
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

    public SpringAiDiagnosisAiClient(ChatClient.Builder chatClientBuilder,
                                     DiagnosisPromptBuilder promptBuilder,
                                     DiagnosisResultValidator resultValidator,
                                     AgentToolRegistry agentToolRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.promptBuilder = promptBuilder;
        this.resultValidator = resultValidator;
        this.agentToolRegistry = agentToolRegistry;
    }

    @Override
    public DiagnosisResponse diagnose(DiagnosisContextDto context, RuleRegistry ruleRegistry) {
        long start = System.currentTimeMillis();
        try {
            String prompt = promptBuilder.build(context, ruleRegistry);
            log.info("spring ai diagnose start requestId={} customerId={} recordDate={} mealType={} promptChars={} ruleCount={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                prompt.length(), ruleRegistry.getRules() == null ? 0 : ruleRegistry.getRules().size());
            DiagnosisResponse response = chatClient.prompt()
                .tools(agentToolRegistry)
                .user(prompt)
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
