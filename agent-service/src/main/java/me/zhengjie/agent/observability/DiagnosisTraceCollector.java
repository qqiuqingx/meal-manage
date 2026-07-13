package me.zhengjie.agent.observability;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收集单次诊断内的工具调用和模型轮次事件，并负责调用预算与重复调用复用。
 */
@Component
public class DiagnosisTraceCollector {

    private final ThreadLocal<DiagnosisTraceState> holder = new ThreadLocal<>();

    /**
     * 开始一轮诊断 trace 收集。
     *
     * @param maxToolCalls 本次诊断允许的最大工具调用次数
     */
    public void openSession(int maxToolCalls) {
        DiagnosisTraceState state = new DiagnosisTraceState();
        state.maxToolCalls = maxToolCalls;
        holder.set(state);
    }

    /**
     * 清理当前线程绑定的诊断 trace 状态。
     */
    public void closeSession() {
        holder.remove();
    }

    /**
     * 工具调用前执行预算和缓存检查。
     *
     * @param toolName 工具名称
     * @param inputDigest 入参摘要
     * @return 当前调用的决策
     */
    public ToolDecision beforeToolCall(String toolName, String inputDigest) {
        DiagnosisTraceState state = holder.get();
        if (state == null) {
            return ToolDecision.proceed();
        }
        String cacheKey = cacheKey(toolName, inputDigest);
        CachedToolResult cachedToolResult = state.cache.get(cacheKey);
        if (cachedToolResult != null) {
            state.events.add(buildToolEvent("TOOL_CACHE_HIT", toolName, inputDigest, true, true,
                cachedToolResult.resultCount, 0L, null, null));
            return ToolDecision.cached(cachedToolResult.result);
        }
        if (state.actualToolCalls >= state.maxToolCalls) {
            state.budgetExceeded = true;
            state.fallbackReason = "工具调用超出预算，诊断数据不完整，需人工核对。";
            state.events.add(buildToolEvent("TOOL_BUDGET_EXCEEDED", toolName, inputDigest, false, false,
                0, 0L, "IllegalStateException", "tool call budget exceeded"));
            return ToolDecision.rejected(new IllegalStateException("tool call budget exceeded"));
        }
        state.actualToolCalls++;
        return ToolDecision.proceed();
    }

    /**
     * 记录工具调用成功结果，并缓存相同入参的返回值。
     *
     * @param toolName 工具名称
     * @param inputDigest 入参摘要
     * @param result 工具返回值
     * @param costMs 调用耗时
     */
    public void recordToolSuccess(String toolName, String inputDigest, Object result, long costMs) {
        DiagnosisTraceState state = holder.get();
        if (state == null) {
            return;
        }
        int resultCount = resultCount(result);
        state.cache.put(cacheKey(toolName, inputDigest), new CachedToolResult(result, resultCount));
        state.events.add(buildToolEvent("TOOL_CALL", toolName, inputDigest, true, false, resultCount, costMs, null, null));
    }

    /**
     * 记录工具调用失败；关键工具失败时会提升为 fallback 原因。
     *
     * @param toolName 工具名称
     * @param inputDigest 入参摘要
     * @param costMs 耗时
     * @param ex 异常对象
     */
    public void recordToolFailure(String toolName, String inputDigest, long costMs, RuntimeException ex) {
        DiagnosisTraceState state = holder.get();
        if (state == null) {
            return;
        }
        if (isCriticalTool(toolName)) {
            state.criticalToolFailed = true;
            state.fallbackReason = "关键工具调用失败，诊断数据不完整，需人工核对。";
        }
        state.events.add(buildToolEvent("TOOL_CALL", toolName, inputDigest, false, false,
            0, costMs, ex.getClass().getSimpleName(), ex.getMessage()));
    }

    /**
     * 记录模型轮次开始。
     *
     * @param round 模型轮次
     */
    public void recordModelRoundStart(int round) {
        DiagnosisTraceState state = holder.get();
        if (state == null) {
            return;
        }
        DiagnosisTraceEvent event = new DiagnosisTraceEvent();
        event.setEventType("MODEL_ROUND_START");
        event.setRound(round);
        state.events.add(event);
    }

    /**
     * 记录模型轮次结束。
     *
     * @param round 模型轮次
     * @param hasToolCalls 本轮是否有工具调用
     * @param toolCallCount 本轮工具数量
     * @param toolNames 工具名称列表
     * @param costMs 耗时
     */
    public void recordModelRoundCompleted(int round, boolean hasToolCalls, int toolCallCount, String toolNames, long costMs) {
        DiagnosisTraceState state = holder.get();
        if (state == null) {
            return;
        }
        DiagnosisTraceEvent event = new DiagnosisTraceEvent();
        event.setEventType("MODEL_ROUND_COMPLETED");
        event.setRound(round);
        event.setSuccess(true);
        event.setResultCount(toolCallCount);
        event.setToolNames(hasToolCalls ? toolNames : "");
        event.setCostMs(costMs);
        state.events.add(event);
    }

    /**
     * 记录模型轮次失败。
     *
     * @param round 模型轮次
     * @param ex 异常对象
     * @param costMs 耗时
     */
    public void recordModelRoundFailed(int round, RuntimeException ex, long costMs) {
        DiagnosisTraceState state = holder.get();
        if (state == null) {
            return;
        }
        DiagnosisTraceEvent event = new DiagnosisTraceEvent();
        event.setEventType("MODEL_ROUND_FAILED");
        event.setRound(round);
        event.setSuccess(false);
        event.setCostMs(costMs);
        event.setErrorType(ex.getClass().getSimpleName());
        event.setErrorMessage(ex.getMessage());
        state.events.add(event);
    }

