package me.zhengjie.modules.agent.security;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 主系统签发给 Agent 的短期客服访问上下文，不能由前端构造。
 */
@Data
public class AgentAccessContext {

    /** 当前客服用户 ID。 */
    private Long operatorId;
    /** 当前客服用户名。 */
    private String operatorName;
    /** 当前会话 ID。 */
    private String sessionId;
    /** 当前链路请求 ID。 */
    private String requestId;
    /** 可用业务权限，仅用于内部校验，不记录到普通日志。 */
    private List<String> permissions = new ArrayList<>();
    /** 是否拥有全部业务数据范围。 */
    private boolean allDataScope;
    /** 非全量数据范围时可访问的部门 ID。 */
    private List<Long> dataScopeDeptIds = new ArrayList<>();
    /** Unix 秒级过期时间。 */
    private long expiresAt;
}
