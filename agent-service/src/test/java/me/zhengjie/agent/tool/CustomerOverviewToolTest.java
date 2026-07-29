package me.zhengjie.agent.tool;

import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.client.LegacyMapBusinessQueryDataClientStub;
import me.zhengjie.agent.query.client.dto.CustomerOverviewResponse;
import org.junit.jupiter.api.Test;
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

    /** 仅实现工具需要的强类型方法；其他测试接口返回空受控结果。 */
    private static class StubClient extends LegacyMapBusinessQueryDataClientStub {
        private final CustomerOverviewResponse response;
        StubClient(CustomerOverviewResponse response) { this.response = response; }
        @Override
        public CustomerOverviewResponse customerOverviewTyped(Long customerId, String customerCode) { return response; }
    }
}
