package me.zhengjie.modules.agent.formdraft.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/** 登录客服领取草稿后的完整类型化结果。 */
@Data
public class AgentFormDraftClaimResult implements Serializable {
    /** 草稿业务 ID。 */
    private String draftId;
    /** 草稿类型。 */
    private String draftType;
    /** 协议版本。 */
    private String schemaVersion;
    /** 领取后的状态。 */
    private String status;
    /** 当前修订版本。 */
    private Integer revision;
    /** CREATE_CUSTOMER_WITH_ORDER 对应的完整 payload。 */
    private CustomerWithOrderDraftPayload customerWithOrderPayload;
    /** CREATE_ORDER 对应的完整 payload。 */
    private CustomerOrderDraftPayload orderPayload;
    /** 已识别字段。 */
    private List<String> recognizedFields;
    /** 待补充字段。 */
    private List<String> missingFields;
    /** 稳定脱敏告警。 */
    private List<AgentFormDraftWarning> warnings;
    /** 来源会话 ID，用于返回原会话。 */
    private String sourceSessionId;
    /** 过期时间。 */
    private Timestamp expiresAt;
}
