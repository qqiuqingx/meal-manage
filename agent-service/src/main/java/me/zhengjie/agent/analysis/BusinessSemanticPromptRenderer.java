package me.zhengjie.agent.analysis;

import me.zhengjie.agent.query.domain.AgentMetricDefinition;

import java.util.Set;
import java.util.stream.Collectors;

/** 将版本化指标目录渲染为精简模型知识，不包含工具、结果字段或数据库实现。 */
public class BusinessSemanticPromptRenderer {
    private final BusinessSemanticCatalog catalog;

    public BusinessSemanticPromptRenderer(BusinessSemanticCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 渲染本轮可选的业务指标、时间策略和相邻口径。
     *
     * @param availableTools 当前账号可用工具，仅用于服务端筛选
     * @return 可审计且不暴露执行实现的模型知识文本
     */
    public String render(Set<String> availableTools) {
        return "语义目录版本=" + catalog.version() + "。可选指标：\n" + catalog.metrics(availableTools).stream()
            .map(this::renderMetric)
            .collect(Collectors.joining("\n"));
    }

    private String renderMetric(AgentMetricDefinition definition) {
        return "- " + definition.getMetric().name()
            + " | 领域=" + definition.getDomain().name()
            + " | 含义=" + definition.getSemanticDescription()
            + " | 默认时间=" + definition.getDefaultTemporalPolicy().name()
            + " | 支持维度=" + definition.getDimensions().stream().map(Enum::name).sorted().collect(Collectors.joining(","))
            + " | 常见表达=" + String.join("、", definition.getCommonBusinessTerms());
    }
}
