package me.zhengjie.agent.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;
import java.util.function.Function;

/** 应用层访问模型的统一端口，业务代码不感知 DeepSeek 等 provider 配置。 */
public interface AgentModelGateway {
    boolean isConfigured(String profile);
    ModelProfile profile(String profile);
    ChatClient chatClient(String profile);

    /** @return 当前 profile 是否至少配置了一个可用备用 provider。 */
    default boolean hasFallback(String profile) { return false; }

    /** 在完整模型调用边界执行受控 fallback；调用方必须在 lambda 内构建 prompt、tool 和解析过程。 */
    default <T> T execute(String profile, Function<ChatClient, T> invocation) { return invocation.apply(chatClient(profile)); }

    /**
     * 校验任务所需模型能力，禁止把能力不匹配的 provider 静默用于当前任务。
     *
     * @param profile 模型 profile
     * @param structuredOutput 是否要求结构化输出
     * @param toolCalling 是否要求工具调用
     * @return 已校验 profile
     */
    default ModelProfile requireCapabilities(String profile, boolean structuredOutput, boolean toolCalling) {
        ModelProfile selected = profile(profile);
        if (structuredOutput && !selected.structuredOutput()) {
            throw new IllegalStateException("MODEL_CAPABILITY_UNSUPPORTED: structured-output");
        }
        if (toolCalling && !selected.toolCalling()) {
            throw new IllegalStateException("MODEL_CAPABILITY_UNSUPPORTED: tool-calling");
        }
        return selected;
    }

    record ModelProfile(String id, String model, long timeoutMs, boolean structuredOutput, boolean toolCalling, int maxRetries) { }
}
