package me.zhengjie.agent.infrastructure.llm;

import me.zhengjie.agent.config.AgentProperties;
import org.springframework.ai.chat.client.ChatClient;

/** 单个模型 provider 的受控适配端口；业务层只通过 FallbackModelExecutor 访问。 */
public interface ProviderModelGateway {
    /** @return 稳定 provider 标识。 */
    String providerId();
    /** @return provider 是否已完成无付费调用的配置与 Bean 可用性校验。 */
    boolean isConfigured();
    /** 校验 provider 能否执行当前 profile 所需的结构化输出和工具调用能力。 */
    boolean supports(AgentProperties.ModelProfile profile);
    /** 为一次完整请求创建独立 ChatClient，禁止复用失败 provider 的中间状态。 */
    ChatClient chatClient(AgentProperties.ModelProfile profile);
}
