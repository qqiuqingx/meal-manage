package me.zhengjie.modules.customer.order.service;

import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderSaveDto;
import me.zhengjie.utils.PageResult;

import java.util.List;
import java.util.Set;

/**
 * 客户订单服务接口
 */
public interface CustomerOrderService {

    /**
     * 分页查询订单
     */
    PageResult<?> query(CustomerOrderQueryCriteria criteria, Integer current, Integer size);

    /**
     * 获取订单详情
     */
    CustomerOrderDetailDto getDetail(Long id);

    /**
     * 创建订单
     */
    void create(CustomerOrderSaveDto dto);

    /**
     * 更新订单
     */
    void update(CustomerOrderSaveDto dto);

    /**
     * 删除订单
     */
    void delete(Set<Long> ids);

    /**
     * 根据客户ID分页查询订单
     */
    PageResult<?> getOrdersByCustomerId(Long customerId, Integer current, Integer size);

    /**
     * 查询可关联的试餐订单。
     *
     * @param keyword 订单编号、客户姓名、手机号或客户编号关键字
     * @param excludeId 需要排除的当前订单ID
     * @return 父套餐名称包含“试餐”的订单列表
     */
    List<?> getTrialOrderOptions(String keyword, Long excludeId);

    /**
     * 校验订单是否与现有订单冲突
     * @param dto 订单数据
     * @param excludeId 编辑时排除的订单ID
     * @throws BadRequestException 校验失败时抛出异常
     */
    void validateOrderConflict(CustomerOrderSaveDto dto, Long excludeId);
}
