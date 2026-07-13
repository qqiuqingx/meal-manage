package me.zhengjie.modules.agent.service.impl;

import me.zhengjie.modules.agent.domain.AgentActionAudit;
import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import me.zhengjie.modules.agent.domain.AgentDiagnosisMetric;
import me.zhengjie.modules.agent.domain.dto.AgentBusinessQueryAuditStatsDto;
import me.zhengjie.modules.agent.domain.dto.AgentOperationStatsDto;
import me.zhengjie.modules.agent.domain.dto.AgentOperationStatsQuery;
import me.zhengjie.modules.agent.mapper.AgentActionAuditMapper;
import me.zhengjie.modules.agent.mapper.AgentDiagnosisFeedbackMapper;
import me.zhengjie.modules.agent.mapper.AgentDiagnosisMetricMapper;
import me.zhengjie.modules.agent.service.AgentBusinessQueryAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOperationStatsServiceImplTest {

    @Mock
    private AgentDiagnosisMetricMapper metricMapper;

    @Mock
    private AgentActionAuditMapper actionAuditMapper;

    @Mock
    private AgentDiagnosisFeedbackMapper feedbackMapper;

    @Mock
    private AgentBusinessQueryAuditService businessQueryAuditService;

    @InjectMocks
    private AgentOperationStatsServiceImpl service;

    @Test
    void shouldCalculateOperationStatsWithActionAuditsScopedByMetricRequestIds() {
        when(metricMapper.selectList(any())).thenReturn(Arrays.asList(
            metric("req-1", false, "[\"ORDER_EXPIRED\"]", 2, 3, 1, 100L),
            metric("req-2", true, "[\"AI_RESULT_INVALID\"]", 1, 1, 0, 300L)
        ));
        when(actionAuditMapper.selectList(any())).thenReturn(Arrays.asList(
            audit("req-1", true),
            audit("req-outside", true)
        ));
        when(feedbackMapper.selectList(any())).thenReturn(Arrays.asList(
            feedback("ACCEPTED", "ORDER_EXPIRED"),
            feedback("REJECTED", "CUSTOMER_EXCLUDE_DATE_HIT")
        ));
        when(businessQueryAuditService.stats(any())).thenReturn(businessStats());

        AgentOperationStatsQuery query = new AgentOperationStatsQuery();
        query.setRecordDateStart("2026-07-01");
        query.setRecordDateEnd("2026-07-31");
        query.setMealType("LUNCH");
        AgentOperationStatsDto stats = service.stats(query);

        assertEquals(2L, stats.getDiagnosisCount());
        assertEquals(1L, stats.getFallbackCount());
        assertEquals(0.5D, stats.getFallbackRate());
        assertEquals(200D, stats.getAverageDiagnosisCostMs());
        assertEquals(1L, stats.getToolFailureCount());
        assertEquals(0.25D, stats.getToolFailureRate());
        assertEquals(3L, stats.getActionDraftCount());
        assertEquals(1L, stats.getActionDraftConfirmedCount());
        assertEquals(1D / 3D, stats.getActionDraftConfirmationRate());
        assertEquals(2L, stats.getFeedbackCount());
        assertEquals(0.5D, stats.getFeedbackAcceptedRate());
        assertEquals(1L, stats.getReasonCodeDistribution().get("ORDER_EXPIRED"));
        assertEquals(1L, stats.getHighFrequencyUnknownReasons().get("AI_RESULT_INVALID"));
        assertEquals(1L, stats.getActualReasonDistribution().get("ORDER_EXPIRED"));
        assertEquals(1L, stats.getFallbackSourceDistribution().get("ELADMIN_CLIENT"));
        assertEquals(1L, stats.getFailureTypeDistribution().get("AGENT_SERVICE_TIMEOUT"));
        assertEquals(4L, stats.getBusinessQueryCount());
        assertEquals(1L, stats.getBusinessQueryPartialCount());
        assertEquals(0.25D, stats.getBusinessQueryPartialRate());
        assertEquals(2L, stats.getBusinessQueryCachedCount());
        assertEquals(0.5D, stats.getBusinessQueryCachedRate());
        assertEquals(1L, stats.getBusinessQueryFailureCount());
        assertEquals(250D, stats.getAverageBusinessQueryCostMs());
        assertEquals(3L, stats.getBusinessQueryToolDistribution().get("customerOverview"));

        verify(actionAuditMapper).selectList(any());
    }

    @Test
    void shouldReturnZeroActionAuditsWhenNoMetricRequestIds() {
        when(metricMapper.selectList(any())).thenReturn(Collections.singletonList(metric(null, false, "[]", 1, 0, 0, 10L)));
        when(feedbackMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(businessQueryAuditService.stats(any())).thenReturn(new AgentBusinessQueryAuditStatsDto());

        AgentOperationStatsDto stats = service.stats(new AgentOperationStatsQuery());

        assertEquals(1L, stats.getActionDraftCount());
        assertEquals(0L, stats.getActionDraftConfirmedCount());
        assertEquals(0D, stats.getActionDraftConfirmationRate());
        verify(actionAuditMapper, never()).selectList(any());
    }

    private AgentDiagnosisMetric metric(String requestId,
                                        boolean fallback,
                                        String reasonCodes,
                                        int actionDraftCount,
                                        int toolCallCount,
                                        int toolFailureCount,
                                        long diagnosisCostMs) {
        AgentDiagnosisMetric metric = new AgentDiagnosisMetric();
        metric.setRequestId(requestId);
        metric.setFallback(fallback);
        metric.setFallbackSource(fallback ? "ELADMIN_CLIENT" : null);
        metric.setFailureType(fallback ? "AGENT_SERVICE_TIMEOUT" : null);
        metric.setReasonCodes(reasonCodes);
        metric.setActionDraftCount(actionDraftCount);
        metric.setToolCallCount(toolCallCount);
        metric.setToolFailureCount(toolFailureCount);
        metric.setDiagnosisCostMs(diagnosisCostMs);
        return metric;
    }

    private AgentActionAudit audit(String requestId, boolean success) {
        AgentActionAudit audit = new AgentActionAudit();
        audit.setRequestId(requestId);
        audit.setSuccess(success);
        return audit;
    }

    private AgentDiagnosisFeedback feedback(String accepted, String actualReasonCode) {
        AgentDiagnosisFeedback feedback = new AgentDiagnosisFeedback();
        feedback.setAccepted(accepted);
        feedback.setActualReasonCode(actualReasonCode);
        return feedback;
    }

    private AgentBusinessQueryAuditStatsDto businessStats() {
        AgentBusinessQueryAuditStatsDto stats = new AgentBusinessQueryAuditStatsDto();
        stats.setQueryCount(4L);
        stats.setPartialCount(1L);
        stats.setPartialRate(0.25D);
        stats.setCachedCount(2L);
        stats.setCachedRate(0.5D);
        stats.setFailureCount(1L);
        stats.setFailureRate(0.25D);
        stats.setAverageCostMs(250D);
        stats.getToolDistribution().put("customerOverview", 3L);
        return stats;
    }
}
