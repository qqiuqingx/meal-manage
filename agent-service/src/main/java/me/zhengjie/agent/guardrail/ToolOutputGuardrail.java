package me.zhengjie.agent.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.tool.ToolRegistry;

/** 工具结果进入模型上下文前的类型、条数、脱敏和不可信文本护栏。 */
public class ToolOutputGuardrail {
    private final ObjectMapper objectMapper;
    private final SensitiveDataPolicy sensitiveDataPolicy;

    /** 创建工具输出护栏并复用统一敏感数据策略。 */
    public ToolOutputGuardrail(ObjectMapper objectMapper, SensitiveDataPolicy sensitiveDataPolicy) {
        this.objectMapper = objectMapper;
        this.sensitiveDataPolicy = sensitiveDataPolicy;
    }

    /** 校验固定工具结果信封；失败时不把下游原始异常返回模型。 */
    public JsonNode validate(ToolRegistry.ToolSpec<?> spec, String rawJson) {
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (node == null || !node.isObject()) throw rejected("TOOL_OUTPUT_INVALID", "tool output must be an object");
            if (resultCount(node) > spec.maxResults()) {
                throw rejected("TOOL_RESULT_LIMIT_EXCEEDED", "tool result exceeded the registered result limit");
            }
            sensitiveDataPolicy.assertSafe(node);
            return node;
        } catch (ToolGuardrailException exception) {
            throw exception;
        } catch (Exception exception) {
            throw rejected("TOOL_OUTPUT_INVALID", "tool output does not match the registered contract");
        }
    }

    /** 统计列表信封及候选/公共菜单聚合 data 中的业务行数。 */
    private int resultCount(JsonNode node) {
        JsonNode items = node.get("items");
        if (items != null && items.isArray()) return items.size();
        JsonNode data = node.get("data");
        if (data == null || !data.isObject()) return 0;
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
        return 0;
    }

    /** 构造统一工具输出拒绝异常。 */
    private ToolGuardrailException rejected(String code, String message) { return new ToolGuardrailException(code, message); }
}
