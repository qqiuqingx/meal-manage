package me.zhengjie.modules.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.AgentActionAudit;
import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import me.zhengjie.modules.agent.domain.AgentDiagnosisMetric;
import me.zhengjie.modules.agent.domain.dto.AgentBusinessQueryAuditCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentBusinessQueryAuditStatsDto;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisReasonDto;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.domain.dto.AgentOperationStatsDto;
import me.zhengjie.modules.agent.domain.dto.AgentOperationStatsQuery;
import me.zhengjie.modules.agent.mapper.AgentActionAuditMapper;
import me.zhengjie.modules.agent.mapper.AgentDiagnosisFeedbackMapper;
import me.zhengjie.modules.agent.mapper.AgentDiagnosisMetricMapper;
import me.zhengjie.modules.agent.service.AgentBusinessQueryAuditService;
import me.zhengjie.modules.agent.service.AgentOperationStatsService;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 智能排查运营指标服务实现。
 */
@Service
@RequiredArgsConstructor
public class AgentOperationStatsServiceImpl implements AgentOperationStatsService {

    private static final List<String> UNKNOWN_REASON_CODES = Arrays.asList("AI_RESULT_INVALID", "DATA_INCOMPLETE_NEED_RECHECK");

    private final AgentDiagnosisMetricMapper metricMapper;
    private final AgentActionAuditMapper actionAuditMapper;
    private final AgentDiagnosisFeedbackMapper feedbackMapper;
    private final AgentBusinessQueryAuditService businessQueryAuditService;

    @Override
    public void recordDiagnosis(AgentDiagnosisResponse response, String sessionId, long costMs) {
        if (response == null) {
            return;
        }
        AgentDiagnosisMetric metric = new AgentDiagnosisMetric();
        metric.setRequestId(response.getRequestId());
        metric.setSessionId(sessionId);
        metric.setCustomerId(response.getCustomerId());
        metric.setRecordDate(response.getRecordDate());
        metric.setMealType(response.getMealType());
        metric.setFallback(response.isFallback());
        metric.setFallbackReason(response.getFallbackReason());
        metric.setFallbackSource(response.getFallbackSource());
        metric.setFailureType(response.getFailureType());
        metric.setConfidence(response.getConfidence());
        metric.setModelName(response.getModelName());
        metric.setReasonCodes(JSON.toJSONString(reasonCodes(response)));
        metric.setActionDraftCount(response.getActionDrafts() == null ? 0 : response.getActionDrafts().size());
        metric.setToolCallCount(response.getToolCallSummary() == null ? 0 : response.getToolCallSummary().size());
        metric.setToolFailureCount(toolFailureCount(response));
        metric.setDiagnosisCostMs(Math.max(costMs, 0));
        metric.setCreateTime(new Timestamp(System.currentTimeMillis()));
        metricMapper.insert(metric);
    }

