package me.zhengjie.modules.agent.formdraft.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/** 草稿稳定告警；展示文本不得包含手机号、地址或对话原文。 */
@Data
public class AgentFormDraftWarning implements Serializable {
    /** 稳定告警码。 */
    @NotBlank
    private String code;
    /** 可安全展示的脱敏提示。 */
    private String message;
}
