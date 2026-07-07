package me.zhengjie.modules.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 智能排查诊断结果客服反馈记录。
 */
@Data
@TableName("agent_diagnosis_feedback")
public class AgentDiagnosisFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求链路 ID。 */
    private String requestId;

    /** 聊天会话 ID。 */
    private String sessionId;

    /** 客户 ID。 */
    private Long customerId;

    /** 客户名称。 */
    private String customerName;

    /** 诊断日期。 */
    private String recordDate;

    /** 餐次。 */
    private String mealType;

    /** AI 预测原因码 JSON 数组。 */
    private String predictedReasonCodes;

    /** 反馈结论：ACCEPTED、REJECTED、PARTIAL。 */
    private String accepted;

    /** 客服确认的真实原因码。 */
    private String actualReasonCode;

    /** 客服备注。 */
    private String comment;

    /** 操作人。 */
    private String operator;

    /** 创建时间。 */
    private Timestamp createTime;
}
