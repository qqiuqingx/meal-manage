package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 智能排查候选菜统计查询请求。
 */
@Data
public class AgentCandidateDishStatsRequest {
    @NotBlank
    private String recordDate;
}
