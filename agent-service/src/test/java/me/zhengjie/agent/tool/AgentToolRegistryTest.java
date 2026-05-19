package me.zhengjie.agent.tool;

import me.zhengjie.agent.client.DiagnosisToolDataClient;
import me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentToolRegistryTest {

    @Test
    void shouldDelegateCustomerProfileTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getCustomerProfile(DiagnosisToolCustomerLookupRequest request) {
                return Map.of("customerId", request.getCustomerId(), "customerName", "张三");
            }
        });
        DiagnosisToolCustomerLookupRequest request = new DiagnosisToolCustomerLookupRequest();
        request.setCustomerId(1001L);

        Map<String, Object> result = registry.getCustomerProfile(request);

        assertEquals(1001L, result.get("customerId"));
        assertEquals("张三", result.get("customerName"));
    }

    @Test
    void shouldDelegateCustomerOrdersTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public List<Map<String, Object>> listCustomerOrders(DiagnosisToolCustomerOrdersRequest request) {
                return List.of(Map.of("customerCode", request.getCustomerCode(), "packageName", "控糖套餐"));
            }
        });
        DiagnosisToolCustomerOrdersRequest request = new DiagnosisToolCustomerOrdersRequest();
        request.setCustomerCode("C1001");

        List<Map<String, Object>> result = registry.listCustomerOrders(request);

        assertEquals("C1001", result.get(0).get("customerCode"));
        assertEquals("控糖套餐", result.get(0).get("packageName"));
    }

    @Test
    void shouldDelegateMealPlanTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getMealPlan(DiagnosisToolMealPlanLookupRequest request) {
                return Map.of("recordDate", request.getRecordDate(), "mealType", request.getMealType(), "status", "SUCCESS");
            }
        });
        DiagnosisToolMealPlanLookupRequest request = new DiagnosisToolMealPlanLookupRequest();
        request.setRecordDate("2026-05-19");
        request.setMealType("LUNCH");

        Map<String, Object> result = registry.getMealPlan(request);

        assertEquals("2026-05-19", result.get("recordDate"));
        assertEquals("LUNCH", result.get("mealType"));
        assertEquals("SUCCESS", result.get("status"));
    }

    @Test
    void shouldDelegateCandidateDishStatsTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public List<Map<String, Object>> getCandidateDishStats(DiagnosisToolCandidateDishStatsRequest request) {
                return List.of(Map.of("recordDate", request.getRecordDate(), "candidateCount", 12));
            }
        });
        DiagnosisToolCandidateDishStatsRequest request = new DiagnosisToolCandidateDishStatsRequest();
        request.setRecordDate("2026-05-19");

        List<Map<String, Object>> result = registry.getCandidateDishStats(request);

        assertEquals("2026-05-19", result.get(0).get("recordDate"));
        assertEquals(12, result.get(0).get("candidateCount"));
    }

    private static class StubDiagnosisToolDataClient implements DiagnosisToolDataClient {

        @Override
        public Map<String, Object> getCustomerProfile(DiagnosisToolCustomerLookupRequest request) {
            return Map.of();
        }

        @Override
        public List<Map<String, Object>> listCustomerOrders(DiagnosisToolCustomerOrdersRequest request) {
            return List.of();
        }

        @Override
        public Map<String, Object> getMealPlan(DiagnosisToolMealPlanLookupRequest request) {
            return Map.of();
        }

        @Override
        public List<Map<String, Object>> getCandidateDishStats(DiagnosisToolCandidateDishStatsRequest request) {
            return List.of();
        }
    }
}
