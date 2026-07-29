package me.zhengjie.agent.query.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.query.client.dto.BusinessRuleResponse;
import me.zhengjie.agent.query.client.dto.CustomerCandidateListResponse;
import me.zhengjie.agent.query.client.dto.CustomerOverviewResponse;
import me.zhengjie.agent.query.client.dto.DishListResponse;
import me.zhengjie.agent.query.client.dto.MealPlanListResponse;
import me.zhengjie.agent.query.client.dto.OrderListResponse;
import me.zhengjie.agent.query.client.dto.OrderSummaryResponse;
import me.zhengjie.agent.query.client.dto.PackageSpecResponse;
import me.zhengjie.agent.query.client.dto.RefundListResponse;
import me.zhengjie.agent.query.client.dto.VerificationListResponse;

import java.util.List;
import java.util.Map;

/**
 * 为历史 Map 形式的单元测试替身提供强类型转换。
 *
 * <p>生产接口只暴露强类型主契约；测试可继承本类并仅覆盖场景实际使用的 Map 方法，
 * 逐步迁移测试数据而不把兼容转换重新带回生产源码。</p>
 */
public class LegacyMapBusinessQueryDataClientStub implements BusinessQueryDataClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> resolveCustomer(Long customerId, String customerCode,
                                               String customerName) {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> customerOverview(Long customerId, String customerCode) {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listOrders(Long customerId, Integer status, int page, int size) {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> orderDetail(Long orderId, String orderCode, Long customerId) {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listVerifications(Long customerId, Long orderId,
                                                 String mealType, int limit) {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listVerifications(Long customerId, Long orderId,
                                                 String mealType, int limit,
                                                 String startDate, String endDate) {
        return listVerifications(customerId, orderId, mealType, limit);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit) {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listRefunds(Long customerId, Long orderId, int limit,
                                           String startDate, String endDate) {
        return listRefunds(customerId, orderId, limit);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listMealPlans(Long customerId, String recordDate,
                                             String mealType) {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listMealPlans(Long customerId, String recordDate,
                                             String mealType, Long customerMealPlanId) {
        return listMealPlans(customerId, recordDate, mealType);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> packageDetail(Long parentPackageId) {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> explainRule(String topic) {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> listDishes(List<Integer> dishIds) {
        return Map.of();
    }

    /** {@inheritDoc} */
    @Override
    public CustomerCandidateListResponse resolveCustomerTyped(Long customerId,
                                                              String customerCode,
                                                              String customerName) {
        return convert(resolveCustomer(customerId, customerCode, customerName),
            CustomerCandidateListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public CustomerOverviewResponse customerOverviewTyped(Long customerId,
                                                          String customerCode) {
        return convert(customerOverview(customerId, customerCode),
            CustomerOverviewResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public OrderListResponse listOrdersTyped(Long customerId, Integer status,
                                             int page, int size) {
        return convert(listOrders(customerId, status, page, size), OrderListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public OrderSummaryResponse orderDetailTyped(Long orderId, String orderCode,
                                                 Long customerId) {
        return convert(orderDetail(orderId, orderCode, customerId),
            OrderSummaryResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public VerificationListResponse listVerificationsTyped(Long customerId, Long orderId,
                                                           String mealType, int limit,
                                                           String startDate, String endDate) {
        return convert(listVerifications(customerId, orderId, mealType, limit,
            startDate, endDate), VerificationListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public RefundListResponse listRefundsTyped(Long customerId, Long orderId, int limit,
                                               String startDate, String endDate) {
        return convert(listRefunds(customerId, orderId, limit, startDate, endDate),
            RefundListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public MealPlanListResponse listMealPlansTyped(Long customerId, String recordDate,
                                                   String startDate, String endDate,
                                                   String mealType, Long customerMealPlanId,
                                                   int page, int size) {
        return convert(listMealPlans(customerId, recordDate, mealType,
            customerMealPlanId), MealPlanListResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public PackageSpecResponse packageDetailTyped(Long parentPackageId) {
        return convert(packageDetail(parentPackageId), PackageSpecResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public BusinessRuleResponse explainRuleTyped(String topic) {
        return convert(explainRule(topic), BusinessRuleResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public DishListResponse listDishesTyped(List<Integer> dishIds) {
        return convert(listDishes(dishIds), DishListResponse.class);
    }

    /**
     * 将测试展示 Map 转为指定 DTO；空结果同样生成字段默认值对象。
     *
     * @param source 测试数据
     * @param responseType DTO 类型
     * @param <T> DTO 类型参数
     * @return 转换后的测试 DTO
     */
    private <T> T convert(Map<String, Object> source, Class<T> responseType) {
        return MAPPER.convertValue(source == null ? Map.of() : source, responseType);
    }
}
