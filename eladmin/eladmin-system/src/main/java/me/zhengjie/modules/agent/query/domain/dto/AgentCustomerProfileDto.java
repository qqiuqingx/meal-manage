package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 客户档案搜索摘要。
 *
 * <p>该 DTO 与客户详情 DTO 隔离，姓名和手机号只允许以脱敏值离开主系统。</p>
 */
@Data
public class AgentCustomerProfileDto {
    /** 客户稳定 ID，仅作为工具间不透明关联键。 */
    private Long customerId;
    /** 客户业务编号。 */
    private String customerCode;
    /** 脱敏客户姓名。 */
    private String maskedName;
    /** 是否存在订单。 */
    private boolean hasOrder;
    /** 客户档案创建时间。 */
    private LocalDateTime createTime;
    /** 脱敏手机号。 */
    private String maskedPhone;
}
