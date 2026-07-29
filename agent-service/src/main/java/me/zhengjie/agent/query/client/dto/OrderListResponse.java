package me.zhengjie.agent.query.client.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主系统订单分页摘要的强类型传输契约。 */
public class OrderListResponse {
    private long total;
    private List<OrderSummaryResponse> items = new ArrayList<>();
    private boolean truncated;

    public long getTotal() { return total; } public void setTotal(long total) { this.total = total; }
    public List<OrderSummaryResponse> getItems() { return items; }
    public void setItems(List<OrderSummaryResponse> items) { this.items = items == null ? new ArrayList<>() : items; }
    public boolean isTruncated() { return truncated; } public void setTruncated(boolean truncated) { this.truncated = truncated; }

    /** 显式转换为受控卡片结构。 */
    public Map<String, Object> toPresentationMap() {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("total", total); result.put("truncated", truncated);
        result.put("items", items.stream().map(OrderSummaryResponse::toPresentationMap).toList()); return result;
    }
}
