package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主系统退餐记录列表的强类型 Agent 传输契约，不包含退款金额。 */
public class RefundListResponse {
    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper();
    private long total; private List<RefundLog> items = new ArrayList<>(); private boolean truncated;
    public long getTotal() { return total; } public void setTotal(long value) { total = value; }
    public List<RefundLog> getItems() { return items; } public void setItems(List<RefundLog> value) { items = value == null ? new ArrayList<>() : value; }
    public boolean isTruncated() { return truncated; } public void setTruncated(boolean value) { truncated = value; }
    /** 过渡期适配旧 Map 返回。 */
    public static RefundListResponse fromLegacyMap(Map<String, Object> value) { return value == null || value.isEmpty() ? new RefundListResponse() : LEGACY_MAPPER.convertValue(value, RefundListResponse.class); }
    /** 显式转换为展示层卡片数据。 */
    public Map<String, Object> toPresentationMap() { Map<String, Object> result = new LinkedHashMap<>(); result.put("total", total); result.put("truncated", truncated); result.put("items", items.stream().map(RefundLog::toPresentationMap).toList()); return result; }

    /** 单条退餐记录。 */
    public static class RefundLog {
        private Long refundId; private Long customerId; private Long orderId; private Integer refundBreakfastCount; private Integer refundLunchDinnerCount;
        private Integer verifiedBreakfastCount; private Integer verifiedLunchDinnerCount; private String refundReason; private Date operateTime;
        public Long getRefundId() { return refundId; } public void setRefundId(Long value) { refundId = value; }
        public Long getCustomerId() { return customerId; } public void setCustomerId(Long value) { customerId = value; }
        public Long getOrderId() { return orderId; } public void setOrderId(Long value) { orderId = value; }
        public Integer getRefundBreakfastCount() { return refundBreakfastCount; } public void setRefundBreakfastCount(Integer value) { refundBreakfastCount = value; }
        public Integer getRefundLunchDinnerCount() { return refundLunchDinnerCount; } public void setRefundLunchDinnerCount(Integer value) { refundLunchDinnerCount = value; }
        public Integer getVerifiedBreakfastCount() { return verifiedBreakfastCount; } public void setVerifiedBreakfastCount(Integer value) { verifiedBreakfastCount = value; }
        public Integer getVerifiedLunchDinnerCount() { return verifiedLunchDinnerCount; } public void setVerifiedLunchDinnerCount(Integer value) { verifiedLunchDinnerCount = value; }
        public String getRefundReason() { return refundReason; } public void setRefundReason(String value) { refundReason = value; }
        public Date getOperateTime() { return operateTime; } public void setOperateTime(Date value) { operateTime = value; }
        private Map<String, Object> toPresentationMap() { Map<String, Object> item = new LinkedHashMap<>(); item.put("refundId", refundId); item.put("customerId", customerId); item.put("orderId", orderId); item.put("refundBreakfastCount", refundBreakfastCount); item.put("refundLunchDinnerCount", refundLunchDinnerCount); item.put("verifiedBreakfastCount", verifiedBreakfastCount); item.put("verifiedLunchDinnerCount", verifiedLunchDinnerCount); item.put("refundReason", refundReason); item.put("operateTime", operateTime); return item; }
    }
}
