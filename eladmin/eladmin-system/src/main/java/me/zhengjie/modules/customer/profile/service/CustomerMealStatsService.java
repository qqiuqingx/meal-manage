package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarSaveResult;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsRowDto;
import me.zhengjie.utils.PageResult;

/**
 * 客户用餐统计页的订单列表和订单排餐日历服务。
 */
public interface CustomerMealStatsService {

    /**
     * 对订单分页并批量补充客户共享资料和订单统计。
     *
     * @param criteria 客户编号、姓名、手机号和统计月份筛选
     * @param page 从 1 开始的页码
     * @param size 每页订单数
     * @return 当前页订单行及符合条件的订单总数
     */
    PageResult<CustomerMealStatsRowDto> queryMealStats(CustomerMealStatsQueryCriteria criteria, Integer page, Integer size);

    /**
     * 查询单笔订单指定月份的排餐日历。
     *
     * @param orderId 订单ID
     * @param statsMonth 查询月份，格式 yyyy-MM
     * @return 当前订单的日历、覆盖和编辑状态
     */
    CustomerOrderMealCalendarDto getOrderCalendar(Long orderId, String statsMonth);

    /**
     * 保存单笔订单指定月份的完整数量覆盖快照。
     *
     * @param orderId URL 指定的订单ID
     * @param request 当前月份、修订标记和完整覆盖快照
     * @return 保存后的修订标记及清理排餐数量
     */
    CustomerOrderMealCalendarSaveResult saveOrderCalendar(Long orderId, CustomerOrderMealCalendarSaveDto request);
}
