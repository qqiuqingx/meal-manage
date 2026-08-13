package me.zhengjie.modules.agent.formdraft.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/** 草稿脱敏摘要；不会返回手机号、地址或完整 payload。 */
@Data
public class AgentFormDraftSummary implements Serializable {
    /** 草稿业务 ID。 */
    private String draftId;
    /** 草稿类型。 */
    private String draftType;
    /** 当前状态。 */
    private String status;
    /** 当前版本。 */
    private Integer revision;
    /** 已识别字段数量。 */
    private int recognizedFieldCount;
    /** 待补充的安全字段路径。 */
    private List<String> missingFields;
    /** 稳定脱敏告警。 */
    private List<AgentFormDraftWarning> warnings;
    /** 来源会话 ID。 */
    private String sourceSessionId;
    /** 过期时间。 */
    private Timestamp expiresAt;
    /** 正式创建后的业务记录 ID，仅已提交时存在。 */
    private Long targetBusinessId;
}
