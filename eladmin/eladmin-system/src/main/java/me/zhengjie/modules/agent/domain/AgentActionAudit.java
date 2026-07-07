package me.zhengjie.modules.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 智能排查动作确认审计记录。
 */
@Data
@TableName("agent_action_audit")
public class AgentActionAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求链路 ID。 */
    private String requestId;

    /** 聊天会话 ID。 */
    private String sessionId;

    /** 幂等键，同一动作确认请求必须唯一。 */
    private String idempotencyKey;

    /** 动作码。 */
    private String actionCode;

    /** 动作标题。 */
    private String actionTitle;

    /** 风险等级。 */
    private String riskLevel;

    /** 目标对象类型。 */
    private String targetType;

    /** 目标对象 ID。 */
    private String targetId;

    /** 变更前证据快照 JSON。 */
    private String beforeSnapshot;

    /** 变更后预览 JSON。 */
    private String afterPreview;

    /** 确认所需权限。 */
    private String requiredPermission;

    /** 是否完成高风险二次确认。 */
    private Boolean secondConfirmed;

    /** 执行状态。 */
    private String status;

    /** 是否执行成功。 */
    private Boolean success;

    /** 失败原因或待处理原因。 */
    private String failureReason;

    /** 正式执行结果 JSON。 */
    private String executionResult;

    /** 操作人。 */
    private String operator;

    /** 客服备注。 */
    private String comment;

    /** 创建时间。 */
    private Timestamp createTime;

    /** 更新时间。 */
    private Timestamp updateTime;
}
