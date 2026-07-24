package me.zhengjie.agent.api.error;

import java.util.LinkedHashMap;
import java.util.Map;

/** 对外稳定的 Agent API 错误响应，禁止包含堆栈、令牌和内部地址。 */
public class AgentApiError {
    private String code;
    private String message;
    private String requestId;
    private boolean retryable;
    private Map<String, String> details = new LinkedHashMap<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }
    public Map<String, String> getDetails() { return details; }
    public void setDetails(Map<String, String> details) { this.details = details == null ? new LinkedHashMap<>() : details; }
}
