package me.zhengjie.agent.service.impl;

import me.zhengjie.agent.domain.dto.LlmConnectivityRequest;
import me.zhengjie.agent.domain.dto.LlmConnectivityResponse;
import me.zhengjie.agent.service.LlmConnectivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SpringAiLlmConnectivityService implements LlmConnectivityService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmConnectivityService.class);
    private static final String DEFAULT_PROMPT = "请只回复 pong";

    private final ChatClient chatClient;
    private final String baseUrl;
    private final String model;

    public SpringAiLlmConnectivityService(ChatClient.Builder chatClientBuilder,
                                          @Value("${spring.ai.deepseek.base-url:}") String baseUrl,
                                          @Value("${spring.ai.deepseek.chat.options.model:}") String model) {
        this.chatClient = chatClientBuilder.build();
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public LlmConnectivityResponse test(LlmConnectivityRequest request) {
        long start = System.currentTimeMillis();
        LlmConnectivityResponse response = new LlmConnectivityResponse();
        response.setBaseUrl(baseUrl);
        response.setModel(model);
        try {
            String prompt = resolvePrompt(request);
            log.info("llm connectivity test start baseUrl={} model={} promptChars={}", baseUrl, model, prompt.length());
            String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
            response.setSuccess(true);
            response.setContent(content);
            response.setCostMs(System.currentTimeMillis() - start);
            log.info("llm connectivity test completed baseUrl={} model={} contentChars={} costMs={}",
                baseUrl, model, content == null ? 0 : content.length(), response.getCostMs());
        } catch (RuntimeException ex) {
            response.setSuccess(false);
            response.setCostMs(System.currentTimeMillis() - start);
            response.setErrorType(ex.getClass().getSimpleName());
            response.setErrorMessage(ex.getMessage());
            log.warn("llm connectivity test failed baseUrl={} model={} costMs={} errorType={} errorMessage={}",
                baseUrl, model, response.getCostMs(), response.getErrorType(), response.getErrorMessage(), ex);
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
