package me.zhengjie.agent.validator;

/**
 * 结构化诊断结果的校验错误摘要。
 */
public class DiagnosisValidationError {

    private String field;
    private String code;
    private String message;
    private String rawValueDigest;

    public DiagnosisValidationError() {
    }

    public DiagnosisValidationError(String field, String code, String message, String rawValueDigest) {
        this.field = field;
        this.code = code;
        this.message = message;
        this.rawValueDigest = rawValueDigest;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRawValueDigest() {
        return rawValueDigest;
    }

    public void setRawValueDigest(String rawValueDigest) {
        this.rawValueDigest = rawValueDigest;
    }
}
