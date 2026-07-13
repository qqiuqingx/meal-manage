package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主系统菜品与配料摘要列表的强类型 Agent 传输契约。 */
public class DishListResponse {
    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper();
    private long total; private List<DishSummary> items = new ArrayList<>(); private boolean truncated;
    public long getTotal() { return total; } public void setTotal(long value) { total = value; }
    public List<DishSummary> getItems() { return items; } public void setItems(List<DishSummary> value) { items = value == null ? new ArrayList<>() : value; }
    public boolean isTruncated() { return truncated; } public void setTruncated(boolean value) { truncated = value; }
    /** 过渡期适配未迁移 Map。 */
    public static DishListResponse fromLegacyMap(Map<String, Object> value) { return value == null || value.isEmpty() ? new DishListResponse() : LEGACY_MAPPER.convertValue(value, DishListResponse.class); }
    /** 显式生成菜品卡片数据。 */
    public Map<String, Object> toPresentationMap() { Map<String, Object> result = new LinkedHashMap<>(); result.put("total", total); result.put("truncated", truncated); result.put("items", items.stream().map(DishSummary::toPresentationMap).toList()); return result; }
    /** 单个菜品及受控配料摘要。 */
    public static class DishSummary {
        private Integer dishId; private String dishName; private String dishTypeCode; private String dishTypeName; private List<String> mealTypes = new ArrayList<>(); private Boolean enabled; private List<String> ingredientNames = new ArrayList<>(); private boolean ingredientsTruncated;
        public Integer getDishId() { return dishId; } public void setDishId(Integer value) { dishId = value; }
        public String getDishName() { return dishName; } public void setDishName(String value) { dishName = value; }
        public String getDishTypeCode() { return dishTypeCode; } public void setDishTypeCode(String value) { dishTypeCode = value; }
        public String getDishTypeName() { return dishTypeName; } public void setDishTypeName(String value) { dishTypeName = value; }
        public List<String> getMealTypes() { return mealTypes; } public void setMealTypes(List<String> value) { mealTypes = value == null ? new ArrayList<>() : value; }
        public Boolean getEnabled() { return enabled; } public void setEnabled(Boolean value) { enabled = value; }
        public List<String> getIngredientNames() { return ingredientNames; } public void setIngredientNames(List<String> value) { ingredientNames = value == null ? new ArrayList<>() : value; }
        public boolean isIngredientsTruncated() { return ingredientsTruncated; } public void setIngredientsTruncated(boolean value) { ingredientsTruncated = value; }
        private Map<String, Object> toPresentationMap() { Map<String, Object> item = new LinkedHashMap<>(); item.put("dishId", dishId); item.put("dishName", dishName); item.put("dishTypeCode", dishTypeCode); item.put("dishTypeName", dishTypeName); item.put("mealTypes", mealTypes); item.put("enabled", enabled); item.put("ingredientNames", ingredientNames); item.put("ingredientsTruncated", ingredientsTruncated); return item; }
    }
}
