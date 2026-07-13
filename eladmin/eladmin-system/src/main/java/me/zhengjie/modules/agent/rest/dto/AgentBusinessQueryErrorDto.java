package me.zhengjie.modules.agent.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Agent 内部只读查询的稳定错误响应，不包含堆栈、SQL 或敏感业务数据。 */
@Data
@AllArgsConstructor
public class AgentBusinessQueryErrorDto {
    /** 稳定错误码。 */
    private String code;
    /** 对 Agent 安全可见的简要说明。 */
    private String message;
}
