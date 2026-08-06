package me.zhengjie.agent.presentation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 对模型展示候选执行后端二次校验。
 *
 * <p>校验只依据结构摘要和安全卡片的形态，不读取或复制业务值。图表还必须满足完整性、
 * 数量上限和字段类型约束；饼图超限按设计降为柱状图。</p>
 */
public class PresentationSuggestionValidator {
    private static final int MAX_BAR_LINE_CATEGORIES = 50;
    private static final int MAX_PIE_CATEGORIES = 8;

    /** 校验候选并返回可供工厂转换的稳定结果，不向调用方抛出模型原文。 */
    public ValidationResult validate(PresentationSuggestion suggestion,
                                     CardSchemaInspector.SchemaSummary schema, JsonNode safeCardData) {
        if (suggestion == null || schema == null) return invalid("PRESENTATION_SUGGESTION_INVALID");
        PresentationDescriptor.View view = suggestion.parsedView();
        if (view == null) {
            return invalid("PRESENTATION_VIEW_INVALID");
        }
        try {
            PresentationDescriptor.requireLabel(suggestion.title(), "title", PresentationDescriptor.MAX_TITLE_LENGTH);
        } catch (RuntimeException exception) {
            return invalid("PRESENTATION_LABEL_INVALID");
        }
        CardSchemaInspector.SchemaField container = findPath(schema, suggestion.dataPath());
        if (container == null || !("ARRAY".equals(container.shape()) || "OBJECT".equals(container.shape()))
            || suggestion.dataPath() == null) return invalid("PRESENTATION_UNKNOWN_PATH");

        if (view == PresentationDescriptor.View.BAR || view == PresentationDescriptor.View.LINE
            || view == PresentationDescriptor.View.PIE) {
            if (!schema.complete() || schema.truncated() || !schema.warnings().isEmpty()) {
                return invalid("PRESENTATION_DATA_INCOMPLETE");
            }
            if (!"ARRAY".equals(container.shape())) return invalid("PRESENTATION_CHART_DATA_INVALID");
            CardSchemaInspector.SchemaField dimension = directField(schema, suggestion.dataPath(), suggestion.dimensionField());
            if (dimension == null || !chartDimension(dimension.type())) return invalid("PRESENTATION_CHART_DIMENSION_INVALID");
            List<String> metrics = suggestion.metricFields();
            if (metrics == null || metrics.isEmpty() || metrics.size() > PresentationDescriptor.MAX_CHART_METRICS) {
                return invalid("PRESENTATION_CHART_METRIC_LIMIT");
            }
            for (String metric : metrics) {
                CardSchemaInspector.SchemaField field = directField(schema, suggestion.dataPath(), metric);
                if (field == null || !"NUMBER".equals(field.type())) return invalid("PRESENTATION_CHART_METRIC_INVALID");
            }
            List<String> metricLabels = labels(metrics, suggestion.metricLabels());
            if (metricLabels == null) return invalid("PRESENTATION_LABEL_INVALID");
            String dimensionLabel;
            try {
                dimensionLabel = labelOrField(suggestion.dimensionLabel(), dimension.fieldName());
            } catch (RuntimeException exception) {
                return invalid("PRESENTATION_LABEL_INVALID");
            }
            int count = arraySize(safeCardData, suggestion.dataPath());
            if (count == 0) return invalid("PRESENTATION_CHART_DATA_EMPTY");
            PresentationSuggestion normalized = new PresentationSuggestion(view.name(), suggestion.title(),
                suggestion.dataPath(), dimension.fieldName(), metrics, dimensionLabel, metricLabels);
            if (view == PresentationDescriptor.View.PIE && count > MAX_PIE_CATEGORIES) {
                if (count > MAX_BAR_LINE_CATEGORIES) return invalid("PRESENTATION_CHART_CATEGORY_LIMIT");
                normalized = new PresentationSuggestion("BAR", suggestion.title(), suggestion.dataPath(),
                    dimension.fieldName(), metrics, dimensionLabel, metricLabels);
            } else if (count > MAX_BAR_LINE_CATEGORIES) {
                return invalid("PRESENTATION_CHART_CATEGORY_LIMIT");
            }
            return valid(normalized, directFields(schema, suggestion.dataPath()));
        }

        if (view != PresentationDescriptor.View.TEXT && view != PresentationDescriptor.View.TABLE) {
            return invalid("PRESENTATION_VIEW_INVALID");
        }
        List<CardSchemaInspector.SchemaField> fields = directFields(schema, suggestion.dataPath());
        if (view == PresentationDescriptor.View.TABLE && !"ARRAY".equals(container.shape())) {
            return invalid("PRESENTATION_TABLE_DATA_INVALID");
        }
        if (fields.isEmpty() && view == PresentationDescriptor.View.TABLE) return invalid("PRESENTATION_NO_SAFE_FIELDS");
        return valid(new PresentationSuggestion(view.name(), suggestion.title(), suggestion.dataPath(), null,
            List.of(), null, List.of()), fields);
    }

