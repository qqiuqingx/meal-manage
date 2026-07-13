package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主系统客户候选列表的强类型 Agent 传输契约。 */
public class CustomerCandidateListResponse {
    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper();
    private long total; private List<CustomerCandidate> items = new ArrayList<>(); private boolean truncated;
    public long getTotal() { return total; } public void setTotal(long value) { total = value; }
    public List<CustomerCandidate> getItems() { return items; } public void setItems(List<CustomerCandidate> value) { items = value == null ? new ArrayList<>() : value; }
    public boolean isTruncated() { return truncated; } public void setTruncated(boolean value) { truncated = value; }
    /** 过渡期适配未迁移 Map。 */
    public static CustomerCandidateListResponse fromLegacyMap(Map<String, Object> value) { return value == null || value.isEmpty() ? new CustomerCandidateListResponse() : LEGACY_MAPPER.convertValue(value, CustomerCandidateListResponse.class); }
    /** 显式生成候选卡片数据。 */
    public Map<String, Object> toPresentationMap() { Map<String, Object> result = new LinkedHashMap<>(); result.put("total", total); result.put("truncated", truncated); result.put("items", items.stream().map(CustomerCandidate::toPresentationMap).toList()); return result; }
    /** 单个脱敏客户候选。 */
    public static class CustomerCandidate {
        private Long customerId; private String customerCode; private String customerName; private String maskedPhone;
        public Long getCustomerId() { return customerId; } public void setCustomerId(Long value) { customerId = value; }
        public String getCustomerCode() { return customerCode; } public void setCustomerCode(String value) { customerCode = value; }
        public String getCustomerName() { return customerName; } public void setCustomerName(String value) { customerName = value; }
        public String getMaskedPhone() { return maskedPhone; } public void setMaskedPhone(String value) { maskedPhone = value; }
        private Map<String, Object> toPresentationMap() { Map<String, Object> item = new LinkedHashMap<>(); item.put("customerId", customerId); item.put("customerCode", customerCode); item.put("customerName", customerName); item.put("maskedPhone", maskedPhone); return item; }
    }
}
