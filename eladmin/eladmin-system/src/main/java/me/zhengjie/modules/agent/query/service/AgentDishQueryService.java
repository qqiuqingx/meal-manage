package me.zhengjie.modules.agent.query.service;

import me.zhengjie.modules.agent.query.domain.dto.AgentDishSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidatePreviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentScheduledMenuResponseDto;

import java.util.List;

/** Agent 菜品与配料只读查询服务。 */
public interface AgentDishQueryService {
    /**
     * 按受控菜品 ID 集合查询菜品及限量配料摘要。
     *
     * @param dishIds 已由统一工具输入护栏限制的菜品 ID 集合
     * @return 包含总数、截断标记和菜品摘要的结果
     */
    AgentListResultDto<AgentDishSummaryDto> listByIds(List<Integer> dishIds);

    /**
     * 按菜名、受控菜品类型和启用状态执行真实 SQL 分页查询。
     *
     * @param name 菜名关键字，可为空
     * @param dishType 菜品类型代码，可为空
     * @param enabled 是否启用，可为空
     * @param page 从 1 开始的页码
     * @param size 单页数量，最大 20
     * @return 菜品及限量配料摘要分页结果
     */
    AgentListResultDto<AgentDishSummaryDto> search(String name, String dishType, Boolean enabled, int page, int size);

    /**
     * 查询指定日期和受控餐次集合的公共排期菜单，不关联任何客户、订单或配送信息。
     *
     * @param recordDate 日期（yyyy-MM-dd）
     * @param mealTypes 餐次代码集合，仅允许 LUNCH、DINNER
     * @return 按餐次分组且限量的菜品摘要
     */
    AgentScheduledMenuResponseDto listScheduled(String recordDate, List<String> mealTypes);

    /**
     * 预览指定客户在某日餐次的排期候选菜及过滤状态，不写入排餐记录。
     *
     * @param customerId 客户稳定 ID
     * @param orderId 服务客户订单 ID，可为空；存在时只使用该订单的有效套餐
     * @param recordDate 排餐日期
     * @param mealType 餐次代码
     * @return 受控候选菜摘要
     */
    AgentDishCandidatePreviewDto previewCandidates(Long customerId, Long orderId, String recordDate, String mealType);
}
