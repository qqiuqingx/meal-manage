package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查运营统计查询条件。
 */
@Data
public class AgentOperationStatsQuery {

    private String recordDateStart;

    private String recordDateEnd;

    private String mealType;
}
