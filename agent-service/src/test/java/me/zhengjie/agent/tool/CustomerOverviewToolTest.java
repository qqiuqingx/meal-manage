package me.zhengjie.agent.tool;

import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.client.dto.CustomerOverviewResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 强类型工具应直接返回客户端 DTO，而非将 Map 暴露到能力层。 */
class CustomerOverviewToolTest {
    @Test
    void returnsTypedCustomerOverview() {
        CustomerOverviewResponse response = new CustomerOverviewResponse(); response.setCustomerCode("C1001");
        BusinessQueryDataClient client = new StubClient(response);
        CustomerOverviewTool tool = new CustomerOverviewTool(client);
        CustomerOverviewResponse actual = tool.execute(new CustomerOverviewTool.Input(1001L, "C1001"));
        assertEquals("C1001", actual.getCustomerCode());
        assertEquals(CustomerOverviewResponse.class, tool.outputType());
    }

    /** 仅实现工具需要的方法；其他历史接口返回空受控结果。 */
    private static class StubClient implements BusinessQueryDataClient {
        private final CustomerOverviewResponse response;
        StubClient(CustomerOverviewResponse response) { this.response = response; }
        public CustomerOverviewResponse customerOverviewTyped(Long customerId, String customerCode) { return response; }
        public Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName) { return Map.of(); }
        public Map<String, Object> customerOverview(Long customerId, String customerCode) { return Map.of(); }
        public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) { return Map.of(); }
        public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) { return Map.of(); }
        public Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit) { return Map.of(); }
        public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) { return Map.of(); }
        public Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType) { return Map.of(); }
        public Map<String, Object> explainRule(String topic) { return Map.of(); }
        public Map<String, Object> listDishes(List<Integer> dishIds) { return Map.of(); }
    }
}
