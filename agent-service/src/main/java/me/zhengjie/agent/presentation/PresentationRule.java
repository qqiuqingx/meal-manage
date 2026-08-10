package me.zhengjie.agent.presentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static me.zhengjie.agent.presentation.PresentationDescriptor.Chart;
import static me.zhengjie.agent.presentation.PresentationDescriptor.Layout;
import static me.zhengjie.agent.presentation.PresentationDescriptor.Summary;
import static me.zhengjie.agent.presentation.PresentationDescriptor.Table;
import static me.zhengjie.agent.presentation.PresentationDescriptor.View;

/**
 * 一个 cardType 的确定性展示规则。
 *
 * <p>规则模板只保存展示语义；sourceToolCallId 和业务数据在运行时由
 * {@link PresentationService} 注入/读取。variants 用于同一卡片由多个工具返回不同安全路径的场景。</p>
 */
public final class PresentationRule {
    private final String cardType;
    private final Template template;
    private final Map<String, Template> variants;

    /** 创建只有一个默认模板的系统规则。 */
    public PresentationRule(String cardType, Template template) {
        this(cardType, template, Map.of());
    }

    /** 创建支持按工具名分支的系统规则，但仍只占用一个 cardType 登记位。 */
    public PresentationRule(String cardType, Template template, Map<String, Template> variants) {
        this.cardType = cardType;
        this.template = template;
        this.variants = variants == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(variants));
    }

    public String cardType() { return cardType; }
    public String getCardType() { return cardType; }
    public Template template() { return template; }
    public Template getTemplate() { return template; }
    public Map<String, Template> variants() { return variants; }
    public Map<String, Template> getVariants() { return variants; }

    /** 按工具名选择模板；未知工具使用默认模板。 */
    public Template templateFor(String toolName) {
        return toolName == null ? template : variants.getOrDefault(toolName, template);
    }

    /** 展示模板；不含卡片调用 ID和任何业务值。 */
    public record Template(String title, Layout layout, View defaultView, List<View> availableViews,
                           Summary summary, Table table, Chart chart) {
        public Template {
            availableViews = availableViews == null ? List.of() : List.copyOf(availableViews);
        }

        /** 创建一个表格模板。 */
        public static Template table(String title, String dataPath, List<PresentationDescriptor.Field> columns) {
            return new Template(title, Layout.TABS, View.TABLE, List.of(View.TABLE), null,
                new Table(dataPath, columns, List.of()), null);
        }

        /** 创建摘要模板。 */
        public static Template summary(String title, String dataPath, List<PresentationDescriptor.Field> fields) {
            return new Template(title, Layout.TABS, View.TEXT, List.of(View.TEXT),
                new Summary(dataPath, fields), null, null);
        }

        /** 返回替换表格/图表配置后的不可变模板。 */
        public Template withViews(View defaultView, List<View> views, Table table, Chart chart) {
            return new Template(title, layout, defaultView, views, summary, table, chart);
        }

        /** 返回替换业务标题后的不可变模板，其他视图和字段规则保持不变。 */
        public Template withTitle(String businessTitle) {
            return new Template(businessTitle, layout, defaultView, availableViews, summary, table, chart);
        }
    }

    /** 稳定复制列定义，避免规则构建过程中被外部列表修改。 */
    public static List<PresentationDescriptor.Field> fields(PresentationDescriptor.Field... values) {
        List<PresentationDescriptor.Field> result = new ArrayList<>();
        if (values != null) Collections.addAll(result, values);
        return List.copyOf(result);
    }
}