    @Override
    public AgentOperationStatsDto stats(AgentOperationStatsQuery query) {
        AgentOperationStatsQuery safeQuery = query == null ? new AgentOperationStatsQuery() : query;
        List<AgentDiagnosisMetric> metrics = metricMapper.selectList(metricWrapper(safeQuery));
        List<AgentActionAudit> audits = actionAudits(metrics);
        List<AgentDiagnosisFeedback> feedbacks = feedbackMapper.selectList(feedbackWrapper(safeQuery));

        AgentOperationStatsDto stats = new AgentOperationStatsDto();
        long diagnosisCount = metrics.size();
        long fallbackCount = metrics.stream().filter(item -> Boolean.TRUE.equals(item.getFallback())).count();
        long toolCallCount = metrics.stream().map(AgentDiagnosisMetric::getToolCallCount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long toolFailureCount = metrics.stream().map(AgentDiagnosisMetric::getToolFailureCount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long actionDraftCount = metrics.stream().map(AgentDiagnosisMetric::getActionDraftCount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long confirmedActionCount = audits.stream().filter(item -> Boolean.TRUE.equals(item.getSuccess())).count();

        stats.setDiagnosisCount(diagnosisCount);
        stats.setFallbackCount(fallbackCount);
        stats.setFallbackRate(rate(fallbackCount, diagnosisCount));
        stats.setAverageDiagnosisCostMs(averageCost(metrics));
        stats.setToolFailureCount(toolFailureCount);
        stats.setToolFailureRate(rate(toolFailureCount, toolCallCount));
        stats.setActionDraftCount(actionDraftCount);
        stats.setActionDraftConfirmedCount(confirmedActionCount);
        stats.setActionDraftConfirmationRate(rate(confirmedActionCount, actionDraftCount));
        stats.setFeedbackCount((long) feedbacks.size());
        stats.setFeedbackAcceptedRate(rate(feedbacks.stream().filter(item -> "ACCEPTED".equals(item.getAccepted())).count(), feedbacks.size()));
        stats.setReasonCodeDistribution(reasonDistribution(metrics));
        stats.setActualReasonDistribution(actualReasonDistribution(feedbacks));
        stats.setHighFrequencyUnknownReasons(unknownReasonDistribution(stats.getReasonCodeDistribution()));
        stats.setFallbackSourceDistribution(fallbackSourceDistribution(metrics));
        stats.setFailureTypeDistribution(failureTypeDistribution(metrics));
        applyBusinessQueryStats(stats, businessQueryAuditService.stats(businessQueryCriteria(safeQuery)));
        return stats;
    }

    /**
     * 将业务只读查询指标并入运营看板，便于同一入口观察诊断和业务查询健康度。
     */
    private void applyBusinessQueryStats(AgentOperationStatsDto target, AgentBusinessQueryAuditStatsDto source) {
        if (target == null || source == null) return;
        target.setBusinessQueryCount(source.getQueryCount());
        target.setBusinessQueryPartialCount(source.getPartialCount());
        target.setBusinessQueryPartialRate(source.getPartialRate());
        target.setBusinessQueryCachedCount(source.getCachedCount());
        target.setBusinessQueryCachedRate(source.getCachedRate());
        target.setBusinessQueryFailureCount(source.getFailureCount());
        target.setBusinessQueryFailureRate(source.getFailureRate());
        target.setBusinessQueryPermissionDeniedCount(source.getPermissionDeniedCount());
        target.setAverageBusinessQueryCostMs(source.getAverageCostMs());
        target.setBusinessQueryDomainDistribution(source.getDomainDistribution());
        target.setBusinessQueryToolDistribution(source.getToolDistribution());
        target.setBusinessQueryFailureTypeDistribution(source.getFailureTypeDistribution());
    }

    /**
     * 将运营统计日期条件映射为业务查询审计创建时间范围。
     */
    private AgentBusinessQueryAuditCriteria businessQueryCriteria(AgentOperationStatsQuery query) {
        AgentBusinessQueryAuditCriteria criteria = new AgentBusinessQueryAuditCriteria();
        if (!isBlank(query.getRecordDateStart())) criteria.setCreateTimeStart(query.getRecordDateStart() + " 00:00:00");
        if (!isBlank(query.getRecordDateEnd())) criteria.setCreateTimeEnd(query.getRecordDateEnd() + " 23:59:59");
        return criteria;
    }

    /**
     * 查询当前统计范围内诊断请求关联的动作审计记录，保证确认率分子和动作草稿分母口径一致。
     */
    private List<AgentActionAudit> actionAudits(List<AgentDiagnosisMetric> metrics) {
        Set<String> requestIds = metrics.stream()
            .map(AgentDiagnosisMetric::getRequestId)
            .filter(requestId -> !isBlank(requestId))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestIds.isEmpty()) {
            return Collections.emptyList();
        }
        return actionAuditMapper.selectList(new LambdaQueryWrapper<AgentActionAudit>()
                .in(AgentActionAudit::getRequestId, requestIds))
            .stream()
            .filter(audit -> audit != null && requestIds.contains(audit.getRequestId()))
            .collect(Collectors.toList());
    }

    /**
     * 构造诊断指标查询条件。
     */
    private LambdaQueryWrapper<AgentDiagnosisMetric> metricWrapper(AgentOperationStatsQuery query) {
        return new LambdaQueryWrapper<AgentDiagnosisMetric>()
            .ge(!isBlank(query.getRecordDateStart()), AgentDiagnosisMetric::getRecordDate, query.getRecordDateStart())
            .le(!isBlank(query.getRecordDateEnd()), AgentDiagnosisMetric::getRecordDate, query.getRecordDateEnd())
            .eq(!isBlank(query.getMealType()), AgentDiagnosisMetric::getMealType, query.getMealType())
            .orderByDesc(AgentDiagnosisMetric::getCreateTime);
    }

    /**
     * 构造反馈查询条件。
     */
    private LambdaQueryWrapper<AgentDiagnosisFeedback> feedbackWrapper(AgentOperationStatsQuery query) {
        return new LambdaQueryWrapper<AgentDiagnosisFeedback>()
            .ge(!isBlank(query.getRecordDateStart()), AgentDiagnosisFeedback::getRecordDate, query.getRecordDateStart())
            .le(!isBlank(query.getRecordDateEnd()), AgentDiagnosisFeedback::getRecordDate, query.getRecordDateEnd())
            .eq(!isBlank(query.getMealType()), AgentDiagnosisFeedback::getMealType, query.getMealType());
    }

    private List<String> reasonCodes(AgentDiagnosisResponse response) {
        if (response.getReasons() == null) {
            return Collections.emptyList();
        }
        return response.getReasons().stream()
            .map(AgentDiagnosisReasonDto::getCode)
            .filter(code -> !isBlank(code))
            .collect(Collectors.toList());
    }

    private int toolFailureCount(AgentDiagnosisResponse response) {
        if (response.getToolCallSummary() == null) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> item : response.getToolCallSummary()) {
            if (item != null && (!isBlank(asString(item.get("errorType"))) || "TOOL_CALL_FAILED".equals(asString(item.get("eventType"))))) {
                count++;
            }
        }
        return count;
    }

    private Double averageCost(List<AgentDiagnosisMetric> metrics) {
        return metrics.stream()
            .map(AgentDiagnosisMetric::getDiagnosisCostMs)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .average()
            .orElse(0D);
    }

    private Map<String, Long> reasonDistribution(List<AgentDiagnosisMetric> metrics) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (AgentDiagnosisMetric metric : metrics) {
            for (String code : parseCodes(metric.getReasonCodes())) {
                distribution.put(code, distribution.getOrDefault(code, 0L) + 1);
            }
        }
        return distribution;
    }

