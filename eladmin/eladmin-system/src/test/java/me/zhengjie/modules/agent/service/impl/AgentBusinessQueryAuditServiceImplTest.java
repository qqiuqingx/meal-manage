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
        response.setCached(true);
        response.setPartial(false);
        me.zhengjie.modules.agent.domain.dto.DiagnosisSlots slots = new me.zhengjie.modules.agent.domain.dto.DiagnosisSlots();
        slots.setCustomerId(1000L); slots.setCustomerCode("C1000"); slots.setOrderId(1001L);
        slots.setOrderCode("O1001"); slots.setRecordDate("2026-07-14"); response.setSlots(slots);
        response.setCards(List.of(Map.of("type", "SERVICE_CUSTOMER_DETAIL", "data", Map.of("total", 2))));
        response.setToolFacts(List.of(Map.of("toolName", "getServiceCustomerDetail", "data", Map.of("total", 2))));
        response.setToolTraceSummary(List.of(Map.of("toolName", "getServiceCustomerDetail", "resultCount", 2)));

        service.record(response, "service01", 123L);

        ArgumentCaptor<AgentBusinessQueryAudit> captor = ArgumentCaptor.forClass(AgentBusinessQueryAudit.class);
        verify(auditMapper).insert(captor.capture());
        AgentBusinessQueryAudit audit = captor.getValue();
        assertEquals("service01", audit.getOperator());
        assertEquals("CUSTOMER", audit.getQueryDomain());
        assertEquals("DETAIL", audit.getQueryAction());
        assertEquals(2, audit.getResultCount());
        assertTrue(audit.getCached());
        assertEquals(123L, audit.getCostMs());
        assertEquals("LLM_TOOL_CALLING", audit.getAnalysisSource());
        assertEquals("2026-07-14", audit.getResolvedRecordDate());
        assertEquals("O1001", audit.getOrderCode());
        assertEquals("[\"getServiceCustomerDetail\"]", audit.getToolNames());
        assertEquals("[]", audit.getMetricCodes());
        assertEquals("VALID", audit.getAnswerValidationResult());
    }

    /** 统一工具调用响应不再写入固定查询计划字段。 */
    @Test
    void shouldRecordToolCallingWithoutLegacySemanticFields() {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("session-tool"); response.setRequestId("req-tool"); response.setStatus("ANSWERED");
        response.setCards(List.of(Map.of("type", "MEAL_PLAN_LIST", "data", Map.of("items", List.of()))));
        response.setToolTraceSummary(List.of(Map.of("toolName", "listMealPlans", "resultCount", 0)));

        service.record(response, "service01", 10L);

        ArgumentCaptor<AgentBusinessQueryAudit> captor = ArgumentCaptor.forClass(AgentBusinessQueryAudit.class);
        verify(auditMapper).insert(captor.capture());
        assertEquals("LLM_TOOL_CALLING", captor.getValue().getAnalysisSource());
    }

    @Test
    void shouldRecordStableWarningAsPartialQueryFailureType() {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("session-1"); response.setRequestId("req-2"); response.setStatus("ANSWERED");
        response.setPartial(true); response.setWarnings(List.of("searchServiceCustomers:TOOL_PERMISSION_DENIED"));

        service.record(response, "service01", 10L);

        ArgumentCaptor<AgentBusinessQueryAudit> captor = ArgumentCaptor.forClass(AgentBusinessQueryAudit.class);
        verify(auditMapper).insert(captor.capture());
        assertEquals("TOOL_PERMISSION_DENIED", captor.getValue().getFailureType());
        assertEquals("PARTIAL", captor.getValue().getAnswerValidationResult());
    }

    /** 澄清不执行工具时仍必须写入稳定领域和动作，满足数据库非空约束。 */
    @Test
    void shouldRecordClarificationWithoutLegacySemanticFields() {
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId("session-clarify"); response.setRequestId("req-clarify"); response.setStatus("NEED_MORE_INFO");

        service.record(response, "service01", 5L);

        ArgumentCaptor<AgentBusinessQueryAudit> captor = ArgumentCaptor.forClass(AgentBusinessQueryAudit.class);
        verify(auditMapper).insert(captor.capture());
        assertEquals("BUSINESS_QUERY", captor.getValue().getQueryDomain());
        assertEquals("CLARIFY", captor.getValue().getQueryAction());
        assertTrue(captor.getValue().getClarificationRequired());
    }

    @Test
    void shouldAggregateBusinessQueryAuditStats() {
        when(auditMapper.selectList(any())).thenAnswer(invocation -> {
            List<AgentBusinessQueryAudit> audits = Arrays.asList(
                audit("CUSTOMER", "[\"searchCustomerProfiles\"]", "[\"ACTIVE_SERVICE_CUSTOMER_COUNT\"]", true, false, null, "VALID", 100L),
                audit("ORDER", "[\"searchServiceCustomers\",\"getServiceCustomerDetail\"]", "[\"ACTIVE_ORDER_COUNT\"]", false, true, "TOOL_INPUT_INVALID", "PARTIAL", 300L),
                audit("MEAL_PLAN", "[\"listMealPlans\"]", "[\"EXPIRING_ORDER_COUNT\"]", false, true, "TOOL_PERMISSION_DENIED", "PARTIAL", 200L)
            );
            audits.get(0).setAnalysisSource("LLM");
            audits.get(1).setAnalysisSource("LLM_TOOL_CALLING");
            audits.get(2).setAnalysisSource("LLM_TOOL_CALLING");
            return audits;
        });

        AgentBusinessQueryAuditStatsDto stats = service.stats(null);

        assertEquals(3L, stats.getQueryCount());
        assertEquals(2L, stats.getPartialCount());
        assertEquals(2D / 3D, stats.getPartialRate());
        assertEquals(1L, stats.getCachedCount());
        assertEquals(1D / 3D, stats.getCachedRate());
        assertEquals(2L, stats.getFailureCount());
        assertEquals(1L, stats.getPermissionDeniedCount());
        assertEquals(200D, stats.getAverageCostMs());
        assertEquals(200L, stats.getP50CostMs());
        assertEquals(300L, stats.getP95CostMs());
        assertEquals(1L, stats.getDirectAnswerCount());
        assertEquals(1D / 3D, stats.getDirectAnswerRate());
        assertEquals(1L, stats.getDomainDistribution().get("CUSTOMER"));
        assertEquals(1L, stats.getToolDistribution().get("searchCustomerProfiles"));
        assertEquals(1L, stats.getMetricDistribution().get("EXPIRING_ORDER_COUNT"));
        assertEquals(1L, stats.getFailureTypeDistribution().get("TOOL_INPUT_INVALID"));
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
