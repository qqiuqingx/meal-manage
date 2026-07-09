package me.zhengjie.modules.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 智能排查单次诊断运营指标记录。
 */
@Data
@TableName("agent_diagnosis_metric")
public class AgentDiagnosisMetric {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求链路 ID。 */
    private String requestId;

    /** 聊天会话 ID。 */
    private String sessionId;

    /** 客户 ID。 */
    private Long customerId;

    /** 诊断日期。 */
    private String recordDate;

    /** 餐次。 */
    private String mealType;

    /** 是否兜底结果。 */
    private Boolean fallback;

    /** 兜底原因。 */
    private String fallbackReason;

    /** 兜底来源。 */
    private String fallbackSource;

    /** 失败类型。 */
    private String failureType;

    /** 可信度。 */
    private String confidence;

    /** 模型标识。 */
    private String modelName;

    /** 原因码 JSON 数组。 */
    private String reasonCodes;

    /** 动作草稿数量。 */
    private Integer actionDraftCount;

    /** 工具调用次数。 */
    private Integer toolCallCount;

    /** 工具失败次数。 */
    private Integer toolFailureCount;

    /** 诊断耗时毫秒。 */
    private Long diagnosisCostMs;

    /** 创建时间。 */
    private Timestamp createTime;
}
