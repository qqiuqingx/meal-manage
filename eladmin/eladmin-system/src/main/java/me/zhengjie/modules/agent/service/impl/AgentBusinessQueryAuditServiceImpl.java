package me.zhengjie.modules.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.AgentBusinessQueryAudit;
import me.zhengjie.modules.agent.domain.dto.AgentBusinessQueryAuditCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentBusinessQueryAuditStatsDto;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.modules.agent.mapper.AgentBusinessQueryAuditMapper;
import me.zhengjie.modules.agent.service.AgentBusinessQueryAuditService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.PageUtil;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 业务查询审计实现，仅记录稳定标识和计数。 */
@Service
@RequiredArgsConstructor
public class AgentBusinessQueryAuditServiceImpl implements AgentBusinessQueryAuditService {
    private final AgentBusinessQueryAuditMapper auditMapper;

    /** {@inheritDoc} */
    @Override
    public void record(AgentChatResponse response, String operator, long costMs) {
        if (response == null || !isAuditable(response)) return;
        AgentBusinessQueryAudit audit = new AgentBusinessQueryAudit();
        audit.setOperator(operator); audit.setSessionId(response.getSessionId()); audit.setRequestId(response.getRequestId());
        Map<String, Object> plan = response.getQueryPlan() == null ? Collections.emptyMap() : response.getQueryPlan();
        audit.setQueryDomain(queryDomain(response, plan)); audit.setQueryAction(queryAction(response, plan));
        DiagnosisSlots slots = response.getSlots();
        if (slots != null) { audit.setCustomerId(slots.getCustomerId()); audit.setCustomerCode(slots.getCustomerCode()); }
        Map<String, Object> entities = plan.get("entities") instanceof Map ? (Map<String, Object>) plan.get("entities") : Collections.emptyMap();
        audit.setOrderId(longValue(entities.get("orderId"))); audit.setOrderCode(string(entities.get("orderCode")));
        Object tools = plan.get("toolNames"); audit.setToolNames(JSON.toJSONString(tools instanceof List ? tools : Collections.emptyList()));
        audit.setResultCount(resultCount(response.getInsightResult())); audit.setCached(response.isCached()); audit.setPartial(response.isPartial());
        audit.setFailureType(resolveFailureType(response));
        Map<String, Object> semanticTrace = response.getSemanticTraceSummary() == null
            ? Collections.emptyMap() : response.getSemanticTraceSummary();
        audit.setAnalysisSource(firstNonBlank(string(semanticTrace.get("semanticSource")), string(plan.get("analysisSource"))));
        audit.setAnalysisConfidence(semanticTrace.get("semanticConfidence") == null
            ? doubleValue(plan.get("analysisConfidence")) : doubleValue(semanticTrace.get("semanticConfidence")));
        audit.setSemanticFallbackReason(string(semanticTrace.get("fallbackReason")));
        audit.setSemanticCatalogVersion(string(semanticTrace.get("semanticCatalogVersion")));
        audit.setTemporalExpression(string(semanticTrace.get("temporalExpression")));
        audit.setResolvedRecordDate(string(semanticTrace.get("resolvedRecordDate")));
        audit.setResolvedStartDate(string(semanticTrace.get("resolvedStartDate")));
        audit.setResolvedEndDate(string(semanticTrace.get("resolvedEndDate")));
        audit.setPendingContextReused(Boolean.TRUE.equals(semanticTrace.get("pendingContextReused")));
        audit.setClarificationRequired("NEED_MORE_INFO".equals(response.getStatus()));
        audit.setMetricCodes(JSON.toJSONString(list(plan.get("metrics")))); audit.setDimensionCodes(JSON.toJSONString(list(plan.get("dimensions"))));
        audit.setUnsupportedReason(unsupportedReason(response)); audit.setAnswerValidationResult(answerValidationResult(response));
        audit.setCostMs(Math.max(0, costMs)); audit.setCreateTime(new Timestamp(System.currentTimeMillis()));
        auditMapper.insert(audit);
    }

    /** 返回首个非空追踪值，兼容升级前仅在 QueryPlan 中记录来源的响应。 */
    private String firstNonBlank(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first;
    }

    /**
     * 按查询条件分页返回业务查询审计记录，默认按创建时间倒序。
     *
     * @param criteria 审计查询条件
     * @return 分页审计记录
     */
    @Override
    public PageResult<AgentBusinessQueryAudit> query(AgentBusinessQueryAuditCriteria criteria) {
        AgentBusinessQueryAuditCriteria safeCriteria = criteria == null ? new AgentBusinessQueryAuditCriteria() : criteria;
        Page<AgentBusinessQueryAudit> page = new Page<>(normalizePage(safeCriteria.getPage()) + 1L, normalizeSize(safeCriteria.getSize()));
        return PageUtil.toPage(auditMapper.selectPage(page, wrapper(safeCriteria)));
    }

