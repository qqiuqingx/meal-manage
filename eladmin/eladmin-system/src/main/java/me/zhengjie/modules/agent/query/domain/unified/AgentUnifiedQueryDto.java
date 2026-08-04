package me.zhengjie.modules.agent.query.domain.unified;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agent 统一领域接口使用的非金额、脱敏数据契约集合。 */
public final class AgentUnifiedQueryDto {
    private AgentUnifiedQueryDto() { }

    /** 客户档案列表项。 */
    @Data
    public static class ProfileItem {
        /** 客户稳定关联 ID。 */ private Long customerId;
        /** 客户编号。 */ private String customerCode;
        /** 脱敏姓名。 */ private String maskedName;
        /** 是否存在订单。 */ private boolean hasOrder;
        /** 档案创建时间。 */ private LocalDateTime createTime;
        /** 脱敏手机号。 */ private String maskedPhone;
    }

    /** 早餐与午晚餐共享规则下的餐数余额。 */
    @Data
    public static class MealBalanceItem {
        /** 早餐总数。 */ private int breakfastCount;
        /** 午晚餐共享池总数。 */ private int lunchDinnerCount;
        /** 已核销早餐数。 */ private int verifiedBreakfast;
        /** 已核销午餐数。 */ private int verifiedLunch;
        /** 已核销晚餐数。 */ private int verifiedDinner;
        /** 剩余早餐数。 */ private int remainingBreakfast;
        /** 剩余午晚餐数。 */ private int remainingLunchDinner;
    }

    /** 订单根服务客户列表项。 */
    @Data
    public static class ServiceCustomerItem {
        /** 客户稳定关联 ID。 */ private Long customerId;
        /** 客户编号。 */ private String customerCode;
        /** 脱敏姓名。 */ private String maskedName;
        /** 订单稳定关联 ID。 */ private Long orderId;
        /** 订单编号。 */ private String orderCode;
        /** 订单状态。 */ private String status;
        /** 成交时间。 */ private String dealTime;
        /** 创建时间。 */ private String createTime;
        /** 服务开始日期。 */ private String startDate;
        /** 服务结束日期。 */ private String endDate;
        /** 订单开始生效餐次。 */ private String startMealType;
        /** 订单购买餐次类型。 */ private String mealType;
        /** 订单排餐模式。 */ private String scheduleMode;
        /** 受控配送日期列表。 */ private List<String> deliveryDates = new ArrayList<>();
        /** 父套餐名称。 */ private String parentPackageName;
        /** 子套餐名称。 */ private String childPackageName;
        /** 餐数池余额。 */ private MealBalanceItem mealBalance;
        /** 排餐记录数。 */ private int mealPlanCount;
        /** 核销记录数。 */ private int verificationCount;
        /** 退餐记录数。 */ private int refundCount;
    }

    /** 服务客户详情聚合数据。 */
    @Data
    public static class ServiceCustomerDetailItem {
        /** 客户档案摘要。 */ private ProfileItem profile;
        /** 客户饮食限制标签，仅在具体客户详情中返回。 */ private List<String> allergyTags = new ArrayList<>();
        /** 客户主动排除的菜品 ID。 */ private List<Integer> excludedDishIds = new ArrayList<>();
        /** 客户停送日期与餐次摘要。 */ private List<Map<String, Object>> excludedDates = new ArrayList<>();
        /** 客户特殊要求限长摘要。 */ private String specialRequirements;
        /** 脱敏地址摘要。 */ private List<Map<String, Object>> addresses = new ArrayList<>();
        /** 客户关联订单摘要。 */ private List<ServiceCustomerItem> orders = new ArrayList<>();
        /** 最近排餐摘要。 */ private List<Map<String, Object>> mealPlans = new ArrayList<>();
        /** 最近核销摘要。 */ private List<Map<String, Object>> verifications = new ArrayList<>();
        /** 最近退餐摘要。 */ private List<Map<String, Object>> refunds = new ArrayList<>();
        /** 客户详情告警。 */ private List<String> warnings = new ArrayList<>();
    }

    /** 排餐列表项。 */
    @Data
    public static class MealPlanItem {
        /** 客户稳定关联 ID。 */ private Long customerId;
        /** 客户编号。 */ private String customerCode;
        /** 订单稳定关联 ID。 */ private Long orderId;
        /** 排餐日期。 */ private String recordDate;
        /** 餐次。 */ private String mealType;
        /** 生成/客户状态摘要。 */ private String status;
        /** 是否已核销。 */ private boolean verified;
        /** 失败原因摘要。 */ private String failureReason;
        /** 菜品摘要。 */ private List<Map<String, Object>> dishes = new ArrayList<>();
    }

    /** 核销列表项。 */
    @Data
    public static class VerificationItem {
        /** 客户稳定关联 ID。 */ private Long customerId;
        /** 订单稳定关联 ID。 */ private Long orderId;
        /** 排餐日期。 */ private String recordDate;
        /** 餐次。 */ private String mealType;
        /** 核销餐数。 */ private int count;
        /** 是否已退餐。 */ private boolean refunded;
        /** 操作时间。 */ private String operateTime;
    }

    /** 退餐列表项。 */
    @Data
    public static class RefundItem {
        /** 退早餐数。 */ private int breakfastCount;
        /** 退午晚餐数。 */ private int lunchDinnerCount;
        /** 已核销早餐数。 */ private int verifiedBreakfastCount;
        /** 已核销午晚餐数。 */ private int verifiedLunchDinnerCount;
        /** 客户稳定关联 ID。 */ private Long customerId;
        /** 订单稳定关联 ID。 */ private Long orderId;
        /** 退餐原因限长摘要。 */ private String reason;
        /** 操作时间。 */ private String operateTime;
    }

    /** 菜品搜索列表项。 */
    @Data
    public static class DishItem {
        /** 菜品 ID。 */ private Integer dishId;
        /** 菜品名称。 */ private String name;
        /** 菜品类型。 */ private String dishType;
        /** 是否启用。 */ private boolean enabled;
        /** 限量配料摘要。 */ private List<String> ingredients = new ArrayList<>();
    }

    /** 套餐详情项。 */
    @Data
    public static class PackageDetailItem {
        /** 父套餐 ID。 */ private Long packageId;
        /** 父套餐编码。 */ private String packageCode;
        /** 父套餐名称。 */ private String packageName;
        /** 子套餐规格摘要。 */ private List<Map<String, Object>> subPackages = new ArrayList<>();
    }

    /** 运营指标聚合项。 */
    @Data
    public static class MetricItem {
        /** 指标枚举。 */ private String metric;
        /** 指标总数。 */ private long total;
        /** 受控维度聚合。 */ private Map<String, Long> dimensions = new LinkedHashMap<>();
        /** 查询时间。 */ private String queriedAt;
        /** 指标告警。 */ private List<String> warnings = new ArrayList<>();
    }

    /** 版本化规则项。 */
    @Data
    public static class RuleItem {
        /** 规则 ID。 */ private String ruleId;
        /** 规则版本。 */ private String version;
        /** 规则标题。 */ private String title;
        /** 规则正文。 */ private String content;
        /** 生效时间。 */ private String effectiveFrom;
        /** 更新时间。 */ private String updatedAt;
    }
}
