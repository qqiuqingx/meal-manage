package me.zhengjie.agent.tool.output;

import java.util.ArrayList;
import java.util.List;

/** saveFormDraft 的确定性安全输出，不包含完整草稿 payload。 */
public class FormDraftToolOutput {
    private boolean success;
    private String operation;
    private String draftId;
    private String draftType;
    private String status;
    private Integer revision;
    private String expiresAt;
    private List<String> recognizedFields = new ArrayList<>();
    private List<String> missingFields = new ArrayList<>();
    private List<WarningOutput> warnings = new ArrayList<>();
    public boolean isSuccess() { return success; } public void setSuccess(boolean v) { success = v; }
    public String getOperation() { return operation; } public void setOperation(String v) { operation = v; }
    public String getDraftId() { return draftId; } public void setDraftId(String v) { draftId = v; }
    public String getDraftType() { return draftType; } public void setDraftType(String v) { draftType = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Integer getRevision() { return revision; } public void setRevision(Integer v) { revision = v; }
    public String getExpiresAt() { return expiresAt; } public void setExpiresAt(String v) { expiresAt = v; }
    public List<String> getRecognizedFields() { return recognizedFields; } public void setRecognizedFields(List<String> v) { recognizedFields = v; }
    public List<String> getMissingFields() { return missingFields; } public void setMissingFields(List<String> v) { missingFields = v; }
    public List<WarningOutput> getWarnings() { return warnings; } public void setWarnings(List<WarningOutput> v) { warnings = v; }
    public static class WarningOutput {
        private String code; private String message;
        public String getCode() { return code; } public void setCode(String v) { code = v; }
        public String getMessage() { return message; } public void setMessage(String v) { message = v; }
    }
}
