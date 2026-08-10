package me.zhengjie.agent.infrastructure.llm;

import com.openai.core.JsonValue;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Provider 异常分类同时兼容 Spring HTTP 与 Spring AI 2 OpenAI SDK 异常。 */
class ProviderCallExceptionClassifierTest {
    private final ProviderCallExceptionClassifier classifier = new ProviderCallExceptionClassifier();

    @Test
    void openAiRateLimitAndServerErrorsMustBeRecoverable() {
        assertTrue(classifier.isRecoverable(new StubOpenAIServiceException(429)));
        assertTrue(classifier.isRecoverable(new StubOpenAIServiceException(500)));
        assertTrue(classifier.isRecoverable(new StubOpenAIServiceException(503)));
    }

    @Test
    void openAiClientErrorsMustNotBeRecoverable() {
        assertFalse(classifier.isRecoverable(new StubOpenAIServiceException(400)));
        assertFalse(classifier.isRecoverable(new StubOpenAIServiceException(401)));
    }

    @Test
    void openAiIoAndRetryableErrorsMustBeRecoverableThroughCauseChain() {
        assertTrue(classifier.isRecoverable(new RuntimeException("wrapped", new OpenAIIoException("network failed"))));
        assertTrue(classifier.isRecoverable(new OpenAIRetryableException("retryable transport failure")));
    }

    /** 仅暴露 HTTP 状态码的 OpenAI SDK 服务异常测试替身。 */
    private static final class StubOpenAIServiceException extends OpenAIServiceException {
        private final int statusCode;

        /**
         * 创建指定 HTTP 状态码的异常。
         *
         * @param statusCode HTTP 状态码
         */
        private StubOpenAIServiceException(int statusCode) {
            super(statusCode + ": test", null);
            this.statusCode = statusCode;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public Headers headers() {
            return null;
        }

        @Override
        public JsonValue body() {
            return null;
        }

        @Override
        public Optional<String> code() {
            return Optional.empty();
        }

        @Override
        public Optional<String> param() {
            return Optional.empty();
        }

        @Override
        public Optional<String> type() {
            return Optional.empty();
        }
    }
}
