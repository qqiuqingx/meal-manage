package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 由 Agent 工具事实生成、前端按白名单解释的固定 UI 动作。 */
@Data
public class AgentUiActionDto {
    private String type;
    private String label;
    private boolean enabled;
    private Map<String, String> payload = new LinkedHashMap<>();
}
