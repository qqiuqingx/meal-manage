package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** 主系统 Agent 订单摘要的强类型传输契约，不定义任何金额字段。 */
public class OrderSummaryResponse {
    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper();
    private Long orderId;
    private String orderCode;
    private Long customerId;
    private String customerCode;
    private Integer statusCode;
    private String statusName;
    private String startDate;
    private String startMealTypeCode;
    private String endDate;
    private String mealTypeCode;
    private String scheduleModeCode;
    private Long parentPackageId;
    private String parentPackageName;
    private Long childPackageId;
    private String childPackageName;
    private Integer verificationRecordCount;
    private Integer refundRecordCount;
    private Integer mealPlanRecordCount;
    private MealBalance mealBalance;

    public Long getOrderId() { return orderId; } public void setOrderId(Long value) { orderId = value; }
    public String getOrderCode() { return orderCode; } public void setOrderCode(String value) { orderCode = value; }
    public Long getCustomerId() { return customerId; } public void setCustomerId(Long value) { customerId = value; }
    public String getCustomerCode() { return customerCode; } public void setCustomerCode(String value) { customerCode = value; }
    public Integer getStatusCode() { return statusCode; } public void setStatusCode(Integer value) { statusCode = value; }
    public String getStatusName() { return statusName; } public void setStatusName(String value) { statusName = value; }
    public String getStartDate() { return startDate; } public void setStartDate(String value) { startDate = value; }
    public String getStartMealTypeCode() { return startMealTypeCode; } public void setStartMealTypeCode(String value) { startMealTypeCode = value; }
    public String getEndDate() { return endDate; } public void setEndDate(String value) { endDate = value; }
    public String getMealTypeCode() { return mealTypeCode; } public void setMealTypeCode(String value) { mealTypeCode = value; }
    public String getScheduleModeCode() { return scheduleModeCode; } public void setScheduleModeCode(String value) { scheduleModeCode = value; }
    public Long getParentPackageId() { return parentPackageId; } public void setParentPackageId(Long value) { parentPackageId = value; }
    public String getParentPackageName() { return parentPackageName; } public void setParentPackageName(String value) { parentPackageName = value; }
    public Long getChildPackageId() { return childPackageId; } public void setChildPackageId(Long value) { childPackageId = value; }
    public String getChildPackageName() { return childPackageName; } public void setChildPackageName(String value) { childPackageName = value; }
    public Integer getVerificationRecordCount() { return verificationRecordCount; } public void setVerificationRecordCount(Integer value) { verificationRecordCount = value; }
    public Integer getRefundRecordCount() { return refundRecordCount; } public void setRefundRecordCount(Integer value) { refundRecordCount = value; }
    public Integer getMealPlanRecordCount() { return mealPlanRecordCount; } public void setMealPlanRecordCount(Integer value) { mealPlanRecordCount = value; }
    public MealBalance getMealBalance() { return mealBalance; } public void setMealBalance(MealBalance value) { mealBalance = value; }

    /** 过渡期将测试替身的 Map 转换为强类型订单摘要。 */
    public static OrderSummaryResponse fromLegacyMap(Map<String, Object> value) {
        return value == null || value.isEmpty() ? new OrderSummaryResponse() : LEGACY_MAPPER.convertValue(value, OrderSummaryResponse.class);
    }

    /** 明确生成仅供卡片使用的字段集合。 */
    public Map<String, Object> toPresentationMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId); result.put("orderCode", orderCode); result.put("customerId", customerId); result.put("customerCode", customerCode);
        result.put("statusCode", statusCode); result.put("statusName", statusName); result.put("startDate", startDate); result.put("startMealTypeCode", startMealTypeCode);
        result.put("endDate", endDate); result.put("mealTypeCode", mealTypeCode); result.put("scheduleModeCode", scheduleModeCode);
        result.put("parentPackageId", parentPackageId); result.put("parentPackageName", parentPackageName); result.put("childPackageId", childPackageId); result.put("childPackageName", childPackageName);
        result.put("verificationRecordCount", verificationRecordCount); result.put("refundRecordCount", refundRecordCount); result.put("mealPlanRecordCount", mealPlanRecordCount);
        result.put("mealBalance", mealBalance == null ? null : mealBalance.toPresentationMap());
        return result;
    }

    /** 双餐数池余额传输对象。 */
    public static class MealBalance {
        private int breakfastCount; private int lunchDinnerCount; private int verifiedBreakfast; private int verifiedLunch; private int verifiedDinner;
        private int remainingBreakfast; private int remainingLunchDinner;
        public int getBreakfastCount() { return breakfastCount; } public void setBreakfastCount(int value) { breakfastCount = value; }
        public int getLunchDinnerCount() { return lunchDinnerCount; } public void setLunchDinnerCount(int value) { lunchDinnerCount = value; }
        public int getVerifiedBreakfast() { return verifiedBreakfast; } public void setVerifiedBreakfast(int value) { verifiedBreakfast = value; }
        public int getVerifiedLunch() { return verifiedLunch; } public void setVerifiedLunch(int value) { verifiedLunch = value; }
        public int getVerifiedDinner() { return verifiedDinner; } public void setVerifiedDinner(int value) { verifiedDinner = value; }
        public int getRemainingBreakfast() { return remainingBreakfast; } public void setRemainingBreakfast(int value) { remainingBreakfast = value; }
        public int getRemainingLunchDinner() { return remainingLunchDinner; } public void setRemainingLunchDinner(int value) { remainingLunchDinner = value; }
        private Map<String, Object> toPresentationMap() {
            return Map.of("breakfastCount", breakfastCount, "lunchDinnerCount", lunchDinnerCount, "verifiedBreakfast", verifiedBreakfast,
                "verifiedLunch", verifiedLunch, "verifiedDinner", verifiedDinner, "remainingBreakfast", remainingBreakfast,
                "remainingLunchDinner", remainingLunchDinner);
        }
    }
}
