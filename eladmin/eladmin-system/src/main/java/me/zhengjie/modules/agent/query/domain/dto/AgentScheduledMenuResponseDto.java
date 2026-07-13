package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 指定日期公共排期菜单的强类型分组响应。 */
@Data
public class AgentScheduledMenuResponseDto {
    /** 查询日期（yyyy-MM-dd）。 */
    private String recordDate;
    /** 按请求餐次稳定排序的菜单分组。 */
    private List<AgentScheduledMenuGroupDto> groups = new ArrayList<>();
    /** 所有请求餐次的已配置菜品总数。 */
    private int total;
    /** 菜品明细是否因内部接口上限截断。 */
    private boolean truncated;
}
