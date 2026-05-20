package me.zhengjie.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.client.DiagnosisToolDataClient;
import me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    private final LogSink logSink;

    @Autowired
    public AgentToolRegistry(DiagnosisToolDataClient toolDataClient, ObjectMapper objectMapper) {
        this(toolDataClient, objectMapper, new Slf4jLogSink());
    }

    public AgentToolRegistry(DiagnosisToolDataClient toolDataClient) {
        this(toolDataClient, new ObjectMapper(), new Slf4jLogSink());
    }

    AgentToolRegistry(DiagnosisToolDataClient toolDataClient, ObjectMapper objectMapper, LogSink logSink) {
        this.toolDataClient = toolDataClient;
        this.objectMapper = objectMapper;
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

    private <T> T invokeTool(String toolName, Object request, Supplier<T> supplier) {
        long start = System.currentTimeMillis();
        String requestId = requestId();
        String inputJson = toJson(request);
        logSink.toolCallStarted(toolName, requestId, inputJson);
        try {
            T result = supplier.get();
            String outputJson = toJson(result);
            logSink.toolCallCompleted(toolName, requestId, inputJson, outputJson, System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            logSink.toolCallFailed(toolName, requestId, inputJson, System.currentTimeMillis() - start, ex);
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

    interface LogSink {
        void toolCallStarted(String toolName, String requestId, String inputJson);

        void toolCallCompleted(String toolName, String requestId, String inputJson, String outputJson, long costMs);

        void toolCallFailed(String toolName, String requestId, String inputJson, long costMs, RuntimeException ex);
    }

    private static class Slf4jLogSink implements LogSink {
        @Override
        public void toolCallStarted(String toolName, String requestId, String inputJson) {
            log.info("诊断阶段 stage=工具调用开始 requestId={} tool={} input={}", requestId, toolName, inputJson);
        }

        @Override
        public void toolCallCompleted(String toolName, String requestId, String inputJson, String outputJson, long costMs) {
            log.info("诊断阶段 stage=工具调用完成 requestId={} tool={} input={} output={} costMs={}",
                requestId, toolName, inputJson, outputJson, costMs);
        }

        @Override
        public void toolCallFailed(String toolName, String requestId, String inputJson, long costMs, RuntimeException ex) {
            log.warn("诊断阶段 stage=工具调用失败 requestId={} tool={} input={} costMs={} errorType={} errorMessage={}",
                requestId, toolName, inputJson, costMs, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        }
    }
}
