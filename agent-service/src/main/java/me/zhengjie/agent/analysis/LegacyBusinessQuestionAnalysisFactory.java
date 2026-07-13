package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryMetric;

import java.util.List;

/**
 * 将已登记的旧业务意图转换为受控问题分析结果，作为 ChatIntent 迁移期间的兼容适配器。
 * 不解析用户输入，也不接收工具名或自由字段。
 */
public final class LegacyBusinessQuestionAnalysisFactory {

    private LegacyBusinessQuestionAnalysisFactory() {
    }

    /**
     * 为可直接迁移的旧业务意图创建分析结果。
     *
     * @param intent 仅允许抽取器产生的已登记旧意图
     * @param slots 已解析并受控的会话槽位
     * @return 可由 QueryPlan 规划器执行的分析结果；组合查询或未登记意图返回 null
     */
    public static BusinessQuestionAnalysis fromIntent(ChatIntent intent, DiagnosisSlots slots) {
        AgentQueryDomain domain = domain(intent);
        if (domain == null) return null;
        BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
        analysis.setDomains(List.of(domain));
        analysis.setEntities(copyEntities(slots));
        analysis.setFilters(copyFilters(slots));
        if (intent == ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY) {
            analysis.setMetrics(List.of(AgentQueryMetric.MEAL_BALANCE));
        } else if (intent == ChatIntent.CUSTOMER_VERIFICATION_QUERY) {
            analysis.setMetrics(List.of(AgentQueryMetric.VERIFICATION_COUNT));
        } else if (intent == ChatIntent.CUSTOMER_REFUND_QUERY) {
            analysis.setMetrics(List.of(AgentQueryMetric.REFUND_COUNT));
        }
        analysis.setSource("LEGACY_COMPATIBILITY");
        analysis.setConfidence(1D);
        return analysis;
    }

    private static AgentQueryDomain domain(ChatIntent intent) {
        if (intent == ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY) return AgentQueryDomain.CUSTOMER;
        if (intent == ChatIntent.CUSTOMER_ORDER_QUERY) return AgentQueryDomain.ORDER;
        if (intent == ChatIntent.CUSTOMER_VERIFICATION_QUERY) return AgentQueryDomain.VERIFICATION;
        if (intent == ChatIntent.CUSTOMER_REFUND_QUERY) return AgentQueryDomain.REFUND;
        return null;
    }

    private static AgentEntityReference copyEntities(DiagnosisSlots slots) {
        AgentEntityReference entities = new AgentEntityReference();
        if (slots == null) return entities;
        entities.setCustomerId(slots.getCustomerId());
        entities.setCustomerCode(slots.getCustomerCode());
        entities.setCustomerName(slots.getCustomerName());
        entities.setOrderId(slots.getOrderId());
        entities.setOrderCode(slots.getOrderCode());
        entities.setMealPlanRecordId(slots.getMealPlanRecordId());
        return entities;
    }

    private static AgentQueryFilters copyFilters(DiagnosisSlots slots) {
        AgentQueryFilters filters = new AgentQueryFilters();
        if (slots == null) return filters;
        filters.setRecordDate(slots.getRecordDate());
        filters.setStartDate(slots.getStartDate());
        filters.setEndDate(slots.getEndDate());
        filters.setMealType(slots.getMealType());
        filters.setOrderStatus(slots.getOrderStatus() == null ? null : String.valueOf(slots.getOrderStatus()));
        return filters;
    }
}
