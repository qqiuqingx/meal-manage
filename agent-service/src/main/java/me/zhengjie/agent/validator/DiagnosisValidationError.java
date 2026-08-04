package me.zhengjie.agent.validator;

/** 结构化诊断结果的稳定校验错误摘要；不保存原始敏感值。 */
public class DiagnosisValidationError {

    private String field;
    private String code;
    private String message;
    private String rawValueDigest;

    /** 创建供 JSON 反序列化使用的空校验错误。 */
    public DiagnosisValidationError() {
    }

    /** 创建包含字段、错误码、稳定摘要及脱敏值摘要的校验错误。 */
    public DiagnosisValidationError(String field, String code, String message, String rawValueDigest) {
        this.field = field;
        this.code = code;
        this.message = message;
        this.rawValueDigest = rawValueDigest;
    }

    public String getField() { return field; }
    public void setField(String value) { field = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public String getRawValueDigest() { return rawValueDigest; }
    public void setRawValueDigest(String value) { rawValueDigest = value; }
}
