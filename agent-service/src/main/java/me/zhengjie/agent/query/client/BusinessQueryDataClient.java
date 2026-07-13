package me.zhengjie.agent.query.client;

import java.util.Map;
import java.util.List;
import me.zhengjie.agent.query.client.dto.DishCandidatePreviewResponse;
import me.zhengjie.agent.query.client.dto.CustomerOverviewResponse;
import me.zhengjie.agent.query.client.dto.OrderListResponse;
import me.zhengjie.agent.query.client.dto.OrderSummaryResponse;
import me.zhengjie.agent.query.client.dto.MealPlanListResponse;
import me.zhengjie.agent.query.client.dto.VerificationListResponse;
import me.zhengjie.agent.query.client.dto.RefundListResponse;
import me.zhengjie.agent.query.client.dto.CustomerCandidateListResponse;
import me.zhengjie.agent.query.client.dto.PackageSpecResponse;
import me.zhengjie.agent.query.client.dto.BusinessRuleResponse;
import me.zhengjie.agent.query.client.dto.DishListResponse;

/**
 * 调用主系统受控业务查询接口的客户端；不支持自由 SQL 或任意 URL。
 */
public interface BusinessQueryDataClient {

    /** 按客户 ID、编号或姓名解析有限候选。 */
    Map<String, Object> resolveCustomer(Long customerId, String customerCode, String customerName);

    /** 查询客户候选的强类型契约，默认适配未迁移的 Map 实现。 */
    default CustomerCandidateListResponse resolveCustomerTyped(Long customerId, String customerCode, String customerName) {
        return CustomerCandidateListResponse.fromLegacyMap(resolveCustomer(customerId, customerCode, customerName));
    }

    /** 查询客户综合概览。 */
    Map<String, Object> customerOverview(Long customerId, String customerCode);

    /**
     * 查询客户概览的强类型契约。默认适配旧 Map 实现，仅用于渐进迁移；生产 HTTP 客户端直接返回 DTO。
     */
    default CustomerOverviewResponse customerOverviewTyped(Long customerId, String customerCode) {
        return CustomerOverviewResponse.fromLegacyMap(customerOverview(customerId, customerCode));
    }

    /** 查询客户订单列表。 */
    Map<String, Object> listOrders(Long customerId, Integer status, int page, int size);

    /** 查询订单分页摘要的强类型契约，默认适配未迁移的 Map 实现。 */
    default OrderListResponse listOrdersTyped(Long customerId, Integer status, int page, int size) {
        return OrderListResponse.fromLegacyMap(listOrders(customerId, status, page, size));
    }

