package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主系统客户排餐列表的 Agent 侧强类型传输契约。 */
public class MealPlanListResponse {
    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper();
    private long total;
    private List<MealPlanSummary> items = new ArrayList<>();
    private boolean truncated;
    private int page;
    private int size;
    private String queriedAt;
    public long getTotal() { return total; } public void setTotal(long total) { this.total = total; }
    public List<MealPlanSummary> getItems() { return items; } public void setItems(List<MealPlanSummary> items) { this.items = items == null ? new ArrayList<>() : items; }
    public boolean isTruncated() { return truncated; } public void setTruncated(boolean truncated) { this.truncated = truncated; }
    public int getPage() { return page; } public void setPage(int page) { this.page = page; }
    public int getSize() { return size; } public void setSize(int size) { this.size = size; }
    public String getQueriedAt() { return queriedAt; } public void setQueriedAt(String queriedAt) { this.queriedAt = queriedAt; }
    /** 过渡期适配历史 Map 返回。 */
    public static MealPlanListResponse fromLegacyMap(Map<String, Object> value) {
        return value == null || value.isEmpty() ? new MealPlanListResponse() : LEGACY_MAPPER.convertValue(value, MealPlanListResponse.class);
    }
    /** 显式生成前端卡片所需字段。 */
    public Map<String, Object> toPresentationMap() {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("total", total); result.put("truncated", truncated);
        result.put("page", page); result.put("size", size); result.put("queriedAt", queriedAt);
        result.put("items", items.stream().map(MealPlanSummary::toPresentationMap).toList()); return result;
    }

    /** 单个客户排餐记录摘要。 */
    public static class MealPlanSummary {
        private Long mealPlanId; private Long customerMealPlanId; private Long customerId; private String customerCode; private Long orderId; private Long parentPackageId; private Long childPackageId;
        private String recordDate; private String mealTypeCode; private String generationStatus; private Integer customerPlanStatus; private boolean verified;
        private boolean firstSuccessful; private String failureReason; private String generateTime; private String maskedDeliveryAddress;
        private int manualReplaceCount; private boolean manualAddition; private List<MealPlanDish> dishes = new ArrayList<>(); private boolean dishesTruncated;
        public Long getMealPlanId() { return mealPlanId; } public void setMealPlanId(Long value) { mealPlanId = value; }
        public Long getCustomerMealPlanId() { return customerMealPlanId; } public void setCustomerMealPlanId(Long value) { customerMealPlanId = value; }
        public Long getCustomerId() { return customerId; } public void setCustomerId(Long value) { customerId = value; }
        public String getCustomerCode() { return customerCode; } public void setCustomerCode(String value) { customerCode = value; }
        public Long getOrderId() { return orderId; } public void setOrderId(Long value) { orderId = value; }
        public Long getParentPackageId() { return parentPackageId; } public void setParentPackageId(Long value) { parentPackageId = value; }
        public Long getChildPackageId() { return childPackageId; } public void setChildPackageId(Long value) { childPackageId = value; }
        public String getRecordDate() { return recordDate; } public void setRecordDate(String value) { recordDate = value; }
        public String getMealTypeCode() { return mealTypeCode; } public void setMealTypeCode(String value) { mealTypeCode = value; }
        public String getGenerationStatus() { return generationStatus; } public void setGenerationStatus(String value) { generationStatus = value; }
        public Integer getCustomerPlanStatus() { return customerPlanStatus; } public void setCustomerPlanStatus(Integer value) { customerPlanStatus = value; }
        public boolean isVerified() { return verified; } public void setVerified(boolean value) { verified = value; }
        public boolean isFirstSuccessful() { return firstSuccessful; } public void setFirstSuccessful(boolean value) { firstSuccessful = value; }
        public String getFailureReason() { return failureReason; } public void setFailureReason(String value) { failureReason = value; }
        public String getGenerateTime() { return generateTime; } public void setGenerateTime(String value) { generateTime = value; }
        public String getMaskedDeliveryAddress() { return maskedDeliveryAddress; } public void setMaskedDeliveryAddress(String value) { maskedDeliveryAddress = value; }
        public int getManualReplaceCount() { return manualReplaceCount; } public void setManualReplaceCount(int value) { manualReplaceCount = value; }
        public boolean isManualAddition() { return manualAddition; } public void setManualAddition(boolean value) { manualAddition = value; }
        public List<MealPlanDish> getDishes() { return dishes; } public void setDishes(List<MealPlanDish> value) { dishes = value == null ? new ArrayList<>() : value; }
        public boolean isDishesTruncated() { return dishesTruncated; } public void setDishesTruncated(boolean value) { dishesTruncated = value; }
        private Map<String, Object> toPresentationMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("mealPlanId", mealPlanId); item.put("customerMealPlanId", customerMealPlanId); item.put("customerId", customerId); item.put("customerCode", customerCode); item.put("orderId", orderId);
            item.put("parentPackageId", parentPackageId); item.put("childPackageId", childPackageId); item.put("recordDate", recordDate); item.put("mealTypeCode", mealTypeCode);
            item.put("generationStatus", generationStatus); item.put("customerPlanStatus", customerPlanStatus); item.put("verified", verified); item.put("firstSuccessful", firstSuccessful);
            item.put("failureReason", failureReason); item.put("generateTime", generateTime); item.put("maskedDeliveryAddress", maskedDeliveryAddress);
            item.put("manualReplaceCount", manualReplaceCount); item.put("manualAddition", manualAddition); item.put("dishesTruncated", dishesTruncated);
            item.put("dishes", dishes.stream().map(MealPlanDish::toPresentationMap).toList()); return item;
        }
    }

    /** 排餐菜品明细传输对象。 */
    public static class MealPlanDish {
        private Integer dishId; private String dishName; private String dishType; private boolean replaced; private String replaceReason;
        private boolean allergyFiltered; private List<String> allergyReasons = new ArrayList<>(); private Integer originalDishId; private String originalDishName;
        public Integer getDishId() { return dishId; } public void setDishId(Integer value) { dishId = value; }
        public String getDishName() { return dishName; } public void setDishName(String value) { dishName = value; }
        public String getDishType() { return dishType; } public void setDishType(String value) { dishType = value; }
        public boolean isReplaced() { return replaced; } public void setReplaced(boolean value) { replaced = value; }
        public String getReplaceReason() { return replaceReason; } public void setReplaceReason(String value) { replaceReason = value; }
        public boolean isAllergyFiltered() { return allergyFiltered; } public void setAllergyFiltered(boolean value) { allergyFiltered = value; }
        public List<String> getAllergyReasons() { return allergyReasons; } public void setAllergyReasons(List<String> value) { allergyReasons = value == null ? new ArrayList<>() : value; }
        public Integer getOriginalDishId() { return originalDishId; } public void setOriginalDishId(Integer value) { originalDishId = value; }
        public String getOriginalDishName() { return originalDishName; } public void setOriginalDishName(String value) { originalDishName = value; }
        private Map<String, Object> toPresentationMap() {
            Map<String, Object> item = new LinkedHashMap<>(); item.put("dishId", dishId); item.put("dishName", dishName);
            item.put("dishType", dishType); item.put("replaced", replaced); item.put("replaceReason", replaceReason);
            item.put("allergyFiltered", allergyFiltered); item.put("allergyReasons", allergyReasons);
            item.put("originalDishId", originalDishId); item.put("originalDishName", originalDishName); return item;
        }
    }
}
