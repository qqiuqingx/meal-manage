package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * Agent 业务只读查询审计查询条件。
 */
@Data
public class AgentBusinessQueryAuditCriteria {

    /** 客服账号。 */
    private String operator;

    /** 会话 ID。 */
    private String sessionId;

    /** 请求链路 ID。 */
    private String requestId;

    /** 查询领域。 */
    private String queryDomain;

    /** 查询动作。 */
    private String queryAction;

    /** 客户 ID。 */
    private Long customerId;

    /** 客户编号。 */
    private String customerCode;

    /** 订单 ID。 */
    private Long orderId;

    /** 订单编号。 */
    private String orderCode;

    /** 是否命中同轮缓存。 */
    private Boolean cached;

    /** 是否部分成功或截断。 */
    private Boolean partial;

    /** 稳定失败类型。 */
    private String failureType;

    /** 创建时间起始，格式 yyyy-MM-dd HH:mm:ss。 */
    private String createTimeStart;

    /** 创建时间结束，格式 yyyy-MM-dd HH:mm:ss。 */
    private String createTimeEnd;

    /** 页码，从 0 开始。 */
    private Integer page = 0;

    /** 每页条数。 */
    private Integer size = 10;
}
