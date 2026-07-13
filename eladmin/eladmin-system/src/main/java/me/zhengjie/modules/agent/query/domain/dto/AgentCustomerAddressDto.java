package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/**
 * Agent 专用客户地址摘要，联系方式和地址均按最小展示原则脱敏。
 */
@Data
public class AgentCustomerAddressDto {

    /** 地址类型代码。 */
    private String addressTypeCode;
    /** 地址类型展示名称。 */
    private String addressTypeName;
    /** 脱敏配送地址。 */
    private String maskedAddress;
    /** 脱敏联系人姓名。 */
    private String maskedContactName;
    /** 脱敏联系人电话。 */
    private String maskedContactPhone;
}