    /**
     * 聚合业务查询审计指标，用于运营看板和上线验收观察。
     *
     * @param criteria 审计统计过滤条件
     * @return 业务查询审计统计结果
     */
    @Override
    public AgentBusinessQueryAuditStatsDto stats(AgentBusinessQueryAuditCriteria criteria) {
        AgentBusinessQueryAuditCriteria safeCriteria = criteria == null ? new AgentBusinessQueryAuditCriteria() : criteria;
        List<AgentBusinessQueryAudit> audits = auditMapper.selectList(wrapper(safeCriteria));
        AgentBusinessQueryAuditStatsDto stats = new AgentBusinessQueryAuditStatsDto();
        long queryCount = audits.size();
        long partialCount = audits.stream().filter(item -> Boolean.TRUE.equals(item.getPartial())).count();
        long cachedCount = audits.stream().filter(item -> Boolean.TRUE.equals(item.getCached())).count();
        long failureCount = audits.stream().filter(item -> !isBlank(item.getFailureType())).count();
        long permissionDeniedCount = audits.stream().filter(item -> isPermissionDenied(item.getFailureType())).count();
        long clarificationRequiredCount = audits.stream().filter(item -> Boolean.TRUE.equals(item.getClarificationRequired())).count();
        long answerValidationRejectedCount = audits.stream().filter(item -> "REJECTED".equals(item.getAnswerValidationResult())).count();
        long directAnswerCount = audits.stream().filter(this::isDirectAnswer).count();
        long semanticFallbackCount = audits.stream().filter(item -> "RULE_FALLBACK".equals(item.getAnalysisSource())).count();
        long pendingContextReuseCount = audits.stream().filter(item -> Boolean.TRUE.equals(item.getPendingContextReused())).count();
        stats.setQueryCount(queryCount);
        stats.setPartialCount(partialCount);
        stats.setPartialRate(rate(partialCount, queryCount));
        stats.setCachedCount(cachedCount);
        stats.setCachedRate(rate(cachedCount, queryCount));
        stats.setFailureCount(failureCount);
        stats.setFailureRate(rate(failureCount, queryCount));
        stats.setPermissionDeniedCount(permissionDeniedCount);
        stats.setClarificationRequiredCount(clarificationRequiredCount);
        stats.setClarificationRequiredRate(rate(clarificationRequiredCount, queryCount));
        stats.setAnswerValidationRejectedCount(answerValidationRejectedCount);
        stats.setAnswerValidationRejectedRate(rate(answerValidationRejectedCount, queryCount));
        stats.setDirectAnswerCount(directAnswerCount);
        stats.setDirectAnswerRate(rate(directAnswerCount, queryCount));
        stats.setClarificationSuccessRate(clarificationSuccessRate(audits));
        stats.setSemanticFallbackCount(semanticFallbackCount);
        stats.setSemanticFallbackRate(rate(semanticFallbackCount, queryCount));
        stats.setPendingContextReuseCount(pendingContextReuseCount);
        stats.setPendingContextReuseRate(rate(pendingContextReuseCount, queryCount));
        stats.setAverageCostMs(audits.stream().map(AgentBusinessQueryAudit::getCostMs).filter(Objects::nonNull).mapToLong(Long::longValue).average().orElse(0D));
        stats.setP95CostMs(percentile(audits.stream().map(AgentBusinessQueryAudit::getCostMs).filter(Objects::nonNull).collect(Collectors.toList()), 0.95D));
        stats.setDomainDistribution(distribution(audits.stream().map(AgentBusinessQueryAudit::getQueryDomain).collect(Collectors.toList())));
        stats.setToolDistribution(toolDistribution(audits));
        stats.setMetricDistribution(metricDistribution(audits));
        stats.setFailureTypeDistribution(distribution(audits.stream().map(AgentBusinessQueryAudit::getFailureType).collect(Collectors.toList())));
        stats.setUnsupportedReasonDistribution(distribution(audits.stream().map(AgentBusinessQueryAudit::getUnsupportedReason).collect(Collectors.toList())));
        stats.setSemanticSourceDistribution(distribution(audits.stream().map(AgentBusinessQueryAudit::getAnalysisSource).collect(Collectors.toList())));
        stats.setSemanticFallbackReasonDistribution(distribution(audits.stream().map(AgentBusinessQueryAudit::getSemanticFallbackReason).collect(Collectors.toList())));
        return stats;
    }

