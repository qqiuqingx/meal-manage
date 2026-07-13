package me.zhengjie.agent.query.client.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主系统候选菜预览的 Agent 侧强类型传输契约。 */
public class DishCandidatePreviewResponse {
    private boolean present;
    private Long customerId;
    private String customerCode;
    private String recordDate;
    private String mealTypeCode;
    private List<Long> parentPackageIds = new ArrayList<>();
    private int totalCandidateCount;
    private int availableCandidateCount;
    private int filteredCandidateCount;
    private List<DishCandidateItem> items = new ArrayList<>();
    private boolean truncated;

    public boolean isPresent() { return present; }
    public void setPresent(boolean present) { this.present = present; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public String getRecordDate() { return recordDate; }
    public void setRecordDate(String recordDate) { this.recordDate = recordDate; }
    public String getMealTypeCode() { return mealTypeCode; }
    public void setMealTypeCode(String mealTypeCode) { this.mealTypeCode = mealTypeCode; }
    public List<Long> getParentPackageIds() { return parentPackageIds; }
    public void setParentPackageIds(List<Long> parentPackageIds) { this.parentPackageIds = parentPackageIds == null ? new ArrayList<>() : parentPackageIds; }
    public int getTotalCandidateCount() { return totalCandidateCount; }
    public void setTotalCandidateCount(int totalCandidateCount) { this.totalCandidateCount = totalCandidateCount; }
    public int getAvailableCandidateCount() { return availableCandidateCount; }
    public void setAvailableCandidateCount(int availableCandidateCount) { this.availableCandidateCount = availableCandidateCount; }
    public int getFilteredCandidateCount() { return filteredCandidateCount; }
    public void setFilteredCandidateCount(int filteredCandidateCount) { this.filteredCandidateCount = filteredCandidateCount; }
    public List<DishCandidateItem> getItems() { return items; }
    public void setItems(List<DishCandidateItem> items) { this.items = items == null ? new ArrayList<>() : items; }
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }

    /** 将已验证的传输 DTO 显式转换为前端卡片数据，隔离网络契约与展示层。 */
    public Map<String, Object> toPresentationMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("present", present); result.put("customerId", customerId); result.put("customerCode", customerCode);
        result.put("recordDate", recordDate); result.put("mealTypeCode", mealTypeCode); result.put("parentPackageIds", parentPackageIds);
        result.put("totalCandidateCount", totalCandidateCount); result.put("availableCandidateCount", availableCandidateCount);
        result.put("filteredCandidateCount", filteredCandidateCount); result.put("truncated", truncated);
        result.put("items", items.stream().map(DishCandidateItem::toPresentationMap).toList());
        return result;
    }

    /** 单条候选菜的强类型传输契约。 */
    public static class DishCandidateItem {
        private Integer dishId;
        private String dishName;
        private String dishTypeCode;
        private List<String> ingredientNames = new ArrayList<>();
        private boolean ingredientsTruncated;
        private boolean available;
        private List<String> filterReasons = new ArrayList<>();

        public Integer getDishId() { return dishId; }
        public void setDishId(Integer dishId) { this.dishId = dishId; }
        public String getDishName() { return dishName; }
        public void setDishName(String dishName) { this.dishName = dishName; }
        public String getDishTypeCode() { return dishTypeCode; }
        public void setDishTypeCode(String dishTypeCode) { this.dishTypeCode = dishTypeCode; }
        public List<String> getIngredientNames() { return ingredientNames; }
        public void setIngredientNames(List<String> ingredientNames) { this.ingredientNames = ingredientNames == null ? new ArrayList<>() : ingredientNames; }
        public boolean isIngredientsTruncated() { return ingredientsTruncated; }
        public void setIngredientsTruncated(boolean ingredientsTruncated) { this.ingredientsTruncated = ingredientsTruncated; }
        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
        public List<String> getFilterReasons() { return filterReasons; }
        public void setFilterReasons(List<String> filterReasons) { this.filterReasons = filterReasons == null ? new ArrayList<>() : filterReasons; }

        private Map<String, Object> toPresentationMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dishId", dishId); item.put("dishName", dishName); item.put("dishTypeCode", dishTypeCode);
            item.put("ingredientNames", ingredientNames); item.put("ingredientsTruncated", ingredientsTruncated);
            item.put("available", available); item.put("filterReasons", filterReasons);
            return item;
        }
    }
}
