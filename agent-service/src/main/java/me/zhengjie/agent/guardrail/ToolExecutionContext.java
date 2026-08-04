package me.zhengjie.agent.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 单轮工具调用预算、同参缓存和可审计事实集合。 */
public class ToolExecutionContext {
    private final ObjectMapper objectMapper;
    private final int maxToolCalls;
    private final int maxRecords;
    private int toolCalls;
    private int modelRounds;
    private int cacheHits;
    private int records;
    private final Map<String, String> cache = new LinkedHashMap<>();
    private final List<ToolFact> facts = new ArrayList<>();

    /** 创建本轮工具执行上下文并设置调用与记录硬上限。 */
    public ToolExecutionContext(ObjectMapper objectMapper, int maxToolCalls, int maxRecords) {
        this.objectMapper = objectMapper;
        this.maxToolCalls = maxToolCalls;
        this.maxRecords = maxRecords;
    }

    /** 在执行前消耗一次工具预算；超过预算以稳定错误交回模型。 */
    public synchronized void beforeCall(String toolName) {
        if (toolCalls >= maxToolCalls) throw new ToolGuardrailException("TOOL_BUDGET_EXCEEDED", "tool call budget exceeded");
        toolCalls++;
    }

    /** 返回同工具同参数的缓存结果，避免模型重复消耗下游预算。 */
    public synchronized String cached(String key) { return cache.get(key); }

    /** 在进入模型回合前消耗回合预算；修复回答也受同一硬上限约束。 */
    public synchronized void beforeModelRound(int maxModelRounds) {
        if (modelRounds >= maxModelRounds) throw new ToolGuardrailException("MODEL_ROUND_BUDGET_EXCEEDED", "model round budget exceeded");
        modelRounds++;
    }

    /** 记录成功或失败的受控工具事实。 */
    public synchronized String record(String toolName, String cardType, String inputJson, String outputJson, boolean success) {
        String callId = "call-" + (facts.size() + 1);
        int count = countItems(outputJson);
        if (success && records + count > maxRecords) {
            throw new ToolGuardrailException("TOOL_RECORD_BUDGET_EXCEEDED", "business record budget exceeded");
        }
        if (success) records += count;
        cache.put(cacheKey(toolName, inputJson), outputJson);
        facts.add(new ToolFact(callId, toolName, cardType, outputJson, success, count));
        return callId;
    }

    /** 记录命中同参缓存的工具事实，不再次消耗业务记录预算。 */
    public synchronized String recordCached(String toolName, String cardType, String outputJson) {
        String callId = "call-" + (facts.size() + 1);
        cacheHits++;
        facts.add(new ToolFact(callId, toolName, cardType, outputJson, true, countItems(outputJson)));
        return callId;
    }

    /** 同参缓存键使用规范化 JSON，字段顺序不同不产生重复查询。 */
    public String cacheKey(String toolName, String inputJson) {
        try { return toolName + "|" + canonicalJson(objectMapper.readTree(inputJson)); }
        catch (Exception ignored) { return toolName + "|" + inputJson; }
    }

    /** 返回工具事实的只读快照。 */
    public synchronized List<ToolFact> facts() { return List.copyOf(facts); }
    public synchronized int successfulToolCalls() { return (int) facts.stream().filter(ToolFact::success).count(); }
    public synchronized int toolCalls() { return toolCalls; }
    /** 返回本轮已消耗的模型回合数。 */
    public synchronized int modelRounds() { return modelRounds; }
    /** 返回本轮命中同参缓存的次数。 */
    public synchronized int cacheHits() { return cacheHits; }
    /** 返回本轮已计入预算的业务记录数。 */
    public synchronized int records() { return records; }

    /** 统计工具输出信封中的业务记录数量，用于记录预算校验。 */
    private int countItems(String outputJson) {
        try {
            JsonNode node = objectMapper.readTree(outputJson);
            JsonNode items = node == null ? null : node.get("items");
            if (items != null && items.isArray()) return items.size();
            JsonNode data = node == null ? null : node.get("data");
            if (data != null && data.isObject()) {
                JsonNode nestedItems = data.get("items");
                if (nestedItems != null && nestedItems.isArray()) return nestedItems.size();
                JsonNode groups = data.get("groups");
                if (groups != null && groups.isArray()) {
                    int total = 0;
                    for (JsonNode group : groups) {
                        JsonNode groupItems = group == null ? null : group.get("items");
                        if (groupItems != null && groupItems.isArray()) total += groupItems.size();
                    }
                    return total;
                }
            }
            return node == null || node.isEmpty() ? 0 : 1;
        } catch (Exception ignored) { return 0; }
    }

    /** 递归按对象字段名排序 JSON，确保语义相同但字段顺序不同的参数共享缓存。 */
    private String canonicalJson(com.fasterxml.jackson.databind.JsonNode node) throws Exception {
        if (node == null || node.isNull() || node.isValueNode()) return node == null ? "null" : node.toString();
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : node) values.add(canonicalJson(item));
            return "[" + String.join(",", values) + "]";
        }
        Map<String, String> fields = new TreeMap<>();
        Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> field = iterator.next();
            fields.put(field.getKey(), canonicalJson(field.getValue()));
        }
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            values.add(objectMapper.writeValueAsString(field.getKey()) + ":" + field.getValue());
        }
        return "{" + String.join(",", values) + "}";
    }

    /** 模型工具调用的受控审计摘要；outputJson 只在 Agent 内部继续用于事实校验。 */
    public record ToolFact(String callId, String toolName, String cardType, String outputJson,
                           boolean success, int resultCount) { }
}
