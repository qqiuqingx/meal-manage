package me.zhengjie.agent.domain.dto;

/**
 * LLM 连通性测试请求。
 */
public class LlmConnectivityRequest {

    private String prompt;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
