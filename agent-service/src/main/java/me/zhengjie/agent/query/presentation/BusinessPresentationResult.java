package me.zhengjie.agent.query.presentation;

import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentMetricDefinition;
import me.zhengjie.agent.query.domain.AgentQueryMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presenter 使用的受控业务结果 DTO。
 *
 * <p>受控展示 Map 只在 {@link #fromPresentationMap(Map)} 边界读取；回答组装器只能调用显式字段方法，
 * 不能读取任意字符串键。原始受控展示 Map 仅供兼容响应序列化，不参与回答逻辑。</p>
 */
public final class BusinessPresentationResult {

    private final boolean empty;
    private final boolean present;
    private final boolean presentDeclared;
    private final boolean truncated;
    private final boolean itemsDeclared;
    private final boolean groupsDeclared;
    private final Object total;
    private final Object activeOrderCount;
    private final Object verificationRecordCount;
    private final Object refundRecordCount;
    private final Object remainingBreakfast;
    private final Object remainingLunchDinner;
    private final Object totalRemaining;
    private final Object verifiedBreakfast;
    private final Object verifiedLunch;
    private final Object verifiedDinner;
    private final Object totalVerified;
    private final Object totalVerifiedBreakfast;
    private final Object totalVerifiedLunch;
    private final Object totalVerifiedDinner;
    private final Object totalCandidateCount;
    private final Object availableCandidateCount;
    private final Object filteredCandidateCount;
    private final Object scannedCount;
    private final String customerCode;
    private final String customerName;
    private final String orderCode;
    private final String dealTime;
    private final String createTime;
    private final String firstPurchaseTime;
    private final String parentPackageName;
    private final String childPackageName;
    private final String dishName;
    private final String recordDate;
    private final String mealTypeCode;
    private final String mealTypeName;
    private final String dishTypeCode;
    private final String generationStatus;
    private final String content;
    private final String metricDefinitionId;
    private final String version;
    private final String ruleId;
    private final String evidenceDocument;
    private final String evidenceAnchor;
    private final String replaceReason;
    private final Long customerId;
    private final Long orderId;
    private final Long customerMealPlanId;
    private final boolean allergyFiltered;
    private final List<String> ingredientNames;
    private final List<String> allergyReasons;
    private final List<BusinessPresentationResult> items;
    private final List<BusinessPresentationResult> groups;
    private final List<BusinessPresentationResult> packages;
    private final List<BusinessPresentationResult> dishes;
    private final List<AgentQueryMetric> reportMetrics;
    private final int recentVerificationCount;
    private final BusinessPresentationResult mealBalance;
    private final Map<AgentQueryMetric, Object> metricValues;
    private final Map<String, Object> presentationMap;

    private BusinessPresentationResult(Map<String, Object> source) {
        Map<String, Object> safe = source == null ? Map.of() : source;
        this.empty = safe.isEmpty();
        this.present = Boolean.TRUE.equals(safe.get("present"));
        this.presentDeclared = safe.containsKey("present");
        this.truncated = Boolean.TRUE.equals(safe.get("truncated"));
        this.itemsDeclared = safe.get("items") instanceof List<?>;
        this.groupsDeclared = safe.get("groups") instanceof List<?>;
        this.total = safe.get("total");
        this.activeOrderCount = safe.get("activeOrderCount");
        this.verificationRecordCount = safe.get("verificationRecordCount");
        this.refundRecordCount = safe.get("refundRecordCount");
        this.remainingBreakfast = safe.get("remainingBreakfast");
        this.remainingLunchDinner = safe.get("remainingLunchDinner");
        this.totalRemaining = safe.get("totalRemaining");
        this.verifiedBreakfast = safe.get("verifiedBreakfast");
        this.verifiedLunch = safe.get("verifiedLunch");
        this.verifiedDinner = safe.get("verifiedDinner");
        this.totalVerified = safe.get("totalVerified");
        this.totalVerifiedBreakfast = safe.get("totalVerifiedBreakfast");
        this.totalVerifiedLunch = safe.get("totalVerifiedLunch");
        this.totalVerifiedDinner = safe.get("totalVerifiedDinner");
        this.totalCandidateCount = safe.get("totalCandidateCount");
        this.availableCandidateCount = safe.get("availableCandidateCount");
        this.filteredCandidateCount = safe.get("filteredCandidateCount");
        this.scannedCount = safe.get("scannedCount");
        this.customerCode = text(safe.get("customerCode"));
        this.customerName = text(safe.get("customerName"));
        this.orderCode = text(safe.get("orderCode"));
        this.dealTime = text(safe.get("dealTime"));
        this.createTime = text(safe.get("createTime"));
        this.firstPurchaseTime = text(safe.get("firstPurchaseTime"));
        this.parentPackageName = text(safe.get("parentPackageName"));
        this.childPackageName = text(safe.get("childPackageName"));
        this.dishName = text(safe.get("dishName"));
        this.recordDate = text(safe.get("recordDate"));
        this.mealTypeCode = text(safe.get("mealTypeCode"));
        this.mealTypeName = text(safe.get("mealTypeName"));
        this.dishTypeCode = text(safe.get("dishTypeCode"));
        this.generationStatus = text(safe.get("generationStatus"));
        this.content = text(safe.get("content"));
        this.metricDefinitionId = text(safe.get("metricDefinitionId"));
        this.version = text(safe.get("version"));
        this.ruleId = text(safe.get("ruleId"));
        this.evidenceDocument = text(safe.get("evidenceDocument"));
        this.evidenceAnchor = text(safe.get("evidenceAnchor"));
        this.replaceReason = text(safe.get("replaceReason"));
        this.customerId = longValue(safe.get("customerId"));
        this.orderId = longValue(safe.get("orderId"));
        this.customerMealPlanId = longValue(safe.get("customerMealPlanId"));
        this.allergyFiltered = Boolean.TRUE.equals(safe.get("allergyFiltered"));
        this.ingredientNames = textList(safe.get("ingredientNames"));
        this.allergyReasons = textList(safe.get("allergyReasons"));
        this.items = children(safe.get("items"));
        this.groups = children(safe.get("groups"));
        this.packages = children(safe.get("packages"));
        this.dishes = children(safe.get("dishes"));
        this.reportMetrics = metrics(safe.get("reportMetrics"));
        this.recentVerificationCount = listSize(safe.get("recentVerifications"));
        this.mealBalance = child(safe.get("mealBalance"));
        this.metricValues = metricValues(safe);
        this.presentationMap = Collections.unmodifiableMap(new LinkedHashMap<>(safe));
    }

    /**
     * 将基础设施层生成的受控展示 Map 转换为 Presenter DTO。
     *
     * @param source 工具适配器已经裁剪的展示字段
     * @return 不暴露任意 Map key 的 Presenter 输入
     */
    public static BusinessPresentationResult fromPresentationMap(Map<String, Object> source) {
        return new BusinessPresentationResult(source);
    }

    public boolean isEmpty() { return empty; }
    public boolean isPresent() { return present; }
    public boolean isExplicitlyAbsent() { return presentDeclared && !present; }
    public boolean isTruncated() { return truncated; }
    public boolean isItemsDeclared() { return itemsDeclared; }
    public boolean isGroupsDeclared() { return groupsDeclared; }
    public Object getTotal() { return total; }
    public Object totalOr(Object fallback) { return total == null ? fallback : total; }
    public Object getActiveOrderCount() { return activeOrderCount; }
    public Object getVerificationRecordCount() { return verificationRecordCount; }
    public Object getRefundRecordCount() { return refundRecordCount; }
    public Object getRemainingBreakfast() { return remainingBreakfast; }
    public Object getRemainingLunchDinner() { return remainingLunchDinner; }
    public Object getTotalRemaining() { return totalRemaining; }
    public Object getVerifiedBreakfast() { return verifiedBreakfast; }
    public Object getVerifiedLunch() { return verifiedLunch; }
    public Object getVerifiedDinner() { return verifiedDinner; }
    public Object getTotalVerified() { return totalVerified; }
    public Object getTotalVerifiedBreakfast() { return totalVerifiedBreakfast; }
    public Object getTotalVerifiedLunch() { return totalVerifiedLunch; }
    public Object getTotalVerifiedDinner() { return totalVerifiedDinner; }
    public Object getTotalCandidateCount() { return totalCandidateCount; }
    public Object getAvailableCandidateCount() { return availableCandidateCount; }
    public Object getFilteredCandidateCount() { return filteredCandidateCount; }
    public Object getScannedCount() { return scannedCount; }
    public String getCustomerCode() { return customerCode; }
    public String getCustomerName() { return customerName; }
    public String getOrderCode() { return orderCode; }
    public String getDealTime() { return dealTime; }
    public String getCreateTime() { return createTime; }
    public String getFirstPurchaseTime() { return firstPurchaseTime; }
    public String getParentPackageName() { return parentPackageName; }
    public String getChildPackageName() { return childPackageName; }
    public String getDishName() { return dishName; }
    public String getRecordDate() { return recordDate; }
    public String getMealTypeCode() { return mealTypeCode; }
    public String getMealTypeName() { return mealTypeName; }
    public String getDishTypeCode() { return dishTypeCode; }
    public String getGenerationStatus() { return generationStatus; }
    public String getContent() { return content; }
    public String getMetricDefinitionId() { return metricDefinitionId; }
    public String getVersion() { return version; }
    public String getRuleId() { return ruleId; }
    public String getEvidenceDocument() { return evidenceDocument; }
    public String getEvidenceAnchor() { return evidenceAnchor; }
    public String getReplaceReason() { return replaceReason; }
    public Long getCustomerId() { return customerId; }
    public Long getOrderId() { return orderId; }
    public Long getCustomerMealPlanId() { return customerMealPlanId; }
    public boolean isAllergyFiltered() { return allergyFiltered; }
    public List<String> getIngredientNames() { return ingredientNames; }
    public List<String> getAllergyReasons() { return allergyReasons; }
    public List<BusinessPresentationResult> getItems() { return items; }
    public List<BusinessPresentationResult> getGroups() { return groups; }
    public List<BusinessPresentationResult> getPackages() { return packages; }
    public List<BusinessPresentationResult> getDishes() { return dishes; }
    public List<AgentQueryMetric> getReportMetrics() { return reportMetrics; }
    public int getRecentVerificationCount() { return recentVerificationCount; }
    public BusinessPresentationResult getMealBalance() { return mealBalance; }

    /**
     * 读取指标目录登记的结果值；未知指标不会退化为任意字段查询。
     *
     * @param metric 登记指标
     * @return 主系统聚合值
     */
    public Object metricValue(AgentQueryMetric metric) {
        return metric == null ? null : metricValues.get(metric);
    }

    /** 仅供兼容响应序列化使用，不得传回 Presenter。 */
    public Map<String, Object> toPresentationMap() {
        return presentationMap;
    }

    private static Map<AgentQueryMetric, Object> metricValues(Map<String, Object> source) {
        Map<AgentQueryMetric, Object> result = new EnumMap<>(AgentQueryMetric.class);
        for (AgentMetricDefinition definition : AgentMetricCatalog.definitionsView()) {
            Object value = source.get(definition.getResultFieldKey());
            if (value != null) result.put(definition.getMetric(), value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static BusinessPresentationResult child(Object source) {
        if (!(source instanceof Map<?, ?> map)) return null;
        return new BusinessPresentationResult(stringMap(map));
    }

    private static List<BusinessPresentationResult> children(Object source) {
        if (!(source instanceof List<?> list) || list.isEmpty()) return List.of();
        List<BusinessPresentationResult> result = new ArrayList<>();
        for (Object value : list) {
            BusinessPresentationResult child = child(value);
            if (child != null) result.add(child);
        }
        return List.copyOf(result);
    }

    private static List<String> textList(Object source) {
        if (!(source instanceof List<?> list) || list.isEmpty()) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private static List<AgentQueryMetric> metrics(Object source) {
        if (!(source instanceof List<?> list) || list.isEmpty()) return List.of();
        List<AgentQueryMetric> result = new ArrayList<>();
        for (Object value : list) {
            try {
                result.add(value instanceof AgentQueryMetric metric
                    ? metric : AgentQueryMetric.valueOf(String.valueOf(value)));
            } catch (IllegalArgumentException ignored) {
                // 未登记指标不能进入 Presenter。
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key instanceof String textKey) result.put(textKey, value);
        });
        return result;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private static int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }
}
