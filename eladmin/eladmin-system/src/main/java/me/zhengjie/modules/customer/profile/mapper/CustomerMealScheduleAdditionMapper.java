package me.zhengjie.modules.customer.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 客户排餐日历人工新增记录 Mapper。
 */
@Mapper
public interface CustomerMealScheduleAdditionMapper extends BaseMapper<CustomerMealScheduleAddition> {

    /**
     * 查询客户在日期范围内的有效人工新增餐次。
     *
     * @param customerIds 客户ID集合
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 人工新增记录列表
     */
    List<CustomerMealScheduleAddition> selectActiveByCustomerIdsAndDateRange(@Param("customerIds") List<Long> customerIds,
                                                                             @Param("startDate") LocalDate startDate,
                                                                             @Param("endDate") LocalDate endDate);

    /**
     * 查询指定日期餐次的有效人工新增记录。
     *
     * @param recordDate 排餐日期
     * @param mealType 餐次
     * @return 人工新增记录列表
     */
    List<CustomerMealScheduleAddition> selectActiveByDateMeal(@Param("recordDate") LocalDate recordDate,
                                                              @Param("mealType") String mealType);

    /**
     * 查询指定订单日期餐次的有效人工新增记录。
     *
     * @param orderId 订单ID
     * @param recordDate 排餐日期
     * @param mealType 餐次
     * @return 人工新增记录
     */
    CustomerMealScheduleAddition selectActiveByOrderDateMeal(@Param("orderId") Long orderId,
                                                             @Param("recordDate") LocalDate recordDate,
                                                             @Param("mealType") String mealType);

    /**
     * 查询指定订单日期餐次的任意人工新增记录，包含已软删除记录，用于恢复历史记录避免唯一键冲突。
     *
     * @param orderId 订单ID
     * @param recordDate 排餐日期
     * @param mealType 餐次
     * @return 人工新增记录
     */
    CustomerMealScheduleAddition selectAnyByOrderDateMeal(@Param("orderId") Long orderId,
                                                          @Param("recordDate") LocalDate recordDate,
                                                          @Param("mealType") String mealType);

    /**
     * 软删除客户在指定日期范围内不在保留ID集合中的人工新增记录。
     *
     * @param customerId 客户ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param keepIds 保留的人工新增ID
     * @return 影响行数
     */
    int softDeleteMissingByCustomerIdAndDateRange(@Param("customerId") Long customerId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate,
                                                  @Param("keepIds") List<Long> keepIds);
}
