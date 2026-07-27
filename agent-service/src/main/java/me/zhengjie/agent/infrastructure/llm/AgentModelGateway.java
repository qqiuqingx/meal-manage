package me.zhengjie.agent.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;

/** 应用层访问模型的统一端口，业务代码不感知 DeepSeek 等 provider 配置。 */
public interface AgentModelGateway {
    boolean isConfigured(String profile);
    ModelProfile profile(String profile);
    ChatClient chatClient(String profile);
    record ModelProfile(String id, String model, long timeoutMs, boolean structuredOutput, boolean toolCalling, int maxRetries) { }
}
