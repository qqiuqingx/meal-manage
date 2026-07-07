package me.zhengjie.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.client.DiagnosisToolDataClient;
import me.zhengjie.agent.domain.dto.DiagnosisToolCandidateDishStatsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolCustomerOrdersRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealRefundsRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolMealPlanLookupRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolPackageSpecRequest;
import me.zhengjie.agent.domain.dto.DiagnosisToolVerificationLogsRequest;
import me.zhengjie.agent.observability.DiagnosisTraceCollector;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void shouldDelegateCustomerExcludeDatesTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getCustomerExcludeDates(DiagnosisToolCustomerLookupRequest request) {
                return Map.of("customerCode", request.getCustomerCode(), "present", true);
            }
        });
        DiagnosisToolCustomerLookupRequest request = new DiagnosisToolCustomerLookupRequest();
        request.setCustomerCode("C1001");

        Map<String, Object> result = registry.getCustomerExcludeDates(request);

        assertEquals("C1001", result.get("customerCode"));
        assertEquals(true, result.get("present"));
    }

    @Test
    void shouldDelegateOrderMealBalanceTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getOrderMealBalance(DiagnosisToolCustomerOrdersRequest request) {
                return Map.of("customerId", request.getCustomerId(), "orderCount", 2);
            }
        });
        DiagnosisToolCustomerOrdersRequest request = new DiagnosisToolCustomerOrdersRequest();
        request.setCustomerId(1001L);

        Map<String, Object> result = registry.getOrderMealBalance(request);

        assertEquals(1001L, result.get("customerId"));
        assertEquals(2, result.get("orderCount"));
    }

    @Test
    void shouldDelegatePackageSpecTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getPackageSpec(DiagnosisToolPackageSpecRequest request) {
                return Map.of("parentPackageId", request.getParentPackageId(), "present", true);
            }
        });
        DiagnosisToolPackageSpecRequest request = new DiagnosisToolPackageSpecRequest();
        request.setParentPackageId(3001L);

        Map<String, Object> result = registry.getPackageSpec(request);

        assertEquals(3001L, result.get("parentPackageId"));
        assertEquals(true, result.get("present"));
    }

    @Test
    void shouldDelegateDishCandidateDetailTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public List<Map<String, Object>> getDishCandidateDetail(DiagnosisToolCandidateDishStatsRequest request) {
                return List.of(Map.of("recordDate", request.getRecordDate(), "candidateCount", 8));
            }
        });
        DiagnosisToolCandidateDishStatsRequest request = new DiagnosisToolCandidateDishStatsRequest();
        request.setRecordDate("2026-05-19");

        List<Map<String, Object>> result = registry.getDishCandidateDetail(request);

        assertEquals("2026-05-19", result.get(0).get("recordDate"));
        assertEquals(8, result.get(0).get("candidateCount"));
    }

    @Test
    void shouldDelegateVerificationLogsTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public List<Map<String, Object>> listVerificationLogs(DiagnosisToolVerificationLogsRequest request) {
                return List.of(Map.of("orderId", request.getOrderId(), "mealType", request.getMealType()));
            }
        });
        DiagnosisToolVerificationLogsRequest request = new DiagnosisToolVerificationLogsRequest();
        request.setOrderId(2001L);
        request.setMealType("LUNCH");

        List<Map<String, Object>> result = registry.listVerificationLogs(request);

        assertEquals(2001L, result.get(0).get("orderId"));
        assertEquals("LUNCH", result.get(0).get("mealType"));
    }

    @Test
    void shouldDelegateMealRefundsTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public List<Map<String, Object>> listMealRefunds(DiagnosisToolMealRefundsRequest request) {
                return List.of(Map.of("orderId", request.getOrderId(), "refundLunchDinnerCount", 3));
            }
        });
        DiagnosisToolMealRefundsRequest request = new DiagnosisToolMealRefundsRequest();
        request.setOrderId(2001L);

        List<Map<String, Object>> result = registry.listMealRefunds(request);

        assertEquals(2001L, result.get(0).get("orderId"));
        assertEquals(3, result.get(0).get("refundLunchDinnerCount"));
    }

    @Test
    void shouldDelegateMealPlanGenerationSnapshotTool() {
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getMealPlanGenerationSnapshot(DiagnosisToolMealPlanLookupRequest request) {
                return Map.of("recordDate", request.getRecordDate(), "present", true);
            }
        });
        DiagnosisToolMealPlanLookupRequest request = new DiagnosisToolMealPlanLookupRequest();
        request.setRecordDate("2026-05-19");
        request.setMealType("DINNER");

        Map<String, Object> result = registry.getMealPlanGenerationSnapshot(request);

        assertEquals("2026-05-19", result.get("recordDate"));
        assertEquals(true, result.get("present"));
    }

    @Test
    void shouldLogFullInputAndOutputForToolCalls() throws Exception {
        RecordingLogSink logSink = new RecordingLogSink();
        DiagnosisTraceCollector traceCollector = new DiagnosisTraceCollector();
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getCustomerProfile(DiagnosisToolCustomerLookupRequest request) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("customerId", request.getCustomerId());
                result.put("customerName", "张三");
                return result;
            }
        }, new ObjectMapper(), traceCollector, logSink);

        MDC.put("requestId", "trace-1001");
        traceCollector.openSession(8);
        DiagnosisToolCustomerLookupRequest request = new DiagnosisToolCustomerLookupRequest();
        request.setCustomerId(1001L);
        request.setCustomerCode("C1001");

        Map<String, Object> result = registry.getCustomerProfile(request);

        assertEquals(1001L, result.get("customerId"));
        assertEquals(2, logSink.events.size());
        assertTrue(logSink.events.get(0).startsWith("start:getCustomerProfile:trace-1001:"));
        assertTrue(logSink.events.get(1).startsWith("completed:getCustomerProfile:trace-1001:"));
        assertEquals(1, traceCollector.snapshotToolSummary().size());
        traceCollector.closeSession();
    }

    @Test
    void shouldLogFailureWithFullInput() {
        RecordingLogSink logSink = new RecordingLogSink();
        DiagnosisTraceCollector traceCollector = new DiagnosisTraceCollector();
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getMealPlan(DiagnosisToolMealPlanLookupRequest request) {
                throw new IllegalStateException("broken");
            }
        }, new ObjectMapper(), traceCollector, logSink);

        MDC.put("requestId", "trace-1002");
        traceCollector.openSession(8);
        DiagnosisToolMealPlanLookupRequest request = new DiagnosisToolMealPlanLookupRequest();
        request.setRecordDate("2026-05-19");
        request.setMealType("LUNCH");

        assertThrows(IllegalStateException.class, () -> registry.getMealPlan(request));

        assertEquals(2, logSink.events.size());
        assertTrue(logSink.events.get(0).startsWith("start:getMealPlan:trace-1002:"));
        assertTrue(logSink.events.get(1).startsWith("failed:getMealPlan:trace-1002:"));
        assertTrue(traceCollector.shouldFallback());
        traceCollector.closeSession();
    }

    @Test
    void shouldReuseSameToolResultForSameInput() {
        RecordingLogSink logSink = new RecordingLogSink();
        DiagnosisTraceCollector traceCollector = new DiagnosisTraceCollector();
        AtomicInteger count = new AtomicInteger();
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getCustomerProfile(DiagnosisToolCustomerLookupRequest request) {
                count.incrementAndGet();
                return Map.of("customerId", request.getCustomerId());
            }
        }, new ObjectMapper(), traceCollector, logSink);
        traceCollector.openSession(8);

        DiagnosisToolCustomerLookupRequest request = new DiagnosisToolCustomerLookupRequest();
        request.setCustomerId(1001L);
        registry.getCustomerProfile(request);
        registry.getCustomerProfile(request);

        assertEquals(1, count.get());
        assertTrue(logSink.events.stream().anyMatch(event -> event.startsWith("cache:getCustomerProfile:")));
        assertEquals(2, traceCollector.snapshotToolSummary().size());
        traceCollector.closeSession();
    }

    @Test
    void shouldRejectToolCallWhenBudgetExceeded() {
        RecordingLogSink logSink = new RecordingLogSink();
        DiagnosisTraceCollector traceCollector = new DiagnosisTraceCollector();
        AgentToolRegistry registry = new AgentToolRegistry(new StubDiagnosisToolDataClient() {
            @Override
            public Map<String, Object> getCustomerProfile(DiagnosisToolCustomerLookupRequest request) {
                return Map.of("customerId", request.getCustomerId());
            }
        }, new ObjectMapper(), traceCollector, logSink);
        traceCollector.openSession(1);

        DiagnosisToolCustomerLookupRequest first = new DiagnosisToolCustomerLookupRequest();
        first.setCustomerId(1001L);
        DiagnosisToolCustomerLookupRequest second = new DiagnosisToolCustomerLookupRequest();
        second.setCustomerId(1002L);

        registry.getCustomerProfile(first);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> registry.getCustomerProfile(second));

        assertEquals("tool call budget exceeded", ex.getMessage());
        assertTrue(logSink.events.stream().anyMatch(event -> event.startsWith("rejected:getCustomerProfile:")));
        assertTrue(traceCollector.shouldFallback());
        traceCollector.closeSession();
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

        @Override
        public Map<String, Object> getCustomerExcludeDates(DiagnosisToolCustomerLookupRequest request) {
            return Map.of();
        }

        @Override
        public Map<String, Object> getOrderMealBalance(DiagnosisToolCustomerOrdersRequest request) {
            return Map.of();
        }

        @Override
        public Map<String, Object> getPackageSpec(DiagnosisToolPackageSpecRequest request) {
            return Map.of();
        }

        @Override
        public List<Map<String, Object>> getDishCandidateDetail(DiagnosisToolCandidateDishStatsRequest request) {
            return List.of();
        }

        @Override
        public List<Map<String, Object>> listVerificationLogs(DiagnosisToolVerificationLogsRequest request) {
            return List.of();
        }

        @Override
        public List<Map<String, Object>> listMealRefunds(DiagnosisToolMealRefundsRequest request) {
            return List.of();
        }

        @Override
        public Map<String, Object> getMealPlanGenerationSnapshot(DiagnosisToolMealPlanLookupRequest request) {
            return Map.of();
        }
    }

    private static class RecordingLogSink implements AgentToolRegistry.LogSink {
        private final List<String> events = new java.util.ArrayList<>();

        @Override
        public void toolCallStarted(String toolName, String requestId, String inputDigest) {
            events.add("start:" + toolName + ":" + requestId + ":" + inputDigest);
        }

        @Override
        public void toolCallCompleted(String toolName, String requestId, String inputDigest, int resultCount, long costMs) {
            events.add("completed:" + toolName + ":" + requestId + ":" + inputDigest + ":" + resultCount);
        }

        @Override
        public void toolCallFailed(String toolName, String requestId, String inputDigest, long costMs, RuntimeException ex) {
            events.add("failed:" + toolName + ":" + requestId + ":" + inputDigest + ":" + ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }

        @Override
        public void toolCallCacheHit(String toolName, String requestId, String inputDigest) {
            events.add("cache:" + toolName + ":" + requestId + ":" + inputDigest);
        }

        @Override
        public void toolCallRejected(String toolName, String requestId, String inputDigest, RuntimeException ex) {
            events.add("rejected:" + toolName + ":" + requestId + ":" + inputDigest + ":" + ex.getMessage());
        }
    }
}
