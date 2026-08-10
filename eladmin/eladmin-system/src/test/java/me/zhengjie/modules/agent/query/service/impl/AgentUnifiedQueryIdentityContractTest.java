package me.zhengjie.modules.agent.query.service.impl;

import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerProfileDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDailyCustomerStatsDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderSummaryDto;
import me.zhengjie.modules.agent.query.domain.unified.AgentMetricQueryRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentProfileSearchRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentServiceCustomerSearchRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentUnifiedQueryDto;
import me.zhengjie.modules.agent.query.domain.unified.AgentUnifiedQueryResponse;
import me.zhengjie.modules.agent.query.service.AgentBusinessRuleQueryService;
import me.zhengjie.modules.agent.query.service.AgentCustomerQueryService;
import me.zhengjie.modules.agent.query.service.AgentDishQueryService;
import me.zhengjie.modules.agent.query.service.AgentHistoryQueryService;
import me.zhengjie.modules.agent.query.service.AgentMealPlanQueryService;
import me.zhengjie.modules.agent.query.service.AgentOperationQueryService;
import me.zhengjie.modules.agent.query.service.AgentOrderQueryService;
import me.zhengjie.modules.agent.query.service.AgentPackageQueryService;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 主系统内部 Agent 客户身份、下单时间和指标分组契约测试。 */
@ExtendWith(MockitoExtension.class)
class AgentUnifiedQueryIdentityContractTest {
    @Mock private AgentCustomerQueryService customerQueryService;
    @Mock private AgentOrderQueryService orderQueryService;
    @Mock private AgentHistoryQueryService historyQueryService;
    @Mock private AgentMealPlanQueryService mealPlanQueryService;
    @Mock private AgentDishQueryService dishQueryService;
    @Mock private AgentPackageQueryService packageQueryService;
    @Mock private AgentOperationQueryService operationQueryService;
    @Mock private AgentBusinessRuleQueryService businessRuleQueryService;
    @Mock private CustomerProfileMapper customerProfileMapper;
    @InjectMocks private AgentUnifiedQueryServiceImpl service;

    /** 内部 Agent 客户契约使用完整姓名，不再声明旧 maskedName 字段，并保留手机号脱敏字段。 */
    @Test
    void shouldExposeAuthorizedCustomerNameWithoutMaskedName() {
        assertFieldContract(AgentCustomerProfileDto.class);
        assertFieldContract(AgentUnifiedQueryDto.ProfileItem.class);
        assertFieldContract(AgentUnifiedQueryDto.ServiceCustomerItem.class);
    }

