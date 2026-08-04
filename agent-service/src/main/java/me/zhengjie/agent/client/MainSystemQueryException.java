package me.zhengjie.agent.client;

/** 主系统只读查询失败的稳定异常；禁止携带原始 HTTP 响应、SQL 和内部 URL。 */
public class MainSystemQueryException extends RuntimeException {
    private final String code;

    /** 创建不暴露底层响应细节的主系统查询异常。 */
    public MainSystemQueryException(String code) {
        super(code);
        this.code = code;
    }

    /** 创建带内部原因但对外只暴露稳定错误码的主系统查询异常。 */
    public MainSystemQueryException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String getCode() { return code; }
}
