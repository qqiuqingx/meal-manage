package me.zhengjie.agent.tool.input.formdraft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** 模型可调用的表单草稿创建或修订输入。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = false)
public class SaveFormDraftInput {
    private String draftId;
    private Integer expectedRevision;
    @JsonPropertyDescription("只能为 CREATE_CUSTOMER_WITH_ORDER 或 CREATE_ORDER")
    private String draftType;
    private String schemaVersion = "v1";
    private CustomerWithOrderDraftInput customerWithOrderPayload;
    private CustomerOrderDraftInput orderPayload;
    private List<String> recognizedFields;
    private List<String> missingFields;
    private List<WarningInput> warnings;
    private String convertedFrom;
    public String getDraftId() { return draftId; } public void setDraftId(String v) { draftId = v; }
    public Integer getExpectedRevision() { return expectedRevision; } public void setExpectedRevision(Integer v) { expectedRevision = v; }
    public String getDraftType() { return draftType; } public void setDraftType(String v) { draftType = v; }
    public String getSchemaVersion() { return schemaVersion; } public void setSchemaVersion(String v) { schemaVersion = v; }
    public CustomerWithOrderDraftInput getCustomerWithOrderPayload() { return customerWithOrderPayload; } public void setCustomerWithOrderPayload(CustomerWithOrderDraftInput v) { customerWithOrderPayload = v; }
    public CustomerOrderDraftInput getOrderPayload() { return orderPayload; } public void setOrderPayload(CustomerOrderDraftInput v) { orderPayload = v; }
    public List<String> getRecognizedFields() { return recognizedFields; } public void setRecognizedFields(List<String> v) { recognizedFields = v; }
    public List<String> getMissingFields() { return missingFields; } public void setMissingFields(List<String> v) { missingFields = v; }
    public List<WarningInput> getWarnings() { return warnings; } public void setWarnings(List<WarningInput> v) { warnings = v; }
    public String getConvertedFrom() { return convertedFrom; } public void setConvertedFrom(String v) { convertedFrom = v; }

    /** 稳定告警码；主系统会替换展示文案。 */
    public static class WarningInput {
        private String code; private String message;
        public String getCode() { return code; } public void setCode(String v) { code = v; }
        public String getMessage() { return message; } public void setMessage(String v) { message = v; }
    }
}
