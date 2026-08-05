package me.zhengjie.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.client.MainSystemQueryClient;
import me.zhengjie.agent.client.MainSystemQueryException;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.guardrail.ToolGuardrailException;
import me.zhengjie.agent.guardrail.ToolInputGuardrail;
import me.zhengjie.agent.guardrail.ToolOutputGuardrail;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import me.zhengjie.agent.tool.input.ExplainBusinessRuleInput;
import me.zhengjie.agent.tool.input.GetPackageDetailInput;
import me.zhengjie.agent.tool.input.GetServiceCustomerDetailInput;
import me.zhengjie.agent.tool.input.ListMealPlansInput;
import me.zhengjie.agent.tool.input.ListRefundsInput;
import me.zhengjie.agent.tool.input.ListScheduledDishesInput;
import me.zhengjie.agent.tool.input.ListVerificationsInput;
import me.zhengjie.agent.tool.input.PreviewDishCandidatesInput;
import me.zhengjie.agent.tool.input.QueryBusinessMetricsInput;
import me.zhengjie.agent.tool.input.SearchCustomerProfilesInput;
import me.zhengjie.agent.tool.input.SearchDishesInput;
import me.zhengjie.agent.tool.input.SearchServiceCustomersInput;

/**
 * 统一强类型工具门面。
 *
 * <p>工具 callback 在每轮按白名单动态生成；callback 只调用这里登记的主系统端口，
 * 不暴露任意 HTTP、SQL、Mapper 或权限上下文给模型。</p>
 */
@Component
public class BusinessAgentTools {
    private static final Logger log = LoggerFactory.getLogger(BusinessAgentTools.class);
    private final ToolRegistry registry;
    private final MainSystemQueryClient queryClient;
    private final ToolInputGuardrail inputGuardrail;
    private final ToolOutputGuardrail outputGuardrail;
    private final ObjectMapper objectMapper;
    private final Map<String, Function<Object, Object>> executors = new LinkedHashMap<>();

    public BusinessAgentTools(ToolRegistry registry, MainSystemQueryClient queryClient,
                              ToolInputGuardrail inputGuardrail, ToolOutputGuardrail outputGuardrail,
                              ObjectMapper objectMapper) {
        this.registry = registry;
        this.queryClient = queryClient;
        this.inputGuardrail = inputGuardrail;
        this.outputGuardrail = outputGuardrail;
        this.objectMapper = objectMapper;
        registerExecutors();
    }

    /** 按主系统白名单生成本轮唯一可见的 Spring AI ToolCallback 集合。 */
    public List<ToolCallback> callbacksFor(Set<String> availableTools, ToolExecutionContext context) {
        return registry.visibleTo(availableTools).stream()
            .map(spec -> callback(spec, context))
            .toList();
    }

    /** 执行工具的强类型适配器；仅供 callback 和契约测试使用。 */
    public Object execute(String name, Object input) {
        ToolRegistry.ToolSpec<?> spec = registry.require(name);
        inputGuardrail.validateObject(spec, input);
        Function<Object, Object> executor = executors.get(name);
        if (executor == null) throw new IllegalArgumentException("TOOL_NOT_AVAILABLE: " + name);
        return executor.apply(input);
    }

    /** 为工具定义创建绑定输入、输出和预算护栏的回调。 */
    private ToolCallback callback(ToolRegistry.ToolSpec<?> spec, ToolExecutionContext context) {
        FunctionToolCallback<Object, Object> definition = FunctionToolCallback.builder(
                spec.name(), (Function<Object, Object>) ignored -> null)
            .description(spec.description())
            .inputType(spec.inputType())
            .build();
        return new GuardedCallback(definition, spec, context);
    }

    /** 将唯一工具注册表中的每个工具绑定到主系统查询客户端。 */
    private void registerExecutors() {
        executors.put(ToolRegistry.SEARCH_CUSTOMER_PROFILES, input -> queryClient.searchCustomerProfiles((SearchCustomerProfilesInput) input));
        executors.put(ToolRegistry.SEARCH_SERVICE_CUSTOMERS, input -> queryClient.searchServiceCustomers((SearchServiceCustomersInput) input));
        executors.put(ToolRegistry.GET_SERVICE_CUSTOMER_DETAIL, input -> queryClient.getServiceCustomerDetail((GetServiceCustomerDetailInput) input));
        executors.put(ToolRegistry.LIST_MEAL_PLANS, input -> queryClient.listMealPlans((ListMealPlansInput) input));
        executors.put(ToolRegistry.LIST_VERIFICATIONS, input -> queryClient.listVerifications((ListVerificationsInput) input));
        executors.put(ToolRegistry.LIST_REFUNDS, input -> queryClient.listRefunds((ListRefundsInput) input));
        executors.put(ToolRegistry.PREVIEW_DISH_CANDIDATES, input -> queryClient.previewDishCandidates((PreviewDishCandidatesInput) input));
        executors.put(ToolRegistry.LIST_SCHEDULED_DISHES, input -> queryClient.listScheduledDishes((ListScheduledDishesInput) input));
        executors.put(ToolRegistry.SEARCH_DISHES, input -> queryClient.searchDishes((SearchDishesInput) input));
        executors.put(ToolRegistry.GET_PACKAGE_DETAIL, input -> queryClient.getPackageDetail((GetPackageDetailInput) input));
        executors.put(ToolRegistry.QUERY_BUSINESS_METRICS, input -> queryClient.queryBusinessMetrics((QueryBusinessMetricsInput) input));
        executors.put(ToolRegistry.EXPLAIN_BUSINESS_RULE, input -> queryClient.explainBusinessRule((ExplainBusinessRuleInput) input));
    }

