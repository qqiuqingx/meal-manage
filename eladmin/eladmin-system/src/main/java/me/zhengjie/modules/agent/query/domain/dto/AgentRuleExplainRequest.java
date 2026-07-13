package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** Agent 规则解释受控请求。 */
@Data
public class AgentRuleExplainRequest {
    /** 白名单规则主题。 */
    @NotBlank
    @Size(max = 64)
    private String topic;
}
