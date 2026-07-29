package me.zhengjie.agent.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import me.zhengjie.agent.config.AgentProperties;

/** Spring AI provider 适配器；provider 专属 Bean 被限制在 infrastructure 包。 */
@Component
public class SpringAiAgentModelGateway implements AgentModelGateway {
    private final ChatClient.Builder builder;
    private final AgentProperties properties;
    public SpringAiAgentModelGateway(ObjectProvider<ChatClient.Builder> builderProvider, AgentProperties properties) { this.builder = builderProvider.getIfAvailable(); this.properties = properties; }
    public boolean isConfigured(String profile) {
        return builder != null && properties.getModels().getProfiles().containsKey(normalize(profile));
    }
    public ModelProfile profile(String profile) {
        String id = normalize(profile);
        AgentProperties.ModelProfile configured = properties.getModels().getProfiles().get(id);
        if (configured == null) throw new IllegalArgumentException("MODEL_PROFILE_NOT_AVAILABLE: " + id);
        return new ModelProfile(id, configured.getModel(), configured.getTimeoutMs(), configured.isStructuredOutput(), configured.isToolCalling(), configured.getMaxRetries());
    }
    public ChatClient chatClient(String profile) {
        if (builder == null) throw new IllegalStateException("MODEL_PROFILE_NOT_AVAILABLE: " + profile);
        ModelProfile selected = profile(profile);
        return builder.clone()
            .defaultOptions(ChatOptions.builder().model(selected.model()).build())
            .build();
    }
    private String normalize(String profile) {
        return profile == null || profile.isBlank() ? "default" : profile.trim();
    }
}
