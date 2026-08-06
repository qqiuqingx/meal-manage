package me.zhengjie.agent.presentation;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * v1 卡片展示描述。
 *
 * <p>该对象只保存安全卡片中的路径、字段语义和格式，不保存任何业务值，也不包含
 * HTML、Markdown、组件名或图表库配置。</p>
 */
public record PresentationDescriptor(
    String schemaVersion,
    String sourceToolCallId,
    String cardType,
    DecisionSource decisionSource,
    String title,
    Layout layout,
    View defaultView,
    List<View> availableViews,
    Summary summary,
    Table table,
    Chart chart) {

    private static final Pattern PATH = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\[\\])?(\\.[A-Za-z][A-Za-z0-9_]*(\\[\\])?)*");
    private static final Pattern PLAIN_TEXT = Pattern.compile("^[^<>{}]*$");
    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_LABEL_LENGTH = 30;
    public static final int MAX_TABLE_COLUMNS = 20;
    public static final int MAX_CHART_METRICS = 4;
    public static final int MAX_SECTIONS = 8;

    /** 校验实际对外展示描述的 v1 固定枚举、路径和数量边界。 */
    public PresentationDescriptor {
        requireText(schemaVersion, "schemaVersion");
        if (!"v1".equals(schemaVersion)) throw invalid("PRESENTATION_SCHEMA_VERSION_INVALID");
        requireText(sourceToolCallId, "sourceToolCallId");
        requireText(cardType, "cardType");
        if (decisionSource == null) throw invalid("PRESENTATION_DECISION_SOURCE_INVALID");
        requireLabel(title, "title", MAX_TITLE_LENGTH);
        if (layout != Layout.TABS) throw invalid("PRESENTATION_LAYOUT_INVALID");
        availableViews = availableViews == null ? List.of() : List.copyOf(availableViews);
        if (availableViews.isEmpty() || availableViews.stream().anyMatch(Objects::isNull)
            || availableViews.stream().distinct().count() != availableViews.size()) {
            throw invalid("PRESENTATION_VIEW_INVALID");
        }
        if (defaultView == null || !availableViews.contains(defaultView)) {
            throw invalid("PRESENTATION_DEFAULT_VIEW_INVALID");
        }
        validateSummary(summary);
        validateTable(table);
        validateChart(chart);
    }

    /** 展示决策来源；Phase 02 的已知卡片全部使用 SYSTEM。 */
    public enum DecisionSource { SYSTEM, LLM }

    /** v1 固定布局。 */
    public enum Layout { TABS }

    /** v1 固定视图白名单。 */
    public enum View { TEXT, TABLE, BAR, LINE, PIE }

    /** v1 固定字段格式化类型。 */
    public enum Format { TEXT, DATE, DATE_TIME, STATUS, MEAL_TYPE, NUMBER, BOOLEAN }

    /** 摘要字段描述，只引用安全数据路径。 */
    public record Summary(String dataPath, List<Field> fields) {
        public Summary {
            requirePath(dataPath, "summary.dataPath");
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    /** 表格或摘要中的单个受控字段。 */
    public record Field(String field, String label, Format format) {
        public Field {
            requirePath(field, "field");
            requireLabel(label, "label", MAX_LABEL_LENGTH);
            if (format == null) throw invalid("PRESENTATION_FORMAT_INVALID");
        }
    }

    /** 表格描述；sections 只允许一层受控子表。 */
    public record Table(String dataPath, List<Field> columns, List<Section> sections) {
        public Table {
            requirePath(dataPath, "table.dataPath");
            columns = columns == null ? List.of() : List.copyOf(columns);
            sections = sections == null ? List.of() : List.copyOf(sections);
        }
    }

    /** 详情卡的受控子表，不允许递归 sections。 */
    public record Section(String id, String title, String dataPath, List<Field> columns) {
        public Section {
            requireText(id, "section.id");
            requireLabel(title, "section.title", MAX_TITLE_LENGTH);
            requirePath(dataPath, "section.dataPath");
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    /** 图表描述，只保存维度和指标字段引用，不保存 ECharts option 或业务数据。 */
    public record Chart(View type, String dataPath, String dimensionField, List<String> metricFields,
                        String dimensionLabel, List<String> metricLabels) {
        public Chart {
            if (type != View.BAR && type != View.LINE && type != View.PIE) {
                throw invalid("PRESENTATION_CHART_TYPE_INVALID");
            }
            requirePath(dataPath, "chart.dataPath");
            requirePath(dimensionField, "chart.dimensionField");
            metricFields = metricFields == null ? List.of() : List.copyOf(metricFields);
            metricLabels = metricLabels == null ? List.of() : List.copyOf(metricLabels);
            metricFields.forEach(value -> requirePath(value, "chart.metricField"));
            requireLabel(dimensionLabel, "chart.dimensionLabel", MAX_LABEL_LENGTH);
            metricLabels.forEach(value -> requireLabel(value, "chart.metricLabel", MAX_LABEL_LENGTH));
        }
    }

    private static void validateSummary(Summary value) {
        if (value == null) return;
        value.fields().forEach(PresentationDescriptor::validateField);
    }

    private static void validateTable(Table value) {
        if (value == null) return;
        if (value.columns().size() > MAX_TABLE_COLUMNS) throw invalid("PRESENTATION_TABLE_COLUMN_LIMIT");
        if (value.sections().size() > MAX_SECTIONS) throw invalid("PRESENTATION_SECTION_LIMIT");
        value.columns().forEach(PresentationDescriptor::validateField);
        value.sections().forEach(section -> {
            if (section.columns().size() > MAX_TABLE_COLUMNS) throw invalid("PRESENTATION_TABLE_COLUMN_LIMIT");
            section.columns().forEach(PresentationDescriptor::validateField);
        });
    }

    private static void validateChart(Chart value) {
        if (value == null) return;
        if (value.metricFields().isEmpty() || value.metricFields().size() > MAX_CHART_METRICS
            || value.metricLabels().size() != value.metricFields().size()) {
            throw invalid("PRESENTATION_CHART_METRIC_LIMIT");
        }
    }

    private static void validateField(Field value) {
        if (value == null) throw invalid("PRESENTATION_FIELD_INVALID");
    }

    /** 检查路径是否为固定字段路径，而不是表达式或动态代码。 */
    public static void requirePath(String value, String name) {
        requireText(value, name);
        if (!PATH.matcher(value).matches()) throw invalid("PRESENTATION_PATH_INVALID");
    }

    /** 检查标题和字段标签为普通文本并满足长度上限。 */
    public static void requireLabel(String value, String name, int maxLength) {
        requireText(value, name);
        if (value.length() > maxLength || !PLAIN_TEXT.matcher(value).matches()) {
            throw invalid("PRESENTATION_LABEL_INVALID");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw invalid("PRESENTATION_REQUIRED_FIELD: " + name);
    }

    private static IllegalArgumentException invalid(String code) {
        return new IllegalArgumentException(code);
    }
}
