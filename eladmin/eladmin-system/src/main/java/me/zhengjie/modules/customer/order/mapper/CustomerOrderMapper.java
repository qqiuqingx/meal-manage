package me.zhengjie.modules.customer.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealVerifiedCountDto;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import me.zhengjie.modules.customer.order.domain.dto.OrderVerifiedCountDto;
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
     * 查询可关联的试餐订单，父套餐名称必须包含“试餐”。
     *
     * @param keyword 订单编号、客户姓名、手机号或客户编号关键字
     * @param excludeId 需要排除的当前订单ID
     * @param limit 最大返回条数
     * @return 可选试餐订单列表
     */
    List<CustomerOrder> findTrialOrders(@Param("keyword") String keyword,
                                        @Param("excludeId") Long excludeId,
                                        @Param("limit") Integer limit);

    /**
     * 根据客户ID查询最新订单（按成交时间倒序）
     */
    CustomerOrder findLatestByCustomerId(@Param("customerId") Long customerId);

    /**
     * 根据客户ID查询所有有效订单（status=1）
     * @param customerId 客户ID
     * @return 有效订单列表，按成交时间倒序
     */
    List<CustomerOrder> findActiveOrdersByCustomerId(@Param("customerId") Long customerId);

    /**
     * 批量查询客户的进行中订单
     */
    List<CustomerOrder> findActiveOrdersByCustomerIds(@Param("customerIds") List<Long> customerIds,
                                                      @Param("startedBeforeDate") LocalDate startedBeforeDate);

    /**
     * 统计同一客户在同一时间段内的订单数量
     */
    int countOverlappingOrders(@Param("customerId") Long customerId,
                              @Param("startDate") java.time.LocalDate startDate,
                              @Param("excludeId") Long excludeId);

    /**
     * 统计同一客户在同一时间段内的全餐次订单数量
     */
    int countAllMealTypeOrders(@Param("customerId") Long customerId,
                               @Param("startDate") java.time.LocalDate startDate,
                               @Param("excludeId") Long excludeId);

    /**
     * 统计同一客户在同一时间段内的指定餐次订单数量
     */
    int countMealTypeOrders(@Param("customerId") Long customerId,
                           @Param("startDate") java.time.LocalDate startDate,
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

    /**
     * 查询指定日期范围内的所有订单（用于日志对比）
     */
    List<CustomerOrder> findByDateRangeAndMealType(@Param("targetDate") LocalDate targetDate,
                                                    @Param("mealType") String mealType);

    /**
     * 原子性增加核销餐数（verified_count+1，remaining_count-1）
     * @param orderId 订单ID
     * @return 更新行数，0表示失败（如剩余餐数不足）
     */
    int incrementVerifiedCount(@Param("orderId") Long orderId);

    /**
     * 原子性增加核销餐数和金额
     * @param orderId 订单ID
     * @param price 单价
     * @return 更新行数，0表示失败（如剩余餐数不足）
     */
    int incrementVerifiedCountAndAmount(@Param("orderId") Long orderId, @Param("price") java.math.BigDecimal price);

    /**
     * 当剩余餐数为0时，原子性更新订单状态为已完成
     * @param orderId 订单ID
     * @return 更新行数，0表示无需更新（订单未完成或已完成）
     */
    int updateStatusToCompletedWhenFinished(@Param("orderId") Long orderId);

    /**
     * 根据订单ID和餐次类型统计已核销餐数
     * @param orderId 订单ID
     * @return 各餐次已核销数列表
     */
    List<OrderVerifiedCountDto> sumVerifiedCountByOrderId(@Param("orderId") Long orderId);

    /**
     * 批量统计订单各餐次已核销餐数
     */
    List<OrderMealVerifiedCountDto> sumVerifiedCountByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * 回退核销状态（删除核销日志时调用）
     * verified_count-1, remaining_count+1, verified_amount-price, 如果订单状态为已完成则回退为进行中
     * @param orderId 订单ID
     * @param price 核销单价（根据 mealType 决定使用 breakfastPrice 或 lunchDinnerPrice）
     * @return 更新行数
     */
    int revertVerification(@Param("orderId") Long orderId, @Param("price") java.math.BigDecimal price);

    /**
     * 退餐：更新订单状态为已退餐
     * @param orderId 订单ID
     * @return 更新行数
     */
    int updateStatusToRefunded(@Param("orderId") Long orderId);

}
