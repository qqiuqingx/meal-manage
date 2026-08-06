package me.zhengjie.agent.presentation;

import me.zhengjie.agent.tool.ToolRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static me.zhengjie.agent.presentation.PresentationDescriptor.Format;
import static me.zhengjie.agent.presentation.PresentationDescriptor.View;

/**
 * 启动期展示规则校验器。
 *
 * <p>规则错误是配置错误，必须阻止服务启动；当前工具没有规则则只形成稳定健康告警，
 * 由后续 LLM 兜底阶段处理。</p>
 */
public class PresentationRuleValidator {
    private final PresentationFieldCatalog catalog;

    /** 使用安全字段目录创建校验器。 */
    public PresentationRuleValidator(PresentationFieldCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("PRESENTATION_CATALOG_REQUIRED");
        this.catalog = catalog;
    }

    /** 校验全部规则的唯一性、工具卡片覆盖和字段引用。 */
    public void validateAll(List<PresentationRule> rules, ToolRegistry toolRegistry) {
        if (rules == null || toolRegistry == null) throw invalid("PRESENTATION_RULES_INVALID");
        Set<String> cardTypes = new HashSet<>();
        Set<String> registeredCardTypes = new HashSet<>();
        toolRegistry.all().forEach(spec -> registeredCardTypes.add(spec.cardType()));
        for (PresentationRule rule : rules) {
            if (rule == null || rule.cardType() == null || !cardTypes.add(rule.cardType())) {
                throw invalid("PRESENTATION_RULE_DUPLICATE");
            }
            if (!registeredCardTypes.contains(rule.cardType())) throw invalid("PRESENTATION_RULE_UNKNOWN_CARD_TYPE");
            validateTemplate(rule.cardType(), rule.template());
            for (PresentationRule.Template variant : rule.variants().values()) {
                validateTemplate(rule.cardType(), variant);
            }
        }
    }

    /** 校验单条规则，供配置单元测试和扩展规则加载器使用。 */
    public void validate(PresentationRule rule) {
        if (rule == null || rule.cardType() == null) throw invalid("PRESENTATION_RULE_INVALID");
        validateTemplate(rule.cardType(), rule.template());
        rule.variants().values().forEach(template -> validateTemplate(rule.cardType(), template));
    }

    /** 校验展示模板的视图、数量、路径和字段类型。 */
    public void validateTemplate(String cardType, PresentationRule.Template template) {
        if (template == null || template.layout() != PresentationDescriptor.Layout.TABS
            || template.defaultView() == null || template.availableViews() == null
            || template.availableViews().isEmpty() || template.availableViews().stream().anyMatch(view -> view == null)
            || template.availableViews().stream().distinct().count() != template.availableViews().size()
            || !template.availableViews().contains(template.defaultView())) {
            throw invalid("PRESENTATION_VIEW_INVALID");
        }
        PresentationDescriptor.requireLabel(template.title(), "title", PresentationDescriptor.MAX_TITLE_LENGTH);
        if (template.availableViews().contains(View.TABLE) && template.table() == null) {
            throw invalid("PRESENTATION_TABLE_REQUIRED");
        }
        if (template.availableViews().contains(View.TEXT) && template.summary() == null) {
            throw invalid("PRESENTATION_SUMMARY_REQUIRED");
        }
        if (template.availableViews().contains(View.BAR) || template.availableViews().contains(View.LINE)
            || template.availableViews().contains(View.PIE)) {
            if (template.chart() == null) throw invalid("PRESENTATION_CHART_REQUIRED");
        }
        validateSummary(cardType, template.summary());
        validateTable(cardType, template.table());
        validateChart(cardType, template.chart());
    }

    private void validateSummary(String cardType, PresentationDescriptor.Summary summary) {
        if (summary == null) return;
        requirePath(cardType, summary.dataPath());
        summary.fields().forEach(field -> requireField(cardType, summary.dataPath(), field));
    }

    private void validateTable(String cardType, PresentationDescriptor.Table table) {
        if (table == null) return;
        requirePath(cardType, table.dataPath());
        if (table.columns().size() > PresentationDescriptor.MAX_TABLE_COLUMNS) throw invalid("PRESENTATION_TABLE_COLUMN_LIMIT");
        table.columns().forEach(field -> requireField(cardType, table.dataPath(), field));
        if (table.sections().size() > PresentationDescriptor.MAX_SECTIONS) throw invalid("PRESENTATION_SECTION_LIMIT");
        table.sections().forEach(section -> {
            requirePath(cardType, section.dataPath());
            if (section.columns().size() > PresentationDescriptor.MAX_TABLE_COLUMNS) throw invalid("PRESENTATION_TABLE_COLUMN_LIMIT");
            section.columns().forEach(field -> requireField(cardType, section.dataPath(), field));
        });
    }

    private void validateChart(String cardType, PresentationDescriptor.Chart chart) {
        if (chart == null) return;
        requirePath(cardType, chart.dataPath());
        if (chart.metricFields().isEmpty()
            || chart.metricFields().size() > PresentationDescriptor.MAX_CHART_METRICS
            || chart.metricLabels().size() != chart.metricFields().size()) {
            throw invalid("PRESENTATION_CHART_METRIC_LIMIT");
        }
        PresentationFieldCatalog.FieldDefinition dimension = catalog.requireField(cardType, chart.dataPath(), chart.dimensionField());
        if (!dimension.chartAllowed() || dimension.type() != PresentationFieldCatalog.Type.TEXT) {
            throw invalid("PRESENTATION_CHART_DIMENSION_INVALID");
        }
        for (String metric : chart.metricFields()) {
            PresentationFieldCatalog.FieldDefinition field = catalog.requireField(cardType, chart.dataPath(), metric);
            if (!field.chartAllowed() || field.type() != PresentationFieldCatalog.Type.NUMBER) {
                throw invalid("PRESENTATION_CHART_METRIC_INVALID");
            }
        }
    }

    private void requirePath(String cardType, String dataPath) {
        if (!catalog.hasPath(cardType, dataPath)) throw invalid("PRESENTATION_UNKNOWN_PATH");
    }

    private void requireField(String cardType, String dataPath, PresentationDescriptor.Field field) {
        if (field == null || !catalog.hasField(cardType, dataPath, field.field())) {
            throw invalid("PRESENTATION_UNKNOWN_FIELD");
        }
        PresentationFieldCatalog.FieldDefinition definition = catalog.requireField(cardType, dataPath, field.field());
        if (!compatible(field.format(), definition.type())) throw invalid("PRESENTATION_FIELD_FORMAT_INVALID");
    }

    private boolean compatible(Format format, PresentationFieldCatalog.Type type) {
        if (format == null) return false;
        return switch (format) {
            case TEXT -> type == PresentationFieldCatalog.Type.TEXT;
            case DATE -> type == PresentationFieldCatalog.Type.DATE || type == PresentationFieldCatalog.Type.DATE_TIME;
            case DATE_TIME -> type == PresentationFieldCatalog.Type.DATE_TIME || type == PresentationFieldCatalog.Type.DATE;
            case STATUS -> type == PresentationFieldCatalog.Type.STATUS || type == PresentationFieldCatalog.Type.TEXT;
            case MEAL_TYPE -> type == PresentationFieldCatalog.Type.MEAL_TYPE || type == PresentationFieldCatalog.Type.TEXT;
            case NUMBER -> type == PresentationFieldCatalog.Type.NUMBER;
            case BOOLEAN -> type == PresentationFieldCatalog.Type.BOOLEAN;
        };
    }

    private IllegalStateException invalid(String code) { return new IllegalStateException(code); }
}
