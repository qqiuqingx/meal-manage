package me.zhengjie.agent.domain.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** 前端只可按固定动作类型映射本地路由的 Agent UI 动作。 */
public class AgentUiAction {
    private String type;
    private String label;
    private boolean enabled;
    private Map<String, String> payload = new LinkedHashMap<>();
    public String getType() { return type; } public void setType(String v) { type = v; }
    public String getLabel() { return label; } public void setLabel(String v) { label = v; }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { enabled = v; }
    public Map<String, String> getPayload() { return payload; } public void setPayload(Map<String, String> v) { payload = v == null ? new LinkedHashMap<>() : v; }
}
