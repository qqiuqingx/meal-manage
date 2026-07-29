package me.zhengjie.agent.tool;

import me.zhengjie.agent.query.client.BusinessQueryDataClient;
import me.zhengjie.agent.query.client.dto.CustomerOverviewResponse;
import org.springframework.stereotype.Component;

/** 客户概览的首个强类型只读工具；新能力可直接消费 DTO，不读取任意 Map key。 */
@Component
public class CustomerOverviewTool implements AgentTool<CustomerOverviewTool.Input, CustomerOverviewResponse> {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
        "customerOverview", "CUSTOMER", "OVERVIEW", "customerProfile:list", 1, 3000,
        "INTERNAL", true, "v1", "v1", "CustomerOverviewTool.Input", "CustomerOverviewResponse"
    );
    private final BusinessQueryDataClient client;

    public CustomerOverviewTool(BusinessQueryDataClient client) { this.client = client; }
    public ToolDescriptor descriptor() { return DESCRIPTOR; }
    public Class<Input> inputType() { return Input.class; }
    public Class<CustomerOverviewResponse> outputType() { return CustomerOverviewResponse.class; }

    /** 查询已授权客户概览，客户 ID 与客户编号至少由上游受控计划提供其一。 */
    public CustomerOverviewResponse execute(Input input) {
        if (input == null) throw new IllegalArgumentException("customer overview input is required");
        return client.customerOverviewTyped(input.customerId(), input.customerCode());
    }

    /** 工具输入仅包含受控客户标识。 */
    public record Input(Long customerId, String customerCode) { }
}
