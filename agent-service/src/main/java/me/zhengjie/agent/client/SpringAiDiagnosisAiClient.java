package me.zhengjie.agent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.observability.DiagnosisTraceCollector;
import me.zhengjie.agent.observability.DiagnosisToolCallLoggingAdvisor;
import me.zhengjie.agent.prompt.DiagnosisPromptBuilder;
import me.zhengjie.agent.prompt.DiagnosisPromptPolicyLoader;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.summary.DiagnosisSuggestionTemplateService;
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
    private static final String CUSTOMER_ID_KEY = "customerId";
    private static final String CUSTOMER_CODE_KEY = "customerCode";
    private static final String RECORD_DATE_KEY = "recordDate";
    private static final String MEAL_TYPE_KEY = "mealType";
    private static final String STAGE_KEY = "stage";
    private static final String FALLBACK_KEY = "fallback";
    private static final String FALLBACK_REASON_KEY = "fallbackReason";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final DiagnosisResponseParser responseParser;
    private final DiagnosisPromptBuilder promptBuilder;
    private final DiagnosisResultValidator resultValidator;
    private final DiagnosisTraceCollector traceCollector;
    private final DiagnosisSuggestionTemplateService suggestionTemplateService;
    private final AgentToolRegistry agentToolRegistry;
    private final DiagnosisToolCallLoggingAdvisor toolCallLoggingAdvisor;
    private final boolean toolModeEnabled;
    private final String modelName;

    @Value("${agent.diagnosis.phase2-enabled:true}")
    private boolean phase2Enabled = true;

    @Value("${agent.diagnosis.max-tool-calls:8}")
    private int maxToolCalls = 8;

    @Value("${agent.diagnosis.trace-enabled:true}")
    private boolean traceEnabled = true;

    @Value("${agent.diagnosis.suggestion-template-enabled:true}")
    private boolean suggestionTemplateEnabled = true;

    public SpringAiDiagnosisAiClient(ChatClient.Builder chatClientBuilder,
                                     ObjectMapper objectMapper,
                                     DiagnosisPromptBuilder promptBuilder,
                                     DiagnosisResultValidator resultValidator,
                                     DiagnosisTraceCollector traceCollector,
                                     DiagnosisSuggestionTemplateService suggestionTemplateService,
                                     AgentToolRegistry agentToolRegistry,
                                     DiagnosisToolCallLoggingAdvisor toolCallLoggingAdvisor,
                                     @Value("${spring.ai.deepseek.chat.options.model:}") String modelName,
                                     @Value("${agent.diagnosis.tool-mode-enabled:true}") boolean toolModeEnabled) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.responseParser = new DiagnosisResponseParser(objectMapper);
        this.promptBuilder = promptBuilder;
        this.resultValidator = resultValidator;
        this.traceCollector = traceCollector;
        this.suggestionTemplateService = suggestionTemplateService;
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
        int effectiveMaxToolCalls = maxToolCalls > 0 ? maxToolCalls : new DiagnosisPromptPolicyLoader().load().getToolPolicy().getMaxToolCalls();
        traceCollector.openSession(effectiveMaxToolCalls);
        putDiagnosisMdc(context, "AI_CLIENT_STARTED");
        try {
            log.info("诊断阶段 stage=开始构建提示词 requestId={} customerId={} recordDate={} mealType={} toolModeEnabled={} ruleCount={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                toolModeEnabled, ruleRegistry.getRules() == null ? 0 : ruleRegistry.getRules().size());
            String prompt = toolModeEnabled
                ? promptBuilder.buildToolPrompt(context, ruleRegistry)
                : promptBuilder.buildLegacyPrompt(context, ruleRegistry);
            MDC.put(STAGE_KEY, "PROMPT_READY");
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
            MDC.put(STAGE_KEY, "MODEL_CALLING");
            log.info("诊断阶段 stage=开始模型调用 requestId={} customerId={} recordDate={} mealType={} toolModeEnabled={} promptChars={} ruleCount={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                toolModeEnabled, prompt.length(), ruleRegistry.getRules() == null ? 0 : ruleRegistry.getRules().size());
            ChatClient.CallResponseSpec callResponseSpec = requestSpec.user(prompt).call();
            ChatClientResponse rawClientResponse = callResponseSpec.chatClientResponse();
            ChatResponse rawChatResponse = rawClientResponse == null ? null : rawClientResponse.chatResponse();
            DiagnosisResponse response = responseParser.parse(extractContent(rawChatResponse));
            if (phase2Enabled && suggestionTemplateEnabled) {
                response = suggestionTemplateService.applyTemplates(response);
            }
            log.info("诊断阶段 stage=模型原始返回 requestId={} rawClientResponse={} rawChatResponse={} rawContext={} rawResults={} rawMetadata={} parsedResponse={}",
                MDC.get(REQUEST_ID_KEY),
                rawClientResponse,
                rawChatResponse,
                rawClientResponse == null ? null : rawClientResponse.context(),
                rawChatResponse == null ? null : rawChatResponse.getResults(),
                rawChatResponse == null ? null : rawChatResponse.getMetadata(),
                serializeDiagnosisResponse(response));
            if (traceCollector.shouldFallback()) {
                DiagnosisResponse fallback = resultValidator.validateOrFallback(null, context, ruleRegistry);
                attachTrace(fallback);
                fallback.setFallbackReason(traceCollector.fallbackReason());
                MDC.put(FALLBACK_KEY, String.valueOf(fallback.isFallback()));
                MDC.put(FALLBACK_REASON_KEY, safe(fallback.getFallbackReason()));
                MDC.put(STAGE_KEY, "MODEL_FALLBACK");
                return fallback;
            }
            DiagnosisResponse validated = resultValidator.validateOrFallback(response, context, ruleRegistry);
            attachTrace(validated);
            if (!validated.isFallback()) {
                validated.setModelName("spring-ai-chat-client");
            } else if (validated.getFallbackReason() == null) {
                validated.setFallbackReason(traceCollector.fallbackReason());
            }
            MDC.put(FALLBACK_KEY, String.valueOf(validated.isFallback()));
            MDC.put(FALLBACK_REASON_KEY, safe(validated.getFallbackReason()));
            MDC.put(STAGE_KEY, "MODEL_COMPLETED");
            log.info("诊断阶段 stage=模型调用完成 requestId={} customerId={} recordDate={} mealType={} fallback={} reasonCount={} costMs={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                validated.isFallback(), validated.getReasons() == null ? 0 : validated.getReasons().size(),
                System.currentTimeMillis() - start);
            return validated;
        } catch (RuntimeException ex) {
            MDC.put(STAGE_KEY, "MODEL_FAILED");
            MDC.put(FALLBACK_REASON_KEY, safe(traceCollector.fallbackReason()));
            log.warn("诊断阶段 stage=模型调用失败并回退 requestId={} customerId={} recordDate={} mealType={} costMs={} errorType={} errorMessage={}",
                MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType(),
                System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            DiagnosisResponse fallback = resultValidator.validateOrFallback(null, context, ruleRegistry);
            attachTrace(fallback);
            fallback.setFallbackReason(traceCollector.fallbackReason());
            return fallback;
        } finally {
            traceCollector.closeSession();
            clearDiagnosisMdc();
        }
    }

    private void attachTrace(DiagnosisResponse response) {
        if (response == null || !phase2Enabled || !traceEnabled) {
            return;
        }
        response.setDiagnosisTrace(traceCollector.snapshotTrace());
        response.setToolCallSummary(traceCollector.snapshotToolSummary());
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

    private void putDiagnosisMdc(DiagnosisContextDto context, String stage) {
        MDC.put(CUSTOMER_ID_KEY, context.getCustomerId() == null ? "" : String.valueOf(context.getCustomerId()));
        MDC.put(CUSTOMER_CODE_KEY, safe(context.getCustomerCode()));
        MDC.put(RECORD_DATE_KEY, safe(context.getRecordDate()));
        MDC.put(MEAL_TYPE_KEY, safe(context.getMealType()));
        MDC.put(STAGE_KEY, safe(stage));
    }

    private void clearDiagnosisMdc() {
        MDC.remove(CUSTOMER_ID_KEY);
        MDC.remove(CUSTOMER_CODE_KEY);
        MDC.remove(RECORD_DATE_KEY);
        MDC.remove(MEAL_TYPE_KEY);
        MDC.remove(STAGE_KEY);
        MDC.remove(FALLBACK_KEY);
        MDC.remove(FALLBACK_REASON_KEY);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
