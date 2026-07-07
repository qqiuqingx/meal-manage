package me.zhengjie.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.client.DiagnosisToolDataClient;
import me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealRefundsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolPackageSpecRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolVerificationLogsRequest;
import me.zhengjie.agent.observability.DiagnosisTraceCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Spring AI Tool Calling 注册入口。
 */
@Component
public class AgentToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentToolRegistry.class);
    private static final String REQUEST_ID_KEY = "requestId";

    private final DiagnosisToolDataClient toolDataClient;
    private final ObjectMapper objectMapper;
    private final DiagnosisTraceCollector traceCollector;
    private final LogSink logSink;

    @Autowired
    public AgentToolRegistry(DiagnosisToolDataClient toolDataClient,
                             ObjectMapper objectMapper,
                             DiagnosisTraceCollector traceCollector) {
        this(toolDataClient, objectMapper, traceCollector, new Slf4jLogSink());
    }

    public AgentToolRegistry(DiagnosisToolDataClient toolDataClient) {
        this(toolDataClient, new ObjectMapper(), new DiagnosisTraceCollector(), new Slf4jLogSink());
    }

    public AgentToolRegistry(DiagnosisToolDataClient toolDataClient,
                             ObjectMapper objectMapper,
                             DiagnosisTraceCollector traceCollector,
                             LogSink logSink) {
        this.toolDataClient = toolDataClient;
        this.objectMapper = objectMapper;
        this.traceCollector = traceCollector;
        this.logSink = logSink;
    }

    @Tool(name = "getCustomerProfile", description = "查询客户基础档案。仅在需要判断客户排除日期、配送要求、客户状态等客户信息时调用。")
    public Map<String, Object> getCustomerProfile(DiagnosisToolCustomerLookupRequest request) {
        return invokeTool("getCustomerProfile", request, () -> toolDataClient.getCustomerProfile(request));
    }

    @Tool(name = "listCustomerOrders", description = "查询客户订单列表。仅在需要判断订单是否有效、剩余餐数、套餐信息时调用。")
    public List<Map<String, Object>> listCustomerOrders(DiagnosisToolCustomerOrdersRequest request) {
        return invokeTool("listCustomerOrders", request, () -> toolDataClient.listCustomerOrders(request));
    }

    @Tool(name = "getMealPlan", description = "查询指定日期和餐次的排餐详情。仅在需要判断排餐是否生成、生成状态、失败原因时调用。")
    public Map<String, Object> getMealPlan(DiagnosisToolMealPlanLookupRequest request) {
        return invokeTool("getMealPlan", request, () -> toolDataClient.getMealPlan(request));
    }

    @Tool(name = "getCandidateDishStats", description = "查询指定日期候选菜统计。仅在需要判断候选菜或套餐过滤后数量时调用。")
    public List<Map<String, Object>> getCandidateDishStats(DiagnosisToolCandidateDishStatsRequest request) {
        return invokeTool("getCandidateDishStats", request, () -> toolDataClient.getCandidateDishStats(request));
    }

    @Tool(name = "getCustomerExcludeDates", description = "查询客户停送、排除日期和排除餐次。仅在需要判断客户是否因停送或请假未排餐时调用。")
    public Map<String, Object> getCustomerExcludeDates(DiagnosisToolCustomerLookupRequest request) {
        return invokeTool("getCustomerExcludeDates", request, () -> toolDataClient.getCustomerExcludeDates(request));
    }

    @Tool(name = "getOrderMealBalance", description = "查询订单有效期、餐次类型和早餐/午晚餐剩余餐数。仅在需要判断订单是否可继续排餐或是否餐数不足时调用。")
    public Map<String, Object> getOrderMealBalance(DiagnosisToolCustomerOrdersRequest request) {
        return invokeTool("getOrderMealBalance", request, () -> toolDataClient.getOrderMealBalance(request));
    }

    @Tool(name = "getPackageSpec", description = "查询父套餐、子套餐和餐品规格。仅在需要判断套餐规格缺失或套餐禁用时调用。")
    public Map<String, Object> getPackageSpec(DiagnosisToolPackageSpecRequest request) {
        return invokeTool("getPackageSpec", request, () -> toolDataClient.getPackageSpec(request));
    }

    @Tool(name = "getDishCandidateDetail", description = "查询候选菜池诊断明细。仅在需要判断候选菜、套餐过滤或过敏忌口过滤结果时调用。")
    public List<Map<String, Object>> getDishCandidateDetail(DiagnosisToolCandidateDishStatsRequest request) {
        return invokeTool("getDishCandidateDetail", request, () -> toolDataClient.getDishCandidateDetail(request));
    }

    @Tool(name = "listVerificationLogs", description = "查询客户或订单在指定日期范围内的核销记录。仅在需要判断核销是否消耗餐数时调用。")
    public List<Map<String, Object>> listVerificationLogs(DiagnosisToolVerificationLogsRequest request) {
        return invokeTool("listVerificationLogs", request, () -> toolDataClient.listVerificationLogs(request));
    }

    @Tool(name = "listMealRefunds", description = "查询客户或订单退餐、停餐、退款记录。仅在需要判断退餐或停餐是否影响排餐时调用。")
    public List<Map<String, Object>> listMealRefunds(DiagnosisToolMealRefundsRequest request) {
        return invokeTool("listMealRefunds", request, () -> toolDataClient.listMealRefunds(request));
    }

    @Tool(name = "getMealPlanGenerationSnapshot", description = "查询排餐生成快照、失败原因和失败客户摘要。仅在需要判断排餐生成任务是否失败时调用。")
    public Map<String, Object> getMealPlanGenerationSnapshot(DiagnosisToolMealPlanLookupRequest request) {
        return invokeTool("getMealPlanGenerationSnapshot", request, () -> toolDataClient.getMealPlanGenerationSnapshot(request));
    }

    private <T> T invokeTool(String toolName, Object request, Supplier<T> supplier) {
        long start = System.currentTimeMillis();
        String requestId = requestId();
        String inputDigest = digest(toJson(request));
        DiagnosisTraceCollector.ToolDecision decision = traceCollector.beforeToolCall(toolName, inputDigest);
        if (decision.cached()) {
            logSink.toolCallCacheHit(toolName, requestId, inputDigest);
            return (T) decision.cachedResult();
        }
        if (!decision.shouldProceed()) {
            logSink.toolCallRejected(toolName, requestId, inputDigest, decision.rejectedException());
            throw decision.rejectedException();
        }
        logSink.toolCallStarted(toolName, requestId, inputDigest);
        try {
            T result = supplier.get();
            int resultCount = resultCount(result);
            traceCollector.recordToolSuccess(toolName, inputDigest, result, System.currentTimeMillis() - start);
            logSink.toolCallCompleted(toolName, requestId, inputDigest, resultCount, System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            traceCollector.recordToolFailure(toolName, inputDigest, System.currentTimeMillis() - start, ex);
            logSink.toolCallFailed(toolName, requestId, inputDigest, System.currentTimeMillis() - start, ex);
            throw ex;
        }
    }

    private String requestId() {
        String requestId = MDC.get(REQUEST_ID_KEY);
        return requestId == null ? "" : requestId;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(6, bytes.length); i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value == null ? 0 : value.hashCode());
        }
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

    public interface LogSink {
        void toolCallStarted(String toolName, String requestId, String inputDigest);

        void toolCallCompleted(String toolName, String requestId, String inputDigest, int resultCount, long costMs);

        void toolCallFailed(String toolName, String requestId, String inputDigest, long costMs, RuntimeException ex);

        void toolCallCacheHit(String toolName, String requestId, String inputDigest);

        void toolCallRejected(String toolName, String requestId, String inputDigest, RuntimeException ex);
    }

    private static class Slf4jLogSink implements LogSink {
        @Override
        public void toolCallStarted(String toolName, String requestId, String inputDigest) {
            log.info("诊断阶段 stage=工具调用开始 requestId={} tool={} inputDigest={}", requestId, toolName, inputDigest);
        }

        @Override
        public void toolCallCompleted(String toolName, String requestId, String inputDigest, int resultCount, long costMs) {
            log.info("诊断阶段 stage=工具调用完成 requestId={} tool={} inputDigest={} resultCount={} costMs={}",
                requestId, toolName, inputDigest, resultCount, costMs);
        }

        @Override
        public void toolCallFailed(String toolName, String requestId, String inputDigest, long costMs, RuntimeException ex) {
            log.warn("诊断阶段 stage=工具调用失败 requestId={} tool={} inputDigest={} costMs={} errorType={} errorMessage={}",
                requestId, toolName, inputDigest, costMs, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        }

        @Override
        public void toolCallCacheHit(String toolName, String requestId, String inputDigest) {
            log.info("诊断阶段 stage=工具调用复用 requestId={} tool={} inputDigest={}", requestId, toolName, inputDigest);
        }

        @Override
        public void toolCallRejected(String toolName, String requestId, String inputDigest, RuntimeException ex) {
            log.warn("诊断阶段 stage=工具调用拒绝 requestId={} tool={} inputDigest={} errorType={} errorMessage={}",
                requestId, toolName, inputDigest, ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}
