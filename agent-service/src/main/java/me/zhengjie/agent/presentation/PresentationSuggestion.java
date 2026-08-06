package me.zhengjie.agent.presentation;

import java.util.List;

/**
 * LLM 只能返回的最小展示候选。
 *
 * <p>候选只描述视图和字段引用，不允许携带数据值、代码、表达式、HTML 或前端组件配置。
 * 普通标签会在 {@link PresentationSuggestionValidator} 中再次校验。</p>
 */
public record PresentationSuggestion(
    String view,
    String title,
    String dataPath,
    String dimensionField,
    List<String> metricFields,
    String dimensionLabel,
    List<String> metricLabels) {

    /** 复制集合并保持候选对象不可变。 */
    public PresentationSuggestion {
        metricFields = metricFields == null ? List.of() : List.copyOf(metricFields);
        metricLabels = metricLabels == null ? List.of() : List.copyOf(metricLabels);
    }

    /** 返回固定白名单视图；非法文本由 validator 拒绝。 */
    public PresentationDescriptor.View parsedView() {
        if (view == null) return null;
        try { return PresentationDescriptor.View.valueOf(view.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return null; }
    }
}
