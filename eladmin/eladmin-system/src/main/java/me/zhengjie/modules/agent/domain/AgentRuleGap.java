package me.zhengjie.modules.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 智能排查规则缺口维护记录。
 */
@Data
@TableName("agent_rule_gap")
public class AgentRuleGap {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 缺口类型。 */
    private String gapType;

    /** 来源类型。 */
    private String sourceType;

    /** 来源记录 ID。 */
    private Long sourceId;

    /** 请求链路 ID。 */
    private String requestId;

    /** 客户 ID。 */
    private Long customerId;

    /** 诊断日期。 */
    private String recordDate;

    /** 餐次。 */
    private String mealType;

    /** 预测原因码 JSON 数组。 */
    private String predictedReasonCodes;

    /** 客服确认真实原因码。 */
    private String actualReasonCode;

    /** 反馈结论。 */
    private String accepted;

    /** 缺口说明。 */
    private String gapDescription;

    /** 出现次数。 */
    private Integer occurrenceCount;

    /** 状态：OPEN、IN_PROGRESS、RESOLVED、IGNORED。 */
    private String status;

    /** 处理人。 */
    private String owner;

    /** 操作人。 */
    private String operator;

    /** 创建时间。 */
    private Timestamp createTime;

    /** 更新时间。 */
    private Timestamp updateTime;
}
