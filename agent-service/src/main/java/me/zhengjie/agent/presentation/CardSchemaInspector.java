package me.zhengjie.agent.presentation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 从安全卡片中提取只含结构的摘要。
 *
 * <p>该类从不把 JsonNode 的业务值写入结果。字段名也会经过敏感字段过滤，
 * 并通过深度、路径数和数组样本上限限制异常卡片对规划 prompt 的放大。</p>
 */
public class CardSchemaInspector {
    public static final int DEFAULT_MAX_DEPTH = 6;
    public static final int DEFAULT_MAX_PATHS = 80;
    private static final Set<String> ENVELOPE_FIELDS = Set.of(
        "schemaversion", "total", "page", "size", "truncated", "queriedat", "warnings", "errorcode");
    private static final Set<String> SENSITIVE_TERMS = Set.of(
        "customerid", "orderid", "dishid", "packageid", "userid", "phone", "mobile", "address",
        "amount", "price", "money", "payment", "token", "secret", "password", "permission",
        "authorization", "cookie", "sql", "jdbc", "credential", "apikey", "api_key");

    private final int maxDepth;
    private final int maxPaths;

    /** 使用受控默认边界创建结构检查器。 */
    public CardSchemaInspector() { this(DEFAULT_MAX_DEPTH, DEFAULT_MAX_PATHS); }

    /** 创建自定义边界的结构检查器，供边界测试使用。 */
    public CardSchemaInspector(int maxDepth, int maxPaths) {
        if (maxDepth < 1 || maxPaths < 1) throw new IllegalArgumentException("CARD_SCHEMA_LIMIT_INVALID");
        this.maxDepth = maxDepth;
        this.maxPaths = maxPaths;
    }

    /** 提取未知卡片的结构摘要；cardType 只用于日志调用方，不进入摘要。 */
    public SchemaSummary inspect(String cardType, JsonNode safeCardData) {
        return inspect(safeCardData);
    }

    /** 递归提取路径、字段名、类型、形态和完整性标记，不保留任何字段值。 */
    public SchemaSummary inspect(JsonNode safeCardData) {
        List<SchemaField> fields = new ArrayList<>();
        Set<String> paths = new LinkedHashSet<>();
        Set<String> warnings = new LinkedHashSet<>();
        if (safeCardData == null || safeCardData.isNull()) {
            warnings.add("SCHEMA_EMPTY_ROOT");
            return new SchemaSummary(fields, false, false, List.copyOf(warnings));
        }
        collect(safeCardData, "", 0, fields, paths, warnings);
        boolean truncated = safeCardData.path("truncated").asBoolean(false)
            || safeCardData.path("data").path("truncated").asBoolean(false);
        if (safeCardData.path("warnings").isArray() && !safeCardData.path("warnings").isEmpty()) truncated = true;
        if (safeCardData.path("data").path("warnings").isArray()
            && !safeCardData.path("data").path("warnings").isEmpty()) truncated = true;
        boolean complete = !truncated && warnings.isEmpty();
        return new SchemaSummary(List.copyOf(fields), truncated, complete, List.copyOf(warnings));
    }

    private void collect(JsonNode node, String path, int depth, List<SchemaField> fields,
                         Set<String> paths, Set<String> warnings) {
        if (node == null || node.isNull()) return;
        if (depth > maxDepth) {
            warnings.add("SCHEMA_DEPTH_LIMIT_REACHED");
            return;
        }
        if (node.isObject()) {
            Iterator<java.util.Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                java.util.Map.Entry<String, JsonNode> entry = iterator.next();
                String name = entry.getKey();
                String normalized = name.toLowerCase(Locale.ROOT);
                if (!name.matches("[A-Za-z][A-Za-z0-9_]*")) {
                    warnings.add("SCHEMA_UNSAFE_FIELD_OMITTED");
                    continue;
                }
                if (isForbidden(normalized)) {
                    warnings.add("SCHEMA_SENSITIVE_FIELD_OMITTED");
                    continue;
                }
                if (path.isEmpty() && ENVELOPE_FIELDS.contains(normalized)) continue;
                String childPath = path.isEmpty() ? name : path + "." + name;
                if (!addField(childPath, name, entry.getValue(), fields, paths, warnings)) return;
                collect(entry.getValue(), childPath, depth + 1, fields, paths, warnings);
            }
            return;
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                warnings.add("SCHEMA_EMPTY_ARRAY");
                return;
            }
            String itemPath = path.endsWith("[]") ? path : path + "[]";
            int sampleCount = 0;
            for (JsonNode item : node) {
                collect(item, itemPath, depth + 1, fields, paths, warnings);
                if (++sampleCount >= 3) break;
            }
        }
    }

    private boolean addField(String path, String fieldName, JsonNode node, List<SchemaField> fields,
                             Set<String> paths, Set<String> warnings) {
        if (paths.contains(path)) return true;
        if (paths.size() >= maxPaths) {
            warnings.add("SCHEMA_PATH_LIMIT_REACHED");
            return false;
        }
        String type = node == null || node.isNull() ? "NULL"
            : node.isTextual() ? "TEXT" : node.isNumber() ? "NUMBER"
            : node.isBoolean() ? "BOOLEAN" : node.isArray() ? "ARRAY" : node.isObject() ? "OBJECT" : "UNKNOWN";
        String shape = node != null && node.isArray() ? "ARRAY"
            : node != null && node.isObject() ? "OBJECT" : "SCALAR";
        fields.add(new SchemaField(path, fieldName, type, shape));
        paths.add(path);
        return true;
    }

    private boolean isForbidden(String normalized) {
        return normalized.endsWith("id") || normalized.endsWith("ids")
            || SENSITIVE_TERMS.stream().anyMatch(normalized::contains);
    }

    /** 单个结构字段；不包含 JsonNode 或业务值。 */
    public record SchemaField(String path, String fieldName, String type, String shape) { }

    /** 结构摘要及其完整性状态。 */
    public record SchemaSummary(List<SchemaField> paths, boolean truncated, boolean complete,
                                List<String> warnings) {
        public SchemaSummary {
            paths = paths == null ? List.of() : List.copyOf(paths);
            warnings = warnings == null ? List.of() : List.copyOf(new LinkedHashSet<>(warnings));
        }

        /** 兼容调用方使用 fields 语义读取路径列表。 */
        public List<SchemaField> fields() { return paths; }
    }
}