    /** 客户档案和服务客户行必须保留客户编号，并返回已授权的完整姓名。 */
    @Test
    void shouldReturnCustomerCodeAndFullNameInUnifiedResults() {
        AgentCustomerProfileDto profile = new AgentCustomerProfileDto();
        profile.setCustomerId(7L);
        profile.setCustomerCode("C1007");
        profile.setCustomerName("张三");
        profile.setMaskedPhone("138****8000");
        AgentListResultDto<AgentCustomerProfileDto> profiles = new AgentListResultDto<>();
        profiles.setItems(List.of(profile));
        profiles.setTotal(1);
        when(customerQueryService.searchProfiles(any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
            .thenReturn(profiles);

        AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ProfileItem> result =
            service.searchCustomerProfiles(new AgentProfileSearchRequest());

        assertEquals("C1007", result.getItems().get(0).getCustomerCode());
        assertEquals("张三", result.getItems().get(0).getCustomerName());
        assertEquals("138****8000", result.getItems().get(0).getMaskedPhone());
    }

    /** 服务客户下单时间按成交时间优先、创建时间回退，并保留两个原始事实字段。 */
    @Test
    void shouldDeriveOrderTimeDeterministically() {
        AgentOrderSummaryDto withDeal = order(LocalDateTime.of(2026, 8, 1, 10, 0), LocalDateTime.of(2026, 8, 2, 11, 0));
        AgentOrderSummaryDto withoutDeal = order(null, LocalDateTime.of(2026, 8, 3, 12, 0));
        AgentOrderSummaryDto withoutTimes = order(null, null);
        AgentListResultDto<AgentOrderSummaryDto> orders = new AgentListResultDto<>();
        orders.setItems(List.of(withDeal, withoutDeal, withoutTimes));
        orders.setTotal(3);
        when(orderQueryService.searchServiceCustomers(any(), any(), any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
            .thenReturn(orders);
        when(customerProfileMapper.selectBatchIds(any())).thenReturn(List.of(profile(9L, "C1009", "李四")));

        List<AgentUnifiedQueryDto.ServiceCustomerItem> items = service
            .searchServiceCustomers(new AgentServiceCustomerSearchRequest()).getItems();

        assertEquals(items.get(0).getDealTime(), items.get(0).getOrderTime());
        assertEquals(items.get(1).getCreateTime(), items.get(1).getOrderTime());
        assertNull(items.get(2).getOrderTime());
        assertEquals("李四", items.get(0).getCustomerName());
        assertEquals("C1009", items.get(0).getCustomerCode());
    }

    /** 指标 breakdown 必须逐项保持 dimensions 的键和值及其插入顺序。 */
    @Test
    void shouldBuildDeterministicMetricBreakdownWithoutChangingDimensions() {
        AgentDailyCustomerStatsDto daily = new AgentDailyCustomerStatsDto();
        daily.setScheduledCustomerCount(5);
        LinkedHashMap<String, Long> dimensions = new LinkedHashMap<>();
        dimensions.put("BREAKFAST", 2L);
        dimensions.put("LUNCH", 3L);
        LinkedHashMap<String, java.util.Map<String, Long>> grouped = new LinkedHashMap<>();
        grouped.put("DAILY_SCHEDULED_CUSTOMER_COUNT", dimensions);
        daily.setMetricMealTypeBreakdown(grouped);
        when(operationQueryService.dailyCustomers(any())).thenReturn(daily);

        AgentMetricQueryRequest request = new AgentMetricQueryRequest();
        request.setMetric("DAILY_SCHEDULED_CUSTOMER_COUNT");
        AgentUnifiedQueryDto.MetricItem metric = service.queryBusinessMetrics(request).getData();

        assertEquals(dimensions, metric.getDimensions());
        assertEquals(2, metric.getBreakdown().size());
        assertEquals("BREAKFAST", metric.getBreakdown().get(0).getLabel());
        assertEquals(2L, metric.getBreakdown().get(0).getValue());
        assertEquals("LUNCH", metric.getBreakdown().get(1).getLabel());
        assertEquals(3L, metric.getBreakdown().get(1).getValue());
    }

    /** 核销数据总数必须读取未删除核销记录条数，不能误用按日去重客户指标。 */
    @Test
    void shouldReturnVerificationRecordCountWithoutDailyDate() {
        when(historyQueryService.countVerificationRecords()).thenReturn(27L);
        AgentMetricQueryRequest request = new AgentMetricQueryRequest();
        request.setMetric("VERIFICATION_RECORD_COUNT");

        AgentUnifiedQueryDto.MetricItem metric = service.queryBusinessMetrics(request).getData();

        assertEquals("VERIFICATION_RECORD_COUNT", metric.getMetric());
        assertEquals(27L, metric.getTotal());
        assertTrue(metric.getBreakdown().isEmpty());
    }

    /** 字段契约必须同时包含完整姓名和手机号脱敏字段，且移除旧姓名字段。 */
    private void assertFieldContract(Class<?> type) {
        assertNotNull(field(type, "customerName"));
        assertNotNull(field(type, "customerCode"));
        assertFalse(hasField(type, "maskedName"));
        if (type != AgentUnifiedQueryDto.ServiceCustomerItem.class) assertNotNull(field(type, "maskedPhone"));
    }

    /** 查找字段，供反射契约断言使用。 */
    private Field field(Class<?> type, String name) {
        try { return type.getDeclaredField(name); }
        catch (NoSuchFieldException exception) { return null; }
    }

    /** 判断类是否声明了指定字段。 */
    private boolean hasField(Class<?> type, String name) { return field(type, name) != null; }

    /** 构造最小服务客户订单事实。 */
    private AgentOrderSummaryDto order(LocalDateTime dealTime, LocalDateTime createTime) {
        AgentOrderSummaryDto order = new AgentOrderSummaryDto();
        order.setCustomerId(9L);
        order.setCustomerCode("C1009");
        order.setDealTime(dealTime);
        order.setCreateTime(createTime);
        return order;
    }

    /** 构造批量姓名查询的最小档案。 */
    private CustomerProfile profile(Long id, String code, String name) {
        CustomerProfile profile = new CustomerProfile();
        profile.setId(id);
        profile.setCustomerCode(code);
        profile.setCustomerName(name);
        return profile;
    }
}
