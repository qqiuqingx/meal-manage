package me.zhengjie.agent.presentation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static me.zhengjie.agent.presentation.PresentationDescriptor.Field;
import static me.zhengjie.agent.presentation.PresentationDescriptor.Format;
import static me.zhengjie.agent.presentation.PresentationDescriptor.View;

/**
 * 生成未知卡片的通用展示描述。
 *
 * <p>工厂只从安全结构和安全卡片的节点形态生成有限表格、键值摘要或文本视图，
 * 不做分组、求和、占比等业务计算。</p>
 */
public class GenericPresentationFactory {
    private final PresentationSuggestionValidator validator;

    /** 使用默认候选校验器创建通用展示工厂。 */
    public GenericPresentationFactory() { this(new PresentationSuggestionValidator()); }

    /** 创建可复用的通用展示工厂。 */
    public GenericPresentationFactory(PresentationSuggestionValidator validator) {
        if (validator == null) throw new IllegalArgumentException("PRESENTATION_VALIDATOR_REQUIRED");
        this.validator = validator;
    }

    /** 在规划失败时生成安全数组表格、对象摘要或文本提示。 */
    public PresentationDescriptor createFallback(String sourceToolCallId, String cardType, JsonNode safeCardData,
                                                 CardSchemaInspector.SchemaSummary schema) {
        if (schema != null) {
            for (CardSchemaInspector.SchemaField candidate : schema.paths()) {
                if (!"ARRAY".equals(candidate.shape())) continue;
                List<CardSchemaInspector.SchemaField> fields = validator.directFields(schema, candidate.path());
                if (fields.isEmpty() || arraySize(safeCardData, candidate.path()) == 0) continue;
                return table(sourceToolCallId, cardType, "数据列表", candidate.path(), fields);
            }
            for (CardSchemaInspector.SchemaField candidate : schema.paths()) {
                if (!"OBJECT".equals(candidate.shape())) continue;
                List<CardSchemaInspector.SchemaField> fields = validator.directFields(schema, candidate.path());
                if (!fields.isEmpty()) return summary(sourceToolCallId, cardType, "数据摘要", candidate.path(), fields);
            }
        }
        return new PresentationDescriptor("v1", sourceToolCallId, cardType,
            PresentationDescriptor.DecisionSource.SYSTEM, "展示格式暂不可用", PresentationDescriptor.Layout.TABS,
            View.TEXT, List.of(View.TEXT), new PresentationDescriptor.Summary("data", List.of()), null, null);
    }

    /** 将经过 validator 校验的候选转换为 v1 描述；不再读取任何业务值。 */
    public PresentationDescriptor createFromSuggestion(String sourceToolCallId, String cardType,
                                                        PresentationSuggestionValidator.ValidationResult result) {
        if (result == null || !result.valid() || result.suggestion() == null) {
            throw new IllegalArgumentException("PRESENTATION_SUGGESTION_INVALID");
        }
        PresentationSuggestion suggestion = result.suggestion();
        View view = suggestion.parsedView();
        List<Field> fields = toFields(result.fields());
        if (view == View.TEXT) {
            return new PresentationDescriptor("v1", sourceToolCallId, cardType,
                PresentationDescriptor.DecisionSource.LLM, suggestion.title(), PresentationDescriptor.Layout.TABS,
                View.TEXT, List.of(View.TEXT), new PresentationDescriptor.Summary(suggestion.dataPath(), fields), null, null);
        }
        if (view == View.TABLE) {
            return new PresentationDescriptor("v1", sourceToolCallId, cardType,
                PresentationDescriptor.DecisionSource.LLM, suggestion.title(), PresentationDescriptor.Layout.TABS,
                View.TABLE, List.of(View.TABLE), null,
                new PresentationDescriptor.Table(suggestion.dataPath(), fields, List.of()), null);
        }
        PresentationDescriptor.Chart chart = new PresentationDescriptor.Chart(view, suggestion.dataPath(),
            suggestion.dimensionField(), suggestion.metricFields(), suggestion.dimensionLabel(), suggestion.metricLabels());
        return new PresentationDescriptor("v1", sourceToolCallId, cardType,
            PresentationDescriptor.DecisionSource.LLM, suggestion.title(), PresentationDescriptor.Layout.TABS,
            view, List.of(view), null, null, chart);
    }

    /** 兼容直观命名的通用降级入口。 */
    public PresentationDescriptor create(String sourceToolCallId, String cardType, JsonNode safeCardData,
                                         CardSchemaInspector.SchemaSummary schema) {
        return createFallback(sourceToolCallId, cardType, safeCardData, schema);
    }

    private PresentationDescriptor table(String callId, String cardType, String title, String path,
                                         List<CardSchemaInspector.SchemaField> fields) {
        return new PresentationDescriptor("v1", callId, cardType, PresentationDescriptor.DecisionSource.SYSTEM,
            title, PresentationDescriptor.Layout.TABS, View.TABLE, List.of(View.TABLE), null,
            new PresentationDescriptor.Table(path, toFields(fields), List.of()), null);
    }

    private PresentationDescriptor summary(String callId, String cardType, String title, String path,
                                           List<CardSchemaInspector.SchemaField> fields) {
        return new PresentationDescriptor("v1", callId, cardType, PresentationDescriptor.DecisionSource.SYSTEM,
            title, PresentationDescriptor.Layout.TABS, View.TEXT, List.of(View.TEXT),
            new PresentationDescriptor.Summary(path, toFields(fields)), null, null);
    }

    private List<Field> toFields(List<CardSchemaInspector.SchemaField> fields) {
        List<Field> result = new ArrayList<>();
        for (CardSchemaInspector.SchemaField field : fields) {
            if (result.size() >= PresentationDescriptor.MAX_TABLE_COLUMNS || !safeToken(field.fieldName())) break;
            result.add(new Field(field.fieldName(), label(field.fieldName()), format(field.type())));
        }
        return List.copyOf(result);
    }

    private Format format(String type) {
        return switch (type) {
            case "NUMBER" -> Format.NUMBER;
            case "BOOLEAN" -> Format.BOOLEAN;
            case "DATE" -> Format.DATE;
            case "DATE_TIME" -> Format.DATE_TIME;
            default -> Format.TEXT;
        };
    }

    private String label(String value) {
        return value.length() <= PresentationDescriptor.MAX_LABEL_LENGTH
            ? value : value.substring(0, PresentationDescriptor.MAX_LABEL_LENGTH);
    }

    private boolean safeToken(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9_]*"); }

    private int arraySize(JsonNode root, String path) {
        JsonNode node = root;
        if (node == null || path == null) return 0;
        for (String segment : path.split("\\.")) {
            node = node.path(segment.endsWith("[]") ? segment.substring(0, segment.length() - 2) : segment);
            if (segment.endsWith("[]") && !node.isArray()) return 0;
        }
        return node.isArray() ? node.size() : 0;
    }
}
