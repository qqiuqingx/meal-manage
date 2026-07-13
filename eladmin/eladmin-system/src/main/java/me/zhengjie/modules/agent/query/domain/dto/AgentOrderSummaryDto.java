package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Agent 专用订单摘要，仅包含客服只读查询需要的非金额字段。
 */
@Data
public class AgentOrderSummaryDto {

    /** 稳定订单 ID。 */
    private Long orderId;
    /** 订单业务编号。 */
    private String orderCode;
    /** 订单所属客户 ID。 */
    private Long customerId;
    /** 订单所属客户编号。 */
    private String customerCode;
    /** 状态代码。 */
    private Integer statusCode;
    /** 状态展示名称。 */
    private String statusName;
    /** 服务开始日期。 */
    private LocalDate startDate;
    /** 开始餐次代码。 */
    private String startMealTypeCode;
    /** 服务结束日期。 */
    private LocalDate endDate;
    /** 订单餐次类型代码。 */
    private String mealTypeCode;
    /** 订单排餐模式代码。 */
    private String scheduleModeCode;
    /** 父套餐 ID。 */
    private Long parentPackageId;
    /** 父套餐名称。 */
    private String parentPackageName;
    /** 子套餐 ID。 */
    private Long childPackageId;
    /** 子套餐名称。 */
    private String childPackageName;
    /** 关联的有效核销记录数量。 */
    private Integer verificationRecordCount;
    /** 关联的退餐记录数量。 */
    private Integer refundRecordCount;
    /** 关联的有效排餐记录数量。 */
    private Integer mealPlanRecordCount;
    /** 早餐和午晚餐余额明细。 */
    private AgentOrderMealBalanceDto mealBalance;
}
