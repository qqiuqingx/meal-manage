package me.zhengjie.agent.domain.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 由 saveFormDraft 成功工具事实确定性生成的脱敏草稿摘要。 */
public class FormDraftSummary {
    private String draftId;
    private String type;
    private String status;
    private Integer revision;
    private List<String> recognizedFields = new ArrayList<>();
    private List<String> missingFields = new ArrayList<>();
    private List<Map<String, Object>> warnings = new ArrayList<>();
    private String expiresAt;
    public String getDraftId() { return draftId; } public void setDraftId(String v) { draftId = v; }
    public String getType() { return type; } public void setType(String v) { type = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Integer getRevision() { return revision; } public void setRevision(Integer v) { revision = v; }
    public List<String> getRecognizedFields() { return recognizedFields; } public void setRecognizedFields(List<String> v) { recognizedFields = v == null ? new ArrayList<>() : v; }
    public List<String> getMissingFields() { return missingFields; } public void setMissingFields(List<String> v) { missingFields = v == null ? new ArrayList<>() : v; }
    public List<Map<String, Object>> getWarnings() { return warnings; } public void setWarnings(List<Map<String, Object>> v) { warnings = v == null ? new ArrayList<>() : v; }
    public String getExpiresAt() { return expiresAt; } public void setExpiresAt(String v) { expiresAt = v; }
}