    /**
     * 构造业务查询审计过滤条件；仅支持稳定标识和时间范围，不支持按原始工具请求/响应搜索。
     */
    private LambdaQueryWrapper<AgentBusinessQueryAudit> wrapper(AgentBusinessQueryAuditCriteria criteria) {
        return new LambdaQueryWrapper<AgentBusinessQueryAudit>()
            .eq(!isBlank(criteria.getOperator()), AgentBusinessQueryAudit::getOperator, criteria.getOperator())
            .eq(!isBlank(criteria.getSessionId()), AgentBusinessQueryAudit::getSessionId, criteria.getSessionId())
            .eq(!isBlank(criteria.getRequestId()), AgentBusinessQueryAudit::getRequestId, criteria.getRequestId())
            .eq(!isBlank(criteria.getQueryDomain()), AgentBusinessQueryAudit::getQueryDomain, criteria.getQueryDomain())
            .eq(!isBlank(criteria.getQueryAction()), AgentBusinessQueryAudit::getQueryAction, criteria.getQueryAction())
            .eq(criteria.getCustomerId() != null, AgentBusinessQueryAudit::getCustomerId, criteria.getCustomerId())
            .eq(!isBlank(criteria.getCustomerCode()), AgentBusinessQueryAudit::getCustomerCode, criteria.getCustomerCode())
            .eq(criteria.getOrderId() != null, AgentBusinessQueryAudit::getOrderId, criteria.getOrderId())
            .eq(!isBlank(criteria.getOrderCode()), AgentBusinessQueryAudit::getOrderCode, criteria.getOrderCode())
            .eq(criteria.getCached() != null, AgentBusinessQueryAudit::getCached, criteria.getCached())
            .eq(criteria.getPartial() != null, AgentBusinessQueryAudit::getPartial, criteria.getPartial())
            .eq(!isBlank(criteria.getFailureType()), AgentBusinessQueryAudit::getFailureType, criteria.getFailureType())
            .ge(!isBlank(criteria.getCreateTimeStart()), AgentBusinessQueryAudit::getCreateTime, criteria.getCreateTimeStart())
            .le(!isBlank(criteria.getCreateTimeEnd()), AgentBusinessQueryAudit::getCreateTime, criteria.getCreateTimeEnd())
            .orderByDesc(AgentBusinessQueryAudit::getCreateTime);
    }