    /** 按订单 ID 或订单编号查询单笔非金额详情。 */
    Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId);

    /** 查询单笔订单的强类型契约，默认适配未迁移的 Map 实现。 */
    default OrderSummaryResponse orderDetailTyped(Long orderId, String orderCode, Long customerId) {
        return OrderSummaryResponse.fromLegacyMap(orderDetail(orderId, orderCode, customerId));
    }

    /** 查询客户或订单最近核销记录。 */
    Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit);

    /** 按受控日期范围查询核销记录。 */
    default Map<String, Object> listVerifications(Long customerId, Long orderId, String mealType, int limit,
                                                  String startDate, String endDate) {
        return listVerifications(customerId, orderId, mealType, limit);
    }

    /** 查询核销记录的强类型契约，默认适配未迁移的 Map 实现。 */
    default VerificationListResponse listVerificationsTyped(Long customerId, Long orderId, String mealType, int limit, String startDate, String endDate) {
        return VerificationListResponse.fromLegacyMap(listVerifications(customerId, orderId, mealType, limit, startDate, endDate));
    }

    /** 查询客户或订单退餐记录。 */
    Map<String, Object> listRefunds(Long customerId, Long orderId, int limit);

    /** 按受控日期范围查询退餐记录。 */
    default Map<String, Object> listRefunds(Long customerId, Long orderId, int limit, String startDate, String endDate) {
        return listRefunds(customerId, orderId, limit);
    }

    /** 查询退餐记录的强类型契约，默认适配未迁移的 Map 实现。 */
    default RefundListResponse listRefundsTyped(Long customerId, Long orderId, int limit, String startDate, String endDate) {
        return RefundListResponse.fromLegacyMap(listRefunds(customerId, orderId, limit, startDate, endDate));
    }

    /** 查询客户指定日期和餐次的排餐及菜品摘要。 */
    Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType);

    /**
     * 查询客户指定日期餐次或受控客户排餐记录 ID 的排餐摘要。
     *
     * @param customerId 当前客户约束，可为空
     * @param recordDate 单日查询日期，可为空
     * @param mealType 餐次，可为空
     * @param customerMealPlanId 客户排餐记录 ID，可为空
     * @return 受控的排餐与菜品摘要
     */
    default Map<String, Object> listMealPlans(Long customerId, String recordDate, String mealType, Long customerMealPlanId) {
        return listMealPlans(customerId, recordDate, mealType);
    }

    /** 查询排餐列表的强类型契约，默认适配未迁移的 Map 实现。 */
    default MealPlanListResponse listMealPlansTyped(Long customerId, String recordDate, String mealType, Long customerMealPlanId) {
        return MealPlanListResponse.fromLegacyMap(listMealPlans(customerId, recordDate, mealType, customerMealPlanId));
    }

    /**
     * 按单日、餐次和受控分页查询客户排餐范围；customerId 为 null 时由主系统 SQL 数据范围限制结果。
     *
     * @param customerId 单客户约束；范围查询传 null
     * @param recordDate 必填单日日期
     * @param mealType 单一餐次；为空时查询该日全部排餐餐次
     * @param page 页码，从 1 开始
     * @param size 每页条数，最大 50
     * @return 仅包含脱敏字段、客户编号与排餐过滤事实的结果
     */
    default MealPlanListResponse listMealPlansRangeTyped(Long customerId, String recordDate, String mealType, int page, int size) {
        return listMealPlansTyped(customerId, recordDate, mealType, null);
    }

    /** 按主套餐稳定 ID 查询父子套餐和餐品规格。 */
    default Map<String, Object> packageDetail(Long parentPackageId) {
        throw new UnsupportedOperationException("package detail client is not configured");
    }

    /** 查询套餐规格的强类型契约，默认适配未迁移的 Map 实现。 */
    default PackageSpecResponse packageDetailTyped(Long parentPackageId) { return PackageSpecResponse.fromLegacyMap(packageDetail(parentPackageId)); }

    /** 查询主系统版本化业务规则。 */
    Map<String, Object> explainRule(String topic);

    /** 查询业务规则的强类型契约，默认适配未迁移的 Map 实现。 */
    default BusinessRuleResponse explainRuleTyped(String topic) { return BusinessRuleResponse.fromLegacyMap(explainRule(topic)); }

    /** 查询受控菜品 ID 的限量配料摘要。 */
    Map<String, Object> listDishes(List<Integer> dishIds);

    /** 查询菜品与配料摘要的强类型契约，默认适配未迁移的 Map 实现。 */
    default DishListResponse listDishesTyped(List<Integer> dishIds) { return DishListResponse.fromLegacyMap(listDishes(dishIds)); }

    /** 查询指定日期餐次的公共排期菜单，不返回客户相关数据。 */
    default Map<String, Object> listScheduledDishes(String recordDate, String mealType) {
        throw new UnsupportedOperationException("scheduled menu client is not configured");
    }

    /**
     * 查询指定日期、受控餐次集合的公共排期菜单；默认实现保留旧客户端的单餐次兼容能力。
     *
     * @param recordDate 查询日期
     * @param mealTypes 服务端白名单生成的餐次集合
     * @return 按餐次分组的菜单结果
     */
    default Map<String, Object> listScheduledDishes(String recordDate, List<String> mealTypes) {
        if (mealTypes != null && mealTypes.size() == 1) return listScheduledDishes(recordDate, mealTypes.get(0));
        return listScheduledDishes(recordDate, (String) null);
    }

    /** 预览客户指定日期餐次的排期候选菜和过滤摘要。 */
    default DishCandidatePreviewResponse previewDishCandidates(Long customerId, String recordDate, String mealType) {
        throw new UnsupportedOperationException("dish candidate preview client is not configured");
    }

    /** 查询指定日期的跨客户工作量聚合，结果不包含客户明细。 */
    default Map<String, Object> dailyCustomerWorkload(String recordDate, String mealType) {
        throw new UnsupportedOperationException("daily customer workload client is not configured");
    }

    /** 按登记维度查询指定日期的跨客户工作量聚合；维度只能来自受控 QueryPlan。 */
    default Map<String, Object> dailyCustomerWorkload(String recordDate, String mealType, List<String> dimensions) {
        return dailyCustomerWorkload(recordDate, mealType);
    }

    /** 查询活跃客户去重数。 */
    default Map<String, Object> activeCustomerSummary() {
        throw new UnsupportedOperationException("active customer summary client is not configured");
    }

    /** 查询日期范围内到期的进行中订单数。 */
    default Map<String, Object> expiringOrderSummary(String startDate, String endDate) {
        throw new UnsupportedOperationException("expiring order summary client is not configured");
    }
}
