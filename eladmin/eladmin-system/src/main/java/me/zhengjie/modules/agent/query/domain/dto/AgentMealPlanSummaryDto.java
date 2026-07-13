package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Agent 客户排餐查询摘要。 */
@Data
public class AgentMealPlanSummaryDto {
    /** 排餐主单 ID。 */ private Long mealPlanId;
    /** 客户排餐记录 ID。 */ private Long customerMealPlanId;
    /** 客户 ID。 */ private Long customerId;
    /** 客户业务编号，所有客户相关 Agent 证据的稳定识别信息。 */ private String customerCode;
    /** 订单 ID。 */ private Long orderId;
    /** 使用的父套餐 ID。 */ private Long parentPackageId;
    /** 使用的子套餐 ID。 */ private Long childPackageId;
    /** 排餐日期。 */ private LocalDate recordDate;
    /** 餐次。 */ private String mealTypeCode;
    /** 主单生成状态。 */ private String generationStatus;
    /** 客户排餐状态。 */ private Integer customerPlanStatus;
    /** 是否已核销。 */ private boolean verified;
    /** 首次成功排餐标记。 */ private boolean firstSuccessful;
    /** 失败原因，限长摘要。 */ private String failureReason;
    /** 排餐主单生成时间，用作可追溯批次摘要。 */ private java.sql.Timestamp generateTime;
    /** 脱敏后的实际配送地址。 */ private String maskedDeliveryAddress;
    /** 手工换菜记录数量。 */ private int manualReplaceCount;
    /** 是否由人工新增排餐规则命中。 */ private boolean manualAddition;
    /** 菜品明细。 */ private List<AgentMealPlanDishItemDto> dishes = new ArrayList<>();
    /** 菜品是否截断。 */ private boolean dishesTruncated;
}