    /** 在 Spring AI callback 之前执行输入/输出/预算/缓存护栏。 */
    private final class GuardedCallback implements ToolCallback {
        private final ToolCallback definition;
        private final ToolRegistry.ToolSpec<?> spec;
        private final ToolExecutionContext context;

        /** 创建带输入校验、同参缓存和输出校验的工具回调包装器。 */
        private GuardedCallback(ToolCallback definition, ToolRegistry.ToolSpec<?> spec, ToolExecutionContext context) {
            this.definition = definition;
            this.spec = spec;
            this.context = context;
        }

        @Override public ToolDefinition getToolDefinition() { return definition.getToolDefinition(); }

        /** 执行一次工具回调并记录输入、输出、稳定错误码、缓存状态和耗时。 */
        @Override public String call(String rawInput) {
            long startedAt = System.nanoTime();
            log.info("AGENT_DEBUG_TOOL_REQUEST requestId={} toolName={} rawInput={}",
                MDC.get("requestId"), spec.name(), rawInput);
            try {
                Object input = inputGuardrail.validate((ToolRegistry.ToolSpec<Object>) spec, rawInput);
                String key = context.cacheKey(spec.name(), rawInput);
                String cached = context.cached(key);
                if (cached != null) {
                    String callId = context.recordCached(spec.name(), spec.cardType(), cached);
                    logToolResponse(spec.name(), callId, "CACHED", null, cached, startedAt);
                    return cached;
                }
                context.beforeCall(spec.name());
                Object output = executors.get(spec.name()).apply(input);
                String json = objectMapper.writeValueAsString(output);
                outputGuardrail.validate(spec, json);
                String callId = context.record(spec.name(), spec.cardType(), rawInput, json, true);
                logToolResponse(spec.name(), callId, "SUCCESS", null, json, startedAt);
                return json;
            } catch (RuntimeException exception) {
                String code = stableCode(exception);
                String json = errorJson(code);
                String callId = null;
                try { callId = context.record(spec.name(), spec.cardType(), rawInput, json, false); }
                catch (RuntimeException ignored) { /* 预算错误本身不应覆盖稳定工具错误。 */ }
                logToolResponse(spec.name(), callId, "FAILED", code, json, startedAt);
                return json;
            } catch (Exception exception) {
                String json = errorJson("TOOL_OUTPUT_INVALID");
                String callId = null;
                try { callId = context.record(spec.name(), spec.cardType(), rawInput, json, false); }
                catch (RuntimeException ignored) { }
                logToolResponse(spec.name(), callId, "FAILED", "TOOL_OUTPUT_INVALID", json, startedAt);
                return json;
            }
        }

        /** 输出工具响应调试日志；日志异常不得影响工具返回值。 */
        private void logToolResponse(String toolName, String callId, String status, String errorCode,
                                     String outputJson, long startedAt) {
            try {
                if ("FAILED".equals(status)) {
                    log.warn("AGENT_DEBUG_TOOL_RESPONSE requestId={} toolName={} callId={} status={} errorCode={} costMs={} output={}",
                        MDC.get("requestId"), toolName, callId, status, errorCode, elapsedMs(startedAt), outputJson);
                } else {
                    log.info("AGENT_DEBUG_TOOL_RESPONSE requestId={} toolName={} callId={} status={} costMs={} output={}",
                        MDC.get("requestId"), toolName, callId, status, elapsedMs(startedAt), outputJson);
                }
            } catch (RuntimeException ignored) {
                // 调试日志失败不能改变工具业务结果。
            }
        }

        /** 计算从工具调用开始到当前的毫秒耗时。 */
        private long elapsedMs(long startedAt) {
            return (System.nanoTime() - startedAt) / 1_000_000L;
        }

        /** 将工具异常归一化为可交给模型处理的稳定错误码。 */
        private String stableCode(RuntimeException exception) {
            if (exception instanceof ToolGuardrailException guardrail) return guardrail.getCode();
            if (exception instanceof MainSystemQueryException query) return query.getCode();
            return "TOOL_EXECUTION_FAILED";
        }

        /** 构造不含堆栈和下游原文的工具错误 JSON。 */
        private String errorJson(String code) {
            return "{\"schemaVersion\":\"v1\",\"errorCode\":\"" + code + "\",\"retryable\":false,\"items\":[]}";
        }
    }
}
