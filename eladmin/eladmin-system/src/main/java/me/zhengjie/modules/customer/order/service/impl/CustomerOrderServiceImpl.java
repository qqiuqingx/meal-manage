package me.zhengjie.modules.customer.order.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderSaveDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.order.util.OrderStartMealTypeUtil;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRule;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRuleDto;
import me.zhengjie.modules.customer.orderReplaceRule.mapper.CustomerOrderReplaceRuleMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.StringUtils;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import me.zhengjie.modules.meal.util.ScheduleKeyUtil;
import cn.hutool.json.JSONUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户订单服务实现
 */
@Service
public class CustomerOrderServiceImpl implements CustomerOrderService {

    @Autowired
    private CustomerOrderMapper orderMapper;

    @Autowired
    private CustomerProfileMapper profileMapper;

    @Autowired
    private CustomerProfileService customerProfileService;

    @Autowired
    private ParentPackageMapper parentPackageMapper;

    @Autowired
    private SubPackageMapper subPackageMapper;

    @Autowired
    private CustomerOrderReplaceRuleMapper replaceRuleMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private MealPlanCustomerMapper mealPlanCustomerMapper;

    private static final DateTimeFormatter ORDER_CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public PageResult<?> query(CustomerOrderQueryCriteria criteria, Integer current, Integer size) {
        // 排餐日期筛选：先计算符合条件的订单ID，写入criteria供SQL IN条件使用
        if (criteria.getScheduleDate() != null) {
            criteria.setEligibleOrderIds(Collections.emptyList());
            List<Long> ids = computeEligibleOrderIds(criteria.getScheduleDate());
            criteria.setEligibleOrderIds(ids.isEmpty() ? Arrays.asList(-1L) : ids);
        }

        Page<CustomerOrder> page = new Page<>(current, size);
        List<CustomerOrder> list = orderMapper.findAll(criteria, page);

        fillOrderCountFields(list);

        return new PageResult<>(list, page.getTotal());
    }

    private void fillOrderCountFields(List<CustomerOrder> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, Integer> scheduledCountMap = buildScheduledCountMap(list);
        Map<Long, Integer> todayUnverifiedCountMap = buildTodayUnverifiedCountMap(list);
        for (CustomerOrder order : list) {
            int breakfast = order.getBreakfastCount() != null ? order.getBreakfastCount() : 0;
            int lunchDinner = order.getLunchDinnerCount() != null ? order.getLunchDinnerCount() : 0;
            order.setTotalCount(breakfast + lunchDinner);
            order.setScheduledCount(scheduledCountMap.getOrDefault(order.getId(), 0));
            int remaining = order.getRemainingCount() != null ? order.getRemainingCount() : 0;
            int todayUnverified = todayUnverifiedCountMap.getOrDefault(order.getId(), 0);
            order.setEstimatedRemainingCount(Math.max(remaining - todayUnverified, 0));
        }
    }