    private Map<String, Long> actualReasonDistribution(List<AgentDiagnosisFeedback> feedbacks) {
        return feedbacks.stream()
            .filter(item -> !isBlank(item.getActualReasonCode()))
            .collect(Collectors.groupingBy(AgentDiagnosisFeedback::getActualReasonCode, LinkedHashMap::new, Collectors.counting()));
    }

    private Map<String, Long> unknownReasonDistribution(Map<String, Long> reasonDistribution) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String code : UNKNOWN_REASON_CODES) {
            Long count = reasonDistribution.get(code);
            if (count != null && count > 0) {
                result.put(code, count);
            }
        }
        return result;
    }

    private Map<String, Long> fallbackSourceDistribution(List<AgentDiagnosisMetric> metrics) {
        return metrics.stream()
            .filter(item -> !isBlank(item.getFallbackSource()))
            .collect(Collectors.groupingBy(AgentDiagnosisMetric::getFallbackSource, LinkedHashMap::new, Collectors.counting()));
    }

    private Map<String, Long> failureTypeDistribution(List<AgentDiagnosisMetric> metrics) {
        return metrics.stream()
            .filter(item -> !isBlank(item.getFailureType()))
            .collect(Collectors.groupingBy(AgentDiagnosisMetric::getFailureType, LinkedHashMap::new, Collectors.counting()));
    }

    private List<String> parseCodes(String value) {
        if (isBlank(value)) {
            return Collections.emptyList();
        }
        try {
            return JSON.parseArray(value, String.class);
        } catch (Exception ex) {
            List<String> single = new ArrayList<>();
            single.add(value);
            return single;
        }
    }

    private Double rate(long numerator, long denominator) {
        return denominator == 0 ? 0D : numerator * 1D / denominator;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
