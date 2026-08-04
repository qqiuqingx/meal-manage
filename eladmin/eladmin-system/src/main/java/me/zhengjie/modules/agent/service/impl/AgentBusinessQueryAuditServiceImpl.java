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
import java.util.LinkedHashSet;
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
        audit.setQueryDomain(queryDomain(response)); audit.setQueryAction(queryAction(response));
        DiagnosisSlots slots = response.getSlots();
        if (slots != null) { audit.setCustomerId(slots.getCustomerId()); audit.setCustomerCode(slots.getCustomerCode()); }
        audit.setOrderId(slots == null ? null : slots.getOrderId());
        audit.setOrderCode(slots == null ? null : slots.getOrderCode());
        audit.setToolNames(JSON.toJSONString(toolNames(response)));
        audit.setResultCount(resultCount(response)); audit.setCached(response.isCached()); audit.setPartial(response.isPartial());
        audit.setFailureType(resolveFailureType(response));
        audit.setAnalysisSource(hasToolFacts(response) ? "LLM_TOOL_CALLING" : null);
        audit.setResolvedRecordDate(slots == null ? null : slots.getRecordDate());
        audit.setResolvedStartDate(slots == null ? null : slots.getStartDate());
        audit.setResolvedEndDate(slots == null ? null : slots.getEndDate());
        audit.setClarificationRequired("NEED_MORE_INFO".equals(response.getStatus()));
        audit.setMetricCodes(JSON.toJSONString(extractCodes(response, "metric")));
        audit.setDimensionCodes(JSON.toJSONString(extractCodes(response, "dimension")));
        audit.setUnsupportedReason(unsupportedReason(response)); audit.setAnswerValidationResult(answerValidationResult(response));
        audit.setCostMs(Math.max(0, costMs)); audit.setCreateTime(new Timestamp(System.currentTimeMillis()));
        auditMapper.insert(audit);
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
        List<Long> costs = audits.stream().map(AgentBusinessQueryAudit::getCostMs)
            .filter(Objects::nonNull).collect(Collectors.toList());
        stats.setAverageCostMs(costs.stream().mapToLong(Long::longValue).average().orElse(0D));
        stats.setP50CostMs(percentile(costs, 0.50D));
        stats.setP95CostMs(percentile(costs, 0.95D));
        stats.setDomainDistribution(distribution(audits.stream().map(AgentBusinessQueryAudit::getQueryDomain).collect(Collectors.toList())));
        stats.setToolDistribution(toolDistribution(audits));
        stats.setMetricDistribution(metricDistribution(audits));
        stats.setFailureTypeDistribution(distribution(audits.stream().map(AgentBusinessQueryAudit::getFailureType).collect(Collectors.toList())));
        stats.setUnsupportedReasonDistribution(distribution(audits.stream().map(AgentBusinessQueryAudit::getUnsupportedReason).collect(Collectors.toList())));
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

    /** 从工具追踪或事实摘要计算本轮结果数量，不读取模型自然语言答案。 */
    private int resultCount(AgentChatResponse response) {
        int total = 0;
        if (response.getToolTraceSummary() != null) {
            for (Map<String, Object> trace : response.getToolTraceSummary()) {
                Object count = trace == null ? null : trace.get("resultCount");
                if (count instanceof Number) total += Math.max(0, ((Number) count).intValue());
            }
        }
        if (total > 0 || response.getToolFacts() == null) return total;
        for (Map<String, Object> fact : response.getToolFacts()) {
            Object data = fact == null ? null : fact.get("data");
            total += resultCountFromData(data);
        }
        return total;
    }

    /** 解析受控事实中的 total/items，单对象结果按一条计数。 */
    @SuppressWarnings("unchecked")
    private int resultCountFromData(Object data) {
        if (!(data instanceof Map)) return data == null ? 0 : 1;
        Map<String, Object> values = (Map<String, Object>) data;
        Object total = values.get("total");
        if (total instanceof Number) return Math.max(0, ((Number) total).intValue());
        Object items = values.get("items");
        return items instanceof List ? ((List<?>) items).size() : 1;
    }

    /** 提取工具追踪中的唯一工具名称，作为审计摘要而非路由依据。 */
    private List<String> toolNames(AgentChatResponse response) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (response.getToolTraceSummary() != null) {
            for (Map<String, Object> trace : response.getToolTraceSummary()) {
                if (trace != null && trace.get("toolName") != null) names.add(String.valueOf(trace.get("toolName")));
            }
        }
        return new ArrayList<>(names);
    }

    /** 判断响应是否包含统一工具事实。 */
    private boolean hasToolFacts(AgentChatResponse response) {
        return response.getCards() != null && !response.getCards().isEmpty()
            || response.getToolFacts() != null && !response.getToolFacts().isEmpty()
            || response.getToolTraceSummary() != null && !response.getToolTraceSummary().isEmpty();
    }

    /** 从工具事实提取受控指标或维度代码，避免把自由文本写入运营审计。 */
    @SuppressWarnings("unchecked")
    private List<String> extractCodes(AgentChatResponse response, String fieldName) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (response.getToolFacts() == null) return new ArrayList<>();
        for (Map<String, Object> fact : response.getToolFacts()) {
            Object data = fact == null ? null : fact.get("data");
            if (!(data instanceof Map)) continue;
            Object value = ((Map<String, Object>) data).get(fieldName);
            if (value instanceof String && ((String) value).matches("[A-Z][A-Z0-9_]{1,63}")) codes.add((String) value);
            if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item != null && String.valueOf(item).matches("[A-Z][A-Z0-9_]{1,63}")) codes.add(String.valueOf(item));
                }
            }
        }
        return new ArrayList<>(codes);
    }
    /** 将审计分页页码归一化为非负值。 */
    private int normalizePage(Integer page) { return page == null || page < 0 ? 0 : page; }
    /** 将审计分页大小归一化到 1 到 100。 */
    private int normalizeSize(Integer size) { return size == null || size < 1 ? 10 : Math.min(size, 100); }
    /** 计算统计比例，分母为空时返回零。 */
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

    /** 根据成功工具输出的卡片类型写入稳定审计领域，不参与工具选择。 */
    private String queryDomain(AgentChatResponse response) {
        if ("OUT_OF_SCOPE".equals(response.getStatus())) return "OUT_OF_SCOPE";
        String type = firstCardType(response);
        if (type.startsWith("METRIC")) return "OPERATION_STATISTICS";
        if (type.startsWith("CUSTOMER") || type.startsWith("SERVICE_CUSTOMER")) return "CUSTOMER";
        if (type.startsWith("MEAL_PLAN")) return "MEAL_PLAN";
        if (type.startsWith("VERIFICATION")) return "VERIFICATION";
        if (type.startsWith("REFUND")) return "REFUND";
        if (type.startsWith("DISH")) return "DISH";
        if (type.startsWith("PACKAGE")) return "PACKAGE";
        if (type.startsWith("DIAGNOSIS")) return "MEAL_PLAN_DIAGNOSIS";
        return "BUSINESS_QUERY";
    }

    /** 根据状态和卡片类型写入稳定审计动作，不保留模型查询计划。 */
    private String queryAction(AgentChatResponse response) {
        if ("OUT_OF_SCOPE".equals(response.getStatus())) return "REJECT";
        if ("NEED_MORE_INFO".equals(response.getStatus())) return "CLARIFY";
        if (firstCardType(response).contains("DETAIL")) return "DETAIL";
        if (firstCardType(response).contains("LIST")) return "LIST";
        return "SUMMARY";
    }

    /** 返回首张卡片类型；无卡片的澄清和失败响应返回空串。 */
    private String firstCardType(AgentChatResponse response) {
        if (response.getCards() == null || response.getCards().isEmpty()) return "";
        Object type = response.getCards().get(0).get("type");
        return type == null ? "" : String.valueOf(type).toUpperCase(java.util.Locale.ROOT);
    }

    /** 优先记录工具返回的稳定失败码，避免部分回答被 ANSWERED 状态掩盖。 */
    private String resolveFailureType(AgentChatResponse response) {
        if (response.getWarnings() != null) for (String warning : response.getWarnings()) {
            if (!isBlank(warning) && (warning.startsWith("TOOL_") || warning.startsWith("AGENT_QUERY_")
                || warning.startsWith("BUSINESS_QUERY_"))) return warning;
            if (!isBlank(warning) && warning.contains(":")) {
                String suffix = warning.substring(warning.lastIndexOf(':') + 1);
                if (suffix.matches("[A-Z][A-Z0-9_]{2,63}")) return suffix;
            }
        }
        return response.getStatus() != null && !"ANSWERED".equals(response.getStatus()) ? response.getStatus() : null;
    }

    /** 统计工具白名单拒绝和主系统权限拒绝，供运营看板展示。 */
    private boolean isPermissionDenied(String failureType) {
        return "TOOL_PERMISSION_DENIED".equals(failureType) || "AGENT_QUERY_ACCESS_DENIED".equals(failureType)
            || "AGENT_QUERY_UNAUTHORIZED".equals(failureType) || "TOOL_ACCESS_DENIED".equals(failureType);
    }

    /** 仅记录统一工具事实、澄清、部分结果和拒绝，避免把闲聊写入业务审计。 */
    private boolean isAuditable(AgentChatResponse response) {
        return hasToolFacts(response) || response.isPartial() || "OUT_OF_SCOPE".equals(response.getStatus())
            || "NEED_MORE_INFO".equals(response.getStatus()) || "ERROR".equals(response.getStatus());
    }

    /** 从响应告警中提取稳定的不可用或澄清原因。 */
    private String unsupportedReason(AgentChatResponse response) {
        if ("NEED_MORE_INFO".equals(response.getStatus())) return "CLARIFICATION_REQUIRED";
        return "OUT_OF_SCOPE".equals(response.getStatus()) ? "OUT_OF_SCOPE" : null;
    }

    /** 根据回答护栏告警计算审计中的回答校验状态。 */
    private String answerValidationResult(AgentChatResponse response) {
        if (response.getWarnings() != null && response.getWarnings().stream().anyMatch(value -> value != null
            && (value.contains("SENSITIVE_DATA_REJECTED") || value.contains("UNTRUSTED_TEXT_REJECTED")
            || value.contains("WRITE_OPERATION_CLAIM_REJECTED") || value.contains("ANSWER_")))) return "REJECTED";
        return response.isPartial() ? "PARTIAL" : "VALID";
    }

    /** 对非空业务代码执行稳定频次聚合。 */
    private Map<String, Long> distribution(List<String> values) {
        Map<String, Long> result = new LinkedHashMap<>();
        values.stream().filter(value -> !isBlank(value)).forEach(value -> result.put(value, result.getOrDefault(value, 0L) + 1));
        return result;
    }

    /** 统计审计记录中的工具调用频次。 */
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
