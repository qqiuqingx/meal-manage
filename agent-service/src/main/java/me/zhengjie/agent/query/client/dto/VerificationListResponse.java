package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主系统核销记录列表的强类型 Agent 传输契约。 */
public class VerificationListResponse {
    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper();
    private long total; private List<VerificationLog> items = new ArrayList<>(); private boolean truncated;
    public long getTotal() { return total; } public void setTotal(long value) { total = value; }
    public List<VerificationLog> getItems() { return items; } public void setItems(List<VerificationLog> value) { items = value == null ? new ArrayList<>() : value; }
    public boolean isTruncated() { return truncated; } public void setTruncated(boolean value) { truncated = value; }
    /** 过渡期适配旧 Map 返回。 */
    public static VerificationListResponse fromLegacyMap(Map<String, Object> value) { return value == null || value.isEmpty() ? new VerificationListResponse() : LEGACY_MAPPER.convertValue(value, VerificationListResponse.class); }
    /** 显式转换为展示层卡片数据。 */
    public Map<String, Object> toPresentationMap() { Map<String, Object> result = new LinkedHashMap<>(); result.put("total", total); result.put("truncated", truncated); result.put("items", items.stream().map(VerificationLog::toPresentationMap).toList()); return result; }

    /** 单条核销记录。 */
    public static class VerificationLog {
        private Long verificationId; private Long customerId; private Long orderId; private Long mealPlanCustomerId; private Date recordDate;
        private String mealTypeCode; private Integer verificationCount; private boolean refunded; private Date operateTime;
        public Long getVerificationId() { return verificationId; } public void setVerificationId(Long value) { verificationId = value; }
        public Long getCustomerId() { return customerId; } public void setCustomerId(Long value) { customerId = value; }
        public Long getOrderId() { return orderId; } public void setOrderId(Long value) { orderId = value; }
        public Long getMealPlanCustomerId() { return mealPlanCustomerId; } public void setMealPlanCustomerId(Long value) { mealPlanCustomerId = value; }
        public Date getRecordDate() { return recordDate; } public void setRecordDate(Date value) { recordDate = value; }
        public String getMealTypeCode() { return mealTypeCode; } public void setMealTypeCode(String value) { mealTypeCode = value; }
        public Integer getVerificationCount() { return verificationCount; } public void setVerificationCount(Integer value) { verificationCount = value; }
        public boolean isRefunded() { return refunded; } public void setRefunded(boolean value) { refunded = value; }
        public Date getOperateTime() { return operateTime; } public void setOperateTime(Date value) { operateTime = value; }
        private Map<String, Object> toPresentationMap() { Map<String, Object> item = new LinkedHashMap<>(); item.put("verificationId", verificationId); item.put("customerId", customerId); item.put("orderId", orderId); item.put("mealPlanCustomerId", mealPlanCustomerId); item.put("recordDate", recordDate); item.put("mealTypeCode", mealTypeCode); item.put("verificationCount", verificationCount); item.put("refunded", refunded); item.put("operateTime", operateTime); return item; }
    }
}
