package me.zhengjie.agent.query.client;

/** 主系统内部查询返回稳定错误码时的受控客户端异常，不携带原始响应文本。 */
public class BusinessQueryClientException extends RuntimeException {
    private final String failureCode;

    public BusinessQueryClientException(String failureCode) {
        super(failureCode);
        this.failureCode = failureCode;
    }

    /** 返回可写入工具告警和审计的稳定失败码。 */
    public String getFailureCode() {
        return failureCode;
    }
}
