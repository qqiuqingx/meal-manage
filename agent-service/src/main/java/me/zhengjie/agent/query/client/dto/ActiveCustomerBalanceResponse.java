package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** 主系统返回的活跃客户餐数余额明细受控契约。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActiveCustomerBalanceResponse {
    private String metricDefinitionId;
    private long total;
    private List<ActiveCustomerBalanceItem> items = new ArrayList<>();
    private int page;
    private int size;
    private boolean truncated;
    private String queriedAt;
    private String timezone;
    public String getMetricDefinitionId() { return metricDefinitionId; }
    public void setMetricDefinitionId(String metricDefinitionId) { this.metricDefinitionId = metricDefinitionId; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public List<ActiveCustomerBalanceItem> getItems() { return items; }
    public void setItems(List<ActiveCustomerBalanceItem> items) { this.items = items == null ? new ArrayList<>() : items; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }
    public String getQueriedAt() { return queriedAt; }
    public void setQueriedAt(String queriedAt) { this.queriedAt = queriedAt; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    /** 转换为回答工厂可消费的通用安全 Map。 */
    public Map<String, Object> toPresentationMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metricDefinitionId", metricDefinitionId); result.put("total", total);
        result.put("items", items.stream().map(ActiveCustomerBalanceItem::toPresentationMap).toList());
        result.put("page", page); result.put("size", size); result.put("truncated", truncated);
        result.put("queriedAt", queriedAt); result.put("timezone", timezone);
        return result;
    }

    /** 活跃客户餐数余额的脱敏明细，只包含接口文档允许展示的五个字段。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActiveCustomerBalanceItem(String customerCode,
                                            String customerNameMasked,
                                            long remainingBreakfast,
                                            long remainingLunchDinner,
                                            long remainingTotal) {
        /** 将强类型明细投影为兼容聊天响应字段。 */
        private Map<String, Object> toPresentationMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("customerCode", customerCode);
            result.put("customerNameMasked", customerNameMasked);
            result.put("remainingBreakfast", remainingBreakfast);
            result.put("remainingLunchDinner", remainingLunchDinner);
            result.put("remainingTotal", remainingTotal);
            return result;
        }
    }
}
