package me.zhengjie.modules.agent.query.service.impl;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.query.domain.dto.AgentBusinessRuleDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerCandidateDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerOverviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerProfileDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidateRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidatePreviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentHistoryQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanDishItemDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationCountDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationDailyRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationOrderRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentPackageDetailRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentPackageSpecDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentRefundLogDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentRuleExplainRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentScheduledMenuQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentVerificationLogDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDailyCustomerStatsDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderMealBalanceDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentSubPackageSpecDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerPackageDto;
import me.zhengjie.modules.agent.query.domain.unified.AgentDishSearchRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentMetricQueryRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentProfileSearchRequest;
import me.zhengjie.modules.agent.query.domain.unified.AgentServiceCustomerDetailRequest;
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
import me.zhengjie.modules.agent.query.service.AgentUnifiedQueryService;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 统一只读查询实现。
 *
 * <p>所有过滤、关系解析和脱敏均在主系统完成；该类不会把业务实体或金额字段直接交给 HTTP 层。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentUnifiedQueryServiceImpl implements AgentUnifiedQueryService {

    private final AgentCustomerQueryService customerQueryService;
    private final AgentOrderQueryService orderQueryService;
    private final AgentHistoryQueryService historyQueryService;
    private final AgentMealPlanQueryService mealPlanQueryService;
    private final AgentDishQueryService dishQueryService;
    private final AgentPackageQueryService packageQueryService;
    private final AgentOperationQueryService operationQueryService;
    private final AgentBusinessRuleQueryService businessRuleQueryService;
    private final CustomerProfileMapper customerProfileMapper;

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ProfileItem> searchCustomerProfiles(AgentProfileSearchRequest request) {
        AgentProfileSearchRequest safe = request == null ? new AgentProfileSearchRequest() : request;
        AgentListResultDto<AgentCustomerProfileDto> source = customerQueryService.searchProfiles(
            safe.getCustomerId(), safe.getCustomerCode(), safe.getCustomerName(), safe.getHasOrder(),
            intValue(safe.getPage(), 1), intValue(safe.getSize(), 20));
        AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ProfileItem> result = new AgentUnifiedQueryResponse<>();
        List<AgentCustomerProfileDto> sourceItems = source == null || source.getItems() == null
            ? Collections.emptyList() : source.getItems();
        copyPage(source, result, sourceItems.stream().map(this::profileItem).collect(Collectors.toList()));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ServiceCustomerItem> searchServiceCustomers(AgentServiceCustomerSearchRequest request) {
        AgentServiceCustomerSearchRequest safe = request == null ? new AgentServiceCustomerSearchRequest() : request;
        AgentListResultDto<AgentOrderSummaryDto> source = orderQueryService.searchServiceCustomers(
            safe.getCustomerId(), safe.getCustomerCode(), safe.getOrderId(), safe.getOrderCode(), safe.getStatus(),
            safe.getDealTimeFrom(), safe.getDealTimeTo(), safe.getPackageCode(), intValue(safe.getPage(), 1), intValue(safe.getSize(), 20));
        Map<Long, String> customerNames = customerNames(source == null ? Collections.emptyList() : source.getItems());
        List<AgentUnifiedQueryDto.ServiceCustomerItem> items = source == null || source.getItems() == null
            ? Collections.emptyList() : source.getItems().stream().map(order -> serviceCustomerItem(order, customerNames)).collect(Collectors.toList());
        AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ServiceCustomerItem> result = new AgentUnifiedQueryResponse<>();
        copyPage(source, result, items);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ServiceCustomerDetailItem> getServiceCustomerDetail(AgentServiceCustomerDetailRequest request) {
        AgentServiceCustomerDetailRequest safe = request == null ? new AgentServiceCustomerDetailRequest() : request;
        List<String> warnings = new ArrayList<>();
        Long customerId = safe.getCustomerId();
        AgentOrderSummaryDto selectedOrder = null;
        if (safe.getOrderId() != null || hasText(safe.getOrderCode())) {
            Long expectedCustomerId = customerId == null ? resolveCustomerId(null, safe.getCustomerCode()) : customerId;
            selectedOrder = orderQueryService.getDetail(safe.getOrderId(), safe.getOrderCode(), expectedCustomerId);
            if (selectedOrder == null) {
                return AgentUnifiedQueryResponse.<AgentUnifiedQueryDto.ServiceCustomerDetailItem>single(null)
                    .warning("SERVICE_CUSTOMER_NOT_FOUND");
            }
            customerId = selectedOrder.getCustomerId();
        } else if (customerId == null) {
            customerId = resolveCustomerId(null, safe.getCustomerCode());
            if (customerId == null) warnings.add("CUSTOMER_AMBIGUOUS_OR_NOT_FOUND");
        }
        if (customerId == null) {
            return AgentUnifiedQueryResponse.<AgentUnifiedQueryDto.ServiceCustomerDetailItem>single(null)
                .warning("CUSTOMER_AMBIGUOUS_OR_NOT_FOUND");
        }
        AgentCustomerOverviewDto overview = customerQueryService.getOverview(customerId, null);
        if (!overview.isPresent()) {
            return AgentUnifiedQueryResponse.<AgentUnifiedQueryDto.ServiceCustomerDetailItem>single(null)
                .warning("CUSTOMER_NOT_FOUND");
        }
        AgentListResultDto<AgentOrderSummaryDto> orderSource = selectedOrder == null
            ? orderQueryService.listForOverview(customerId)
            : singleton(selectedOrder);
        List<AgentOrderSummaryDto> orderItems = orderSource == null || orderSource.getItems() == null
            ? Collections.emptyList() : orderSource.getItems();
        Map<Long, String> customerNames = customerNames(orderItems);
        AgentUnifiedQueryDto.ServiceCustomerDetailItem detail = new AgentUnifiedQueryDto.ServiceCustomerDetailItem();
        detail.setProfile(profileItem(overview));
        detail.setAllergyTags(overview.getAllergyTags() == null ? Collections.emptyList() : overview.getAllergyTags());
        detail.setExcludedDishIds(overview.getExcludedDishIds() == null ? Collections.emptyList() : overview.getExcludedDishIds());
        detail.setExcludedDates(excludedDateMaps(overview.getExcludedDates()));
        detail.setSpecialRequirements(overview.getSpecialRequirements());
        detail.setAddresses(overview.getAddresses() == null ? Collections.emptyList() : overview.getAddresses().stream()
            .map(this::addressMap).collect(Collectors.toList()));
        detail.setOrders(orderItems.stream().map(order -> serviceCustomerItem(order, customerNames)).collect(Collectors.toList()));
        AgentMealPlanQueryRequest mealPlanRequest = new AgentMealPlanQueryRequest();
        mealPlanRequest.setCustomerId(customerId);
        if (selectedOrder != null) mealPlanRequest.setOrderId(selectedOrder.getOrderId());
        mealPlanRequest.setPage(1);
        mealPlanRequest.setSize("DIAGNOSTIC".equalsIgnoreCase(safe.getDetailLevel()) ? 50 : 10);
        AgentListResultDto<AgentMealPlanSummaryDto> mealPlans = mealPlanQueryService.query(mealPlanRequest);
        detail.setMealPlans(mealPlans == null || mealPlans.getItems() == null ? Collections.emptyList()
            : mealPlans.getItems().stream().map(this::mealPlanMap).collect(Collectors.toList()));
        AgentHistoryQueryRequest historyRequest = new AgentHistoryQueryRequest();
        historyRequest.setCustomerId(customerId);
        if (selectedOrder != null) historyRequest.setOrderId(selectedOrder.getOrderId());
        historyRequest.setPage(1);
        historyRequest.setSize(10);
        AgentListResultDto<AgentVerificationLogDto> verificationSource = historyQueryService.listVerifications(historyRequest);
        AgentListResultDto<AgentRefundLogDto> refundSource = historyQueryService.listRefunds(historyRequest);
        detail.setVerifications(verificationSource == null || verificationSource.getItems() == null ? Collections.emptyList()
            : verificationSource.getItems().stream().map(this::verificationMap).collect(Collectors.toList()));
        detail.setRefunds(refundSource == null || refundSource.getItems() == null ? Collections.emptyList()
            : refundSource.getItems().stream().map(this::refundMap).collect(Collectors.toList()));
        if (orderSource != null && orderSource.isTruncated()) warnings.add("ORDER_RESULTS_TRUNCATED");
        if (mealPlans != null && mealPlans.isTruncated()) warnings.add("MEAL_PLAN_RESULTS_TRUNCATED");
        detail.setWarnings(warnings);
        AgentUnifiedQueryResponse<AgentUnifiedQueryDto.ServiceCustomerDetailItem> result = AgentUnifiedQueryResponse.single(detail);
        result.setWarnings(warnings);
        result.setQueriedAt(now());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<AgentUnifiedQueryDto.MealPlanItem> listMealPlans(AgentMealPlanQueryRequest request) {
        AgentMealPlanQueryRequest safe = copyMealPlanRequest(request);
        resolveCustomerAndOrder(safe);
        AgentListResultDto<AgentMealPlanSummaryDto> source = mealPlanQueryService.query(safe);
        List<AgentUnifiedQueryDto.MealPlanItem> items = source == null || source.getItems() == null ? Collections.emptyList()
            : source.getItems().stream().map(this::mealPlanItem).collect(Collectors.toList());
        AgentUnifiedQueryResponse<AgentUnifiedQueryDto.MealPlanItem> result = new AgentUnifiedQueryResponse<>();
        copyPage(source, result, items);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<AgentUnifiedQueryDto.VerificationItem> listVerifications(AgentHistoryQueryRequest request) {
        AgentHistoryQueryRequest safe = copyHistoryRequest(request);
        resolveCustomerAndOrder(safe);
        AgentListResultDto<AgentVerificationLogDto> source = historyQueryService.listVerifications(safe);
        AgentUnifiedQueryResponse<AgentUnifiedQueryDto.VerificationItem> result = new AgentUnifiedQueryResponse<>();
        List<AgentVerificationLogDto> sourceItems = source == null || source.getItems() == null
            ? Collections.emptyList() : source.getItems();
        copyPage(source, result, sourceItems.stream().map(this::verificationItem).collect(Collectors.toList()));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<AgentUnifiedQueryDto.RefundItem> listRefunds(AgentHistoryQueryRequest request) {
        AgentHistoryQueryRequest safe = copyHistoryRequest(request);
        resolveCustomerAndOrder(safe);
        AgentListResultDto<AgentRefundLogDto> source = historyQueryService.listRefunds(safe);
        AgentUnifiedQueryResponse<AgentUnifiedQueryDto.RefundItem> result = new AgentUnifiedQueryResponse<>();
        List<AgentRefundLogDto> sourceItems = source == null || source.getItems() == null
            ? Collections.emptyList() : source.getItems();
        copyPage(source, result, sourceItems.stream().map(this::refundItem).collect(Collectors.toList()));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<Map<String, Object>> previewDishCandidates(AgentDishCandidateRequest request) {
        AgentDishCandidateRequest safe = request == null ? new AgentDishCandidateRequest() : request;
        Long customerId = resolveCustomerId(safe.getCustomerId(), safe.getCustomerCode());
        AgentOrderSummaryDto selectedOrder = null;
        if (hasText(safe.getOrderCode()) || safe.getOrderId() != null) {
            selectedOrder = orderQueryService.getDetail(safe.getOrderId(), safe.getOrderCode(), customerId);
            if (selectedOrder == null) {
                return singleMapResponse(toMap(new AgentDishCandidatePreviewDto()), "totalCandidateCount")
                    .warning("SERVICE_CUSTOMER_NOT_FOUND");
            }
            customerId = selectedOrder.getCustomerId();
        }
        AgentDishCandidatePreviewDto source = customerId == null ? new AgentDishCandidatePreviewDto()
            : dishQueryService.previewCandidates(customerId, selectedOrder == null ? null : selectedOrder.getOrderId(),
                safe.getRecordDate(), safe.getMealType());
        AgentUnifiedQueryResponse<Map<String, Object>> result = singleMapResponse(toMap(source), "totalCandidateCount");
        if (customerId == null) result.warning("CUSTOMER_AMBIGUOUS_OR_NOT_FOUND");
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<Map<String, Object>> listScheduledDishes(AgentScheduledMenuQueryRequest request) {
        AgentScheduledMenuQueryRequest safe = request == null ? new AgentScheduledMenuQueryRequest() : request;
        return singleMapResponse(toMap(dishQueryService.listScheduled(safe.getRecordDate(), safe.getMealTypes())), "total");
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<AgentUnifiedQueryDto.DishItem> searchDishes(AgentDishSearchRequest request) {
        AgentDishSearchRequest safe = request == null ? new AgentDishSearchRequest() : request;
        AgentListResultDto<AgentDishSummaryDto> source = dishQueryService.search(safe.getName(), safe.getDishType(), safe.getEnabled(),
            intValue(safe.getPage(), 1), intValue(safe.getSize(), 20));
        AgentUnifiedQueryResponse<AgentUnifiedQueryDto.DishItem> result = new AgentUnifiedQueryResponse<>();
        List<AgentDishSummaryDto> sourceItems = source == null || source.getItems() == null
            ? Collections.emptyList() : source.getItems();
        copyPage(source, result, sourceItems.stream().map(this::dishItem).collect(Collectors.toList()));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<AgentUnifiedQueryDto.PackageDetailItem> getPackageDetail(AgentPackageDetailRequest request) {
        AgentPackageDetailRequest safe = request == null ? new AgentPackageDetailRequest() : request;
        AgentPackageSpecDto source = safe.getPackageId() != null
            ? packageQueryService.getDetail(safe.getPackageId()) : packageQueryService.getDetailByCode(safe.getPackageCode());
        if (source == null || !source.isPresent()) {
            return AgentUnifiedQueryResponse.<AgentUnifiedQueryDto.PackageDetailItem>single(null).warning("PACKAGE_NOT_FOUND");
        }
        return AgentUnifiedQueryResponse.single(packageItem(source));
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<AgentUnifiedQueryDto.MetricItem> queryBusinessMetrics(AgentMetricQueryRequest request) {
        AgentMetricQueryRequest safe = request == null ? new AgentMetricQueryRequest() : request;
        String metric = upper(safe.getMetric());
        AgentUnifiedQueryDto.MetricItem item = new AgentUnifiedQueryDto.MetricItem();
        item.setMetric(metric);
        switch (metric) {
            case "CUSTOMER_PROFILE_COUNT":
                fillCount(item, operationQueryService.customerProfileCount());
                break;
            case "ACTIVE_SERVICE_CUSTOMER_COUNT":
                fillCount(item, operationQueryService.activeCustomers());
                break;
            case "ACTIVE_ORDER_COUNT":
                fillCount(item, operationQueryService.activeOrders());
                break;
            case "VERIFICATION_RECORD_COUNT":
                item.setTotal(historyQueryService.countVerificationRecords());
                break;
            case "EXPIRING_ORDER_COUNT":
                AgentOperationOrderRequest orderRequest = new AgentOperationOrderRequest();
                orderRequest.setStartDate(safe.getStartDate()); orderRequest.setEndDate(safe.getEndDate());
                fillCount(item, operationQueryService.expiringOrders(orderRequest));
                break;
            case "DAILY_SCHEDULED_CUSTOMER_COUNT":
            case "DAILY_VERIFIED_CUSTOMER_COUNT":
            case "DAILY_UNVERIFIED_CUSTOMER_COUNT":
            case "DAILY_UNSCHEDULED_CUSTOMER_COUNT":
            case "MEAL_PLAN_FAILURE_COUNT":
                AgentDailyCustomerStatsDto daily = dailyStats(safe);
                item.setTotal(dailyTotal(daily, metric));
                item.setQueriedAt(daily.getQueriedAt());
                item.setDimensions(metricDimensions(daily, metric, safe.getDimensions()));
                item.setBreakdown(metricBreakdown(item.getDimensions()));
                break;
            default:
                throw new IllegalArgumentException("运营指标不在白名单内");
        }
        if (item.getQueriedAt() == null) item.setQueriedAt(now());
        return AgentUnifiedQueryResponse.single(item);
    }

    /** {@inheritDoc} */
    @Override
    public AgentUnifiedQueryResponse<AgentUnifiedQueryDto.RuleItem> explainBusinessRule(AgentRuleExplainRequest request) {
        AgentRuleExplainRequest safe = request == null ? new AgentRuleExplainRequest() : request;
        AgentBusinessRuleDto source = businessRuleQueryService.explain(safe.getTopic());
        if (source == null || !source.isPresent()) return AgentUnifiedQueryResponse.<AgentUnifiedQueryDto.RuleItem>single(null).warning("RULE_NOT_FOUND");
        AgentUnifiedQueryDto.RuleItem item = new AgentUnifiedQueryDto.RuleItem();
        item.setRuleId(source.getRuleId()); item.setVersion(source.getVersion()); item.setTitle(source.getTopic());
        item.setContent(source.getContent()); item.setEffectiveFrom(source.getEffectiveFrom()); item.setUpdatedAt(source.getUpdatedAt());
        return AgentUnifiedQueryResponse.single(item);
    }

    /** 复制历史排餐请求，避免在请求对象中回写主系统解析出的内部 ID。 */
    private AgentMealPlanQueryRequest copyMealPlanRequest(AgentMealPlanQueryRequest source) {
        AgentMealPlanQueryRequest target = new AgentMealPlanQueryRequest();
        if (source == null) return target;
        target.setCustomerId(source.getCustomerId()); target.setCustomerCode(source.getCustomerCode());
        target.setOrderId(source.getOrderId()); target.setOrderCode(source.getOrderCode()); target.setRecordDate(source.getRecordDate());
        target.setStartDate(source.getStartDate()); target.setEndDate(source.getEndDate()); target.setMealType(source.getMealType());
        target.setCustomerMealPlanId(source.getCustomerMealPlanId()); target.setPage(source.getPage()); target.setSize(source.getSize());
        return target;
    }

    /** 复制核销或退餐请求，统一补充客户/订单编号解析后的关系条件。 */
    private AgentHistoryQueryRequest copyHistoryRequest(AgentHistoryQueryRequest source) {
        AgentHistoryQueryRequest target = new AgentHistoryQueryRequest();
        if (source == null) return target;
        target.setCustomerId(source.getCustomerId()); target.setOrderId(source.getOrderId()); target.setCustomerCode(source.getCustomerCode());
        target.setOrderCode(source.getOrderCode()); target.setStartDate(source.getStartDate()); target.setEndDate(source.getEndDate());
        target.setMealType(source.getMealType()); target.setRecentLimit(source.getRecentLimit()); target.setPage(source.getPage()); target.setSize(source.getSize());
        return target;
    }

    /** 将客户编号或订单编号解析为主系统已授权的客户 ID。 */
    private void resolveCustomerAndOrder(AgentMealPlanQueryRequest request) {
        if (request.getCustomerId() == null && hasText(request.getCustomerCode())) request.setCustomerId(resolveCustomerId(null, request.getCustomerCode()));
        if (request.getOrderId() == null && hasText(request.getOrderCode())) {
            AgentOrderSummaryDto order = orderQueryService.getDetail(null, request.getOrderCode(), request.getCustomerId());
            if (order != null) { request.setOrderId(order.getOrderId()); if (request.getCustomerId() == null) request.setCustomerId(order.getCustomerId()); }
        }
    }

    /** 将客户编号或订单编号解析为历史查询的稳定关联键。 */
    private void resolveCustomerAndOrder(AgentHistoryQueryRequest request) {
        if (request.getCustomerId() == null && hasText(request.getCustomerCode())) request.setCustomerId(resolveCustomerId(null, request.getCustomerCode()));
        if (request.getOrderId() == null && hasText(request.getOrderCode())) {
            AgentOrderSummaryDto order = orderQueryService.getDetail(null, request.getOrderCode(), request.getCustomerId());
            if (order != null) { request.setOrderId(order.getOrderId()); if (request.getCustomerId() == null) request.setCustomerId(order.getCustomerId()); }
        }
    }

    /** 按客户 ID 或客户编号解析唯一客户；多候选时返回 null，让工具返回稳定澄清告警。 */
    private Long resolveCustomerId(Long customerId, String customerCode) {
        if (customerId != null) return customerId;
        if (!hasText(customerCode)) return null;
        AgentListResultDto<AgentCustomerCandidateDto> candidates = customerQueryService.resolve(null, customerCode, null);
        return candidates.getTotal() == 1 && candidates.getItems() != null && !candidates.getItems().isEmpty()
            ? candidates.getItems().get(0).getCustomerId() : null;
    }

    /** 从已授权订单页批量加载完整姓名，避免 N+1 查询并避免把未授权档案带入统一结果。 */
    private Map<Long, String> customerNames(List<AgentOrderSummaryDto> orders) {
        List<Long> ids = orders == null ? Collections.emptyList() : orders.stream().map(AgentOrderSummaryDto::getCustomerId)
            .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        List<CustomerProfile> profiles = customerProfileMapper.selectBatchIds(ids);
        if (profiles == null) return Collections.emptyMap();
        return profiles.stream().collect(Collectors.toMap(CustomerProfile::getId, CustomerProfile::getCustomerName,
            (left, right) -> left, LinkedHashMap::new));
    }

    /** 将主系统客户档案分页项转换为统一工具字段。 */
    private AgentUnifiedQueryDto.ProfileItem profileItem(AgentCustomerProfileDto source) {
        AgentUnifiedQueryDto.ProfileItem item = new AgentUnifiedQueryDto.ProfileItem();
        item.setCustomerId(source.getCustomerId()); item.setCustomerCode(source.getCustomerCode()); item.setCustomerName(source.getCustomerName());
        item.setHasOrder(source.isHasOrder()); item.setCreateTime(source.getCreateTime()); item.setMaskedPhone(source.getMaskedPhone());
        return item;
    }

    /** 将客户详情中的档案摘要转换为统一工具字段。 */
    private AgentUnifiedQueryDto.ProfileItem profileItem(AgentCustomerOverviewDto source) {
        AgentUnifiedQueryDto.ProfileItem item = new AgentUnifiedQueryDto.ProfileItem();
        item.setCustomerId(source.getCustomerId()); item.setCustomerCode(source.getCustomerCode()); item.setCustomerName(source.getCustomerName());
        item.setHasOrder(source.getTotalOrderCount() > 0); item.setCreateTime(source.getCreateTime()); item.setMaskedPhone(source.getMaskedPhone());
        return item;
    }

    /** 将订单摘要转换为以订单为根的工具行。 */
    private AgentUnifiedQueryDto.ServiceCustomerItem serviceCustomerItem(AgentOrderSummaryDto source, Map<Long, String> customerNames) {
        AgentUnifiedQueryDto.ServiceCustomerItem item = new AgentUnifiedQueryDto.ServiceCustomerItem();
        item.setCustomerId(source.getCustomerId()); item.setCustomerCode(source.getCustomerCode()); item.setCustomerName(customerNames.get(source.getCustomerId()));
        item.setOrderId(source.getOrderId()); item.setOrderCode(source.getOrderCode()); item.setStatus(source.getStatusName());
        item.setDealTime(stringValue(source.getDealTime())); item.setCreateTime(stringValue(source.getCreateTime()));
        item.setOrderTime(orderTime(source));
        item.setStartDate(stringValue(source.getStartDate())); item.setEndDate(stringValue(source.getEndDate()));
        item.setStartMealType(source.getStartMealTypeCode()); item.setMealType(source.getMealTypeCode());
        item.setScheduleMode(source.getScheduleModeCode()); item.setDeliveryDates(source.getDeliveryDates());
        item.setParentPackageName(source.getParentPackageName()); item.setChildPackageName(source.getChildPackageName());
        item.setMealBalance(balance(source.getMealBalance())); item.setMealPlanCount(intValue(source.getMealPlanRecordCount(), 0));
        item.setVerificationCount(intValue(source.getVerificationRecordCount(), 0)); item.setRefundCount(intValue(source.getRefundRecordCount(), 0));
        return item;
    }

    /** 转换早餐、午晚餐两个独立/共享餐数池。 */
    private AgentUnifiedQueryDto.MealBalanceItem balance(AgentOrderMealBalanceDto source) {
        AgentUnifiedQueryDto.MealBalanceItem item = new AgentUnifiedQueryDto.MealBalanceItem();
        if (source == null) return item;
        item.setBreakfastCount(source.getBreakfastCount()); item.setLunchDinnerCount(source.getLunchDinnerCount());
        item.setVerifiedBreakfast(source.getVerifiedBreakfast()); item.setVerifiedLunch(source.getVerifiedLunch());
        item.setVerifiedDinner(source.getVerifiedDinner()); item.setRemainingBreakfast(source.getRemainingBreakfast());
        item.setRemainingLunchDinner(source.getRemainingLunchDinner());
        return item;
    }

    /** 转换排餐列表项，并只保留限量菜品字段。 */
    private AgentUnifiedQueryDto.MealPlanItem mealPlanItem(AgentMealPlanSummaryDto source) {
        AgentUnifiedQueryDto.MealPlanItem item = new AgentUnifiedQueryDto.MealPlanItem();
        item.setCustomerId(source.getCustomerId()); item.setCustomerCode(source.getCustomerCode()); item.setOrderId(source.getOrderId());
        item.setRecordDate(stringValue(source.getRecordDate())); item.setMealType(source.getMealTypeCode());
        item.setStatus(stringValue(source.getGenerationStatus()) + "/" + stringValue(source.getCustomerPlanStatus()));
        item.setVerified(source.isVerified()); item.setFailureReason(source.getFailureReason());
        item.setDishes(source.getDishes().stream().map(this::dishMap).collect(Collectors.toList()));
        return item;
    }

    /** 转换详情中的排餐记录，字段集合与列表工具保持一致。 */
    private Map<String, Object> mealPlanMap(AgentMealPlanSummaryDto source) {
        AgentUnifiedQueryDto.MealPlanItem item = mealPlanItem(source);
        return toMap(item);
    }

    /** 将菜品明细转换为无地址、无金额的普通 Map。 */
    private Map<String, Object> dishMap(AgentMealPlanDishItemDto source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dishId", source.getDishId()); result.put("dishName", source.getDishName()); result.put("dishType", source.getDishType());
        result.put("replaced", source.isReplaced()); result.put("replaceReason", source.getReplaceReason());
        result.put("allergyFiltered", source.isAllergyFiltered()); result.put("allergyReasons", source.getAllergyReasons());
        result.put("originalDishId", source.getOriginalDishId()); result.put("originalDishName", source.getOriginalDishName());
        return result;
    }

    /** 转换核销摘要为统一字符串日期契约。 */
    private AgentUnifiedQueryDto.VerificationItem verificationItem(AgentVerificationLogDto source) {
        AgentUnifiedQueryDto.VerificationItem item = new AgentUnifiedQueryDto.VerificationItem();
        item.setCustomerId(source.getCustomerId()); item.setOrderId(source.getOrderId()); item.setRecordDate(stringValue(source.getRecordDate()));
        item.setMealType(source.getMealTypeCode()); item.setCount(intValue(source.getVerificationCount(), 0)); item.setRefunded(source.isRefunded());
        item.setOperateTime(stringValue(source.getOperateTime()));
        return item;
    }

    /** 转换退餐摘要为不含退款金额的统一字段。 */
    private AgentUnifiedQueryDto.RefundItem refundItem(AgentRefundLogDto source) {
        AgentUnifiedQueryDto.RefundItem item = new AgentUnifiedQueryDto.RefundItem();
        item.setCustomerId(source.getCustomerId()); item.setOrderId(source.getOrderId());
        item.setBreakfastCount(intValue(source.getRefundBreakfastCount(), 0)); item.setLunchDinnerCount(intValue(source.getRefundLunchDinnerCount(), 0));
        item.setVerifiedBreakfastCount(intValue(source.getVerifiedBreakfastCount(), 0)); item.setVerifiedLunchDinnerCount(intValue(source.getVerifiedLunchDinnerCount(), 0));
        item.setReason(source.getRefundReason()); item.setOperateTime(stringValue(source.getOperateTime()));
        return item;
    }

    /** 转换详情中的核销摘要，去掉内部日志 ID。 */
    private Map<String, Object> verificationMap(AgentVerificationLogDto source) { return toMap(verificationItem(source)); }
    /** 转换详情中的退餐摘要，去掉内部日志 ID。 */
    private Map<String, Object> refundMap(AgentRefundLogDto source) { return toMap(refundItem(source)); }

    /** 将停送日期转换为固定字段，避免把客户档案实体或任意 JSON 原样交给模型。 */
    private List<Map<String, Object>> excludedDateMaps(List<?> values) {
        if (values == null) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : values) {
            ExcludedDateDto source = value instanceof ExcludedDateDto ? (ExcludedDateDto) value : null;
            if (source == null || !hasText(source.getDate())) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", source.getDate());
            item.put("mealTypes", source.getMealTypes() == null ? Collections.emptyList() :
                source.getMealTypes().stream().filter(this::hasText).limit(3).collect(Collectors.toList()));
            result.add(item);
        }
        return result;
    }

    /** 将客户地址转换为仅含脱敏字段的详情摘要。 */
    private Map<String, Object> addressMap(me.zhengjie.modules.agent.query.domain.dto.AgentCustomerAddressDto source) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (source == null) return item;
        item.put("typeCode", source.getAddressTypeCode());
        item.put("typeName", source.getAddressTypeName());
        item.put("maskedAddress", source.getMaskedAddress());
        item.put("maskedContactName", source.getMaskedContactName());
        item.put("maskedContactPhone", source.getMaskedContactPhone());
        return item;
    }

    /** 转换菜品搜索项，并保留配料数量上限后的摘要。 */
    private AgentUnifiedQueryDto.DishItem dishItem(AgentDishSummaryDto source) {
        AgentUnifiedQueryDto.DishItem item = new AgentUnifiedQueryDto.DishItem();
        item.setDishId(source.getDishId()); item.setName(source.getDishName()); item.setDishType(source.getDishTypeCode()); item.setEnabled(Boolean.TRUE.equals(source.getEnabled()));
        item.setIngredients(source.getIngredientNames() == null ? new ArrayList<>() : source.getIngredientNames());
        return item;
    }

    /** 转换套餐详情，子套餐只保留规格和启用状态。 */
    private AgentUnifiedQueryDto.PackageDetailItem packageItem(AgentPackageSpecDto source) {
        AgentUnifiedQueryDto.PackageDetailItem item = new AgentUnifiedQueryDto.PackageDetailItem();
        item.setPackageId(source.getParentPackageId()); item.setPackageCode(source.getParentPackageCode()); item.setPackageName(source.getParentPackageName());
        List<Map<String, Object>> children = new ArrayList<>();
        if (source.getSubPackages() != null) for (AgentSubPackageSpecDto child : source.getSubPackages()) {
            Map<String, Object> value = new LinkedHashMap<>(); value.put("subPackageId", child.getSubPackageId()); value.put("subPackageCode", child.getSubPackageCode());
            value.put("subPackageName", child.getSubPackageName()); value.put("meatCount", child.getMeatCount()); value.put("vegCount", child.getVegCount());
            value.put("includeSoup", child.getIncludeSoup()); value.put("includeRice", child.getIncludeRice()); value.put("enabled", child.getEnabled()); children.add(value);
        }
        item.setSubPackages(children);
        return item;
    }

    /** 读取并校验日期指标所需的统一统计请求。 */
    private AgentDailyCustomerStatsDto dailyStats(AgentMetricQueryRequest request) {
        AgentOperationDailyRequest dailyRequest = new AgentOperationDailyRequest();
        dailyRequest.setRecordDate(request.getRecordDate()); dailyRequest.setMealType(request.getMealType()); dailyRequest.setDimensions(request.getDimensions());
        return operationQueryService.dailyCustomers(dailyRequest);
    }

    /** 从每日统计聚合结果中选取枚举指标值。 */
    private long dailyTotal(AgentDailyCustomerStatsDto source, String metric) {
        if ("DAILY_SCHEDULED_CUSTOMER_COUNT".equals(metric)) return source.getScheduledCustomerCount();
        if ("DAILY_VERIFIED_CUSTOMER_COUNT".equals(metric)) return source.getVerifiedCustomerCount();
        if ("DAILY_UNVERIFIED_CUSTOMER_COUNT".equals(metric)) return source.getUnverifiedCustomerCount();
        if ("DAILY_UNSCHEDULED_CUSTOMER_COUNT".equals(metric)) return source.getUnscheduledCustomerCount();
        return source.getMealPlanFailureCount();
    }

    /** 返回受控维度聚合，未指定自定义维度时按餐次返回。 */
    private Map<String, Long> metricDimensions(AgentDailyCustomerStatsDto source, String metric, List<String> dimensions) {
        if (dimensions != null && !dimensions.isEmpty()) return source.getMetricDimensionBreakdown().getOrDefault(metric, Collections.emptyMap());
        return source.getMetricMealTypeBreakdown().getOrDefault(metric, Collections.emptyMap());
    }

    /** 为普通聚合指标复制总数和查询时间。 */
    private void fillCount(AgentUnifiedQueryDto.MetricItem item, AgentOperationCountDto source) {
        if (source == null) return;
        item.setTotal(source.getTotal()); item.setQueriedAt(source.getQueriedAt());
    }

    /** 将有序维度映射确定性转换为仅含 label/value 的展示分组，并保持 dimensions 兼容。 */
    private List<AgentUnifiedQueryDto.MetricBreakdownItem> metricBreakdown(Map<String, Long> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) return new ArrayList<>();
        return dimensions.entrySet().stream()
            .map(entry -> new AgentUnifiedQueryDto.MetricBreakdownItem(entry.getKey(), entry.getValue() == null ? 0L : entry.getValue()))
            .collect(Collectors.toList());
    }

    /** 统一服务客户下单时间口径：成交时间优先，缺失时回退订单创建时间。 */
    private String orderTime(AgentOrderSummaryDto source) {
        return source == null ? null : stringValue(source.getDealTime() == null ? source.getCreateTime() : source.getDealTime());
    }

    /** 将列表源复制为统一分页信封。 */
    private <S, T> void copyPage(AgentListResultDto<S> source, AgentUnifiedQueryResponse<T> target, List<T> items) {
        target.setItems(items == null ? new ArrayList<>() : items);
        if (source == null) { target.setQueriedAt(now()); return; }
        target.setTotal(source.getTotal()); target.setPage(source.getPage()); target.setSize(source.getSize()); target.setTruncated(source.isTruncated());
        target.setQueriedAt(source.getQueriedAt() == null ? now() : source.getQueriedAt());
    }

    /** 构造聚合型菜品响应，使用数据对象中的业务总量而不是把聚合对象误报成一条菜品。 */
    private AgentUnifiedQueryResponse<Map<String, Object>> singleMapResponse(Map<String, Object> data, String totalField) {
        AgentUnifiedQueryResponse<Map<String, Object>> result = AgentUnifiedQueryResponse.single(data);
        if (data == null) return result;
        Object total = data.get(totalField);
        if (total instanceof Number number) result.setTotal(number.longValue());
        Object truncated = data.get("truncated");
        if (truncated instanceof Boolean value) result.setTruncated(value);
        Object items = data.get("items");
        if (items instanceof List<?> list) result.setSize(list.size());
        return result;
    }

    /** 构造单订单列表源，复用统一列表映射。 */
    private AgentListResultDto<AgentOrderSummaryDto> singleton(AgentOrderSummaryDto source) {
        AgentListResultDto<AgentOrderSummaryDto> result = new AgentListResultDto<>();
        result.setItems(new ArrayList<>(List.of(source))); result.setTotal(1); result.setPage(1); result.setSize(1); result.setQueriedAt(now());
        return result;
    }

    /** 将受控 DTO 转为 JSON 对象供候选菜和公共菜单的 data 字段使用。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value == null) return new LinkedHashMap<>();
        return JSON.parseObject(JSON.toJSONString(value), Map.class);
    }

    /** 判断统一查询参数是否包含非空文本。 */
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    /** 将指标和枚举参数归一化为大写。 */
    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT); }
    /** 使用默认值读取可选分页参数。 */
    private int intValue(Integer value, int fallback) { return value == null ? fallback : value; }
    /** 将日期时间等受控值转换为统一字符串。 */
    private String stringValue(Object value) { return value == null ? null : String.valueOf(value); }
    /** 返回统一查询响应使用的业务时区当前时间。 */
    private String now() { return ZonedDateTime.now(ZoneOffset.ofHours(8)).toOffsetDateTime().toString(); }
}
