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
import me.zhengjie.modules.customer.pkg.domain.dto.ParentPackageDto;
import me.zhengjie.modules.customer.pkg.domain.dto.SubPackageDto;
import me.zhengjie.modules.customer.pkg.service.ParentPackageService;
import me.zhengjie.modules.customer.pkg.service.SubPackageService;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealRefundLog;
import me.zhengjie.modules.meal.domain.MealVerificationLog;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanQueryCriteria;
import me.zhengjie.modules.meal.service.MealRefundService;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.modules.meal.service.MealVerificationService;
import me.zhengjie.utils.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final MealVerificationService mealVerificationService;
    private final MealRefundService mealRefundService;
    private final ParentPackageService parentPackageService;
    private final SubPackageService subPackageService;

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
        Long resolvedCustomerId = normalizeCustomerId(customerId);
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

    /**
     * 归一化客户 ID。
     * 诊断链路中将空值、0 和负数统一视为未传，避免阻断按客户编号兜底查询。
     *
     * @param customerId 原始客户 ID
     * @return 归一化后的客户 ID；无效时返回 null
     */
    private Long normalizeCustomerId(Long customerId) {
        return customerId != null && customerId > 0 ? customerId : null;
    }

    private CustomerOrderDetailDto toOrderDetailDto(CustomerOrder order) {
        CustomerOrderDetailDto dto = new CustomerOrderDetailDto();
        dto.setId(order.getId());
        dto.setCustomerId(order.getCustomerId());
        dto.setCustomerName(order.getCustomerName());
        dto.setPhone(order.getPhone());
        dto.setParentPackageId(order.getParentPackageId());
        dto.setChildPackageId(order.getChildPackageId());
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

    /**
     * 查询客户停送和忌口相关配置，返回诊断所需的非敏感字段。
     */
    @Override
    public Map<String, Object> resolveCustomerExcludeDates(Long customerId, String customerCode) {
        CustomerProfileDetailDto profile = resolveCustomerProfile(customerId, customerCode);
        Map<String, Object> result = new LinkedHashMap<>();
        if (profile == null) {
            result.put("present", false);
            result.put("customerId", customerId);
            result.put("customerCode", customerCode);
            return result;
        }
        result.put("present", true);
        result.put("customerId", profile.getId());
        result.put("customerCode", profile.getCustomerCode());
        result.put("customerName", profile.getCustomerName());
        result.put("excludedDates", profile.getExcludedDates());
        result.put("allergyTags", profile.getAllergyTags());
        result.put("excludedDishIds", profile.getExcludedDishIds());
        result.put("excludedDishNames", profile.getExcludedDishNames());
        result.put("medicalRequirements", profile.getMedicalRequirements());
        result.put("specialRequirements", profile.getSpecialRequirements());
        result.put("productionDate", profile.getProductionDate());
        return result;
    }

    /**
     * 查询订单餐数余额，按早餐池和午晚餐池分别统计核销与剩余数量。
     */
    @Override
    public Map<String, Object> resolveOrderMealBalance(Long customerId, String customerCode, Integer page, Integer size) {
        List<CustomerOrderDetailDto> orders = resolveOrders(customerId, customerCode, page, size);
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> orderBalances = new ArrayList<>();
        for (CustomerOrderDetailDto order : orders) {
            List<MealVerificationLog> logs = order.getId() == null ? Collections.emptyList() : mealVerificationService.queryByOrderId(order.getId());
            int verifiedBreakfast = countVerified(logs, "BREAKFAST");
            int verifiedLunchDinner = countVerified(logs, "LUNCH") + countVerified(logs, "DINNER");
            int breakfastCount = safeInt(order.getBreakfastCount());
            int lunchDinnerCount = safeInt(order.getLunchDinnerCount());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", order.getId());
            item.put("orderCode", order.getOrderCode());
            item.put("customerId", order.getCustomerId());
            item.put("customerName", order.getCustomerName());
            item.put("status", order.getStatus());
            item.put("statusDesc", order.getStatusDesc());
            item.put("startDate", order.getStartDate());
            item.put("startMealType", order.getStartMealType());
            item.put("endDate", order.getEndDate());
            item.put("mealType", order.getMealType());
            item.put("scheduleMode", order.getScheduleMode());
            item.put("deliveryDates", order.getDeliveryDates());
            item.put("breakfastCount", breakfastCount);
            item.put("lunchDinnerCount", lunchDinnerCount);
            item.put("verifiedBreakfastCount", verifiedBreakfast);
            item.put("verifiedLunchDinnerCount", verifiedLunchDinner);
            item.put("remainingBreakfastCount", Math.max(breakfastCount - verifiedBreakfast, 0));
            item.put("remainingLunchDinnerCount", Math.max(lunchDinnerCount - verifiedLunchDinner, 0));
            item.put("remainingCount", Math.max(breakfastCount - verifiedBreakfast, 0) + Math.max(lunchDinnerCount - verifiedLunchDinner, 0));
            orderBalances.add(item);
        }
        result.put("customerId", customerId);
        result.put("customerCode", customerCode);
        result.put("orderCount", orderBalances.size());
        result.put("orders", orderBalances);
        return result;
    }

    /**
     * 查询父子套餐规格，优先使用显式套餐 ID；未传时从客户订单中推导当前套餐。
     */
    @Override
    public Map<String, Object> resolvePackageSpec(Long customerId, String customerCode, Long parentPackageId, Long childPackageId) {
        Long resolvedParentPackageId = parentPackageId;
        Long resolvedChildPackageId = childPackageId;
        if (resolvedParentPackageId == null && resolvedChildPackageId == null) {
            List<CustomerOrderDetailDto> orders = resolveOrders(customerId, customerCode, 1, 1);
            if (!CollectionUtils.isEmpty(orders)) {
                CustomerOrderDetailDto order = orders.get(0);
                resolvedParentPackageId = order.getParentPackageId();
                resolvedChildPackageId = order.getChildPackageId();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("customerCode", customerCode);
        result.put("parentPackageId", resolvedParentPackageId);
        result.put("childPackageId", resolvedChildPackageId);

        ParentPackageDto parentPackage = null;
        SubPackageDto childPackage = null;
        if (resolvedParentPackageId != null) {
            try {
                parentPackage = parentPackageService.findById(resolvedParentPackageId);
            } catch (RuntimeException ex) {
                log.warn("诊断阶段 stage=内部父套餐规格解析失败 parentPackageId={} errorType={} errorMessage={}",
                        resolvedParentPackageId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            }
        }
        if (resolvedChildPackageId != null) {
            try {
                childPackage = subPackageService.findById(resolvedChildPackageId);
            } catch (RuntimeException ex) {
                log.warn("诊断阶段 stage=内部子套餐规格解析失败 childPackageId={} errorType={} errorMessage={}",
                        resolvedChildPackageId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            }
        }

        result.put("present", parentPackage != null || childPackage != null);
        result.put("parentPackage", toParentPackageSpecMap(parentPackage));
        result.put("childPackage", toChildPackageSpecMap(childPackage));
        result.put("missingFields", packageMissingFields(parentPackage, childPackage, resolvedParentPackageId, resolvedChildPackageId));
        return result;
    }

    /**
     * 查询候选菜诊断明细，当前基于排餐日期套餐统计生成可追踪摘要。
     */
    @Override
    public List<Map<String, Object>> resolveDishCandidateDetail(String recordDate) {
        List<MealPackageStatDto> stats = resolveCandidateDishStats(recordDate);
        List<Map<String, Object>> details = new ArrayList<>();
        for (MealPackageStatDto stat : stats) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("recordDate", recordDate);
            item.put("packageCode", stat.getPackageCode());
            item.put("packageName", stat.getPackageName());
            item.put("candidateCount", stat.getMealCount());
            item.put("source", "mealPlanService.statByDate");
            details.add(item);
        }
        return details;
    }

    /**
     * 查询核销日志，支持按订单精确查询或按客户订单集合聚合后过滤日期、餐次。
     */
    @Override
    public List<Map<String, Object>> resolveVerificationLogs(Long customerId, String customerCode, Long orderId,
                                                             String recordDateStart, String recordDateEnd, String mealType) {
        List<MealVerificationLog> logs = new ArrayList<>();
        if (orderId != null && orderId > 0) {
            logs.addAll(nullToEmpty(mealVerificationService.queryByOrderId(orderId)));
        } else {
            for (CustomerOrderDetailDto order : resolveOrders(customerId, customerCode, null, null)) {
                if (order.getId() != null) {
                    logs.addAll(nullToEmpty(mealVerificationService.queryByOrderId(order.getId())));
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (MealVerificationLog logRecord : logs) {
            if (!matchMealType(logRecord.getMealType(), mealType) || !matchDateRange(formatDate(logRecord.getRecordDate()), recordDateStart, recordDateEnd)) {
                continue;
            }
            result.add(toVerificationLogMap(logRecord));
        }
        return result;
    }

    /**
     * 查询退餐日志，支持按订单精确查询或按客户订单集合聚合。
     */
    @Override
    public List<Map<String, Object>> resolveMealRefunds(Long customerId, String customerCode, Long orderId) {
        List<MealRefundLog> logs = new ArrayList<>();
        if (orderId != null && orderId > 0) {
            addRefundLog(logs, mealRefundService.queryByOrderId(orderId));
        } else {
            for (CustomerOrderDetailDto order : resolveOrders(customerId, customerCode, null, null)) {
                if (order.getId() != null) {
                    addRefundLog(logs, mealRefundService.queryByOrderId(order.getId()));
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (MealRefundLog logRecord : logs) {
            result.add(toRefundLogMap(logRecord));
        }
        return result;
    }

    /**
     * 查询排餐生成快照，返回生成状态、计数以及失败客户摘要。
     */
    @Override
    public Map<String, Object> resolveMealPlanGenerationSnapshot(String recordDate, String mealType) {
        MealPlanDetailVO detail = resolveMealPlan(recordDate, mealType);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordDate", recordDate);
        result.put("mealType", mealType);
        result.put("present", detail != null);
        if (detail == null) {
            return result;
        }
        if (detail.getMealPlan() != null) {
            result.put("mealPlanId", detail.getMealPlan().getId());
            result.put("status", detail.getMealPlan().getStatus());
            result.put("generateTime", detail.getMealPlan().getGenerateTime());
            result.put("totalCount", detail.getMealPlan().getTotalCount());
        }
        result.put("totalCustomers", detail.getTotalCustomers());
        result.put("successCount", detail.getSuccessCount());
        result.put("failCount", detail.getFailCount());
        List<Map<String, Object>> failedCustomers = new ArrayList<>();
        if (!CollectionUtils.isEmpty(detail.getCustomers())) {
            for (MealPlanDetailVO.CustomerPlanDetail customer : detail.getCustomers()) {
                if (customer != null && Integer.valueOf(0).equals(customer.getStatus())) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("mealPlanCustomerId", customer.getId());
                    item.put("customerId", customer.getCustomerId());
                    item.put("customerCode", customer.getCustomerCode());
                    item.put("customerName", customer.getCustomerName());
                    item.put("orderId", customer.getOrderId());
                    item.put("failReason", customer.getFailReason());
                    item.put("specialRequirements", customer.getSpecialRequirements());
                    failedCustomers.add(item);
                }
            }
        }
        result.put("failedCustomers", failedCustomers);
        return result;
    }

    private List<MealVerificationLog> nullToEmpty(List<MealVerificationLog> logs) {
        return logs == null ? Collections.emptyList() : logs;
    }

    private void addRefundLog(List<MealRefundLog> logs, MealRefundLog logRecord) {
        if (logRecord != null) {
            logs.add(logRecord);
        }
    }

    private int countVerified(List<MealVerificationLog> logs, String mealType) {
        int count = 0;
        for (MealVerificationLog logRecord : logs) {
            if (logRecord != null && mealType.equals(logRecord.getMealType()) && !Integer.valueOf(1).equals(logRecord.getIsRefunded())) {
                count += logRecord.getVerificationCount() == null ? 1 : logRecord.getVerificationCount();
            }
        }
        return count;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean matchMealType(String actualMealType, String expectedMealType) {
        return expectedMealType == null || expectedMealType.trim().isEmpty() || expectedMealType.equals(actualMealType);
    }

    private boolean matchDateRange(String recordDate, String startDate, String endDate) {
        if (recordDate == null) {
            return true;
        }
        if (startDate != null && !startDate.trim().isEmpty() && recordDate.compareTo(startDate.trim()) < 0) {
            return false;
        }
        return endDate == null || endDate.trim().isEmpty() || recordDate.compareTo(endDate.trim()) <= 0;
    }

    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    private String formatDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private Map<String, Object> toVerificationLogMap(MealVerificationLog logRecord) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", logRecord.getId());
        item.put("orderId", logRecord.getOrderId());
        item.put("customerId", logRecord.getCustomerId());
        item.put("mealPlanId", logRecord.getMealPlanId());
        item.put("mealPlanCustomerId", logRecord.getMealPlanCustomerId());
        item.put("recordDate", formatDate(logRecord.getRecordDate()));
        item.put("mealType", logRecord.getMealType());
        item.put("verificationCount", logRecord.getVerificationCount());
        item.put("remainingBefore", logRecord.getRemainingBefore());
        item.put("remainingAfter", logRecord.getRemainingAfter());
        item.put("verifiedTotalBefore", logRecord.getVerifiedTotalBefore());
        item.put("verifiedTotalAfter", logRecord.getVerifiedTotalAfter());
        item.put("operator", logRecord.getOperator());
        item.put("operateTime", formatDateTime(logRecord.getOperateTime()));
        item.put("isRefunded", logRecord.getIsRefunded());
        item.put("refundTime", formatDateTime(logRecord.getRefundTime()));
        return item;
    }

    private Map<String, Object> toRefundLogMap(MealRefundLog logRecord) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", logRecord.getId());
        item.put("orderId", logRecord.getOrderId());
        item.put("customerId", logRecord.getCustomerId());
        item.put("refundAmount", logRecord.getRefundAmount());
        item.put("refundBreakfastCount", logRecord.getRefundBreakfastCount());
        item.put("refundLunchDinnerCount", logRecord.getRefundLunchDinnerCount());
        item.put("verifiedBreakfastCount", logRecord.getVerifiedBreakfastCount());
        item.put("verifiedLunchDinnerCount", logRecord.getVerifiedLunchDinnerCount());
        item.put("refundReason", logRecord.getRefundReason());
        item.put("operator", logRecord.getOperator());
        item.put("operateTime", formatDateTime(logRecord.getOperateTime()));
        return item;
    }

    private Map<String, Object> toParentPackageSpecMap(ParentPackageDto packageDto) {
        if (packageDto == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", packageDto.getId());
        item.put("packageCode", packageDto.getPackageCode());
        item.put("packageName", packageDto.getPackageName());
        item.put("status", packageDto.getStatus());
        item.put("poolPrefix", packageDto.getPoolPrefix());
        item.put("poolStart", packageDto.getPoolStart());
        item.put("poolEnd", packageDto.getPoolEnd());
        item.put("childCount", packageDto.getChildren() == null ? 0 : packageDto.getChildren().size());
        return item;
    }

    private Map<String, Object> toChildPackageSpecMap(SubPackageDto packageDto) {
        if (packageDto == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", packageDto.getId());
        item.put("subPackageCode", packageDto.getSubPackageCode());
        item.put("subPackageName", packageDto.getSubPackageName());
        item.put("meatCount", packageDto.getMeatCount());
        item.put("vegCount", packageDto.getVegCount());
        item.put("includeSoup", packageDto.getIncludeSoup());
        item.put("includeRice", packageDto.getIncludeRice());
        item.put("status", packageDto.getStatus());
        return item;
    }

    private List<String> packageMissingFields(ParentPackageDto parentPackage, SubPackageDto childPackage,
                                              Long parentPackageId, Long childPackageId) {
        List<String> missingFields = new ArrayList<>();
        if (parentPackageId == null) {
            missingFields.add("parentPackageId");
        } else if (parentPackage == null) {
            missingFields.add("parentPackage");
        } else {
            if (parentPackage.getPackageCode() == null || parentPackage.getPackageCode().trim().isEmpty()) {
                missingFields.add("parentPackage.packageCode");
            }
            if (parentPackage.getPackageName() == null || parentPackage.getPackageName().trim().isEmpty()) {
                missingFields.add("parentPackage.packageName");
            }
            if (parentPackage.getChildren() == null || parentPackage.getChildren().isEmpty()) {
                missingFields.add("parentPackage.children");
            }
        }
        if (childPackageId == null) {
            missingFields.add("childPackageId");
        } else if (childPackage == null) {
            missingFields.add("childPackage");
        } else {
            if (childPackage.getMeatCount() == null) {
                missingFields.add("childPackage.meatCount");
            }
            if (childPackage.getVegCount() == null) {
                missingFields.add("childPackage.vegCount");
            }
            if (childPackage.getIncludeSoup() == null) {
                missingFields.add("childPackage.includeSoup");
            }
            if (childPackage.getIncludeRice() == null) {
                missingFields.add("childPackage.includeRice");
            }
        }
        return missingFields;
    }
}
