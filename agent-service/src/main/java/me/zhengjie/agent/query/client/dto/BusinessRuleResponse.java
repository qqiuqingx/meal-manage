package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/** 主系统版本化业务规则的强类型 Agent 传输契约。 */
public class BusinessRuleResponse {
    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper();
    private boolean present; private String ruleId; private String version; private String topic; private String content; private String ownerModule; private String evidenceDocument; private String updatedAt;
    public boolean isPresent() { return present; } public void setPresent(boolean value) { present = value; }
    public String getRuleId() { return ruleId; } public void setRuleId(String value) { ruleId = value; }
    public String getVersion() { return version; } public void setVersion(String value) { version = value; }
    public String getTopic() { return topic; } public void setTopic(String value) { topic = value; }
    public String getContent() { return content; } public void setContent(String value) { content = value; }
    public String getOwnerModule() { return ownerModule; } public void setOwnerModule(String value) { ownerModule = value; }
    public String getEvidenceDocument() { return evidenceDocument; } public void setEvidenceDocument(String value) { evidenceDocument = value; }
    public String getUpdatedAt() { return updatedAt; } public void setUpdatedAt(String value) { updatedAt = value; }
    /** 过渡期适配未迁移 Map。 */
    public static BusinessRuleResponse fromLegacyMap(Map<String, Object> value) { return value == null || value.isEmpty() ? new BusinessRuleResponse() : LEGACY_MAPPER.convertValue(value, BusinessRuleResponse.class); }
    /** 显式生成规则卡片数据。 */
    public Map<String, Object> toPresentationMap() { Map<String, Object> result = new LinkedHashMap<>(); result.put("present", present); result.put("ruleId", ruleId); result.put("version", version); result.put("topic", topic); result.put("content", content); result.put("ownerModule", ownerModule); result.put("evidenceDocument", evidenceDocument); result.put("updatedAt", updatedAt); return result; }
}
