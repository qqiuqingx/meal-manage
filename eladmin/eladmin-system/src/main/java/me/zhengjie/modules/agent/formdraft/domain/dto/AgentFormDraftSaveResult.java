package me.zhengjie.modules.agent.formdraft.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/** 草稿创建或修订结果，不包含完整 payload。 */
@Data
public class AgentFormDraftSaveResult implements Serializable {
    /** 是否保存成功。 */
    private boolean success;
    /** CREATED、UPDATED 或 IDEMPOTENT_REPLAY。 */
    private String operation;
    /** 草稿业务 ID。 */
    private String draftId;
    /** 草稿类型。 */
    private String draftType;
    /** 主系统计算的生命周期状态。 */
    private String status;
    /** 当前修订版本。 */
    private Integer revision;
    /** 过期时间。 */
    private Timestamp expiresAt;
    /** 已识别字段。 */
    private List<String> recognizedFields;
    /** 待补充字段。 */
    private List<String> missingFields;
    /** 稳定脱敏告警。 */
    private List<AgentFormDraftWarning> warnings;
}
