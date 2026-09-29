package me.zhengjie.modules.customer.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 订单排餐日历数量覆盖 Mapper。
 */
@Mapper
public interface CustomerMealScheduleAdditionMapper extends BaseMapper<CustomerMealScheduleAddition> {

    /**
     * 查询客户在日期范围内的有效订单数量覆盖，供排餐生成读取历史餐数分配。
     *
     * @param customerIds 客户ID集合
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 有效数量覆盖列表
     */
    List<CustomerMealScheduleAddition> selectActiveByCustomerIdsAndDateRange(@Param("customerIds") List<Long> customerIds,
                                                                             @Param("startDate") LocalDate startDate,
                                                                             @Param("endDate") LocalDate endDate);

    /**
     * 查询指定日期餐次的有效数量覆盖。
     *
     * @param recordDate 排餐日期
     * @param mealType 餐次
     * @return 有效数量覆盖列表
     */
    List<CustomerMealScheduleAddition> selectActiveByDateMeal(@Param("recordDate") LocalDate recordDate,
                                                              @Param("mealType") String mealType);

    /**
     * 查询指定订单日期餐次的任意数量覆盖，包含已软删除记录，用于恢复历史行避免唯一键冲突。
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
     * 查询指定订单在日期范围内的有效数量覆盖。
     *
     * @param orderId 订单ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 当前订单范围内的有效覆盖列表
     */
    List<CustomerMealScheduleAddition> selectActiveByOrderIdAndDateRange(@Param("orderId") Long orderId,
                                                                         @Param("startDate") LocalDate startDate,
                                                                         @Param("endDate") LocalDate endDate);

    /**
     * 软删除指定订单在日期范围内不在保留ID集合中的覆盖；空集合仅清理该订单和该日期范围。
     *
     * @param orderId 订单ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param keepIds 保留的覆盖记录ID
     * @return 影响行数
     */
    int softDeleteMissingByOrderIdAndDateRange(@Param("orderId") Long orderId,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("keepIds") List<Long> keepIds);

    /**
     * 更新当前订单的有效覆盖，显式写入 NULL 含汤数以清除旧值。
     */
    int updateOrderCalendarOverride(@Param("id") Long id,
                                    @Param("orderId") Long orderId,
                                    @Param("quantity") Integer quantity,
                                    @Param("soupQuantity") Integer soupQuantity,
                                    @Param("remark") String remark,
                                    @Param("updateBy") String updateBy);

    /**
     * 恢复当前订单同日期餐次的软删除覆盖，并显式写入 NULL 含汤数。
     */
    int reviveOrderCalendarOverride(@Param("id") Long id,
                                    @Param("orderId") Long orderId,
                                    @Param("recordDate") LocalDate recordDate,
                                    @Param("mealType") String mealType,
                                    @Param("quantity") Integer quantity,
                                    @Param("soupQuantity") Integer soupQuantity,
                                    @Param("remark") String remark,
                                    @Param("updateBy") String updateBy);
}
