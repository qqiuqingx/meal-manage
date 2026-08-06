package me.zhengjie.agent.presentation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.infrastructure.llm.AgentModelGateway;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 使用独立 presentation profile 规划未知卡片。
 *
 * <p>此规划器不接收卡片 JsonNode、不注册 BusinessAgentTools，也不把模型原文写入日志。
 * 规划失败由上层展示服务转换为通用安全描述。</p>
 */
public class LlmPresentationPlanner implements PresentationPlanner {
    public static final String PROFILE = "presentation";
    private static final String SYSTEM_PROMPT = """
        你是内部卡片展示规划器。只能根据给出的无值结构摘要选择一种固定展示视图。
        只返回一个 JSON 对象，字段只能是 view、title、dataPath、dimensionField、metricFields、dimensionLabel、metricLabels。
        view 只能是 TEXT、TABLE、BAR、LINE、PIE；路径和字段只能原样引用摘要中的结构。
        禁止返回任何数据值、姓名、订单号、日期、数量、状态、内部 ID、敏感字段、代码、表达式、函数、HTML、Markdown、组件名或 ECharts option。
        BAR、LINE、PIE 必须使用完整数组中的字段；摘要被截断或有完整性告警时只能返回 TEXT 或 TABLE。
        """;

    private final AgentModelGateway modelGateway;
    private final ObjectMapper objectMapper;

    /** 创建使用统一 AgentModelGateway 的无工具展示规划器。 */
    public LlmPresentationPlanner(AgentModelGateway modelGateway, ObjectMapper objectMapper) {
        if (modelGateway == null || objectMapper == null) throw new IllegalArgumentException("PRESENTATION_PLANNER_INVALID");
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
    }

    /** 严格调用结构化 profile 并解析候选，任何非 JSON 或未知字段均拒绝。 */
    @Override
    public PresentationSuggestion plan(String cardType, CardSchemaInspector.SchemaSummary schema) {
        if (cardType == null || cardType.isBlank() || schema == null) {
            throw new IllegalArgumentException("PRESENTATION_SCHEMA_INVALID");
        }
        AgentModelGateway.ModelProfile profile = modelGateway.requireCapabilities(PROFILE, true, false);
        if (!modelGateway.isConfigured(PROFILE)) throw new IllegalStateException("PRESENTATION_PROFILE_UNAVAILABLE");
        String input;
        try {
            input = objectMapper.writeValueAsString(new PlanningInput(cardType, schema.paths(), schema.truncated(),
                schema.complete(), schema.warnings(), List.of("TEXT", "TABLE", "BAR", "LINE", "PIE")));
        } catch (Exception exception) {
            throw new IllegalStateException("PRESENTATION_SCHEMA_SERIALIZATION_FAILED", exception);
        }
        CompletableFuture<String> call = CompletableFuture.supplyAsync(() -> modelGateway.execute(PROFILE,
            client -> invoke(client, input)));
        try {
            String raw = call.get(profile.timeoutMs(), TimeUnit.MILLISECONDS);
            return parseStrict(raw);
        } catch (TimeoutException exception) {
            call.cancel(true);
            throw new IllegalStateException("PRESENTATION_MODEL_TIMEOUT", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PRESENTATION_MODEL_INTERRUPTED", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("PRESENTATION_MODEL_UNAVAILABLE", cause);
        }
    }

    /** 发送无工具请求；这里只提供 system/user 文本，不挂载任何工具回调。 */
    private String invoke(ChatClient client, String input) {
        if (client == null) throw new IllegalStateException("PRESENTATION_MODEL_UNAVAILABLE");
        ChatClientResponse response = client.prompt().system(SYSTEM_PROMPT).user(input).call().chatClientResponse();
        ChatResponse chatResponse = response == null ? null : response.chatResponse();
        if (chatResponse == null) throw new IllegalStateException("PRESENTATION_STRUCTURED_OUTPUT_EMPTY");
        Generation generation = chatResponse.getResult();
        if (generation != null && generation.getOutput() != null) return generation.getOutput().getText();
        List<Generation> results = chatResponse.getResults();
        return results == null || results.isEmpty() || results.get(0).getOutput() == null
            ? null : results.get(0).getOutput().getText();
    }

    /** 以严格未知字段和尾部 token 模式解析 JSON，拒绝 Markdown 包裹和任意扩展字段。 */
    private PresentationSuggestion parseStrict(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalStateException("PRESENTATION_STRUCTURED_OUTPUT_INVALID");
        try {
            ObjectMapper strict = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
            JsonParser parser = strict.getFactory().createParser(raw);
            PresentationSuggestion result = strict.readerFor(PresentationSuggestion.class).readValue(parser);
            if (result == null) throw new IllegalStateException("PRESENTATION_STRUCTURED_OUTPUT_INVALID");
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("PRESENTATION_STRUCTURED_OUTPUT_INVALID", exception);
        }
    }

    /** 发送给规划器的无值输入结构。 */
    private record PlanningInput(String cardType, List<CardSchemaInspector.SchemaField> paths,
                                 boolean truncated, boolean complete, List<String> warnings,
                                 List<String> allowedViews) { }
}
