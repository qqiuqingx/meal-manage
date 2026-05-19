package me.zhengjie.agent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI 实现的诊断客户端。
 */
@Component
@ConditionalOnProperty(prefix = "agent.ai", name = "enabled", havingValue = "true")
public class SpringAiDiagnosisAiClient implements DiagnosisAiClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiDiagnosisAiClient.class);
    private static final String REQUEST_ID_KEY = "requestId";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final DiagnosisResponseParser responseParser;
    private final DiagnosisPromptBuilder promptBuilder;
    private final DiagnosisResultValidator resultValidator;
    private final AgentToolRegistry agentToolRegistry;
    private final DiagnosisToolCallLoggingAdvisor toolCallLoggingAdvisor;
    private final boolean toolModeEnabled;
    private final String modelName;

    public SpringAiDiagnosisAiClient(ChatClient.Builder chatClientBuilder,
                                     ObjectMapper objectMapper,
                                     DiagnosisPromptBuilder promptBuilder,
                                     DiagnosisResultValidator resultValidator,
                                     AgentToolRegistry agentToolRegistry,
                                     DiagnosisToolCallLoggingAdvisor toolCallLoggingAdvisor,
                                     @Value("${spring.ai.deepseek.chat.options.model:}") String modelName,
                                     @Value("${agent.diagnosis.tool-mode-enabled:true}") boolean toolModeEnabled) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.responseParser = new DiagnosisResponseParser(objectMapper);
        this.promptBuilder = promptBuilder;
        this.resultValidator = resultValidator;
        this.agentToolRegistry = agentToolRegistry;
        this.toolCallLoggingAdvisor = toolCallLoggingAdvisor;
        this.modelName = modelName;
        this.toolModeEnabled = toolModeEnabled;
    }

    /**
     * 根据开关在工具模式和旧上下文直传模式之间切换，并统一把异常收敛到结果校验器兜底。
     */
    @Override
    public DiagnosisResponse diagnose(DiagnosisContextDto context, RuleRegistry ruleRegistry) {
        long start = System.currentTimeMillis();
        try {
            log.info("诊断阶段 stage=开始构建提示词 requestId={} customerId={} recordDate={} mealType={} toolModeEnabled={} ruleCount={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                toolModeEnabled, ruleRegistry.getRules() == null ? 0 : ruleRegistry.getRules().size());
            String prompt = toolModeEnabled
                ? promptBuilder.buildToolPrompt(context, ruleRegistry)
                : promptBuilder.buildLegacyPrompt(context, ruleRegistry);
            log.info("诊断阶段 stage=提示词已构建 requestId={} customerId={} recordDate={} mealType={} toolModeEnabled={} promptChars={} ruleCount={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                toolModeEnabled, prompt.length(), ruleRegistry.getRules() == null ? 0 : ruleRegistry.getRules().size());
            ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();
            if (toolModeEnabled) {
                // 显式启用 ToolCallAdvisor，把工具循环放到 advisor 链里，便于按轮次打日志。
                requestSpec = requestSpec
                    .advisors(ToolCallAdvisor.builder().build(), toolCallLoggingAdvisor)
                    .tools(agentToolRegistry);
            } else {
                // 兼容旧模式时仍保留模型调用日志，但不注册任何工具。
                requestSpec = requestSpec.advisors(toolCallLoggingAdvisor);
            }
            log.info("诊断阶段 stage=开始模型调用 requestId={} customerId={} recordDate={} mealType={} toolModeEnabled={} promptChars={} ruleCount={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                toolModeEnabled, prompt.length(), ruleRegistry.getRules() == null ? 0 : ruleRegistry.getRules().size());
            ChatClient.CallResponseSpec callResponseSpec = requestSpec.user(prompt).call();
            ChatClientResponse rawClientResponse = callResponseSpec.chatClientResponse();
            ChatResponse rawChatResponse = rawClientResponse == null ? null : rawClientResponse.chatResponse();
            DiagnosisResponse response = responseParser.parse(extractContent(rawChatResponse));
            log.info("诊断阶段 stage=模型原始返回 requestId={} rawClientResponse={} rawChatResponse={} rawContext={} rawResults={} rawMetadata={} parsedResponse={}",
                MDC.get(REQUEST_ID_KEY),
                rawClientResponse,
                rawChatResponse,
                rawClientResponse == null ? null : rawClientResponse.context(),
                rawChatResponse == null ? null : rawChatResponse.getResults(),
                rawChatResponse == null ? null : rawChatResponse.getMetadata(),
                serializeDiagnosisResponse(response));
            DiagnosisResponse validated = resultValidator.validateOrFallback(response, context, ruleRegistry.getVersionDigest());
            if (!validated.isFallback()) {
                validated.setModelName("spring-ai-chat-client");
            }
            log.info("诊断阶段 stage=模型调用完成 requestId={} customerId={} recordDate={} mealType={} fallback={} reasonCount={} costMs={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                validated.isFallback(), validated.getReasons() == null ? 0 : validated.getReasons().size(),
                System.currentTimeMillis() - start);
            return validated;
        } catch (RuntimeException ex) {
            log.warn("诊断阶段 stage=模型调用失败并回退 requestId={} customerId={} recordDate={} mealType={} costMs={} errorType={} errorMessage={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return resultValidator.validateOrFallback(null, context, ruleRegistry.getVersionDigest());
        }
    }

    private String serializeDiagnosisResponse(DiagnosisResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            return "{" + "\"serializationError\":\"" + ex.getClass().getSimpleName() + "\","
                + "\"message\":\"" + escapeJson(ex.getMessage()) + "\"}";
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractContent(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        Generation result = chatResponse.getResult();
        if (result == null || result.getOutput() == null) {
            List<Generation> results = chatResponse.getResults();
            if (results == null || results.isEmpty() || results.get(0).getOutput() == null) {
                return null;
            }
            return results.get(0).getOutput().getText();
        }
        return result.getOutput().getText();
    }
}
