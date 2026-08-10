package me.zhengjie.agent.infrastructure.llm;

import me.zhengjie.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 OpenAI-compatible provider 的配置边界和 Spring AI 2.0 模型选项，不发起真实网络请求。 */
class OpenAiCompatibleProviderModelGatewayTest {

    @Test
    void disabledProviderMustNotCreateModelClient() {
        AgentProperties properties = properties(true, "https://example.test/v1", "test-key", "test-model");
        properties.getModels().getProviders().get("openai-compatible").setEnabled(false);
        OpenAiCompatibleProviderModelGateway gateway = new OpenAiCompatibleProviderModelGateway(properties);

        assertFalse(gateway.isConfigured());
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> gateway.chatClient(profile()));
        assertEquals("MODEL_PROVIDER_NOT_AVAILABLE: openai-compatible", exception.getMessage());
        assertFalse(exception.getMessage().contains("test-key"));
    }

    @Test
    void incompleteConnectionConfigMustBeRejected() {
        for (String missing : new String[]{"base-url", "api-key", "model"}) {
            AgentProperties properties = properties(true, "https://example.test/v1", "test-key", "test-model");
            AgentProperties.ModelProvider provider = properties.getModels().getProviders().get("openai-compatible");
            if ("base-url".equals(missing)) provider.setBaseUrl(" ");
            if ("api-key".equals(missing)) provider.setApiKey(" ");
            if ("model".equals(missing)) provider.setModel(" ");

            assertFalse(new OpenAiCompatibleProviderModelGateway(properties).isConfigured(), missing);
        }
    }

    @Test
    void completeConfigMustBuildModelWithProviderOptionsWithoutNetworkCall() {
        String apiKey = "test-only-api-key";
        OpenAiCompatibleProviderModelGateway gateway = new OpenAiCompatibleProviderModelGateway(
            properties(true, "https://example.test/v1", apiKey, "compatible-model"));

        assertTrue(gateway.isConfigured());
        OpenAiChatModel model = gateway.createModel();
        OpenAiChatOptions options = model.getOptions();

        assertEquals("https://example.test/v1", options.getBaseUrl());
        assertEquals(apiKey, options.getApiKey());
        assertEquals("compatible-model", options.getModel());
        assertNotNull(gateway.chatClient(profile()));
        assertFalse(model.toString().contains(apiKey));
        assertFalse(gateway.toString().contains(apiKey));
    }

    private AgentProperties properties(boolean enabled, String baseUrl, String apiKey, String model) {
        AgentProperties.ModelProvider provider = new AgentProperties.ModelProvider();
        provider.setEnabled(enabled);
        provider.setProtocol("OPENAI_COMPATIBLE");
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(apiKey);
        provider.setModel(model);
        AgentProperties properties = new AgentProperties();
        properties.getModels().setProviders(new LinkedHashMap<>(Map.of("openai-compatible", provider)));
        return properties;
    }

    private AgentProperties.ModelProfile profile() {
        AgentProperties.ModelProfile profile = new AgentProperties.ModelProfile();
        profile.setProvider("openai-compatible");
        return profile;
    }
}