    private int resultCount(Map<String, Object> result) { Object total = result == null ? null : result.get("total"); return total instanceof Number ? ((Number) total).intValue() : 0; }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private Long longValue(Object value) { return value instanceof Number ? ((Number) value).longValue() : null; }
    private Double doubleValue(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : null; }
    private List<?> list(Object value) { return value instanceof List ? (List<?>) value : Collections.emptyList(); }
    private int normalizePage(Integer page) { return page == null || page < 0 ? 0 : page; }
    private int normalizeSize(Integer size) { return size == null || size < 1 ? 10 : Math.min(size, 100); }
    private double rate(long numerator, long denominator) { return denominator == 0 ? 0D : numerator * 1D / denominator; }
    /** 判断一轮是否无需澄清且未以失败/部分结果降级的完整业务回答。 */
    private boolean isDirectAnswer(AgentBusinessQueryAudit audit) {
        return audit != null && !Boolean.TRUE.equals(audit.getClarificationRequired()) && !Boolean.TRUE.equals(audit.getPartial())
            && isBlank(audit.getFailureType()) && "VALID".equals(audit.getAnswerValidationResult());
    }
    /** 计算同一会话发起澄清后是否至少获得一次完整有效回答。 */
    private double clarificationSuccessRate(List<AgentBusinessQueryAudit> audits) {
        Map<String, List<AgentBusinessQueryAudit>> bySession = audits.stream().filter(item -> item != null && !isBlank(item.getSessionId()))
            .collect(Collectors.groupingBy(AgentBusinessQueryAudit::getSessionId));
        long clarified = bySession.values().stream().filter(items -> items.stream().anyMatch(item -> Boolean.TRUE.equals(item.getClarificationRequired()))).count();
        long succeeded = bySession.values().stream().filter(items -> items.stream().anyMatch(item -> Boolean.TRUE.equals(item.getClarificationRequired()))
            && items.stream().anyMatch(this::isDirectAnswer)).count();
        return rate(succeeded, clarified);
    }
    /** 以向上取整索引计算非空耗时列表的分位数。 */
    private long percentile(List<Long> values, double quantile) {
        if (values == null || values.isEmpty()) return 0L;
        List<Long> sorted = values.stream().filter(Objects::nonNull).sorted().collect(Collectors.toList());
        if (sorted.isEmpty()) return 0L;
        int index = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(sorted.size() * quantile) - 1));
        return sorted.get(index);
    }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }

    /**
     * 为尚未生成 QueryPlan 的受控澄清或拒绝响应写入可聚合的稳定领域值。
     * 这类响应不执行业务工具，但仍须审计，不能因数据库非空约束中断客服会话。
     */
    private String queryDomain(AgentChatResponse response, Map<String, Object> plan) {
        String domain = string(plan.get("domain"));
        if (!isBlank(domain)) return domain;
        if ("OUT_OF_SCOPE".equals(response.getStatus())) return "OUT_OF_SCOPE";
        String responseType = response.getResponseType();
        if (responseType != null && responseType.contains("OPERATION")) return "OPERATION_STATISTICS";
        return "BUSINESS_QUERY";
    }

    /** 为尚未生成 QueryPlan 的受控澄清或拒绝响应写入稳定动作值。 */
    private String queryAction(AgentChatResponse response, Map<String, Object> plan) {
        String action = string(plan.get("action"));
        if (!isBlank(action)) return action;
        if ("OUT_OF_SCOPE".equals(response.getStatus())) return "REJECT";
        if (response.getResponseType() != null && response.getResponseType().contains("CLARIFICATION")) return "CLARIFY";
        return "SUMMARY";
    }

    /** 优先记录工具返回的稳定失败码，避免部分回答被 ANSWERED 状态掩盖。 */
    private String resolveFailureType(AgentChatResponse response) {
        if (response.getWarnings() != null) for (String warning : response.getWarnings()) {
            if (!isBlank(warning) && (warning.startsWith("TOOL_") || warning.startsWith("AGENT_QUERY_")
                || warning.startsWith("PLAN_") || warning.startsWith("BUSINESS_QUERY_"))) return warning;
        }
        return response.getStatus() != null && !"ANSWERED".equals(response.getStatus()) ? response.getStatus() : null;
    }

    /** 统计工具白名单拒绝和主系统权限拒绝，供运营看板展示。 */
    private boolean isPermissionDenied(String failureType) {
        return "TOOL_PERMISSION_DENIED".equals(failureType) || "AGENT_QUERY_ACCESS_DENIED".equals(failureType)
            || "AGENT_QUERY_UNAUTHORIZED".equals(failureType);
    }

    /** 仅记录业务查询、澄清和拒绝，排餐诊断仍使用原有诊断审计表。 */
    private boolean isAuditable(AgentChatResponse response) {
        if (response.getResponseType() != null && response.getResponseType().startsWith("BUSINESS_QUERY")) return true;
        return "OUT_OF_SCOPE".equals(response.getStatus());
    }

    private String unsupportedReason(AgentChatResponse response) {
        if (response.getResponseType() != null && response.getResponseType().contains("CLARIFICATION")) return "CLARIFICATION_REQUIRED";
        return "OUT_OF_SCOPE".equals(response.getStatus()) ? "OUT_OF_SCOPE" : null;
    }

    private String answerValidationResult(AgentChatResponse response) {
        if (response.getWarnings() != null && response.getWarnings().stream().anyMatch(value -> "PLAN_RESULT_MISMATCH".equals(value)
            || value != null && value.contains("回答安全校验"))) return "REJECTED";
        return response.isPartial() ? "PARTIAL" : "VALID";
    }

    private Map<String, Long> distribution(List<String> values) {
        Map<String, Long> result = new LinkedHashMap<>();
        values.stream().filter(value -> !isBlank(value)).forEach(value -> result.put(value, result.getOrDefault(value, 0L) + 1));
        return result;
    }

    private Map<String, Long> toolDistribution(List<AgentBusinessQueryAudit> audits) {
        List<String> tools = new ArrayList<>();
        for (AgentBusinessQueryAudit audit : audits) {
            if (audit == null || isBlank(audit.getToolNames())) continue;
            try {
                tools.addAll(JSON.parseArray(audit.getToolNames(), String.class));
            } catch (Exception ignored) {
                tools.add(audit.getToolNames());
            }
        }
        return distribution(tools);
    }

    /** 解析审计中的受控指标数组，损坏历史数据按单值降级且不影响统计接口。 */
    private Map<String, Long> metricDistribution(List<AgentBusinessQueryAudit> audits) {
        List<String> metrics = new ArrayList<>();
        for (AgentBusinessQueryAudit audit : audits) {
            if (audit == null || isBlank(audit.getMetricCodes())) continue;
            try { metrics.addAll(JSON.parseArray(audit.getMetricCodes(), String.class)); }
            catch (Exception ignored) { metrics.add(audit.getMetricCodes()); }
        }
        return distribution(metrics);
    }
}
