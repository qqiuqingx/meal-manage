package me.zhengjie.modules.agent.formdraft.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import me.zhengjie.base.BaseEntity;

import java.sql.Timestamp;

/** Agent 客户或订单表单辅助草稿。 */
@Data
@TableName("agent_form_draft")
public class AgentFormDraft extends BaseEntity {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 不可猜测的草稿业务标识。 */
    private String draftId;
    /** 固定草稿类型。 */
    private String draftType;
    /** 跨服务协议版本。 */
    private String schemaVersion;
    /** 当前生命周期状态。 */
    private String status;
    /** 乐观锁修订版本。 */
    private Integer revision;
    /** 完整类型化草稿 JSON，包含敏感客户资料。 */
    private String payload;
    /** 已识别登记字段路径 JSON。 */
    private String recognizedFields;
    /** 待补充登记字段路径 JSON。 */
    private String missingFields;
    /** 稳定告警码 JSON，不含敏感资料。 */
    private String warnings;
    /** 来源 Agent 会话 ID。 */
    private String sourceSessionId;
    /** 来源用户消息数据库标识。 */
    private String sourceMessageId;
    /** 来源用户消息幂等标识。 */
    private String sourceClientMessageId;
    /** 草稿所属客服用户 ID。 */
    private Long ownerUserId;
    /** 目标表单所需新增权限。 */
    private String targetPermission;
    /** 非敏感流程转换来源。 */
    private String convertedFrom;
    /** 草稿过期时间。 */
    private Timestamp expiresAt;
    /** 首次领取时间。 */
    private Timestamp claimedAt;
    /** 正式业务提交时间。 */
    private Timestamp submittedAt;
    /** 正式创建后的业务记录 ID。 */
    private Long targetBusinessId;
    /** 逻辑删除标记。 */
    @TableLogic
    private Boolean isDel;
}
