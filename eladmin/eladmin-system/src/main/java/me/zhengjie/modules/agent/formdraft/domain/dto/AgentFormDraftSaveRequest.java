package me.zhengjie.modules.agent.formdraft.domain.dto;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/** Agent 内部创建或修订表单草稿的统一请求。 */
@Data
public class AgentFormDraftSaveRequest implements Serializable {
    /** 修订时传入的草稿 ID；创建时为空。 */
    private String draftId;
    /** 修订时必须携带的当前版本。 */
    private Integer expectedRevision;
    /** 固定草稿类型。 */
    @NotBlank
    private String draftType;
    /** 固定协议版本，首期仅支持 v1。 */
    @NotBlank
    private String schemaVersion;
    /** 来源 Agent 会话 ID。 */
    @NotBlank
    private String sourceSessionId;
    /** 本轮用户消息幂等 ID。 */
    @NotBlank
    private String clientMessageId;
    /** 类型化 payload 的 JSON 入口，服务层按 draftType 严格转换并拒绝未知字段。 */
    @NotNull
    private JSONObject payload;
    /** 已识别的登记字段路径。 */
    private List<String> recognizedFields;
    /** 待补充的登记字段路径。 */
    private List<String> missingFields;
    /** 只允许稳定告警码和脱敏提示。 */
    @Valid
    private List<AgentFormDraftWarning> warnings;
    /** 非敏感流程转换来源。 */
    private String convertedFrom;
}
