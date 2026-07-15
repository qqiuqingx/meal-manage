package me.zhengjie.agent.analysis;

import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentMetricDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面向模型的受控业务语义目录。目录会按本轮可用工具裁剪，但不会暴露工具名和结果字段。
 */
public class BusinessSemanticCatalog {

    /** 返回当前目录版本，供语义追踪和审计记录。 */
    public String version() {
        return AgentMetricCatalog.VERSION;
    }

    /**
     * 按服务端已授权工具筛选指标；空集合表示调用方尚未提供预过滤条件，仍由执行层二次鉴权。
     *
     * @param availableTools 当前账号可用的服务端工具名
     * @return 稳定排序的指标定义
     */
    public List<AgentMetricDefinition> metrics(Set<String> availableTools) {
        return AgentMetricCatalog.definitionsView().stream()
            .filter(definition -> availableTools == null || availableTools.isEmpty()
                || availableTools.contains(definition.getToolName()))
            .sorted(Comparator.comparing(definition -> definition.getMetric().name()))
            .collect(Collectors.toUnmodifiableList());
    }
}
