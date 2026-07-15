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
    private List<Map<String, Object>> items = new ArrayList<>();
    private int page;
    private int size;
    private boolean truncated;
    private String queriedAt;
    private String timezone;
    public String getMetricDefinitionId() { return metricDefinitionId; }
    public void setMetricDefinitionId(String metricDefinitionId) { this.metricDefinitionId = metricDefinitionId; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items == null ? new ArrayList<>() : items; }
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
        result.put("metricDefinitionId", metricDefinitionId); result.put("total", total); result.put("items", items);
        result.put("page", page); result.put("size", size); result.put("truncated", truncated);
        result.put("queriedAt", queriedAt); result.put("timezone", timezone);
        return result;
    }
}
