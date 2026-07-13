package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/**
 * 客户姓名模糊查询的候选项，供客服明确选择后继续查询。
 */
@Data
public class AgentCustomerCandidateDto {

    /** 稳定客户 ID。 */
    private Long customerId;
    /** 客户业务编号。 */
    private String customerCode;
    /** 客户姓名。 */
    private String customerName;
    /** 脱敏手机号。 */
    private String maskedPhone;
}
