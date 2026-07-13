package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/**
 * Agent 客户解析受控请求，三种标识至少提供一种。
 */
@Data
public class AgentCustomerResolveRequest {
    /** 客户 ID。 */
    private Long customerId;
    /** 客户编号。 */
    private String customerCode;
    /** 客户姓名关键字。 */
    private String customerName;
}
