package me.zhengjie.modules.agent.session.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 智能排查会话标题更新请求。
 */
@Data
public class AgentChatSessionTitleUpdateRequest {

    @NotBlank
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
