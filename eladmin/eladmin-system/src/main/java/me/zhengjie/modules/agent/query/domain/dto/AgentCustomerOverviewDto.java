package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 专用客户综合概览，所有字段均为非金额且可审计的只读数据。
 */
@Data
public class AgentCustomerOverviewDto {

    /** 客户是否存在。 */
    private boolean present;
    /** 稳定客户 ID。 */
    private Long customerId;
    /** 客户业务编号。 */
    private String customerCode;
    /** 客户姓名。 */
    private String customerName;
    /** 默认脱敏手机号。 */
    private String maskedPhone;
    /** 客户类型/套餐分类展示值，当前无数据时为空。 */
    private String packageCategoryName;
    /** 过敏标签。 */
    private List<String> allergyTags = new ArrayList<>();
    /** 排除菜品 ID。 */
    private List<Integer> excludedDishIds = new ArrayList<>();
    /** 排除日期与餐次规则。 */
    private List<?> excludedDates = new ArrayList<>();
    /** 限长后的特殊要求。 */
    private String specialRequirements;
    /** 脱敏地址列表。 */
    private List<AgentCustomerAddressDto> addresses = new ArrayList<>();
    /** 客户签约父子套餐摘要，不包含价格或金额。 */
    private List<AgentCustomerPackageDto> packages = new ArrayList<>();
    /** 全部订单数。 */
    private int totalOrderCount;
    /** 进行中订单数。 */
    private int activeOrderCount;
    /** 客户聚合餐数余额。 */
    private AgentOrderMealBalanceDto mealBalance;
    /** 最近一条未删除核销摘要，不包含金额。 */
    private AgentVerificationLogDto latestVerification;
    /** 最近一条退餐摘要，不包含退款金额。 */
    private AgentRefundLogDto latestRefund;
}
