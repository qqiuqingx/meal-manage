package me.zhengjie.agent.tool.output;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统一领域工具输出模型。
 *
 * <p>模型只能看到主系统裁剪后的这些类型；这里不定义金额、完整手机号、完整地址、权限或 Token 字段。</p>
 */
public final class ToolOutputs {

    private ToolOutputs() { }

    /** 统一列表/单对象工具返回信封。泛型只用于 Agent 内部适配，序列化结构固定。 */
    public static class ToolResult<T> {
        private String schemaVersion = "v1";
        private List<T> items = new ArrayList<>();
        private long total;
        private int page = 1;
        private int size;
        private boolean truncated;
        private String queriedAt;
        private T data;
        private List<String> warnings = new ArrayList<>();

        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String value) { schemaVersion = value; }
        public List<T> getItems() { return items; }
        public void setItems(List<T> value) { items = value == null ? new ArrayList<>() : value; }
        public long getTotal() { return total; }
        public void setTotal(long value) { total = value; }
        public int getPage() { return page; }
        public void setPage(int value) { page = value; }
        public int getSize() { return size; }
        public void setSize(int value) { size = value; }
        public boolean isTruncated() { return truncated; }
        public void setTruncated(boolean value) { truncated = value; }
        public String getQueriedAt() { return queriedAt; }
        public void setQueriedAt(String value) { queriedAt = value; }
        public T getData() { return data; }
        public void setData(T value) { data = value; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> value) { warnings = value == null ? new ArrayList<>() : value; }
    }

    /** 客户档案摘要；完整姓名仅由已授权的内部 Agent 查询链路返回，手机号仍由主系统脱敏。 */
    public record CustomerProfile(Long customerId, String customerCode, String customerName,
                                  boolean hasOrder, String createTime, String maskedPhone) { }

    /** 早餐和午晚餐独立餐数池。 */
    public record MealBalance(int breakfastCount, int lunchDinnerCount, int verifiedBreakfast,
                              int verifiedLunch, int verifiedDinner, int remainingBreakfast,
                              int remainingLunchDinner) { }

    /** 订单根服务客户行；姓名用于编号辅助确认，内部 ID 只供模型跨工具关联。 */
    public record ServiceCustomer(Long customerId, String customerCode, String customerName,
                                  Long orderId, String orderCode, String status, String dealTime,
                                  String createTime, String orderTime, String startDate, String endDate, String startMealType,
                                  String mealType, String scheduleMode, List<String> deliveryDates,
                                  String parentPackageName, String childPackageName,
                                  MealBalance mealBalance, int mealPlanCount, int verificationCount,
                                  int refundCount) { }

    /** 客户综合快照。 */
    public record ServiceCustomerDetail(CustomerProfile profile, List<String> allergyTags,
                                        List<Integer> excludedDishIds, List<Map<String, Object>> excludedDates,
                                        String specialRequirements, List<Map<String, Object>> addresses,
                                        List<ServiceCustomer> orders, List<Map<String, Object>> mealPlans,
                                        List<Map<String, Object>> verifications,
                                        List<Map<String, Object>> refunds,
                                        List<String> warnings) { }

    /** 排餐行，菜品明细仍由主系统裁剪并限量。 */
    public record MealPlan(Long customerId, String customerCode, Long orderId, String recordDate,
                           String mealType, String status, boolean verified, String failureReason,
                           List<Map<String, Object>> dishes) { }

    /** 核销记录；不包含价格和金额。 */
    public record Verification(Long customerId, Long orderId, String recordDate, String mealType,
                               int count, boolean refunded, String operateTime) { }

    /** 退餐记录；不包含退款金额。 */
    public record Refund(Long customerId, Long orderId, int breakfastCount, int lunchDinnerCount,
                         int verifiedBreakfastCount, int verifiedLunchDinnerCount,
                         String reason, String operateTime) { }

    /** 菜品摘要；自由文本仅允许主系统裁剪后的摘要。 */
    public record Dish(Integer dishId, String name, String dishType, boolean enabled,
                       List<String> ingredients) { }

    /** 父子套餐和受控规格摘要。 */
    public record PackageDetail(Long packageId, String packageCode, String packageName,
                                List<Map<String, Object>> subPackages) { }

    /** 指标展示分组；只含服务端生成的标签和值。 */
    public record MetricBreakdown(String label, long value) { }

    /** 运营指标结果；维度键和展示分组均由服务端枚举生成。 */
    public record Metric(String metric, long total, Map<String, Long> dimensions,
                         List<MetricBreakdown> breakdown,
                         String queriedAt, List<String> warnings) { }

    /** 版本化业务规则。 */
    public record BusinessRule(String ruleId, String version, String title, String content,
                                String effectiveFrom, String updatedAt) { }
}
