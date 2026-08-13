package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 跨服务返回的 Agent 表单草稿脱敏摘要。 */
@Data
public class AgentFormDraftSummaryDto {
    private String draftId;
    private String type;
    private String status;
    private Integer revision;
    private List<String> recognizedFields = new ArrayList<>();
    private List<String> missingFields = new ArrayList<>();
    private List<Map<String, Object>> warnings = new ArrayList<>();
    private String expiresAt;
}
