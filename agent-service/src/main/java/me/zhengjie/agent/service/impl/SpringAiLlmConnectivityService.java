package me.zhengjie.agent.service.impl;

import me.zhengjie.agent.domain.dto.LlmConnectivityRequest;
import me.zhengjie.agent.domain.dto.LlmConnectivityResponse;
import me.zhengjie.agent.service.LlmConnectivityService;
import me.zhengjie.agent.infrastructure.llm.AgentModelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SpringAiLlmConnectivityService implements LlmConnectivityService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmConnectivityService.class);
    private static final String DEFAULT_PROMPT = "请只回复 pong";

    private final AgentModelGateway modelGateway;

    public SpringAiLlmConnectivityService(AgentModelGateway modelGateway) {
        this.modelGateway = modelGateway;
    }

    @Override
    public LlmConnectivityResponse test(LlmConnectivityRequest request) {
        long start = System.currentTimeMillis();
        LlmConnectivityResponse response = new LlmConnectivityResponse();
        AgentModelGateway.ModelProfile profile = modelGateway.profile("connectivity");
        // 连通性结果不暴露 provider URL 或任何鉴权配置。
        response.setModel(profile.model());
        try {
            String prompt = resolvePrompt(request);
            if (!modelGateway.isConfigured("connectivity")) throw new IllegalStateException("MODEL_PROFILE_NOT_AVAILABLE");
            log.info("llm connectivity test start modelProfile={} promptChars={}", profile.id(), prompt.length());
            String content = modelGateway.chatClient("connectivity").prompt()
                .user(prompt)
                .call()
                .content();
            response.setSuccess(true);
            response.setContent(content);
            response.setCostMs(System.currentTimeMillis() - start);
            log.info("llm connectivity test completed modelProfile={} contentChars={} costMs={}",
                profile.id(), content == null ? 0 : content.length(), response.getCostMs());
        } catch (RuntimeException ex) {
            response.setSuccess(false);
            response.setCostMs(System.currentTimeMillis() - start);
            response.setErrorType(ex.getClass().getSimpleName());
            response.setErrorMessage("模型连通性检测失败，请检查模型 profile 配置和网络状态。");
            log.warn("llm connectivity test failed modelProfile={} costMs={} errorType={}",
                profile.id(), response.getCostMs(), response.getErrorType());
        }
        return response;
    }

    private String resolvePrompt(LlmConnectivityRequest request) {
        if (request == null || request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            return DEFAULT_PROMPT;
        }
        return request.getPrompt().trim();
    }
}
