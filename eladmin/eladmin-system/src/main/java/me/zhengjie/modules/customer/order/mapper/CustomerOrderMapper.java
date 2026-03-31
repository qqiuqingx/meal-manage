package me.zhengjie.modules.customer.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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

    /**
     * 统计同一客户在同一时间段内的订单数量
     */
    int countOverlappingOrders(@Param("customerId") Long customerId,
                              @Param("startDate") java.time.LocalDate startDate,
                              @Param("endDate") java.time.LocalDate endDate,
                              @Param("excludeId") Long excludeId);

    /**
     * 统计同一客户在同一时间段内的全餐次订单数量
     */
    int countAllMealTypeOrders(@Param("customerId") Long customerId,
                               @Param("startDate") java.time.LocalDate startDate,
                               @Param("endDate") java.time.LocalDate endDate,
                               @Param("excludeId") Long excludeId);

    /**
     * 统计同一客户在同一时间段内的指定餐次订单数量
     */
    int countMealTypeOrders(@Param("customerId") Long customerId,
                           @Param("startDate") java.time.LocalDate startDate,
                           @Param("endDate") java.time.LocalDate endDate,
                           @Param("mealType") String mealType,
                           @Param("excludeId") Long excludeId);

    /**
     * 批量统计每个客户的进行中订单数量（status=1）
     * @param customerIds 客户ID集合
     * @return Map: key=customerId, value=有效订单数
     */
    List<java.util.Map<String, Object>> countActiveOrdersByCustomerIds(@Param("customerIds") java.util.Set<Long> customerIds);

    /**
     * 查询指定日期和餐次的有效订单候选
     */
    List<CustomerOrder> findMealPlanOrders(@Param("targetDate") LocalDate targetDate,
                                           @Param("mealType") String mealType);

}
