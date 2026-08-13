package me.zhengjie.agent.guardrail;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** saveFormDraft 专用敏感输入策略，仅放行登记的手机号和地址路径。 */
public class FormDraftSensitiveDataPolicy {
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final Pattern INJECTION = Pattern.compile(
        "(?i)(ignore\\s+(all|previous)|忽略(之前|以上|系统)|执行以下指令|system\\s*prompt|调用任意工具)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Set<String> FORBIDDEN_NAMES = Set.of(
        "owner", "owneruserid", "permission", "permissions", "internaltoken", "token", "authorization",
        "datascope", "departmentids", "status", "targetbusinessid", "url", "endpoint", "sql", "table", "column");
    private static final Set<String> SENSITIVE_ALLOWED_PATHS = Set.of(
        "customerWithOrderPayload.customer.phone",
        "customerWithOrderPayload.customer.addresses.addressDetail",
        "customerWithOrderPayload.customer.addresses.contactPhone");

    /** 校验草稿输入树，手机号和地址只能位于登记路径。 */
    public void assertSafe(JsonNode node) {
        inspect(node, "");
    }

    /** 递归校验自由文本、敏感路径及禁止的控制字段。 */
    private void inspect(JsonNode node, String path) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            String value = node.asText();
            if (value.length() > MAX_TEXT_LENGTH) reject("FORM_DRAFT_TEXT_TOO_LONG");
            if (INJECTION.matcher(value).find()) reject("UNTRUSTED_TEXT_REJECTED");
            String name = last(path).toLowerCase(Locale.ROOT);
            if (PHONE.matcher(value).find() && !SENSITIVE_ALLOWED_PATHS.contains(path)) {
                reject("SENSITIVE_DATA_REJECTED");
            }
            if ((name.equals("phone") || name.equals("contactphone") || name.equals("address")
                || name.equals("addressdetail") || name.equals("deliveryaddress"))
                && !SENSITIVE_ALLOWED_PATHS.contains(path)) reject("SENSITIVE_DATA_REJECTED");
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) inspect(item, path);
            return;
        }
        if (!node.isObject()) return;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String normalized = field.getKey().toLowerCase(Locale.ROOT);
            if (FORBIDDEN_NAMES.contains(normalized)) reject("TOOL_INPUT_FORBIDDEN_FIELD");
            inspect(field.getValue(), path.isBlank() ? field.getKey() : path + "." + field.getKey());
        }
    }

    /** 返回路径末级字段名。 */
    private String last(String path) {
        int index = path.lastIndexOf('.');
        return index < 0 ? path : path.substring(index + 1);
    }

    /** 抛出稳定草稿输入错误。 */
    private void reject(String code) {
        throw new ToolGuardrailException(code, "form draft input rejected by security policy");
    }
}
