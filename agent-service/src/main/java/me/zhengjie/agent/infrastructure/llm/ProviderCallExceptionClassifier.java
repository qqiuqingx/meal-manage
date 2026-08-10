package me.zhengjie.agent.infrastructure.llm;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.web.client.HttpStatusCodeException;

/** 区分可安全重放的 provider 故障和不能切换 provider 的结构/业务失败。 */
public class ProviderCallExceptionClassifier {
    /**
     * 判断模型调用异常是否可安全切换到备用 provider。
     *
     * @param error 模型调用抛出的异常，允许包含多层 cause
     * @return 网络故障、超时、HTTP 429 或 5xx 时返回 {@code true}
     */
    public boolean isRecoverable(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof HttpStatusCodeException status) {
                return status.getStatusCode().value() == 429 || status.getStatusCode().is5xxServerError();
            }
            if (current instanceof OpenAIServiceException serviceException) {
                int statusCode = serviceException.statusCode();
                return statusCode == 429 || statusCode >= 500;
            }
            if (current instanceof OpenAIIoException || current instanceof OpenAIRetryableException) {
                return true;
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
