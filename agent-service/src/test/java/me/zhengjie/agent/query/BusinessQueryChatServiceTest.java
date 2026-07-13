package me.zhengjie.agent.query;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.query.tool.AgentBusinessToolExecutor.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证查询聊天服务在客户端不可用时不会构造或执行下游工具调用。 */
class BusinessQueryChatServiceTest {

    @Test
    void shouldReturnStablePartialFailureWhenBusinessClientIsUnavailable() {
        BusinessQueryChatService service = new BusinessQueryChatService(null, new AgentQueryPlanValidator(), new BusinessAnswerValidator());
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setCustomerCode("B3303");

        assertFalse(service.isAvailable());
        assertNull(service.createOrchestrator());
        var result = service.execute(null, "BUSINESS_QUERY_CUSTOMER", slots, "customerOverview", null, List.of());

        assertTrue(result.partial());
        assertEquals(List.of("BUSINESS_QUERY_CLIENT_UNAVAILABLE"), result.warnings());
    }

    @Test
    void shouldMergeToolExecutionMetadataAndHidePermissionDeniedDetails() {
        BusinessQueryChatService service = new BusinessQueryChatService(null, new AgentQueryPlanValidator(), new BusinessAnswerValidator());
        AgentChatResponse response = new AgentChatResponse();
        response.setAssistantMessage("原始回答");

        service.applyToolExecution(response, ToolExecutionResult.cached(java.util.Map.of()),
            ToolExecutionResult.failure("TOOL_PERMISSION_DENIED"));

        assertTrue(response.isCached());
        assertTrue(response.isPartial());
        assertEquals(List.of("TOOL_PERMISSION_DENIED"), response.getWarnings());
        assertTrue(response.getAssistantMessage().contains("缺少查询"));
    }
}
