package me.zhengjie.modules.agent.service.impl;

import me.zhengjie.modules.agent.domain.AgentBusinessQueryAudit;
import me.zhengjie.modules.agent.domain.dto.AgentBusinessQueryAuditStatsDto;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.mapper.AgentBusinessQueryAuditMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentBusinessQueryAuditServiceImplTest {

    @Mock
    private AgentBusinessQueryAuditMapper auditMapper;

    @InjectMocks
    private AgentBusinessQueryAuditServiceImpl service;

    @Test
    void shouldRecordCachedBusinessQueryAudit() {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("session-1");
        response.setRequestId("req-1");
        response.setStatus("ANSWERED");
        response.setResponseType("BUSINESS_QUERY_CUSTOMER");
        response.setCached(true);
        response.setPartial(false);
        response.setInsightResult(Map.of("total", 2));
        response.setQueryPlan(Map.of(
            "domain", "CUSTOMER",
            "action", "OVERVIEW",
            "analysisSource", "RULE",
            "analysisConfidence", 0.95D,
            "metrics", List.of("MEAL_BALANCE"),
            "dimensions", List.of(),
            "toolNames", List.of("customerOverview"),
            "entities", Map.of("orderId", 1001L, "orderCode", "O1001")
        ));

        service.record(response, "service01", 123L);

        ArgumentCaptor<AgentBusinessQueryAudit> captor = ArgumentCaptor.forClass(AgentBusinessQueryAudit.class);
        verify(auditMapper).insert(captor.capture());
        AgentBusinessQueryAudit audit = captor.getValue();
        assertEquals("service01", audit.getOperator());
        assertEquals("CUSTOMER", audit.getQueryDomain());
        assertEquals("OVERVIEW", audit.getQueryAction());
        assertEquals(2, audit.getResultCount());
        assertTrue(audit.getCached());
        assertEquals(123L, audit.getCostMs());
        assertEquals("RULE", audit.getAnalysisSource());
        assertEquals(0.95D, audit.getAnalysisConfidence());
        assertEquals("[\"MEAL_BALANCE\"]", audit.getMetricCodes());
        assertEquals("VALID", audit.getAnswerValidationResult());
    }

    @Test
    void shouldRecordStableWarningAsPartialQueryFailureType() {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("session-1"); response.setRequestId("req-2"); response.setStatus("ANSWERED");
        response.setResponseType("BUSINESS_QUERY_ORDER"); response.setPartial(true); response.setWarnings(List.of("TOOL_PERMISSION_DENIED"));

        service.record(response, "service01", 10L);

        ArgumentCaptor<AgentBusinessQueryAudit> captor = ArgumentCaptor.forClass(AgentBusinessQueryAudit.class);
        verify(auditMapper).insert(captor.capture());
        assertEquals("TOOL_PERMISSION_DENIED", captor.getValue().getFailureType());
        assertEquals("PARTIAL", captor.getValue().getAnswerValidationResult());
    }

    /** 运营统计澄清不执行工具也没有 QueryPlan，审计仍必须满足数据库非空字段约束。 */
    @Test
    void shouldRecordClarificationWithoutQueryPlan() {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("session-clarify"); response.setRequestId("req-clarify"); response.setStatus("NEED_MORE_INFO");
        response.setResponseType("BUSINESS_QUERY_OPERATION_CLARIFICATION");

        service.record(response, "service01", 5L);

        ArgumentCaptor<AgentBusinessQueryAudit> captor = ArgumentCaptor.forClass(AgentBusinessQueryAudit.class);
        verify(auditMapper).insert(captor.capture());
        assertEquals("OPERATION_STATISTICS", captor.getValue().getQueryDomain());
        assertEquals("CLARIFY", captor.getValue().getQueryAction());
        assertTrue(captor.getValue().getClarificationRequired());
    }

    @Test
    void shouldAggregateBusinessQueryAuditStats() {
        when(auditMapper.selectList(any())).thenReturn(Arrays.asList(
            audit("CUSTOMER", "[\"customerOverview\"]", "[\"MEAL_BALANCE\"]", true, false, null, "VALID", 100L),
            audit("ORDER", "[\"listOrders\",\"customerOverview\"]", "[\"ORDER_COUNT\"]", false, true, "PLAN_INVALID", "PARTIAL", 300L),
            audit("MEAL_PLAN", "[\"listMealPlans\"]", "[\"DAILY_UNVERIFIED_CUSTOMER_COUNT\"]", false, true, "TOOL_PERMISSION_DENIED", "PARTIAL", 200L)
        ));

        AgentBusinessQueryAuditStatsDto stats = service.stats(null);

        assertEquals(3L, stats.getQueryCount());
        assertEquals(2L, stats.getPartialCount());
        assertEquals(2D / 3D, stats.getPartialRate());
        assertEquals(1L, stats.getCachedCount());
        assertEquals(1D / 3D, stats.getCachedRate());
        assertEquals(2L, stats.getFailureCount());
        assertEquals(1L, stats.getPermissionDeniedCount());
        assertEquals(200D, stats.getAverageCostMs());
        assertEquals(300L, stats.getP95CostMs());
        assertEquals(1L, stats.getDirectAnswerCount());
        assertEquals(1D / 3D, stats.getDirectAnswerRate());
        assertEquals(1L, stats.getDomainDistribution().get("CUSTOMER"));
        assertEquals(2L, stats.getToolDistribution().get("customerOverview"));
        assertEquals(1L, stats.getMetricDistribution().get("DAILY_UNVERIFIED_CUSTOMER_COUNT"));
        assertEquals(1L, stats.getFailureTypeDistribution().get("PLAN_INVALID"));
    }

    private AgentBusinessQueryAudit audit(String domain, String tools, String metrics, boolean cached, boolean partial,
                                          String failureType, String answerValidationResult, long costMs) {
        AgentBusinessQueryAudit audit = new AgentBusinessQueryAudit();
        audit.setQueryDomain(domain);
        audit.setToolNames(tools);
        audit.setMetricCodes(metrics);
        audit.setCached(cached);
        audit.setPartial(partial);
        audit.setFailureType(failureType);
        audit.setAnswerValidationResult(answerValidationResult);
        audit.setCostMs(costMs);
        return audit;
    }
}
