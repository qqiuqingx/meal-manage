package me.zhengjie.agent.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
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
        public Map<String, ModelProfile> getProfiles() { return profiles; }
        public void setProfiles(Map<String, ModelProfile> profiles) { this.profiles = profiles == null ? new LinkedHashMap<>() : profiles; }
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
    }
}
