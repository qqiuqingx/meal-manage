package me.zhengjie.modules.customer.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户订单 Mapper
 */
@Mapper
public interface CustomerOrderMapper extends BaseMapper<CustomerOrder> {

    /**
     * 多条件查询订单列表（数据库分页）
     */
    List<CustomerOrder> findAll(@Param("criteria") CustomerOrderQueryCriteria criteria, @Param("page") Page<CustomerOrder> page);

    /**
     * 根据订单编号查询(排除指定ID)
     */
    int countByCodeExcludeId(@Param("orderCode") String orderCode, @Param("excludeId") Long excludeId);

    /**
     * 查询今日最大订单编号
     */
    String findTodayMaxOrderCode(@Param("datePrefix") String datePrefix);

    /**
     * 根据客户ID查询最新订单（按成交时间倒序）
     */
    CustomerOrder findLatestByCustomerId(@Param("customerId") Long customerId);
}
