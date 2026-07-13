package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentDishSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidatePreviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;

import java.util.List;

/** Agent 菜品与配料只读查询服务。 */
public interface AgentDishQueryService {
    /**
     * 按受控菜品 ID 集合查询菜品及限量配料摘要。
     *
     * @param dishIds 已由 Agent QueryPlan 限制的菜品 ID 集合
     * @return 包含总数、截断标记和菜品摘要的结果
     */
    AgentListResultDto<AgentDishSummaryDto> listByIds(List<Integer> dishIds);

    /**
     * 查询指定日期、餐次的公共排期菜单，不关联任何客户、订单或配送信息。
     *
     * @param recordDate 日期（yyyy-MM-dd）
     * @param mealType 餐次代码，可为空
     * @return 去重且限量的菜品摘要
     */
    AgentListResultDto<AgentDishSummaryDto> listScheduled(String recordDate, String mealType);

    /**
     * 预览指定客户在某日餐次的排期候选菜及过滤状态，不写入排餐记录。
     *
     * @param customerId 客户稳定 ID
     * @param recordDate 排餐日期
     * @param mealType 餐次代码
     * @return 受控候选菜摘要
     */
    AgentDishCandidatePreviewDto previewCandidates(Long customerId, String recordDate, String mealType);
}