    /** 校验失败时抛出稳定错误码，供独立 planner 测试和严格调用方使用。 */
    public ValidationResult validateOrThrow(PresentationSuggestion suggestion,
                                             CardSchemaInspector.SchemaSummary schema, JsonNode safeCardData) {
        ValidationResult result = validate(suggestion, schema, safeCardData);
        if (!result.valid()) throw new IllegalArgumentException(result.reason());
        return result;
    }

    /** 返回数据路径下的安全直接字段，排除对象、数组和内部字段。 */
    public List<CardSchemaInspector.SchemaField> directFields(CardSchemaInspector.SchemaSummary schema, String dataPath) {
        if (schema == null || dataPath == null) return List.of();
        String prefix = dataPath + (hasArrayContainer(schema, dataPath) ? "[]." : ".");
        List<CardSchemaInspector.SchemaField> result = new ArrayList<>();
        for (CardSchemaInspector.SchemaField field : schema.paths()) {
            if (field.path().startsWith(prefix) && !field.path().substring(prefix.length()).contains(".")) {
                if ("SCALAR".equals(field.shape()) && !result.contains(field)) result.add(field);
            }
        }
        return List.copyOf(result);
    }

    private CardSchemaInspector.SchemaField directField(CardSchemaInspector.SchemaSummary schema, String path, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) return null;
        return directFields(schema, path).stream().filter(field -> field.fieldName().equals(fieldName)).findFirst().orElse(null);
    }

    private boolean hasArrayContainer(CardSchemaInspector.SchemaSummary schema, String path) {
        CardSchemaInspector.SchemaField field = findPath(schema, path);
        return field != null && "ARRAY".equals(field.shape());
    }

    private CardSchemaInspector.SchemaField findPath(CardSchemaInspector.SchemaSummary schema, String path) {
        if (path == null) return null;
        return schema.paths().stream().filter(field -> path.equals(field.path())).findFirst().orElse(null);
    }

    private boolean chartDimension(String type) {
        return "TEXT".equals(type) || "STATUS".equals(type) || "DATE".equals(type) || "DATE_TIME".equals(type);
    }

    private List<String> labels(List<String> fields, List<String> values) {
        if (values != null && !values.isEmpty() && values.size() != fields.size()) return null;
        List<String> result = new ArrayList<>();
        for (int index = 0; index < fields.size(); index++) {
            String value = values == null || values.isEmpty() ? fields.get(index) : values.get(index);
            try { PresentationDescriptor.requireLabel(value, "metricLabel", PresentationDescriptor.MAX_LABEL_LENGTH); }
            catch (RuntimeException exception) { return null; }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private String labelOrField(String value, String field) {
        String result = value == null || value.isBlank() ? field : value;
        PresentationDescriptor.requireLabel(result, "dimensionLabel", PresentationDescriptor.MAX_LABEL_LENGTH);
        return result;
    }

    private int arraySize(JsonNode root, String path) {
        JsonNode node = root;
        if (node == null || path == null) return 0;
        for (String segment : path.split("\\.")) {
            if (segment.endsWith("[]")) {
                node = node.path(segment.substring(0, segment.length() - 2));
                if (!node.isArray()) return 0;
            } else {
                node = node.path(segment);
            }
        }
        return node.isArray() ? node.size() : 0;
    }

    private ValidationResult valid(PresentationSuggestion suggestion, List<CardSchemaInspector.SchemaField> fields) {
        return new ValidationResult(true, "PRESENTATION_VALID", suggestion, fields);
    }

    private ValidationResult invalid(String reason) { return new ValidationResult(false, reason, null, List.of()); }

    /** 后端校验结果；reason 只能是稳定码。 */
    public record ValidationResult(boolean valid, String reason, PresentationSuggestion suggestion,
                                   List<CardSchemaInspector.SchemaField> fields) {
        public ValidationResult {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }
}
