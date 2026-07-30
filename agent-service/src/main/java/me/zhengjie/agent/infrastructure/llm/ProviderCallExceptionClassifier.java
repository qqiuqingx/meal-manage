package me.zhengjie.agent.infrastructure.llm;

import org.springframework.web.client.HttpStatusCodeException;

/** 区分可安全重放的 provider 故障和不能切换 provider 的结构/业务失败。 */
public class ProviderCallExceptionClassifier {
    /** 超时、网络、429、5xx 才能切换备用 provider。 */
    public boolean isRecoverable(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof HttpStatusCodeException status) {
                return status.getStatusCode().value() == 429 || status.getStatusCode().is5xxServerError();
            }
            String name = current.getClass().getName();
            String message = String.valueOf(current.getMessage()).toLowerCase();
            if (name.contains("ResourceAccess") || name.contains("Connect") || name.contains("Timeout")
                || name.contains("UnknownHost") || name.contains("WebClientRequest")) return true;
            if (message.contains("timeout") || message.contains("connection refused") || message.contains("dns")
                || message.contains("status code 429") || message.contains("status 429") || message.contains("status code 5")) return true;
        }
        return false;
    }
}
