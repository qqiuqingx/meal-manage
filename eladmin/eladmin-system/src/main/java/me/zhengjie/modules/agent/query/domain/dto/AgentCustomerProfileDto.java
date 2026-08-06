package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 客户档案搜索摘要。
 *
 * <p>该 DTO 仅由已认证且完成权限、数据范围校验的内部 Agent 查询链路使用；姓名可以完整返回，手机号仍必须脱敏。</p>
 */
@Data
public class AgentCustomerProfileDto {
    /** 客户稳定 ID，仅作为工具间不透明关联键。 */
    private Long customerId;
    /** 客户业务编号。 */
    private String customerCode;
    /** 客户完整姓名，仅供已授权的内部 Agent 链路辅助确认客户编号。 */
    private String customerName;
    /** 是否存在订单。 */
    private boolean hasOrder;
    /** 客户档案创建时间。 */
    private LocalDateTime createTime;
    /** 脱敏手机号。 */
    private String maskedPhone;
}
