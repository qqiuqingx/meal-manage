package me.zhengjie.modules.customer.order.service;

import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderSaveDto;
import me.zhengjie.utils.PageResult;

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
}
