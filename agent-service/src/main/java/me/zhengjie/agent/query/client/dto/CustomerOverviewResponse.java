package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主系统客户概览的 Agent 侧强类型传输契约。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerOverviewResponse {
    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper();
    private boolean present;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String maskedPhone;
    private List<String> allergyTags = new ArrayList<>();
    private List<Integer> excludedDishIds = new ArrayList<>();
    private List<ExcludedDateRule> excludedDates = new ArrayList<>();
    private String specialRequirements;
    private int totalOrderCount;
    private int activeOrderCount;
    private MealBalance mealBalance;
    private List<CustomerPackage> packages = new ArrayList<>();
    private LatestVerification latestVerification;
    private LatestRefund latestRefund;

    public boolean isPresent() { return present; }
    public void setPresent(boolean present) { this.present = present; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getMaskedPhone() { return maskedPhone; }
    public void setMaskedPhone(String maskedPhone) { this.maskedPhone = maskedPhone; }
    public List<String> getAllergyTags() { return allergyTags; }
    public void setAllergyTags(List<String> allergyTags) { this.allergyTags = allergyTags == null ? new ArrayList<>() : allergyTags; }
    public List<Integer> getExcludedDishIds() { return excludedDishIds; }
    public void setExcludedDishIds(List<Integer> excludedDishIds) { this.excludedDishIds = excludedDishIds == null ? new ArrayList<>() : excludedDishIds; }
    public List<ExcludedDateRule> getExcludedDates() { return excludedDates; }
    public void setExcludedDates(List<ExcludedDateRule> excludedDates) { this.excludedDates = excludedDates == null ? new ArrayList<>() : excludedDates; }
    public String getSpecialRequirements() { return specialRequirements; }
    public void setSpecialRequirements(String specialRequirements) { this.specialRequirements = specialRequirements; }
    public int getTotalOrderCount() { return totalOrderCount; }
    public void setTotalOrderCount(int totalOrderCount) { this.totalOrderCount = totalOrderCount; }
    public int getActiveOrderCount() { return activeOrderCount; }
    public void setActiveOrderCount(int activeOrderCount) { this.activeOrderCount = activeOrderCount; }
    public MealBalance getMealBalance() { return mealBalance; }
    public void setMealBalance(MealBalance mealBalance) { this.mealBalance = mealBalance; }
    public List<CustomerPackage> getPackages() { return packages; }
    public void setPackages(List<CustomerPackage> packages) { this.packages = packages == null ? new ArrayList<>() : packages; }
    public LatestVerification getLatestVerification() { return latestVerification; }
    public void setLatestVerification(LatestVerification latestVerification) { this.latestVerification = latestVerification; }
    public LatestRefund getLatestRefund() { return latestRefund; }
    public void setLatestRefund(LatestRefund latestRefund) { this.latestRefund = latestRefund; }

    /** 将历史测试或未迁移实现返回的 Map 转换为 DTO；正式 HTTP 客户端不使用此路径。 */
    public static CustomerOverviewResponse fromLegacyMap(Map<String, Object> value) {
        return value == null || value.isEmpty() ? new CustomerOverviewResponse() : LEGACY_MAPPER.convertValue(value, CustomerOverviewResponse.class);
    }

    /** 将强类型客户概览转换为受控展示数据；工具和 HTTP 层不读取字符串键。 */
    public Map<String, Object> toPresentationMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("present", present); result.put("customerId", customerId); result.put("customerCode", customerCode);
        result.put("customerName", customerName); result.put("maskedPhone", maskedPhone); result.put("allergyTags", allergyTags);
        result.put("excludedDishIds", excludedDishIds); result.put("excludedDates", excludedDates.stream().map(ExcludedDateRule::toPresentationMap).toList()); result.put("specialRequirements", specialRequirements);
        result.put("totalOrderCount", totalOrderCount); result.put("activeOrderCount", activeOrderCount);
        result.put("mealBalance", mealBalance == null ? null : mealBalance.toPresentationMap());
        result.put("packages", packages.stream().map(CustomerPackage::toPresentationMap).toList());
        result.put("latestVerification", latestVerification == null ? null : latestVerification.toPresentationMap());
        result.put("latestRefund", latestRefund == null ? null : latestRefund.toPresentationMap());
        return result;
    }

    /** 订单餐数余额传输对象。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
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

    /** 客户已签约父子套餐传输对象。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomerPackage {
        private Long orderId; private Long parentPackageId; private String parentPackageName; private Long childPackageId; private String childPackageName; private boolean active;
        public Long getOrderId() { return orderId; } public void setOrderId(Long value) { orderId = value; }
        public Long getParentPackageId() { return parentPackageId; } public void setParentPackageId(Long value) { parentPackageId = value; }
        public String getParentPackageName() { return parentPackageName; } public void setParentPackageName(String value) { parentPackageName = value; }
        public Long getChildPackageId() { return childPackageId; } public void setChildPackageId(Long value) { childPackageId = value; }
        public String getChildPackageName() { return childPackageName; } public void setChildPackageName(String value) { childPackageName = value; }
        public boolean isActive() { return active; } public void setActive(boolean active) { this.active = active; }
        private Map<String, Object> toPresentationMap() {
            Map<String, Object> item = new LinkedHashMap<>(); item.put("orderId", orderId); item.put("parentPackageId", parentPackageId);
            item.put("parentPackageName", parentPackageName); item.put("childPackageId", childPackageId); item.put("childPackageName", childPackageName); item.put("active", active); return item;
        }
    }

    /** 客户排除配送日期及适用餐次的强类型规则。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExcludedDateRule {
        private String date;
        private List<String> mealTypes = new ArrayList<>();
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public List<String> getMealTypes() { return mealTypes; }
        public void setMealTypes(List<String> mealTypes) { this.mealTypes = mealTypes == null ? new ArrayList<>() : mealTypes; }
        private Map<String, Object> toPresentationMap() { Map<String, Object> item = new LinkedHashMap<>(); item.put("date", date); item.put("mealTypes", mealTypes); return item; }
    }

    /** 客户概览中的最近核销摘要。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LatestVerification {
        private Long verificationId; private Long orderId; private String mealTypeCode; private Integer verificationCount; private boolean refunded; private String operateTime;
        public Long getVerificationId() { return verificationId; } public void setVerificationId(Long value) { verificationId = value; }
        public Long getOrderId() { return orderId; } public void setOrderId(Long value) { orderId = value; }
        public String getMealTypeCode() { return mealTypeCode; } public void setMealTypeCode(String value) { mealTypeCode = value; }
        public Integer getVerificationCount() { return verificationCount; } public void setVerificationCount(Integer value) { verificationCount = value; }
        public boolean isRefunded() { return refunded; } public void setRefunded(boolean value) { refunded = value; }
        public String getOperateTime() { return operateTime; } public void setOperateTime(String value) { operateTime = value; }
        private Map<String, Object> toPresentationMap() { Map<String, Object> result = new LinkedHashMap<>(); result.put("verificationId", verificationId); result.put("orderId", orderId); result.put("mealTypeCode", mealTypeCode); result.put("verificationCount", verificationCount); result.put("refunded", refunded); result.put("operateTime", operateTime); return result; }
    }

    /** 客户概览中的最近退餐摘要。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LatestRefund {
        private Long refundId; private Long orderId; private Integer refundBreakfastCount; private Integer refundLunchDinnerCount; private String refundReason; private String operateTime;
        public Long getRefundId() { return refundId; } public void setRefundId(Long value) { refundId = value; }
        public Long getOrderId() { return orderId; } public void setOrderId(Long value) { orderId = value; }
        public Integer getRefundBreakfastCount() { return refundBreakfastCount; } public void setRefundBreakfastCount(Integer value) { refundBreakfastCount = value; }
        public Integer getRefundLunchDinnerCount() { return refundLunchDinnerCount; } public void setRefundLunchDinnerCount(Integer value) { refundLunchDinnerCount = value; }
        public String getRefundReason() { return refundReason; } public void setRefundReason(String value) { refundReason = value; }
        public String getOperateTime() { return operateTime; } public void setOperateTime(String value) { operateTime = value; }
        private Map<String, Object> toPresentationMap() { Map<String, Object> result = new LinkedHashMap<>(); result.put("refundId", refundId); result.put("orderId", orderId); result.put("refundBreakfastCount", refundBreakfastCount); result.put("refundLunchDinnerCount", refundLunchDinnerCount); result.put("refundReason", refundReason); result.put("operateTime", operateTime); return result; }
    }
}
