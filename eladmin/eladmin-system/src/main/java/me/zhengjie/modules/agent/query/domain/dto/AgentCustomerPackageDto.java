package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/**
 * Agent 客户签约套餐摘要，不包含套餐价格或订单金额。
 */
@Data
public class AgentCustomerPackageDto {

    /** 关联订单 ID。 */
    private Long orderId;
    /** 父套餐 ID。 */
    private Long parentPackageId;
    /** 父套餐名称。 */
    private String parentPackageName;
    /** 子套餐 ID。 */
    private Long childPackageId;
    /** 子套餐名称。 */
    private String childPackageName;
    /** 订单是否进行中。 */
    private boolean active;
}
