package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;

import java.util.List;
import java.util.Map;

/**
 * 诊断上下文聚合服务
 */
public interface AgentDiagnosisContextService {

    MealPlanDiagnosisContextDto buildContext(MealPlanDiagnosisContextRequest request);

    CustomerProfileDetailDto resolveCustomerProfile(Long customerId, String customerCode);

    List<CustomerOrderDetailDto> resolveOrders(Long customerId, String customerCode, Integer page, Integer size);

    MealPlanDetailVO resolveMealPlan(String recordDate, String mealType);

    List<MealPackageStatDto> resolveCandidateDishStats(String recordDate);

    /**
     * 查询客户排除日期、忌口和特殊要求，返回智能排查工具使用的非敏感字段。
     */
    Map<String, Object> resolveCustomerExcludeDates(Long customerId, String customerCode);

    /**
     * 查询客户订单餐数余额，返回早餐池和午晚餐池的核销数、剩余数。
     */
    Map<String, Object> resolveOrderMealBalance(Long customerId, String customerCode, Integer page, Integer size);

    /**
     * 查询套餐规格，返回父套餐、子套餐规格以及诊断用缺失字段摘要。
     */
    Map<String, Object> resolvePackageSpec(Long customerId, String customerCode, Long parentPackageId, Long childPackageId);

    /**
     * 查询指定排餐日期的候选菜诊断明细，返回套餐维度的候选数量摘要。
     */
    List<Map<String, Object>> resolveDishCandidateDetail(String recordDate);

    /**
     * 查询核销日志，支持按订单或客户聚合，并按日期范围、餐次过滤。
     */
    List<Map<String, Object>> resolveVerificationLogs(Long customerId, String customerCode, Long orderId,
                                                       String recordDateStart, String recordDateEnd, String mealType);

    /**
     * 查询退餐日志，支持按订单或客户订单集合聚合。
     */
    List<Map<String, Object>> resolveMealRefunds(Long customerId, String customerCode, Long orderId);

    /**
     * 查询排餐生成快照，返回计划状态、成功失败数量和失败客户摘要。
     */
    Map<String, Object> resolveMealPlanGenerationSnapshot(String recordDate, String mealType);
}