    /**
     * 获取完整 trace 事件列表。
     *
     * @return trace 事件快照
     */
    public List<DiagnosisTraceEvent> snapshotTrace() {
        DiagnosisTraceState state = holder.get();
        return state == null ? List.of() : new ArrayList<>(state.events);
    }

    /**
     * 获取工具调用摘要，只保留与工具相关的事件。
     *
     * @return 工具摘要列表
     */
    public List<DiagnosisTraceEvent> snapshotToolSummary() {
        DiagnosisTraceState state = holder.get();
        if (state == null) {
            return List.of();
        }
        return state.events.stream()
            .filter(event -> event.getToolName() != null)
            .toList();
    }

    /**
     * 当前诊断是否因关键工具失败或预算超限而需要 fallback。
     *
     * @return 是否需要 fallback
     */
    public boolean shouldFallback() {
        DiagnosisTraceState state = holder.get();
        return state != null && (state.criticalToolFailed || state.budgetExceeded);
    }

    /**
     * 判断本轮是否存在关键工具失败；关键基础数据缺失时即使模型给出结论也不能直接展示。
     *
     * @return 关键工具是否调用失败
     */
    public boolean hasCriticalToolFailure() {
        DiagnosisTraceState state = holder.get();
        return state != null && state.criticalToolFailed;
    }

    /**
     * 判断模型是否尝试超过工具预算；该状态仅在最终结果也不可用时触发整体兜底。
     *
     * @return 是否发生工具预算超限
     */
    public boolean isBudgetExceeded() {
        DiagnosisTraceState state = holder.get();
        return state != null && state.budgetExceeded;
    }

    /**
     * 判断指定工具是否在当前诊断中成功返回，缓存命中也视为具备有效工具证据。
     *
     * @param toolName 规则声明的登记工具名
     * @return 是否存在成功调用或成功缓存命中
     */
    public boolean hasSuccessfulToolCall(String toolName) {
        DiagnosisTraceState state = holder.get();
        if (state == null || toolName == null) return false;
        return state.events.stream().anyMatch(event -> toolName.equals(event.getToolName()) && event.isSuccess()
            && ("TOOL_CALL".equals(event.getEventType()) || "TOOL_CACHE_HIT".equals(event.getEventType())));
    }

    /**
     * 返回 trace 收集过程中产生的 fallback 原因。
     *
     * @return fallback 原因
     */
    public String fallbackReason() {
        DiagnosisTraceState state = holder.get();
        return state == null ? null : state.fallbackReason;
    }

    private DiagnosisTraceEvent buildToolEvent(String eventType,
                                               String toolName,
                                               String inputDigest,
                                               boolean success,
                                               boolean cached,
                                               int resultCount,
                                               long costMs,
                                               String errorType,
                                               String errorMessage) {
        DiagnosisTraceEvent event = new DiagnosisTraceEvent();
        event.setEventType(eventType);
        event.setToolName(toolName);
        event.setInputDigest(inputDigest);
        event.setSuccess(success);
        event.setCached(cached);
        event.setResultCount(resultCount);
        event.setCostMs(costMs);
        event.setErrorType(errorType);
        event.setErrorMessage(errorMessage);
        return event;
    }

    private int resultCount(Object result) {
        if (result == null) {
            return 0;
        }
        if (result instanceof List<?> list) {
            return list.size();
        }
        if (result instanceof Map<?, ?> map) {
            return map.isEmpty() ? 0 : 1;
        }
        return 1;
    }

    private boolean isCriticalTool(String toolName) {
        return "getCustomerProfile".equals(toolName)
            || "listCustomerOrders".equals(toolName)
            || "getMealPlan".equals(toolName);
    }

    private String cacheKey(String toolName, String inputDigest) {
        return toolName + "::" + inputDigest;
    }

    private static class DiagnosisTraceState {
        private int maxToolCalls;
        private int actualToolCalls;
        private boolean budgetExceeded;
        private boolean criticalToolFailed;
        private String fallbackReason;
        private final Map<String, CachedToolResult> cache = new LinkedHashMap<>();
        private final List<DiagnosisTraceEvent> events = new ArrayList<>();
    }

    private record CachedToolResult(Object result, int resultCount) {
    }

    public static class ToolDecision {
        private final boolean proceed;
        private final Object cachedResult;
        private final RuntimeException rejectedException;

        private ToolDecision(boolean proceed, Object cachedResult, RuntimeException rejectedException) {
            this.proceed = proceed;
            this.cachedResult = cachedResult;
            this.rejectedException = rejectedException;
        }

        public static ToolDecision proceed() {
            return new ToolDecision(true, null, null);
        }

        public static ToolDecision cached(Object cachedResult) {
            return new ToolDecision(false, cachedResult, null);
        }

        public static ToolDecision rejected(RuntimeException ex) {
            return new ToolDecision(false, null, ex);
        }

        public boolean shouldProceed() {
            return proceed;
        }

        public boolean cached() {
            return cachedResult != null;
        }

        public Object cachedResult() {
            return cachedResult;
        }

        public RuntimeException rejectedException() {
            return rejectedException;
        }
    }
}
