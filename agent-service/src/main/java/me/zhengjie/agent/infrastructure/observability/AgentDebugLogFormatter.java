package me.zhengjie.agent.infrastructure.observability;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Pattern;

/**
 * 统一格式化 Agent 调试日志正文。
 *
 * <p>日志正文用于定位模型和工具调用问题，但不能把访问令牌、密码或完整手机号写入日志；
 * 同时将换行转义并限制长度，避免日志注入和单条日志无限增长。</p>
 */
public final class AgentDebugLogFormatter {
    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
        "(?i)(\"(?:phone|mobile|contactPhone|address|addressDetail|deliveryAddress)\"\\s*:\\s*\")[^\"]*(\")");
    private static final Pattern SECRET_FIELD = Pattern.compile(
        "(?i)(\"(?:authorization|token|accessToken|internalToken|password|secret|apiKey)\"\\s*:\\s*\")[^\"]*(\")");
    private static final Pattern FORBIDDEN_FIELD = Pattern.compile(
        "(?i)(\"(?:permission|permissions|dataScope|departmentIds|fields|select|sort|orderBy|sql|url|endpoint|table|column)\"\\s*:\\s*\")[^\"]*(\")");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
        "(?i)((?:authorization|token|accessToken|internalToken|password|secret|apiKey)\\s*[=:]\\s*)([^,}\\s]+)");

    private AgentDebugLogFormatter() {
    }

    /**
     * 格式化普通日志文本；关闭正文日志时只返回固定占位符。
     *
     * @param value 待记录的文本
     * @param enabled 是否允许记录正文
     * @return 已脱敏、转义并截断的单行文本
     */
    public static String text(String value, boolean enabled) {
        if (!enabled) return "[content logging disabled]";
        if (value == null) return "";
        String sanitized = redact(value)
            .replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
        return truncate(sanitized);
    }

    /**
     * 将对象序列化为单行调试正文并执行统一脱敏。
     *
     * @param value 待序列化对象
     * @param objectMapper JSON 映射器
     * @param enabled 是否允许记录正文
     * @return JSON 文本或稳定的序列化失败标识
     */
    public static String json(Object value, ObjectMapper objectMapper, boolean enabled) {
        if (!enabled) return "[content logging disabled]";
        if (value == null) return "null";
        try {
            return text(objectMapper.writeValueAsString(value), true);
        } catch (Exception exception) {
            return "[json serialization failed: " + exception.getClass().getSimpleName() + "]";
        }
    }

    /** 执行手机号、密钥字段和常见赋值形式的脱敏。 */
    private static String redact(String value) {
        String result = PHONE.matcher(value).replaceAll(match -> {
            String phone = match.group();
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        });
        result = SENSITIVE_FIELD.matcher(result).replaceAll("$1[REDACTED]$2");
        result = SECRET_FIELD.matcher(result).replaceAll("$1[REDACTED]$2");
        result = FORBIDDEN_FIELD.matcher(result).replaceAll("$1[REDACTED]$2");
        return SECRET_ASSIGNMENT.matcher(result).replaceAll("$1[REDACTED]");
    }

    /** 限制单条日志正文长度，保留长度信息便于判断是否被截断。 */
    private static String truncate(String value) {
        if (value.length() <= MAX_CONTENT_LENGTH) return value;
        return value.substring(0, MAX_CONTENT_LENGTH) + "…[truncated]";
    }
}
