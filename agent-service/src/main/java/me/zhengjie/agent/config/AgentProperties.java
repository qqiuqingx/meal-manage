package me.zhengjie.agent.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 服务的公共基础配置。
 *
 * <p>新增配置应优先收敛到这里或其嵌套对象，避免继续扩散 {@code @Value} 字符串。</p>
 */
@Validated
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    @NotBlank
    private String contextBaseUrl = "http://localhost:8000";
    @NotBlank
    private String contextPath = "/api/internal/agent/meal-plan/context";
    private String internalToken = "";
    @Min(100)
    @Max(120000)
    private long businessQueryTimeoutMs = 8000;
    @Valid
    private Rules rules = new Rules();
    @Valid
    private Models models = new Models();
    @Valid
    private Diagnosis diagnosis = new Diagnosis();
    @Valid
    private Chat chat = new Chat();

    public String getContextBaseUrl() { return contextBaseUrl; }
    public void setContextBaseUrl(String contextBaseUrl) { this.contextBaseUrl = contextBaseUrl; }
    public String getContextPath() { return contextPath; }
    public void setContextPath(String contextPath) { this.contextPath = contextPath; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
    public long getBusinessQueryTimeoutMs() { return businessQueryTimeoutMs; }
    public void setBusinessQueryTimeoutMs(long businessQueryTimeoutMs) { this.businessQueryTimeoutMs = businessQueryTimeoutMs; }
    public Rules getRules() { return rules; }
    public void setRules(Rules rules) { this.rules = rules; }
    public Models getModels() { return models; }
    public void setModels(Models models) { this.models = models; }
    public Diagnosis getDiagnosis() { return diagnosis; }
    public void setDiagnosis(Diagnosis diagnosis) { this.diagnosis = diagnosis == null ? new Diagnosis() : diagnosis; }
    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat == null ? new Chat() : chat; }

    /** 规则资源加载配置；外部 scene 目录采用完整覆盖策略。 */
    public static class Rules {
        @NotBlank
        private String basePath = "rules";
        @Valid
        private Map<String, String> sceneDirectories = new LinkedHashMap<>(Map.of("MEAL_PLAN_NOT_GENERATED", "meal-plan"));

        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }
        public Map<String, String> getSceneDirectories() { return sceneDirectories; }
        public void setSceneDirectories(Map<String, String> sceneDirectories) { this.sceneDirectories = sceneDirectories; }
    }

    /** 与 provider 解耦的模型 profile 声明；任务只选择 profile，不读取 provider 属性。 */
    public static class Models {
        @Valid
        private Map<String, ModelProfile> profiles = new LinkedHashMap<>(Map.of("default", new ModelProfile()));
        @Valid
        private Map<String, ModelProvider> providers = new LinkedHashMap<>(Map.of("deepseek", new ModelProvider()));
        private List<String> fallbackOrder = new ArrayList<>();
        public Map<String, ModelProfile> getProfiles() { return profiles; }
        public void setProfiles(Map<String, ModelProfile> profiles) { this.profiles = profiles == null ? new LinkedHashMap<>() : profiles; }
        public Map<String, ModelProvider> getProviders() { return providers; }
        public void setProviders(Map<String, ModelProvider> value) { providers = value == null ? new LinkedHashMap<>() : value; }
        public List<String> getFallbackOrder() { return fallbackOrder; }
        public void setFallbackOrder(List<String> value) { fallbackOrder = value == null ? new ArrayList<>() : value; }
    }

    public static class ModelProfile {
        @NotBlank
        private String model = "default";
        @Min(100)
        @Max(120000)
        private long timeoutMs = 3000;
        private boolean structuredOutput = true;
        private boolean toolCalling;
        @Min(0)
        @Max(5)
        private int maxRetries = 1;
        private String provider = "deepseek";
        private List<String> fallbackProviders = new ArrayList<>();
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public boolean isStructuredOutput() { return structuredOutput; }
        public void setStructuredOutput(boolean structuredOutput) { this.structuredOutput = structuredOutput; }
        public boolean isToolCalling() { return toolCalling; }
        public void setToolCalling(boolean toolCalling) { this.toolCalling = toolCalling; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public String getProvider() { return provider; }
        public void setProvider(String value) { provider = value == null || value.trim().isEmpty() ? "deepseek" : value.trim(); }
        public List<String> getFallbackProviders() { return fallbackProviders; }
        public void setFallbackProviders(List<String> value) { fallbackProviders = value == null ? new ArrayList<>() : value; }
    }

    /** 单个模型 provider 的非敏感运行配置；密钥只从环境变量绑定，绝不记录到日志。 */
    public static class ModelProvider {
        private boolean enabled = true;
        private String protocol = "DEEPSEEK";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private boolean structuredOutput = true;
        private boolean toolCalling = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String value) { protocol = value == null ? "" : value.trim().toUpperCase(); }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String value) { baseUrl = value == null ? "" : value.trim(); }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { apiKey = value == null ? "" : value.trim(); }
        public String getModel() { return model; }
        public void setModel(String value) { model = value == null ? "" : value.trim(); }
        public boolean isStructuredOutput() { return structuredOutput; }
        public void setStructuredOutput(boolean value) { structuredOutput = value; }
        public boolean isToolCalling() { return toolCalling; }
        public void setToolCalling(boolean value) { toolCalling = value; }
    }

    /** 排餐诊断执行配置。 */
    public static class Diagnosis {
        private boolean phase2Enabled = true;
        private boolean toolModeEnabled = true;
        @Min(1)
        @Max(50)
        private int maxToolCalls = 8;
        private boolean traceEnabled = true;
        private boolean suggestionTemplateEnabled = true;
        public boolean isPhase2Enabled() { return phase2Enabled; }
        public void setPhase2Enabled(boolean phase2Enabled) { this.phase2Enabled = phase2Enabled; }
        public boolean isToolModeEnabled() { return toolModeEnabled; }
        public void setToolModeEnabled(boolean toolModeEnabled) { this.toolModeEnabled = toolModeEnabled; }
        public int getMaxToolCalls() { return maxToolCalls; }
        public void setMaxToolCalls(int maxToolCalls) { this.maxToolCalls = maxToolCalls; }
        public boolean isTraceEnabled() { return traceEnabled; }
        public void setTraceEnabled(boolean traceEnabled) { this.traceEnabled = traceEnabled; }
        public boolean isSuggestionTemplateEnabled() { return suggestionTemplateEnabled; }
        public void setSuggestionTemplateEnabled(boolean suggestionTemplateEnabled) { this.suggestionTemplateEnabled = suggestionTemplateEnabled; }
    }

    /** 聊天理解、语义分析和跨轮上下文配置。 */
    public static class Chat {
        @Valid
        private IntentClassifier intentClassifier = new IntentClassifier();
        @Valid
        private SemanticAnalysis semanticAnalysis = new SemanticAnalysis();
        @Valid
        private BusinessSemantic businessSemantic = new BusinessSemantic();
        @Valid
        private ConversationUnderstanding conversationUnderstanding = new ConversationUnderstanding();
        public IntentClassifier getIntentClassifier() { return intentClassifier; }
        public void setIntentClassifier(IntentClassifier value) { intentClassifier = value == null ? new IntentClassifier() : value; }
        public SemanticAnalysis getSemanticAnalysis() { return semanticAnalysis; }
        public void setSemanticAnalysis(SemanticAnalysis value) { semanticAnalysis = value == null ? new SemanticAnalysis() : value; }
        public BusinessSemantic getBusinessSemantic() { return businessSemantic; }
        public void setBusinessSemantic(BusinessSemantic value) { businessSemantic = value == null ? new BusinessSemantic() : value; }
        public ConversationUnderstanding getConversationUnderstanding() { return conversationUnderstanding; }
        public void setConversationUnderstanding(ConversationUnderstanding value) {
            conversationUnderstanding = value == null ? new ConversationUnderstanding() : value;
        }
    }

    public static class IntentClassifier {
        private IntentClassifierMode mode = IntentClassifierMode.HYBRID;

        /** 返回受控的意图分类模式。 */
        public IntentClassifierMode getMode() { return mode; }

        /** 设置受控意图分类模式，未知配置值由 Spring 在绑定期拒绝。 */
        public void setMode(IntentClassifierMode mode) {
            this.mode = mode == null ? IntentClassifierMode.HYBRID : mode;
        }
    }

    public static class SemanticAnalysis {
        private boolean enabled = true;
        @jakarta.validation.constraints.DecimalMin("0.0")
        @jakarta.validation.constraints.DecimalMax("1.0")
        private double confidenceThreshold = 0.80D;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double value) { confidenceThreshold = value; }
    }

    public static class BusinessSemantic {
        private BusinessSemanticMode mode = BusinessSemanticMode.LLM_FIRST;
        @jakarta.validation.constraints.DecimalMin("0.0")
        @jakarta.validation.constraints.DecimalMax("1.0")
        private double confidenceThreshold = 0.80D;
        private boolean pendingContextEnabled = true;
        @Min(1)
        @Max(1440)
        private int pendingContextTtlMinutes = 30;
        /** 返回受控的业务语义执行模式。 */
        public BusinessSemanticMode getMode() { return mode; }

        /** 设置受控业务语义模式，未知配置值由 Spring 在绑定期拒绝。 */
        public void setMode(BusinessSemanticMode mode) {
            this.mode = mode == null ? BusinessSemanticMode.LLM_FIRST : mode;
        }
        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double value) { confidenceThreshold = value; }
        public boolean isPendingContextEnabled() { return pendingContextEnabled; }
        public void setPendingContextEnabled(boolean value) { pendingContextEnabled = value; }
        public int getPendingContextTtlMinutes() { return pendingContextTtlMinutes; }
        public void setPendingContextTtlMinutes(int value) { pendingContextTtlMinutes = value; }
    }

    public static class ConversationUnderstanding {
        private ConversationUnderstandingMode mode = ConversationUnderstandingMode.SHADOW;

        /** 返回受控的多帧会话理解灰度模式。 */
        public ConversationUnderstandingMode getMode() { return mode; }

        /** 设置受控会话理解模式，未知配置值由 Spring 在绑定期拒绝。 */
        public void setMode(ConversationUnderstandingMode mode) {
            this.mode = mode == null ? ConversationUnderstandingMode.SHADOW : mode;
        }
    }

    /** 意图分类器允许的生产模式。 */
    public enum IntentClassifierMode {
        RULE_ONLY,
        LLM_ONLY,
        HYBRID
    }

    /** 业务问题语义分析允许的生产模式。 */
    public enum BusinessSemanticMode {
        RULE_ONLY,
        SHADOW,
        LLM_FIRST
    }

    /** 多帧会话理解允许的灰度模式。 */
    public enum ConversationUnderstandingMode {
        SHADOW,
        NEW
    }
}
