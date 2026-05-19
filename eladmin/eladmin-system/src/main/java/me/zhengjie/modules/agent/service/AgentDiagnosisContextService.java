package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;

import java.util.List;

/**
 * 诊断上下文聚合服务
 */
public interface AgentDiagnosisContextService {

    MealPlanDiagnosisContextDto buildContext(MealPlanDiagnosisContextRequest request);

    CustomerProfileDetailDto resolveCustomerProfile(Long customerId, String customerCode);

    List<CustomerOrderDetailDto> resolveOrders(Long customerId, String customerCode, Integer page, Integer size);

    MealPlanDetailVO resolveMealPlan(String recordDate, String mealType);

    List<MealPackageStatDto> resolveCandidateDishStats(String recordDate);
}
