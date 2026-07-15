package me.zhengjie.modules.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Timestamp;

/** Agent 只读业务查询审计记录，不保存工具原始请求或响应。 */
@Data
@TableName("agent_business_query_audit")
public class AgentBusinessQueryAudit {
    @TableId(type = IdType.AUTO) private Long id;
    /** 操作客服账号。 */ private String operator;
    /** 聊天会话 ID。 */ private String sessionId;
    /** 请求链路 ID。 */ private String requestId;
    /** 查询领域。 */ private String queryDomain;
    /** 查询动作。 */ private String queryAction;
    /** 客户 ID。 */ private Long customerId;
    /** 客户编号。 */ private String customerCode;
    /** 订单 ID。 */ private Long orderId;
    /** 订单编号。 */ private String orderCode;
    /** 已执行工具名称摘要 JSON。 */ private String toolNames;
    /** 返回结果条数。 */ private Integer resultCount;
    /** 是否命中同轮缓存。 */ private Boolean cached;
    /** 是否部分成功或截断。 */ private Boolean partial;
    /** 失败类型，仅存稳定错误码。 */ private String failureType;
    /** 分析来源，RULE/LLM/HYBRID。 */ private String analysisSource;
    /** 模型或规则分析置信度。 */ private Double analysisConfidence;
    /** 模型降级的稳定原因；正常澄清不填写。 */ private String semanticFallbackReason;
    /** 受控语义目录版本。 */ private String semanticCatalogVersion;
    /** 模型输出的相对时间枚举。 */ private String temporalExpression;
    /** 服务端解析后的单日业务日期。 */ private String resolvedRecordDate;
    /** 服务端解析后的范围开始日期。 */ private String resolvedStartDate;
    /** 服务端解析后的范围结束日期。 */ private String resolvedEndDate;
    /** 是否复用了跨轮 Pending Context。 */ private Boolean pendingContextReused;
    /** 是否向客服发起关键澄清。 */ private Boolean clarificationRequired;
    /** 指标代码 JSON，仅存受控枚举。 */ private String metricCodes;
    /** 维度代码 JSON，仅存受控枚举。 */ private String dimensionCodes;
    /** 未支持问题的稳定拒绝原因。 */ private String unsupportedReason;
    /** 回答安全校验结果，VALID/PARTIAL/REJECTED。 */ private String answerValidationResult;
    /** 处理耗时毫秒。 */ private Long costMs;
    /** 创建时间。 */ private Timestamp createTime;
}
