package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 受控列表查询的统一分页结果。
 *
 * @param <T> 受控明细项类型
 */
@Data
public class AgentListResultDto<T> {

    /** 查询命中的总数量。 */
    private long total;

    /** 当前页受控结果项。 */
    private List<T> items = new ArrayList<>();

    /** 是否因结果上限而截断。 */
    private boolean truncated;
}
