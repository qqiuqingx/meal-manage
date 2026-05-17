package me.zhengjie.modules.agent.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.agent.service.AgentDiagnosisContextService;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanQueryCriteria;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.utils.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 默认诊断上下文聚合实现
 */
@Service
@RequiredArgsConstructor
public class AgentDiagnosisContextServiceImpl implements AgentDiagnosisContextService {

    private final CustomerProfileService customerProfileService;
    private final CustomerOrderService customerOrderService;
    private final MealPlanService mealPlanService;

    @Override
    public MealPlanDiagnosisContextDto buildContext(MealPlanDiagnosisContextRequest request) {
        MealPlanDiagnosisContextDto context = new MealPlanDiagnosisContextDto();
        context.setCustomerId(request.getCustomerId());
        context.setCustomerCode(request.getCustomerCode());
        context.setRecordDate(request.getRecordDate());
        context.setMealType(request.getMealType());

        CustomerProfileDetailDto customerProfile = resolveCustomerProfile(request.getCustomerId(), request.getCustomerCode());
        context.setCustomerProfile(customerProfile);
        if (customerProfile != null) {
            context.setCustomerId(customerProfile.getId());
            context.setCustomerName(customerProfile.getCustomerName());
        }

        context.setOrders(resolveOrders(context.getCustomerId()));
        context.setMealPlan(resolveMealPlan(request.getRecordDate(), request.getMealType()));
        context.setCandidateDishStats(resolveCandidateDishStats(request.getRecordDate()));
        return context;
    }

    private CustomerProfileDetailDto resolveCustomerProfile(Long customerId, String customerCode) {
        try {
            if (customerId != null) {
                return customerProfileService.getDetail(customerId);
            }

            if (customerCode == null || customerCode.trim().isEmpty()) {
                return null;
            }

            CustomerProfileQueryCriteria criteria = new CustomerProfileQueryCriteria();
            criteria.setCustomerCode(customerCode);
            criteria.setPage(0);
            criteria.setSize(1);

            PageResult<?> pageResult = customerProfileService.queryAll(criteria, new Page<>(1, 1));
            if (pageResult == null || CollectionUtils.isEmpty(pageResult.getContent())) {
                return null;
            }

            Object item = pageResult.getContent().get(0);
            if (!(item instanceof CustomerProfile)) {
                return null;
            }
            CustomerProfile profile = (CustomerProfile) item;
            return customerProfileService.getDetail(profile.getId());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<CustomerOrderDetailDto> resolveOrders(Long customerId) {
        if (customerId == null) {
            return Collections.emptyList();
        }
        try {
            PageResult<?> pageResult = customerOrderService.getOrdersByCustomerId(customerId, 1, 100);
            if (pageResult == null || CollectionUtils.isEmpty(pageResult.getContent())) {
                return Collections.emptyList();
            }
            List<CustomerOrderDetailDto> orders = new ArrayList<>();
            for (Object item : pageResult.getContent()) {
                if (item instanceof CustomerOrderDetailDto) {
                    orders.add((CustomerOrderDetailDto) item);
                }
            }
            return orders;
        } catch (RuntimeException ex) {
            return Collections.emptyList();
        }
    }

    private MealPlanDetailVO resolveMealPlan(String recordDate, String mealType) {
        try {
            MealPlanQueryCriteria criteria = new MealPlanQueryCriteria();
            criteria.setRecordDate(recordDate);
            criteria.setMealType(mealType);
            criteria.setPage(1);
            criteria.setSize(1);

            PageResult<MealPlan> pageResult = mealPlanService.queryAll(criteria);
            if (pageResult == null || CollectionUtils.isEmpty(pageResult.getContent())) {
                return null;
            }

            MealPlan mealPlan = pageResult.getContent().stream()
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (mealPlan == null) {
                return null;
            }
            return mealPlanService.queryMealPlanDetail(mealPlan.getId());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<MealPackageStatDto> resolveCandidateDishStats(String recordDate) {
        try {
            List<MealPackageStatDto> stats = mealPlanService.statByDate(recordDate);
            return stats == null ? Collections.emptyList() : stats;
        } catch (RuntimeException ex) {
            return Collections.emptyList();
        }
    }
}
