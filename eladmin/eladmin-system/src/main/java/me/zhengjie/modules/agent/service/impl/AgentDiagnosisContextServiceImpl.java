package me.zhengjie.modules.agent.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.agent.service.AgentDiagnosisContextService;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
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
        log.info("诊断阶段 stage=内部上下文聚合开始 customerId={} customerCode={} recordDate={} mealType={}",
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
        log.info("诊断阶段 stage=内部上下文聚合完成 customerId={} customerCode={} customerName={} recordDate={} mealType={} orders={} mealPlanPresent={} candidateDishStats={} costMs={}",
                context.getCustomerId(), context.getCustomerCode(), context.getCustomerName(), context.getRecordDate(),
                context.getMealType(), context.getOrders() == null ? 0 : context.getOrders().size(), context.getMealPlan() != null,
                context.getCandidateDishStats() == null ? 0 : context.getCandidateDishStats().size(), System.currentTimeMillis() - start);
        return context;
    }

    @Override
    public CustomerProfileDetailDto resolveCustomerProfile(Long customerId, String customerCode) {
        try {
            if (customerId != null && customerId > 0) {
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
            log.warn("诊断阶段 stage=内部客户档案解析失败 customerId={} customerCode={} errorType={} errorMessage={}",
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
                } else if (item instanceof CustomerOrder) {
                    orders.add(toOrderDetailDto((CustomerOrder) item));
                }
            }
            return orders;
        } catch (RuntimeException ex) {
            log.warn("诊断阶段 stage=内部客户订单解析失败 customerId={} resolvedCustomerId={} customerCode={} page={} size={} errorType={} errorMessage={}",
                    customerId, resolvedCustomerId, customerCode, normalizedPage, normalizedSize,
                    ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    private CustomerOrderDetailDto toOrderDetailDto(CustomerOrder order) {
        CustomerOrderDetailDto dto = new CustomerOrderDetailDto();
        dto.setId(order.getId());
        dto.setCustomerId(order.getCustomerId());
        dto.setCustomerName(order.getCustomerName());
        dto.setPhone(order.getPhone());
        dto.setParentPackageName(order.getParentPackageName());
        dto.setChildPackageName(order.getChildPackageName());
        dto.setOrderCode(order.getOrderCode());
        dto.setDepositAmount(order.getDepositAmount());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setFinalAmount(order.getFinalAmount());
        dto.setBreakfastCount(order.getBreakfastCount());
        dto.setLunchDinnerCount(order.getLunchDinnerCount());
        dto.setTotalCount(totalCount(order));
        dto.setBreakfastPrice(order.getBreakfastPrice());
        dto.setLunchDinnerPrice(order.getLunchDinnerPrice());
        dto.setVerifiedCount(order.getVerifiedCount());
        dto.setVerifiedAmount(order.getVerifiedAmount());
        dto.setMealBalance(order.getMealBalance());
        dto.setRemainingCount(order.getRemainingCount());
        dto.setDealTime(order.getDealTime());
        dto.setFirstDeliveryTime(order.getFirstDeliveryTime());
        dto.setStartDate(order.getStartDate());
        dto.setEndDate(order.getEndDate());
        dto.setStatus(order.getStatus());
        dto.setStatusDesc(statusDesc(order.getStatus()));
        dto.setMealType(order.getMealType());
        dto.setMealTypeDesc(mealTypeDesc(order.getMealType()));
        dto.setScheduleMode(order.getScheduleMode());
        dto.setDeliveryDates(order.getDeliveryDates());
        dto.setRemark(order.getRemark());
        dto.setCustomerSource(order.getCustomerSource());
        dto.setCreateTime(order.getCreateTime());
        dto.setUpdateTime(order.getUpdateTime());
        return dto;
    }

    private Integer totalCount(CustomerOrder order) {
        if (order.getTotalCount() != null) {
            return order.getTotalCount();
        }
        int breakfast = order.getBreakfastCount() == null ? 0 : order.getBreakfastCount();
        int lunchDinner = order.getLunchDinnerCount() == null ? 0 : order.getLunchDinnerCount();
        return breakfast + lunchDinner;
    }

    private String statusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0:
                return "已取消";
            case 1:
                return "进行中";
            case 2:
                return "已完成";
            default:
                return "未知";
        }
    }

    private String mealTypeDesc(String mealType) {
        if (mealType == null) {
            return "全餐次";
        }
        switch (mealType) {
            case "LUNCH":
                return "午餐";
            case "DINNER":
                return "晚餐";
            case "ALL":
                return "全餐次";
            default:
                return "未知";
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
            log.warn("诊断阶段 stage=内部排餐解析失败 recordDate={} mealType={} errorType={} errorMessage={}",
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
            log.warn("诊断阶段 stage=内部候选菜品统计解析失败 recordDate={} errorType={} errorMessage={}",
                    recordDate, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }
}