    /**
     * 统计订单当前全部有效排餐数量。
     * 统计口径为有效排餐记录总数，不区分是否已核销。
     *
     * @param list 当前页订单列表
     * @return 订单ID -> 已排餐总数
     */
    private Map<Long, Integer> buildScheduledCountMap(List<CustomerOrder> list) {
        List<Long> orderIds = list.stream()
                .map(CustomerOrder::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderScheduledCountDto> scheduledCounts = mealPlanCustomerMapper.countAllScheduledByOrderIds(orderIds);
        if (scheduledCounts == null || scheduledCounts.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (OrderScheduledCountDto item : scheduledCounts) {
            if (item.getOrderId() != null) {
                result.put(item.getOrderId(), item.getScheduledCount() != null ? item.getScheduledCount() : 0);
            }
        }
        return result;
    }

    /**
     * 统计订单今日已排餐但未核销的数量。
     * 用于计算订单列表页的预计剩余餐数。
     *
     * @param list 当前页订单列表
     * @return 订单ID -> 今日已排未核销数量
     */
    private Map<Long, Integer> buildTodayUnverifiedCountMap(List<CustomerOrder> list) {
        List<Long> orderIds = list.stream()
                .map(CustomerOrder::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderScheduledCountDto> scheduledCounts =
                mealPlanCustomerMapper.countTodayUnverifiedScheduledByOrderIds(orderIds, LocalDate.now());
        if (scheduledCounts == null || scheduledCounts.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (OrderScheduledCountDto item : scheduledCounts) {
            if (item.getOrderId() != null) {
                result.put(item.getOrderId(), item.getScheduledCount() != null ? item.getScheduledCount() : 0);
            }
        }
        return result;
    }

    @Override
    public CustomerOrderDetailDto getDetail(Long id) {
        CustomerOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }

        CustomerProfile profile = profileMapper.selectById(order.getCustomerId());
        if (profile == null) {
            throw new BadRequestException("客户不存在");
        }
        CustomerOrderDetailDto dto = buildDetailDto(order, profile);
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CustomerOrderSaveDto dto) {
        // 先校验订单冲突
        validateOrderConflict(dto, null);
        validateAndNormalize(dto, null);

        // 获取客户信息，用于设置客户编号
        CustomerProfile profile = profileMapper.selectById(dto.getCustomerId());
        if (profile == null) {
            throw new BadRequestException("客户不存在");
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            CustomerOrder order = new CustomerOrder();
            buildOrderEntity(order, dto);
            order.setCustomerCode(profile.getCustomerCode());
            order.setOrderCode(generateOrderCode());
            order.setCreateBy(getCurrentUsername());
            try {
                orderMapper.insert(order);
                saveReplaceRules(order.getId(), dto.getReplaceRules());
                return;
            } catch (DuplicateKeyException ex) {
                if (attempt >= 2) {
                    throw new BadRequestException("订单编号生成失败，请重试或联系管理员");
                }
                // Brief delay before retry to reduce contention
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BadRequestException("订单编号生成被中断");
                }
            }
        }
        throw new BadRequestException("订单编号生成失败，请重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerOrderSaveDto dto) {
        if (dto.getId() == null) {
            throw new BadRequestException("订单ID不能为空");
        }

        CustomerOrder order = orderMapper.selectById(dto.getId());
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        Long originalParentPackageId = order.getParentPackageId();

        // 先校验订单冲突
        validateOrderConflict(dto, dto.getId());
        validateAndNormalize(dto, dto.getId());

        buildOrderEntity(order, dto);
        if (parentPackageChanged(originalParentPackageId, dto.getParentPackageId())) {
            refreshCustomerCodeForParentPackageChange(order, dto.getParentPackageId());
        }
        order.setUpdateBy(getCurrentUsername());

        orderMapper.updateById(order);
        syncProfileDietaryInfo(dto);
        softDeleteRules(dto.getId());
        saveReplaceRules(dto.getId(), dto.getReplaceRules());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("请选择要删除的订单");
        }
        orderMapper.deleteBatchIds(ids);
    }

    @Override
    public PageResult<?> getOrdersByCustomerId(Long customerId, Integer current, Integer size) {
        if (customerId == null) {
            throw new BadRequestException("客户ID不能为空");
        }
        CustomerOrderQueryCriteria criteria = new CustomerOrderQueryCriteria();
        criteria.setCustomerId(customerId);
        return query(criteria, current, size);
    }

    @Override
    public List<?> getTrialOrderOptions(String keyword, Long excludeId) {
        int limit = 20;
        List<CustomerOrder> orders = orderMapper.findTrialOrders(keyword, excludeId, limit);
        fillOrderCountFields(orders);
        return orders;
    }

    /**
     * 生成订单编号: ORD + yyyyMMdd + 3位序号
     */
    private String generateOrderCode() {
        String datePrefix = "ORD" + LocalDate.now().format(ORDER_CODE_DATE);

        String maxCode = orderMapper.findTodayMaxOrderCode(datePrefix);
        int nextNum = 1;
        if (StringUtils.isNotBlank(maxCode) && maxCode.length() > datePrefix.length()) {
            String numPart = maxCode.substring(datePrefix.length());
            try {
                nextNum = Integer.parseInt(numPart) + 1;
            } catch (NumberFormatException e) {
                // 解析失败，从1开始
            }
        }

        return datePrefix + String.format("%03d", nextNum);
    }

    private boolean parentPackageChanged(Long originalParentPackageId, Long newParentPackageId) {
        if (originalParentPackageId == null) {
            return newParentPackageId != null;
        }
        return !originalParentPackageId.equals(newParentPackageId);
    }

    private void refreshCustomerCodeForParentPackageChange(CustomerOrder order, Long newParentPackageId) {
        if (newParentPackageId == null) {
            throw new BadRequestException("父套餐不能为空");
        }
        CustomerProfile profile = profileMapper.selectById(order.getCustomerId());
        if (profile == null) {
            throw new BadRequestException("客户不存在");
        }
        String newCustomerCode = customerProfileService.generateCode(newParentPackageId);
        profile.setCustomerCode(newCustomerCode);
        profile.setUpdateBy(getCurrentUsername());
        profileMapper.updateById(profile);
        order.setCustomerCode(newCustomerCode);
    }

    /**
     * 校验并规范化 DTO
     */
    private void validateAndNormalize(CustomerOrderSaveDto dto, Long excludeId) {
        // 客户校验
        if (dto.getCustomerId() == null) {
            throw new BadRequestException("客户不能为空");
        }

        CustomerProfile profile = profileMapper.selectById(dto.getCustomerId());
        if (profile == null) {
            throw new BadRequestException("客户不存在");
        }

        // 金额校验
        if (dto.getTotalAmount() == null) {
            throw new BadRequestException("总金额不能为空");
        }
        if (dto.getFinalAmount() == null) {
            throw new BadRequestException("成交金额不能为空");
        }
        if (dto.getFinalAmount().compareTo(dto.getTotalAmount()) > 0) {
            throw new BadRequestException("成交金额不能超过总金额");
        }

        // 日期校验：结束日期不再做硬性校验
        // if (dto.getStartDate() != null && dto.getEndDate() != null) {
        //     if (dto.getEndDate().isBefore(dto.getStartDate())) {
        //         throw new BadRequestException("订单结束日期不能早于开始日期");
        //     }
        // }

        // 核销数校验
        int totalCount = getTotalCount(dto);
        int verifiedCount = dto.getVerifiedCount() != null ? dto.getVerifiedCount() : 0;
        if (verifiedCount > totalCount) {
            throw new BadRequestException("核销餐数不能超过合计餐数");
        }

        // 核销金额校验
        BigDecimal verifiedAmount = dto.getVerifiedAmount() != null ? dto.getVerifiedAmount() : BigDecimal.ZERO;
        if (verifiedAmount.compareTo(dto.getFinalAmount()) > 0) {
            throw new BadRequestException("核销金额不能超过成交金额");
        }

        // 自动计算餐费余额和剩余餐数
        BigDecimal mealBalance = dto.getFinalAmount().subtract(verifiedAmount);
        dto.setMealBalance(mealBalance);

        int remainingCount = totalCount - verifiedCount;
        dto.setRemainingCount(remainingCount);

        // 餐数默认值
        if (dto.getBreakfastCount() == null) dto.setBreakfastCount(0);
        if (dto.getLunchDinnerCount() == null) dto.setLunchDinnerCount(0);
        if (dto.getDepositAmount() == null) dto.setDepositAmount(BigDecimal.ZERO);
        if (dto.getBreakfastPrice() == null) dto.setBreakfastPrice(BigDecimal.ZERO);
        if (dto.getLunchDinnerPrice() == null) dto.setLunchDinnerPrice(BigDecimal.ZERO);
        if (dto.getVerifiedAmount() == null) dto.setVerifiedAmount(BigDecimal.ZERO);
        if (dto.getVerifiedCount() == null) dto.setVerifiedCount(0);

        validateTrialConversion(dto);

        // 状态默认值
        if (dto.getStatus() == null) {
            dto.setStatus(1);
        }

        dto.setMealType(OrderStartMealTypeUtil.normalizeOrderMealType(dto.getMealType()));
        dto.setStartMealType(OrderStartMealTypeUtil.normalizeStartMealType(dto.getMealType(), dto.getStartMealType()));
        if (!OrderStartMealTypeUtil.isStartMealTypeAllowed(dto.getMealType(), dto.getStartMealType())) {
            throw new BadRequestException("开始餐次与订单餐次类型不匹配，可选开始餐次：" +
                    String.join("、", toMealTypeDescList(OrderStartMealTypeUtil.allowedStartMealTypes(dto.getMealType()))));
        }
    }

    /**
     * 构建 Order 实体
     */
    private void buildOrderEntity(CustomerOrder order, CustomerOrderSaveDto dto) {
        order.setCustomerId(dto.getCustomerId());
        order.setParentPackageId(dto.getParentPackageId());
        order.setChildPackageId(dto.getChildPackageId());
        order.setDepositAmount(dto.getDepositAmount());
        order.setTotalAmount(dto.getTotalAmount());
        order.setFinalAmount(dto.getFinalAmount());
        order.setBreakfastCount(dto.getBreakfastCount());
        order.setLunchDinnerCount(dto.getLunchDinnerCount());
        order.setBreakfastPrice(dto.getBreakfastPrice());
        order.setLunchDinnerPrice(dto.getLunchDinnerPrice());
        order.setVerifiedCount(dto.getVerifiedCount());
        order.setVerifiedAmount(dto.getVerifiedAmount());
        order.setMealBalance(dto.getMealBalance());
        order.setRemainingCount(dto.getRemainingCount());
        order.setDealTime(dto.getDealTime());
        order.setFirstDeliveryTime(dto.getFirstDeliveryTime());
        order.setStartDate(dto.getStartDate());
        order.setStartMealType(dto.getStartMealType());
        order.setEndDate(dto.getEndDate());
        order.setStatus(dto.getStatus());
        order.setMealType(dto.getMealType());
        order.setScheduleMode(dto.getScheduleMode());
        order.setDeliveryDates(StringUtils.isNotBlank(dto.getDeliveryDatesWithMealTypes())
                ? dto.getDeliveryDatesWithMealTypes()
                : dto.getDeliveryDates());
        order.setRemark(dto.getRemark());
        order.setCustomerSource(dto.getCustomerSource());
        order.setTrialConverted(Boolean.TRUE.equals(dto.getTrialConverted()));
        order.setTrialOrderId(Boolean.TRUE.equals(dto.getTrialConverted()) ? dto.getTrialOrderId() : null);
        order.setMainDishCount(dto.getMainDishCount());
        order.setSideDishCount(dto.getSideDishCount());
        order.setVegCount(dto.getVegCount());
        order.setRiceCount(dto.getRiceCount() != null ? dto.getRiceCount() : 1);
        order.setRiceType(dto.getRiceType() != null ? dto.getRiceType() : "白米饭");
        order.setSoupCount(dto.getSoupCount());
        order.setCustomMenuImage(dto.getCustomMenuImage());
    }

    /**
     * 同步客户档案的饮食偏好字段（编辑订单时）
     */
    private void syncProfileDietaryInfo(CustomerOrderSaveDto dto) {
        if (dto.getCustomerId() == null) {
            return;
        }
        boolean hasAllergyTags = dto.getAllergyTags() != null;
        boolean hasSpecialRequirements = dto.getSpecialRequirements() != null;
        if (!hasAllergyTags && !hasSpecialRequirements) {
            return;
        }
        List<String> cleaned = null;
        String val = null;

        if (hasAllergyTags) {
            cleaned = new ArrayList<>();
            if (dto.getAllergyTags() != null) {
                for (String tag : dto.getAllergyTags()) {
                    if (tag == null) continue;
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
                        cleaned.add(trimmed);
                    }
                }
            }
        }

        if (hasSpecialRequirements) {
            val = dto.getSpecialRequirements();
            if (val != null) {
                val = val.trim();
                if (val.isEmpty()) {
                    val = null;
                }
            }
        }

        LambdaUpdateWrapper<CustomerProfile> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(CustomerProfile::getId, dto.getCustomerId());
        if (hasAllergyTags) {
            // JSON 列不能直接接收 List 参数，否则 MySQL 会把它当成 binary 对象处理
            updateWrapper.set(CustomerProfile::getAllergyTags, JSON.toJSONString(cleaned));
        }
        if (hasSpecialRequirements) {
            updateWrapper.set(CustomerProfile::getSpecialRequirements, val);
        }
        updateWrapper.set(CustomerProfile::getUpdateBy, getCurrentUsername());
        profileMapper.update(null, updateWrapper);
    }

    /**
     * 构建详情 DTO
     */
    private CustomerOrderDetailDto buildDetailDto(CustomerOrder order, CustomerProfile profile) {
        CustomerOrderDetailDto dto = new CustomerOrderDetailDto();
        dto.setId(order.getId());
        dto.setCustomerId(order.getCustomerId());
        dto.setParentPackageId(order.getParentPackageId());
        dto.setChildPackageId(order.getChildPackageId());
        if (profile != null) {
            dto.setCustomerName(profile.getCustomerName());
            dto.setPhone(profile.getPhone());
            dto.setSpecialRequirements(profile.getSpecialRequirements());
            dto.setAllergyTags(profile.getAllergyTags());
        }

        // 填充套餐名称
        if (order.getParentPackageId() != null) {
            ParentPackage parentPackage = parentPackageMapper.selectById(order.getParentPackageId());
            if (parentPackage != null) {
                dto.setParentPackageName(parentPackage.getPackageName());
            }
        }
        if (order.getChildPackageId() != null) {
            SubPackage childPackage = subPackageMapper.selectById(order.getChildPackageId());
            if (childPackage != null) {
                dto.setChildPackageName(childPackage.getSubPackageName());
            }
        }

        dto.setOrderCode(order.getOrderCode());
        dto.setDepositAmount(order.getDepositAmount());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setFinalAmount(order.getFinalAmount());
        dto.setBreakfastCount(order.getBreakfastCount());
        dto.setLunchDinnerCount(order.getLunchDinnerCount());
        dto.setTotalCount(getTotalCountFromOrder(order));
        dto.setBreakfastPrice(order.getBreakfastPrice());
        dto.setLunchDinnerPrice(order.getLunchDinnerPrice());
        dto.setVerifiedCount(order.getVerifiedCount());
        dto.setVerifiedAmount(order.getVerifiedAmount());
        dto.setMealBalance(order.getMealBalance());
        dto.setRemainingCount(order.getRemainingCount());
        dto.setDealTime(order.getDealTime());
        dto.setFirstDeliveryTime(order.getFirstDeliveryTime());
        dto.setStartDate(order.getStartDate());
        dto.setStartMealType(order.getStartMealType());
        dto.setEndDate(order.getEndDate());
        dto.setStatus(order.getStatus());
        dto.setStatusDesc(getStatusDesc(order.getStatus()));
        dto.setMealType(order.getMealType());
        dto.setMealTypeDesc(getMealTypeDesc(order.getMealType()));
        dto.setScheduleMode(order.getScheduleMode());
        dto.setDeliveryDates(order.getDeliveryDates());
        dto.setRemark(order.getRemark());
        dto.setCustomerSource(order.getCustomerSource());
        dto.setTrialConverted(Boolean.TRUE.equals(order.getTrialConverted()));
        dto.setTrialOrderId(order.getTrialOrderId());
        dto.setTrialOrderCode(resolveTrialOrderCode(order.getTrialOrderId()));
        dto.setMainDishCount(order.getMainDishCount());
        dto.setSideDishCount(order.getSideDishCount());
        dto.setVegCount(order.getVegCount());
        dto.setRiceCount(order.getRiceCount());
        dto.setRiceType(order.getRiceType());
        dto.setSoupCount(order.getSoupCount());
        dto.setCustomMenuImage(order.getCustomMenuImage());
        dto.setCreateTime(order.getCreateTime());
        dto.setUpdateTime(order.getUpdateTime());
        dto.setReplaceRules(loadReplaceRules(order.getId()));
        return dto;
    }

    private String resolveTrialOrderCode(Long trialOrderId) {
        if (trialOrderId == null) {
            return null;
        }
        CustomerOrder trialOrder = orderMapper.selectById(trialOrderId);
        return trialOrder != null ? trialOrder.getOrderCode() : null;
    }

    /**
     * 校验试餐成单标记与关联试餐订单，确保关联订单来自父套餐名称包含“试餐”的订单。
     */
    private void validateTrialConversion(CustomerOrderSaveDto dto) {
        if (!Boolean.TRUE.equals(dto.getTrialConverted())) {
            dto.setTrialConverted(false);
            dto.setTrialOrderId(null);
            return;
        }
        if (dto.getTrialOrderId() == null) {
            throw new BadRequestException("请选择关联试餐订单");
        }
        if (dto.getId() != null && dto.getId().equals(dto.getTrialOrderId())) {
            throw new BadRequestException("关联试餐订单不能选择当前订单");
        }
        CustomerOrder trialOrder = orderMapper.selectById(dto.getTrialOrderId());
        if (trialOrder == null) {
            throw new BadRequestException("关联试餐订单不存在");
        }
        ParentPackage parentPackage = null;
        if (trialOrder.getParentPackageId() != null) {
            parentPackage = parentPackageMapper.selectById(trialOrder.getParentPackageId());
        }
        if (parentPackage == null || StringUtils.isBlank(parentPackage.getPackageName())
                || !parentPackage.getPackageName().contains("试餐")) {
            throw new BadRequestException("关联订单必须是父套餐名称包含“试餐”的订单");
        }
    }

    private int getTotalCount(CustomerOrderSaveDto dto) {
        return (dto.getBreakfastCount() != null ? dto.getBreakfastCount() : 0)
            + (dto.getLunchDinnerCount() != null ? dto.getLunchDinnerCount() : 0);
    }

    private int getTotalCountFromOrder(CustomerOrder order) {
        return (order.getBreakfastCount() != null ? order.getBreakfastCount() : 0)
            + (order.getLunchDinnerCount() != null ? order.getLunchDinnerCount() : 0);
    }

    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "已取消";
            case 1: return "进行中";
            case 2: return "已完成";
            default: return "未知";
        }
    }

    private String getMealTypeDesc(String mealType) {
        if (mealType == null) return "早+午餐+晚餐";
        switch (mealType) {
            case "LUNCH": return "午餐";
            case "DINNER": return "晚餐";
            case "LUNCH_DINNER": return "午餐+晚餐";
            case "ALL": return "早+午餐+晚餐";
            default: return "未知";
        }
    }

    @Override
    public void validateOrderConflict(CustomerOrderSaveDto dto, Long excludeId) {
        // 如果没有开始日期，不进行校验
        if (dto.getStartDate() == null) {
            return;
        }

        LocalDate startDate = dto.getStartDate();
        String mealType = OrderStartMealTypeUtil.normalizeOrderMealType(dto.getMealType());
        String startMealType = OrderStartMealTypeUtil.normalizeStartMealType(mealType, dto.getStartMealType());
        if (!OrderStartMealTypeUtil.isStartMealTypeAllowed(mealType, startMealType)) {
            throw new BadRequestException("开始餐次与订单餐次类型不匹配");
        }

        // 校验规则：
        // 1. 全餐次订单：冲突范围内不能已有任何覆盖午/晚的订单
        if ("ALL".equals(mealType)) {
            int count = orderMapper.countAllMealTypeOrders(dto.getCustomerId(), startDate, excludeId);
            if (count > 0) {
                throw new BadRequestException("同一开始日期已存在全餐次订单，不能重复创建");
            }
            int lunchDinnerCount = orderMapper.countMealTypeOrders(dto.getCustomerId(), startDate, "LUNCH_DINNER", excludeId);
            if (lunchDinnerCount > 0) {
                throw new BadRequestException("同一开始日期已存在午餐+晚餐订单，不能创建全餐次订单");
            }
            int lunchCount = orderMapper.countMealTypeOrders(dto.getCustomerId(), startDate, "LUNCH", excludeId);
            if (lunchCount > 0) {
                throw new BadRequestException("同一开始日期已存在午餐订单，不能创建全餐次订单");
            }
            int dinnerCount = orderMapper.countMealTypeOrders(dto.getCustomerId(), startDate, "DINNER", excludeId);
            if (dinnerCount > 0) {
                throw new BadRequestException("同一开始日期已存在晚餐订单，不能创建全餐次订单");
            }
            return;
        }

        // 2. 午餐+晚餐订单：与 ALL / LUNCH / DINNER / LUNCH_DINNER 均互斥
        if ("LUNCH_DINNER".equals(mealType)) {
            int allCount = orderMapper.countAllMealTypeOrders(dto.getCustomerId(), startDate, excludeId);
            if (allCount > 0) {
                throw new BadRequestException("同一开始日期已存在全餐次订单，不能创建午餐+晚餐订单");
            }
            int sameTypeCount = orderMapper.countMealTypeOrders(dto.getCustomerId(), startDate, "LUNCH_DINNER", excludeId);
            if (sameTypeCount > 0) {
                throw new BadRequestException("同一开始日期已存在午餐+晚餐订单，不能重复创建");
            }
            int lunchCount = orderMapper.countMealTypeOrders(dto.getCustomerId(), startDate, "LUNCH", excludeId);
            if (lunchCount > 0) {
                throw new BadRequestException("同一开始日期已存在午餐订单，不能创建午餐+晚餐订单");
            }
            int dinnerCount = orderMapper.countMealTypeOrders(dto.getCustomerId(), startDate, "DINNER", excludeId);
            if (dinnerCount > 0) {
                throw new BadRequestException("同一开始日期已存在晚餐订单，不能创建午餐+晚餐订单");
            }
            return;
        }

        // 3. 午餐或晚餐订单：原有逻辑 + 增加 LUNCH_DINNER 互斥检查
        if ("LUNCH".equals(mealType) || "DINNER".equals(mealType)) {
            int lunchDinnerCount = orderMapper.countMealTypeOrders(dto.getCustomerId(), startDate, "LUNCH_DINNER", excludeId);
            if (lunchDinnerCount > 0) {
                throw new BadRequestException("同一开始日期已存在午餐+晚餐订单，不能创建单独餐次订单");
            }

            int totalCount = orderMapper.countOverlappingOrders(dto.getCustomerId(), startDate, excludeId);
            if (totalCount >= 2) {
                throw new BadRequestException("同一开始日期最多只能有两个不同餐次的订单");
            }

            int sameTypeCount = orderMapper.countMealTypeOrders(dto.getCustomerId(), startDate, mealType, excludeId);
            if (sameTypeCount > 0) {
                throw new BadRequestException("同一开始日期已存在相同餐次的订单");
            }
        }

        // 3. 检查剩余餐数（仅编辑时）
        if (excludeId != null) {
            CustomerOrder existingOrder = orderMapper.selectById(excludeId);
            if (existingOrder != null && existingOrder.getVerifiedCount() != null) {
                int newTotalCount = getTotalCount(dto);
                int existingVerified = existingOrder.getVerifiedCount();
                if (newTotalCount < existingVerified) {
                    throw new BadRequestException("订单餐数不能小于已核销餐数（当前已核销：" + existingVerified + "）");
                }
                // Add atomic check to prevent concurrent modifications
                int currentVerifiedCount = orderMapper.selectById(excludeId).getVerifiedCount();
                if (currentVerifiedCount != existingVerified) {
                    throw new BadRequestException("订单数据已被其他操作修改，请刷新后重试");
                }
            }
        }
    }

    private String getCurrentUsername() {
        try {
            return SecurityUtils.getCurrentUsername();
        } catch (Exception e) {
            return "system";
        }
    }

    /**
     * 计算指定排餐日期符合排餐资格的订单ID列表
     * 5层判断：状态=1、startDate<=日期、剩余餐数>0、排餐模式匹配、客户排除日期不命中
     */
    private List<Long> computeEligibleOrderIds(LocalDate scheduleDate) {
        // 1+2+3: 查询 status=1 且 startDate<=scheduleDate 且 remainingCount>0 的订单
        CustomerOrderQueryCriteria baseCriteria = new CustomerOrderQueryCriteria();
        baseCriteria.setStatus(1);
        baseCriteria.setStartDate(new LocalDate[]{LocalDate.of(2000, 1, 1), scheduleDate});
        Page<CustomerOrder> allPage = new Page<>(1, Integer.MAX_VALUE);
        List<CustomerOrder> candidates = orderMapper.findAll(baseCriteria, allPage);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量加载客户档案（用于排除日期检查）
        Set<Long> customerIds = new HashSet<>();
        for (CustomerOrder order : candidates) {
            if (order.getCustomerId() != null) {
                customerIds.add(order.getCustomerId());
            }
        }
        java.util.Map<Long, CustomerProfile> profileMap = new java.util.HashMap<>();
        if (!customerIds.isEmpty()) {
            List<CustomerProfile> profiles = profileMapper.selectBatchIds(customerIds);
            for (CustomerProfile p : profiles) {
                profileMap.put(p.getId(), p);
            }
        }

        List<Long> eligibleIds = new ArrayList<>();
        for (CustomerOrder order : candidates) {
            // 3: 剩余餐数 > 0（二次校验，SQL 已有 COALESCE 过滤，此处兜底）
            if (Math.max(0, order.getRemainingCount() != null ? order.getRemainingCount() : 0) <= 0) {
                continue;
            }
            // 4: 排餐模式是否匹配日期
            if (!scheduleDateMatches(order, scheduleDate)) {
                continue;
            }
            // 5: 客户排除日期检查
            CustomerProfile profile = profileMap.get(order.getCustomerId());
            if (profile != null && profile.isExcluded(scheduleDate, order.getMealType())) {
                continue;
            }
            eligibleIds.add(order.getId());
        }
        return eligibleIds;
    }

    /**
     * 判断订单排餐模式是否匹配目标日期（忽略餐次，任意餐次匹配即通过）
     */
    private boolean scheduleDateMatches(CustomerOrder order, LocalDate targetDate) {
        String scheduleMode = order.getScheduleMode();
        // DAILY 或 null：始终匹配
        if (scheduleMode == null || "DAILY".equals(scheduleMode)) {
            return true;
        }
        // SCHEDULE：deliveryDates JSON 中包含该日期
        if ("SCHEDULE".equals(scheduleMode)) {
            List<String> dates = parseDeliveryDatesDates(order.getDeliveryDates());
            return dates.contains(targetDate.toString());
        }
        // WEEKDAY：周一至周五
        if ("WEEKDAY".equals(scheduleMode)) {
            return ScheduleKeyUtil.isWeekday(targetDate);
        }
        // WEEKEND：周六至周日
        if ("WEEKEND".equals(scheduleMode)) {
            return ScheduleKeyUtil.isWeekend(targetDate);
        }
        return false;
    }

    /**
     * 从 deliveryDates JSON 中解析日期列表（不区分餐次）
     * 支持新格式 [{"date":"...","mealTypes":[...]}] 和旧格式 ["..."]
     */
    private List<String> parseDeliveryDatesDates(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[{")) {
                List<Object> items = JSONUtil.parseArray(json);
                List<String> dates = new ArrayList<>();
                for (Object item : items) {
                    if (item instanceof cn.hutool.json.JSONObject) {
                        String date = ((cn.hutool.json.JSONObject) item).getStr("date");
                        if (date != null) {
                            dates.add(date);
                        }
                    }
                }
                return dates;
            } else {
                return JSONUtil.toList(json, String.class);
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<String> toMealTypeDescList(List<String> mealTypes) {
        List<String> labels = new ArrayList<>();
        for (String mealType : mealTypes) {
            labels.add(OrderStartMealTypeUtil.mealTypeDesc(mealType));
        }
        return labels;
    }

    // ========== 换菜规则相关方法 ==========

    /**
     * 校验换菜规则
     */
    private void validateReplaceRules(List<CustomerOrderReplaceRuleDto> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }

        Set<Long> sourceDishIds = new HashSet<>();
        for (CustomerOrderReplaceRuleDto rule : rules) {
            if (rule.getSourceDishId() == null) {
                throw new BadRequestException("换菜规则中的原菜不能为空");
            }
            if (rule.getTargetDishId() == null) {
                throw new BadRequestException("换菜规则中的目标菜不能为空");
            }
            if (rule.getSourceDishId().equals(rule.getTargetDishId())) {
                throw new BadRequestException("原菜和目标菜不能相同");
            }
            if (!sourceDishIds.add(rule.getSourceDishId())) {
                throw new BadRequestException("同一订单不能重复配置同一个原菜");
            }

            Dish sourceDish = dishMapper.selectById(rule.getSourceDishId().intValue());
            if (sourceDish == null || !Boolean.TRUE.equals(sourceDish.getEnabled())) {
                throw new BadRequestException("换菜规则中的原菜品不存在或已停用");
            }
            Dish targetDish = dishMapper.selectById(rule.getTargetDishId().intValue());
            if (targetDish == null || !Boolean.TRUE.equals(targetDish.getEnabled())) {
                throw new BadRequestException("换菜规则中的目标菜品不存在或已停用");
            }
        }
    }

    /**
     * 保存换菜规则（新增）
     */
    private void saveReplaceRules(Long orderId, List<CustomerOrderReplaceRuleDto> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        validateReplaceRules(rules);

        for (CustomerOrderReplaceRuleDto dto : rules) {
            Dish sourceDish = dishMapper.selectById(dto.getSourceDishId().intValue());
            Dish targetDish = dishMapper.selectById(dto.getTargetDishId().intValue());

            CustomerOrderReplaceRule rule = new CustomerOrderReplaceRule();
            rule.setOrderId(orderId);
            rule.setSourceDishId(dto.getSourceDishId());
            rule.setSourceDishName(sourceDish.getName());
            rule.setSourceDishType(sourceDish.getDishType());
            rule.setTargetDishId(dto.getTargetDishId());
            rule.setTargetDishName(targetDish.getName());
            rule.setTargetDishType(targetDish.getDishType());
            rule.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
            rule.setRemark(dto.getRemark());
            rule.setDeleted(false);
            rule.setCreateBy(getCurrentUsername());
            replaceRuleMapper.insert(rule);
        }
    }

    /**
     * 软删除订单的所有换菜规则
     */
    private void softDeleteRules(Long orderId) {
        CustomerOrderReplaceRule update = new CustomerOrderReplaceRule();
        update.setDeleted(true);
        update.setUpdateBy(getCurrentUsername());
        replaceRuleMapper.update(update, new QueryWrapper<CustomerOrderReplaceRule>()
                .eq("order_id", orderId)
                .eq("deleted", false));
    }

    /**
     * 加载订单的换菜规则（详情回显，联查菜品表获取最新名称）
     */
    private List<CustomerOrderReplaceRuleDto> loadReplaceRules(Long orderId) {
        List<CustomerOrderReplaceRule> rules = replaceRuleMapper.selectList(
                new QueryWrapper<CustomerOrderReplaceRule>()
                        .eq("order_id", orderId)
                        .eq("deleted", false));
        if (rules.isEmpty()) {
            return Collections.emptyList();
        }

        List<CustomerOrderReplaceRuleDto> result = new ArrayList<>();
        for (CustomerOrderReplaceRule rule : rules) {
            CustomerOrderReplaceRuleDto dto = new CustomerOrderReplaceRuleDto();
            dto.setId(rule.getId());
            dto.setOrderId(rule.getOrderId());
            dto.setSourceDishId(rule.getSourceDishId());
            dto.setTargetDishId(rule.getTargetDishId());
            dto.setEnabled(rule.getEnabled());
            dto.setRemark(rule.getRemark());

            // 联查菜品表获取最新名称和类型
            Dish sourceDish = dishMapper.selectById(rule.getSourceDishId().intValue());
            if (sourceDish != null && Boolean.TRUE.equals(sourceDish.getEnabled())) {
                dto.setSourceDishName(sourceDish.getName());
                dto.setSourceDishType(sourceDish.getDishType());
                dto.setSourceDishInvalid(false);
            } else {
                dto.setSourceDishName(rule.getSourceDishName());
                dto.setSourceDishType(rule.getSourceDishType());
                dto.setSourceDishInvalid(true);
            }

            Dish targetDish = dishMapper.selectById(rule.getTargetDishId().intValue());
            if (targetDish != null && Boolean.TRUE.equals(targetDish.getEnabled())) {
                dto.setTargetDishName(targetDish.getName());
                dto.setTargetDishType(targetDish.getDishType());
                dto.setTargetDishInvalid(false);
            } else {
                dto.setTargetDishName(rule.getTargetDishName());
                dto.setTargetDishType(rule.getTargetDishType());
                dto.setTargetDishInvalid(true);
            }

            result.add(dto);
        }
        return result;
    }
}
