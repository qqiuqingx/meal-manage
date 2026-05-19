package me.zhengjie.modules.agent.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDiagnosisContextServiceImpl implements AgentDiagnosisContextService {

    private static final int DEFAULT_ORDER_PAGE = 1;
    private static final int MAX_ORDER_PAGE_SIZE = 100;

    private final CustomerProfileService customerProfileService;
    private final CustomerOrderService customerOrderService;
    private final MealPlanService mealPlanService;

    @Override
    public MealPlanDiagnosisContextDto buildContext(MealPlanDiagnosisContextRequest request) {
        long start = System.currentTimeMillis();
        log.info("build agent diagnosis context start customerId={} customerCode={} recordDate={} mealType={}",
                request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType());
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

        context.setOrders(resolveOrders(context.getCustomerId(), context.getCustomerCode(), null, null));
        context.setMealPlan(resolveMealPlan(request.getRecordDate(), request.getMealType()));
        context.setCandidateDishStats(resolveCandidateDishStats(request.getRecordDate()));
        log.info("build agent diagnosis context completed customerId={} customerCode={} customerName={} recordDate={} mealType={} orders={} mealPlanPresent={} candidateDishStats={} costMs={}",
                context.getCustomerId(), context.getCustomerCode(), context.getCustomerName(), context.getRecordDate(),
                context.getMealType(), context.getOrders() == null ? 0 : context.getOrders().size(), context.getMealPlan() != null,
                context.getCandidateDishStats() == null ? 0 : context.getCandidateDishStats().size(), System.currentTimeMillis() - start);
        return context;
    }

    @Override
    public CustomerProfileDetailDto resolveCustomerProfile(Long customerId, String customerCode) {
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
            log.warn("resolve customer profile failed customerId={} customerCode={} errorType={} errorMessage={}",
                    customerId, customerCode, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public List<CustomerOrderDetailDto> resolveOrders(Long customerId, String customerCode, Integer page, Integer size) {
        Long resolvedCustomerId = customerId;
        if (resolvedCustomerId == null) {
            CustomerProfileDetailDto customerProfile = resolveCustomerProfile(null, customerCode);
            resolvedCustomerId = customerProfile == null ? null : customerProfile.getId();
        }
        if (resolvedCustomerId == null) {
            return Collections.emptyList();
        }
        int normalizedPage = page == null || page < DEFAULT_ORDER_PAGE ? DEFAULT_ORDER_PAGE : page;
        int normalizedSize = size == null || size < 1 ? MAX_ORDER_PAGE_SIZE : Math.min(size, MAX_ORDER_PAGE_SIZE);
        try {
            PageResult<?> pageResult = customerOrderService.getOrdersByCustomerId(resolvedCustomerId, normalizedPage, normalizedSize);
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
            log.warn("resolve customer orders failed customerId={} resolvedCustomerId={} customerCode={} page={} size={} errorType={} errorMessage={}",
                    customerId, resolvedCustomerId, customerCode, normalizedPage, normalizedSize,
                    ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    @Override
    public MealPlanDetailVO resolveMealPlan(String recordDate, String mealType) {
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
            log.warn("resolve meal plan failed recordDate={} mealType={} errorType={} errorMessage={}",
                    recordDate, mealType, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public List<MealPackageStatDto> resolveCandidateDishStats(String recordDate) {
        try {
            List<MealPackageStatDto> stats = mealPlanService.statByDate(recordDate);
            return stats == null ? Collections.emptyList() : stats;
        } catch (RuntimeException ex) {
            log.warn("resolve candidate dish stats failed recordDate={} errorType={} errorMessage={}",
                    recordDate, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }
}
