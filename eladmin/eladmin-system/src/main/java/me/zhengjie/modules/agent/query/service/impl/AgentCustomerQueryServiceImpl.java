package me.zhengjie.modules.agent.query.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerAddressDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerCandidateDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerOverviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerPackageDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderMealBalanceDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderSummaryDto;
import me.zhengjie.modules.agent.query.service.AgentCustomerQueryService;
import me.zhengjie.modules.agent.query.service.AgentOrderQueryService;
import me.zhengjie.modules.agent.query.service.AgentHistoryQueryService;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.agent.query.domain.dto.AgentHistoryQueryRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentVerificationLogDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentRefundLogDto;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 客户只读查询实现。手机号、地址和超长特殊要求在离开主系统前完成脱敏或截断。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentCustomerQueryServiceImpl implements AgentCustomerQueryService {

    private static final int MAX_CANDIDATES = 10;
    private static final int MAX_SPECIAL_REQUIREMENT_LENGTH = 200;

    private final CustomerProfileMapper customerProfileMapper;
    private final CustomerProfileAddressMapper customerProfileAddressMapper;
    private final CustomerOrderMapper customerOrderMapper;
    private final AgentOrderQueryService agentOrderQueryService;
    private final AgentHistoryQueryService agentHistoryQueryService;

    /** {@inheritDoc} */
    @Override
    public AgentListResultDto<AgentCustomerCandidateDto> resolve(Long customerId, String customerCode, String customerName) {
        AgentListResultDto<AgentCustomerCandidateDto> result = new AgentListResultDto<>();
        LambdaQueryWrapper<CustomerProfile> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null && customerId > 0) wrapper.eq(CustomerProfile::getId, customerId);
        else if (hasText(customerCode)) wrapper.eq(CustomerProfile::getCustomerCode, customerCode.trim());
        else if (hasText(customerName)) wrapper.like(CustomerProfile::getCustomerName, customerName.trim());
        else return result;
        Set<Long> scopedCustomerIds = AgentCustomerDataScopeContext.customerIds();
        if (scopedCustomerIds != null && scopedCustomerIds.isEmpty()) {
            log.info("Agent客户解析未命中 customerId={} customerCode={} reason=EMPTY_DATA_SCOPE", customerId, customerCode);
            return result;
        }
        if (scopedCustomerIds != null) wrapper.in(CustomerProfile::getId, scopedCustomerIds);
        List<CustomerProfile> profiles = customerProfileMapper.selectList(wrapper.orderByDesc(CustomerProfile::getId));
        profiles = preferSingleActiveOrderCustomer(profiles, customerCode);
        result.setTotal(profiles.size());
        int size = Math.min(profiles.size(), MAX_CANDIDATES);
        result.setTruncated(size < profiles.size());
        result.setItems(profiles.subList(0, size).stream().map(this::candidate).collect(Collectors.toList()));
        log.info("Agent客户解析完成 customerId={} customerCode={} candidateCount={} scopeStatus={} scopeSize={}",
            customerId, customerCode, result.getTotal(), AgentCustomerDataScopeContext.status(), scopedCustomerIds == null ? -1 : scopedCustomerIds.size());
        return result;
    }

    /**
     * 兼容历史重复客户编号：仅在按编号查询且多个档案命中时，优先保留唯一拥有进行中订单的档案。
     * 若没有或仍有多个进行中客户，保留原候选集，禁止任意选择客户而导致排餐或档案信息串户。
     *
     * @param profiles 按客户编号及数据范围初步命中的客户档案
     * @param customerCode 当前查询的客户编号；姓名查询不参与该兼容规则
     * @return 可安全唯一确定时的进行中客户，否则返回原候选集
     */
    private List<CustomerProfile> preferSingleActiveOrderCustomer(List<CustomerProfile> profiles, String customerCode) {
        if (!hasText(customerCode) || profiles == null || profiles.size() <= 1) return profiles;
        Set<Long> profileIds = profiles.stream().map(CustomerProfile::getId).filter(java.util.Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (profileIds.isEmpty()) return profiles;
        List<CustomerOrder> activeOrders = customerOrderMapper.selectList(new LambdaQueryWrapper<CustomerOrder>()
            .eq(CustomerOrder::getStatus, 1).in(CustomerOrder::getCustomerId, profileIds));
        Set<Long> activeCustomerIds = activeOrders == null ? Collections.emptySet() : activeOrders.stream()
            .map(CustomerOrder::getCustomerId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (activeCustomerIds.size() != 1) {
            log.info("Agent重复客户编号无法按进行中订单唯一解析 customerCode={} candidateCount={} activeCandidateCount={}",
                customerCode, profiles.size(), activeCustomerIds.size());
            return profiles;
        }
        List<CustomerProfile> activeProfiles = profiles.stream().filter(profile -> activeCustomerIds.contains(profile.getId()))
            .collect(Collectors.toList());
        if (activeProfiles.size() == 1) {
            log.info("Agent重复客户编号按进行中订单解析 customerCode={} candidateCount={} resolvedCustomerId={}",
                customerCode, profiles.size(), activeProfiles.get(0).getId());
            return activeProfiles;
        }
        return profiles;
    }

    /** {@inheritDoc} */
    @Override
    public AgentCustomerOverviewDto getOverview(Long customerId, String customerCode) {
        AgentCustomerOverviewDto overview = new AgentCustomerOverviewDto();
        AgentListResultDto<AgentCustomerCandidateDto> candidates = resolve(customerId, customerCode, null);
        if (candidates.getTotal() != 1 || candidates.getItems().isEmpty()) {
            log.info("Agent客户概览未命中 customerId={} customerCode={} candidateCount={}",
                customerId, customerCode, candidates.getTotal());
            return overview;
        }
        Long resolvedId = candidates.getItems().get(0).getCustomerId();
        CustomerProfile profile = customerProfileMapper.selectByIdWithJson(resolvedId);
        if (profile == null) {
            log.warn("Agent客户概览档案二次加载失败 customerId={} customerCode={} resolvedCustomerId={}",
                customerId, customerCode, resolvedId);
            return overview;
        }
        overview.setPresent(true);
        overview.setCustomerId(profile.getId());
        overview.setCustomerCode(profile.getCustomerCode());
        overview.setCustomerName(profile.getCustomerName());
        overview.setCreateTime(profile.getCreateTime());
        overview.setFirstPurchaseTime(firstPurchaseTime(profile.getId()));
        overview.setMaskedPhone(maskPhone(profile.getPhone()));
        overview.setAllergyTags(profile.getAllergyTags() == null ? Collections.emptyList() : profile.getAllergyTags());
        overview.setExcludedDishIds(profile.getExcludedDishIds() == null ? Collections.emptyList() : profile.getExcludedDishIds());
        overview.setExcludedDates(profile.getExcludedDates() == null ? Collections.emptyList() : profile.getExcludedDates());
        overview.setSpecialRequirements(truncate(profile.getSpecialRequirements()));
        overview.setAddresses(loadAddresses(profile.getId()));
        fillOrderSummary(overview);
        fillRecentHistory(overview);
        return overview;
    }

    /**
     * 查询客户首笔订单的购买时间；优先使用成交时间，历史数据缺失时回退订单创建时间。
     *
     * @param customerId 已通过客服数据范围校验的客户 ID
     * @return 首笔订单购买时间，无订单时返回 null
     */
    private java.time.LocalDateTime firstPurchaseTime(Long customerId) {
        CustomerOrder firstOrder = customerOrderMapper.selectOne(new LambdaQueryWrapper<CustomerOrder>()
                .eq(CustomerOrder::getCustomerId, customerId)
                .orderByAsc(CustomerOrder::getCreateTime)
                .last("LIMIT 1"));
        if (firstOrder == null) return null;
        return firstOrder.getDealTime() == null ? firstOrder.getCreateTime() : firstOrder.getDealTime();
    }

    /** 填充客户最近核销与退餐摘要，查询仍受客户 ID 约束且不读取金额字段。 */
    private void fillRecentHistory(AgentCustomerOverviewDto overview) {
        AgentHistoryQueryRequest request = new AgentHistoryQueryRequest();
        request.setCustomerId(overview.getCustomerId());
        request.setRecentLimit(1);
        List<AgentVerificationLogDto> verifications = agentHistoryQueryService.listVerifications(request).getItems();
        if (verifications != null && !verifications.isEmpty()) overview.setLatestVerification(verifications.get(0));
        List<AgentRefundLogDto> refunds = agentHistoryQueryService.listRefunds(request).getItems();
        if (refunds != null && !refunds.isEmpty()) overview.setLatestRefund(refunds.get(0));
    }

    private void fillOrderSummary(AgentCustomerOverviewDto overview) {
        AgentListResultDto<AgentOrderSummaryDto> orders = agentOrderQueryService.listForOverview(overview.getCustomerId());
        overview.setTotalOrderCount((int) Math.min(orders.getTotal(), Integer.MAX_VALUE));
        overview.setActiveOrderCount((int) orders.getItems().stream()
                .filter(order -> Integer.valueOf(1).equals(order.getStatusCode())).count());
        AgentOrderMealBalanceDto total = new AgentOrderMealBalanceDto();
        for (AgentOrderSummaryDto order : orders.getItems()) {
            if (!Integer.valueOf(1).equals(order.getStatusCode()) || order.getMealBalance() == null) continue;
            AgentOrderMealBalanceDto balance = order.getMealBalance();
            total.setBreakfastCount(total.getBreakfastCount() + balance.getBreakfastCount());
            total.setLunchDinnerCount(total.getLunchDinnerCount() + balance.getLunchDinnerCount());
            total.setVerifiedBreakfast(total.getVerifiedBreakfast() + balance.getVerifiedBreakfast());
            total.setVerifiedLunch(total.getVerifiedLunch() + balance.getVerifiedLunch());
            total.setVerifiedDinner(total.getVerifiedDinner() + balance.getVerifiedDinner());
            total.setRemainingBreakfast(total.getRemainingBreakfast() + balance.getRemainingBreakfast());
            total.setRemainingLunchDinner(total.getRemainingLunchDinner() + balance.getRemainingLunchDinner());
        }
        overview.setMealBalance(total);
        overview.setPackages(orders.getItems().stream().map(this::toPackage).collect(Collectors.toList()));
    }

    /**
     * 将订单上的父子套餐关系转为客户概览可展示的非金额套餐摘要。
     *
     * @param order Agent 订单摘要
     * @return 客户签约套餐摘要
     */
    private AgentCustomerPackageDto toPackage(AgentOrderSummaryDto order) {
        AgentCustomerPackageDto dto = new AgentCustomerPackageDto();
        dto.setOrderId(order.getOrderId());
        dto.setParentPackageId(order.getParentPackageId());
        dto.setParentPackageName(order.getParentPackageName());
        dto.setChildPackageId(order.getChildPackageId());
        dto.setChildPackageName(order.getChildPackageName());
        dto.setActive(Integer.valueOf(1).equals(order.getStatusCode()));
        return dto;
    }

    private List<AgentCustomerAddressDto> loadAddresses(Long customerId) {
        return customerProfileAddressMapper.selectList(new LambdaQueryWrapper<CustomerProfileAddress>()
                        .eq(CustomerProfileAddress::getCustomerId, customerId)
                        .orderByAsc(CustomerProfileAddress::getId))
                .stream().map(this::address).collect(Collectors.toList());
    }

    private AgentCustomerCandidateDto candidate(CustomerProfile profile) {
        AgentCustomerCandidateDto dto = new AgentCustomerCandidateDto();
        dto.setCustomerId(profile.getId());
        dto.setCustomerCode(profile.getCustomerCode());
        dto.setCustomerName(profile.getCustomerName());
        dto.setMaskedPhone(maskPhone(profile.getPhone()));
        return dto;
    }

    private AgentCustomerAddressDto address(CustomerProfileAddress source) {
        AgentCustomerAddressDto dto = new AgentCustomerAddressDto();
        dto.setAddressTypeCode(source.getAddressType());
        dto.setAddressTypeName(addressTypeName(source.getAddressType()));
        dto.setMaskedAddress(maskAddress(source.getAddressDetail()));
        dto.setMaskedContactName(maskName(source.getContactName()));
        dto.setMaskedContactPhone(maskPhone(source.getContactPhone()));
        return dto;
    }

    private String maskPhone(String value) {
        if (!hasText(value)) return null;
        String text = value.trim();
        return text.length() <= 4 ? "****" : text.substring(0, Math.min(3, text.length())) + "****" + text.substring(text.length() - 4);
    }

    private String maskAddress(String value) {
        if (!hasText(value)) return null;
        String text = value.trim();
        return text.length() <= 6 ? "***" : text.substring(0, Math.min(6, text.length())) + "***";
    }

    private String maskName(String value) {
        if (!hasText(value)) return null;
        String text = value.trim();
        return text.length() == 1 ? "*" : text.substring(0, 1) + "*";
    }

    private String truncate(String value) {
        if (!hasText(value)) return null;
        String text = value.trim();
        return text.length() <= MAX_SPECIAL_REQUIREMENT_LENGTH ? text : text.substring(0, MAX_SPECIAL_REQUIREMENT_LENGTH) + "…";
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String addressTypeName(String code) {
        if ("DEFAULT".equals(code)) return "默认地址";
        if ("WORKDAY".equals(code)) return "工作日地址";
        if ("WEEKEND".equals(code)) return "周末地址";
        return "其他地址";
    }
}
