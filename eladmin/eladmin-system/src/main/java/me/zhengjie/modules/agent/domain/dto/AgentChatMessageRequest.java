package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 前端可提交的聊天消息。
 *
 * <p>会话快照、工具白名单和访问上下文必须由主系统服务端生成，不能出现在此请求中。</p>
 */
@Data
public class AgentChatMessageRequest {

    private String sessionId;
    private String clientMessageId;
    /** 固定草稿动作携带的草稿引用；服务端会重新校验所有权和会话。 */
    private String formDraftId;
    @NotBlank
    private String message;
}
