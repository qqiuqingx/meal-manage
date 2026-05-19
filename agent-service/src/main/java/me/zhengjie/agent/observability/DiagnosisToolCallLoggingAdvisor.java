package me.zhengjie.agent.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ChatModelCallAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Logs each Spring AI model round without recording prompts or tool payloads.
 */
@Component
public class DiagnosisToolCallLoggingAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisToolCallLoggingAdvisor.class);
    private static final String ROUND_CONTEXT_KEY = DiagnosisToolCallLoggingAdvisor.class.getName() + ".round";
    private static final int ORDER = ToolCallAdvisor.builder().build().getOrder() + 100;

    private final LogSink logSink;

    /**
     * 默认走 slf4j 输出，测试里会注入自定义 sink 做行为校验。
     */
    public DiagnosisToolCallLoggingAdvisor() {
        this(new Slf4jLogSink());
    }

    DiagnosisToolCallLoggingAdvisor(LogSink logSink) {
        this.logSink = logSink;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        int round = nextRound(chatClientRequest);
        long start = System.currentTimeMillis();
        logSink.modelCallStart(round);
        try {
            ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
            ToolCallSummary summary = summarizeToolCalls(response);
            logSink.modelCallCompleted(round, summary.hasToolCalls(), summary.toolCallCount(), summary.toolNames(),
                System.currentTimeMillis() - start);
            return response;
        } catch (RuntimeException ex) {
            logSink.modelCallFailed(round, ex, System.currentTimeMillis() - start);
            throw ex;
        }
    }

    @Override
    public String getName() {
        return "Diagnosis Tool Call Logging Advisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 同一个 ChatClientRequest 会在工具调用链里复用，这里把轮次记到 context 里便于后续递增。
     */
    private int nextRound(ChatClientRequest request) {
        Object current = request.context().get(ROUND_CONTEXT_KEY);
        int round = current instanceof Number number ? number.intValue() + 1 : 1;
        request.context().put(ROUND_CONTEXT_KEY, round);
        return round;
    }

    /**
     * 只提取工具名和数量，不展开参数和返回内容，避免把敏感业务数据写进日志。
     */
    private ToolCallSummary summarizeToolCalls(ChatClientResponse response) {
        ChatResponse chatResponse = response == null ? null : response.chatResponse();
        if (chatResponse == null || !chatResponse.hasToolCalls()) {
            return new ToolCallSummary(false, 0, "");
        }
        List<String> toolNames = chatResponse.getResults().stream()
            .map(Generation::getOutput)
            .filter(AssistantMessage::hasToolCalls)
            .flatMap(message -> message.getToolCalls().stream())
            .map(AssistantMessage.ToolCall::name)
            .distinct()
            .collect(Collectors.toList());
        return new ToolCallSummary(true, toolNames.size(), String.join(",", toolNames));
    }

    interface LogSink {
        void modelCallStart(int round);

        void modelCallCompleted(int round, boolean hasToolCalls, int toolCallCount, String toolNames, long costMs);

        void modelCallFailed(int round, RuntimeException ex, long costMs);
    }

    private record ToolCallSummary(boolean hasToolCalls, int toolCallCount, String toolNames) {
    }

    private static class Slf4jLogSink implements LogSink {
        @Override
        public void modelCallStart(int round) {
            log.info("诊断阶段 stage=模型轮次开始 requestId={} round={}", MDC.get("requestId"), round);
        }

        @Override
        public void modelCallCompleted(int round, boolean hasToolCalls, int toolCallCount, String toolNames, long costMs) {
            log.info("诊断阶段 stage=模型轮次完成 requestId={} round={} hasToolCalls={} toolCallCount={} toolNames={} costMs={}",
                MDC.get("requestId"), round, hasToolCalls, toolCallCount, toolNames, costMs);
        }

        @Override
        public void modelCallFailed(int round, RuntimeException ex, long costMs) {
            log.warn("诊断阶段 stage=模型轮次失败 requestId={} round={} costMs={} errorType={} errorMessage={}",
                MDC.get("requestId"), round, costMs, ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}
