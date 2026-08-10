package me.zhengjie.agent.infrastructure.llm;

import me.zhengjie.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 模型网关必须严格按 profile 路由并拒绝能力不匹配，不能静默回退到默认模型。 */
class SpringAiAgentModelGatewayTest {

    @Test
    void unknownProfileMustNotFallbackToDefault() {
        SpringAiAgentModelGateway gateway = gateway(properties("default", true, true), null);

        assertFalse(gateway.isConfigured("missing"));
        assertThrows(IllegalArgumentException.class, () -> gateway.profile("missing"));
    }

    @Test
    void unsupportedToolCallingMustFailBeforeModelInvocation() {
        SpringAiAgentModelGateway gateway = gateway(properties("diagnosis", true, false), null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> gateway.requireCapabilities("diagnosis", true, true));

        assertEquals("MODEL_CAPABILITY_UNSUPPORTED: tool-calling", exception.getMessage());
    }

    @Test
    void selectedProfileModelsMustBeAppliedToClonedBuilder() {
        java.util.List<ChatOptions.Builder<?>> captured = new java.util.ArrayList<>();
        ChatClient chatClient = proxy(ChatClient.class, (method, args) -> defaultValue(method.getReturnType()));
        boolean[] originalBuilderMutated = {false};
        Object[] clonedBuilderHolder = new Object[1];
        ChatClient.Builder clonedBuilder = proxy(ChatClient.Builder.class, (method, args) -> {
            if ("defaultOptions".equals(method.getName())) {
                captured.add((ChatOptions.Builder<?>) args[0]);
                return clonedBuilderHolder[0];
            }
            if ("build".equals(method.getName())) return chatClient;
            if ("clone".equals(method.getName())) return clonedBuilderHolder[0];
            return ChatClient.Builder.class.isAssignableFrom(method.getReturnType())
                ? clonedBuilderHolder[0] : defaultValue(method.getReturnType());
        });
        clonedBuilderHolder[0] = clonedBuilder;
        Object[] originalBuilderHolder = new Object[1];
        ChatClient.Builder originalBuilder = proxy(ChatClient.Builder.class, (method, args) -> {
            if ("clone".equals(method.getName())) return clonedBuilder;
            if ("defaultOptions".equals(method.getName())) {
                originalBuilderMutated[0] = true;
                return originalBuilderHolder[0];
            }
            return ChatClient.Builder.class.isAssignableFrom(method.getReturnType())
                ? originalBuilderHolder[0] : defaultValue(method.getReturnType());
        });
        originalBuilderHolder[0] = originalBuilder;
        SpringAiAgentModelGateway gateway = gateway(propertiesWithProfiles(), originalBuilder);

        assertSame(chatClient, gateway.chatClient("default"));
        assertSame(chatClient, gateway.chatClient("presentation"));
        assertEquals(java.util.List.of("model-default", "model-presentation"),
            captured.stream().map(builder -> builder.build().getModel()).toList());
        assertFalse(originalBuilderMutated[0]);
    }

    private AgentProperties properties(String profileId, boolean structuredOutput, boolean toolCalling) {
        AgentProperties properties = new AgentProperties();
        properties.getModels().setProfiles(new LinkedHashMap<>(Map.of(profileId,
            modelProfile(profileId, structuredOutput, toolCalling))));
        return properties;
    }

    private AgentProperties propertiesWithProfiles() {
        AgentProperties properties = new AgentProperties();
        properties.getModels().setProfiles(new LinkedHashMap<>(Map.of(
            "default", modelProfile("default", true, true),
            "presentation", modelProfile("presentation", true, true))));
        return properties;
    }

    private AgentProperties.ModelProfile modelProfile(String profileId, boolean structuredOutput, boolean toolCalling) {
        AgentProperties.ModelProfile profile = new AgentProperties.ModelProfile();
        profile.setModel("model-" + profileId);
        profile.setStructuredOutput(structuredOutput);
        profile.setToolCalling(toolCalling);
        return profile;
    }

    private SpringAiAgentModelGateway gateway(AgentProperties properties, ChatClient.Builder builder) {
        ObjectProvider<ChatClient.Builder> provider = proxy(ObjectProvider.class, (method, args) -> {
            if ("getIfAvailable".equals(method.getName()) || "getObject".equals(method.getName())) return builder;
            if ("iterator".equals(method.getName())) {
                return builder == null ? java.util.Collections.emptyIterator()
                    : java.util.Collections.singletonList(builder).iterator();
            }
            return defaultValue(method.getReturnType());
        });
        return new SpringAiAgentModelGateway(provider, properties);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, ProxyInvocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> invocation.invoke(method, args));
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }

    @FunctionalInterface
    private interface ProxyInvocation {
        Object invoke(java.lang.reflect.Method method, Object[] args);
    }
}
