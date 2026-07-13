package me.zhengjie.modules.customer.order.service;

import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealBalanceDto;

/**
 * 订单餐数余额统一计算器。
 * <p>所有调用方须先提供仅含未删除核销日志的各餐次汇总，避免本类直接访问数据库。</p>
 */
public final class OrderMealBalanceCalculator {

    private OrderMealBalanceCalculator() {
    }

    /**
     * 按订单餐数和核销汇总计算两套餐数池余额。
     *
     * @param order 订单餐数配置
     * @param verifiedBreakfast 未删除早餐核销数
     * @param verifiedLunch 未删除午餐核销数
     * @param verifiedDinner 未删除晚餐核销数
     * @return 早餐、午晚餐餐数池的核销数与剩余数，剩余数最小为零
     */
    public static OrderMealBalanceDto calculate(CustomerOrder order,
                                                int verifiedBreakfast,
                                                int verifiedLunch,
                                                int verifiedDinner) {
        int breakfastCount = order == null || order.getBreakfastCount() == null ? 0 : order.getBreakfastCount();
        int lunchDinnerCount = order == null || order.getLunchDinnerCount() == null ? 0 : order.getLunchDinnerCount();
        int safeVerifiedBreakfast = Math.max(verifiedBreakfast, 0);
        int safeVerifiedLunch = Math.max(verifiedLunch, 0);
        int safeVerifiedDinner = Math.max(verifiedDinner, 0);
        return new OrderMealBalanceDto(
                safeVerifiedBreakfast,
                safeVerifiedLunch,
                safeVerifiedDinner,
                Math.max(breakfastCount - safeVerifiedBreakfast, 0),
                Math.max(lunchDinnerCount - safeVerifiedLunch - safeVerifiedDinner, 0));
    }

    /**
     * 判断订单是否计入客户当前餐数余额。
     *
     * @param order 待判断订单
     * @return 订单状态为进行中时返回 true；餐数是否剩余由统一余额计算结果决定
     */
    public static boolean isActiveOrder(CustomerOrder order) {
        return order != null && Integer.valueOf(1).equals(order.getStatus());
    }
}
