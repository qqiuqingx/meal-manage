package me.zhengjie.agent.guardrail;

/** 护栏拒绝工具输入、输出或回答时使用的稳定异常，不携带底层 SQL、URL 或堆栈信息。 */
public class ToolGuardrailException extends RuntimeException {
    private final String code;

    /** 使用稳定错误码和安全错误消息创建护栏异常。 */
    public ToolGuardrailException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
