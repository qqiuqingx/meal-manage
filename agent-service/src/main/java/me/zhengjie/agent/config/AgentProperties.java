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
    private String internalToken = "";
    @Valid
    private Rules rules = new Rules();
    @Valid
    private Models models = new Models();
    @Valid
    private Chat chat = new Chat();

    public String getContextBaseUrl() { return contextBaseUrl; }
    public void setContextBaseUrl(String contextBaseUrl) { this.contextBaseUrl = contextBaseUrl; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
    public Rules getRules() { return rules; }
    public void setRules(Rules rules) { this.rules = rules; }
    public Models getModels() { return models; }
    public void setModels(Models models) { this.models = models; }
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

    /** 统一业务 Agent 的工具循环预算配置。 */
    public static class Chat {
        @Valid
        private ToolLoop toolLoop = new ToolLoop();
        public ToolLoop getToolLoop() { return toolLoop; }
        public void setToolLoop(ToolLoop value) { toolLoop = value == null ? new ToolLoop() : value; }
    }

    /**
     * 统一业务 Agent 工具循环预算；工具和模型均不能通过用户输入扩大这些边界。
     */
    public static class ToolLoop {
        @Min(1)
        @Max(6)
        private int maxToolCalls = 6;
        @Min(1)
        @Max(4)
        private int maxModelRounds = 4;
        @Min(1)
        @Max(100)
        private int maxRecords = 100;
        @Min(100)
        @Max(10000)
        private long toolTimeoutMs = 3000;
        @Min(0)
        @Max(1)
        private int maxAnswerRepairs = 1;

        public int getMaxToolCalls() { return maxToolCalls; }
        public void setMaxToolCalls(int value) { maxToolCalls = value; }
        public int getMaxModelRounds() { return maxModelRounds; }
        public void setMaxModelRounds(int value) { maxModelRounds = value; }
        public int getMaxRecords() { return maxRecords; }
        public void setMaxRecords(int value) { maxRecords = value; }
        public long getToolTimeoutMs() { return toolTimeoutMs; }
        public void setToolTimeoutMs(long value) { toolTimeoutMs = value; }
        public int getMaxAnswerRepairs() { return maxAnswerRepairs; }
        public void setMaxAnswerRepairs(int value) { maxAnswerRepairs = value; }
    }

}
