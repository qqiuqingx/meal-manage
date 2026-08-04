package me.zhengjie.agent.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Agent 统一敏感字段和不可信自由文本策略。 */
public class SensitiveDataPolicy {
    private static final int MAX_UNTRUSTED_TEXT_LENGTH = 2000;
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern INJECTION = Pattern.compile("(?i)(ignore\\s+(all|previous)|忽略(之前|以上|系统)|执行以下指令|system\\s*prompt|调用任意工具)");
    private static final String FORBIDDEN_FIELD_TERMS = "amount|price|money|payment|refundamount|discount|token|secret|password|permission|datascope|authorization|sql|jdbc|url";

    /** 校验工具结果或事实树，不允许敏感值在 DTO 遗漏时绕过字段级保护。 */
    public void assertSafe(JsonNode node) {
        inspect(node, null);
    }

    /** 校验最终回答文本的敏感信息和写操作声称。 */
    public void assertSafeAnswer(String answer) {
        if (answer == null) return;
        if (PHONE.matcher(answer).find()) throw new ToolGuardrailException("SENSITIVE_DATA_REJECTED", "answer contains sensitive data");
        if (INJECTION.matcher(answer).find()) throw new ToolGuardrailException("UNTRUSTED_TEXT_REJECTED", "answer contains untrusted instruction text");
        if (answer.matches("(?s).*(金额|价格|退款金额|单价|优惠|折扣|支付密码|访问令牌|SQL).*")) {
            throw new ToolGuardrailException("SENSITIVE_DATA_REJECTED", "answer contains forbidden business data");
        }
        if (answer.matches("(?s).*(已修改|已经修改|已下单|已经下单|已排餐|已经排餐|已退款|已经退款|已核销|已经核销).*")) {
            throw new ToolGuardrailException("WRITE_OPERATION_CLAIM_REJECTED", "answer claims a write operation");
        }
    }

    /** 递归检查字段名、手机号、地址策略和不可信自由文本。 */
    private void inspect(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            String value = node.asText();
            if (value.length() > MAX_UNTRUSTED_TEXT_LENGTH) {
                throw new ToolGuardrailException("SENSITIVE_DATA_REJECTED", "tool output contains oversized free text");
            }
            if (PHONE.matcher(value).find()) throw new ToolGuardrailException("SENSITIVE_DATA_REJECTED", "tool output contains phone number");
            if (INJECTION.matcher(value).find()) throw new ToolGuardrailException("UNTRUSTED_TEXT_REJECTED", "tool output contains instruction-like text");
            if (fieldName != null && fieldName.toLowerCase(Locale.ROOT).contains("address")
                && !fieldName.toLowerCase(Locale.ROOT).startsWith("masked")) {
                throw new ToolGuardrailException("SENSITIVE_DATA_REJECTED", "tool output contains unmasked address");
            }
            return;
        }
        if (node.isArray()) {
            ArrayNode values = (ArrayNode) node;
            for (JsonNode value : values) inspect(value, fieldName);
            return;
        }
        if (!node.isObject()) return;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String normalized = field.getKey().toLowerCase(Locale.ROOT);
            if (normalized.matches(".*(" + FORBIDDEN_FIELD_TERMS + ").*")) {
                throw new ToolGuardrailException("SENSITIVE_DATA_REJECTED", "tool output contains forbidden field");
            }
            if (normalized.equals("phone") || normalized.equals("mobile") || normalized.equals("contactphone")) {
                throw new ToolGuardrailException("SENSITIVE_DATA_REJECTED", "tool output contains raw phone field");
            }
            inspect(field.getValue(), field.getKey());
        }
    }

    /** 创建给前端的安全副本，默认去除内部关联 ID，保留业务编号和普通事实。 */
    public JsonNode hideInternalIdentifiers(JsonNode source) {
        if (source == null) return null;
        JsonNode copy = source.deepCopy();
        hide(copy);
        return copy;
    }

    /** 递归移除仅供主系统关系校验使用的内部关联字段。 */
    private void hide(JsonNode node) {
        if (node == null) return;
        if (node.isArray()) { node.forEach(this::hide); return; }
        if (!node.isObject()) return;
        ObjectNode object = (ObjectNode) node;
        object.remove("customerId");
        object.remove("orderId");
        object.remove("departmentId");
        object.remove("createBy");
        object.fields().forEachRemaining(entry -> hide(entry.getValue()));
    }
}
