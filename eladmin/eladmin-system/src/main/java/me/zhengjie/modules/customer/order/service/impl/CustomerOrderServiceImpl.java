package me.zhengjie.modules.customer.order.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.CustomerOrderInlineAudit;
import me.zhengjie.modules.customer.order.domain.CustomerOrderStatus;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderInlineUpdateDto;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderQueryCriteria;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderSaveDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderInlineAuditMapper;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import me.zhengjie.modules.customer.order.util.CustomerOrderAmountPermissionUtil;
import me.zhengjie.modules.customer.order.util.OrderStartMealTypeUtil;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRule;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRuleDto;
import me.zhengjie.modules.customer.orderReplaceRule.mapper.CustomerOrderReplaceRuleMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.service.CustomerDietDictionaryService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private CustomerOrderInlineAuditMapper inlineAuditMapper;

    @Autowired
    private CustomerProfileMapper profileMapper;

    @Autowired
    private CustomerProfileAddressMapper profileAddressMapper;

    @Autowired
    private CustomerProfileService customerProfileService;

    @Autowired
    private CustomerDietDictionaryService dietDictionaryService;

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
        maskAmountFields(list);

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
        maskAmountFields(dto);
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
            if (Integer.valueOf(CustomerOrderStatus.PAUSED.getCode()).equals(order.getStatus())) {
                order.setPauseEffectiveDate(LocalDate.now());
            }
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

    /**
     * 保存已由导入器完成字段转换的首单，核对来源购买数、历史核销基数与剩余数，并使用普通订单编号规则。
     *
     * @param order 导入首单实体
     * @return 新建订单主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createImportedFirstOrder(CustomerOrder order) {
        if (order == null || order.getCustomerId() == null || order.getParentPackageId() == null
                || order.getStartDate() == null || order.getLunchDinnerCount() == null
                || order.getLunchDinnerCount() <= 0 || order.getRemainingCount() == null) {
            throw new BadRequestException("导入首单信息不完整");
        }
        if (order.getStatus() == null || (order.getStatus() != CustomerOrderStatus.ACTIVE.getCode()
                && order.getStatus() != CustomerOrderStatus.PAUSED.getCode()
                && !(order.getStatus() == CustomerOrderStatus.COMPLETED.getCode()
                && Integer.valueOf(0).equals(order.getRemainingCount())))) {
            throw new BadRequestException("导入首单状态与剩余餐数不匹配");
        }
        int importedVerified = order.getImportedVerifiedCount() == null ? 0 : order.getImportedVerifiedCount();
        int verifiedCount = order.getVerifiedCount() == null ? 0 : order.getVerifiedCount();
        int lunchDinnerCount = order.getLunchDinnerCount();
        if (importedVerified < 0 || verifiedCount < importedVerified
                || lunchDinnerCount - verifiedCount != order.getRemainingCount()) {
            throw new BadRequestException("导入首单餐数与核销基数不一致");
        }
        CustomerProfile profile = profileMapper.selectById(order.getCustomerId());
        if (profile == null) {
            throw new BadRequestException("客户档案不存在，无法创建首单");
        }
        order.setCustomerCode(profile.getCustomerCode());

        CustomerOrderSaveDto conflictCheck = new CustomerOrderSaveDto();
        conflictCheck.setCustomerId(order.getCustomerId());
        conflictCheck.setStartDate(order.getStartDate());
        conflictCheck.setMealType(order.getMealType());
        conflictCheck.setBreakfastCount(order.getBreakfastCount());
        conflictCheck.setLunchDinnerCount(order.getLunchDinnerCount());
        validateOrderConflict(conflictCheck, null);

        for (int attempt = 0; attempt < 3; attempt++) {
            order.setOrderCode(generateOrderCode());
            order.setCreateBy(getCurrentUsername());
            try {
                orderMapper.insert(order);
                return order.getId();
            } catch (DuplicateKeyException e) {
                if (attempt == 2) {
                    throw new BadRequestException("订单编号生成失败，请重试或联系管理员");
                }
            }
        }
        throw new BadRequestException("订单编号生成失败，请重试");
    }

    /**
     * 更新客户订单并维护暂停生效日期。
     *
     * @param dto 订单编辑请求；暂停日期由服务端根据状态转换生成
     */
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
        validateAndNormalize(dto, order);

        Integer previousStatus = order.getStatus();
        LocalDate previousPauseEffectiveDate = order.getPauseEffectiveDate();
        buildOrderEntity(order, dto);
        if (Integer.valueOf(1).equals(previousStatus) && Integer.valueOf(4).equals(order.getStatus())) {
            order.setPauseEffectiveDate(LocalDate.now());
        } else if (Integer.valueOf(4).equals(previousStatus) && Integer.valueOf(1).equals(order.getStatus())) {
            order.setPauseEffectiveDate(null);
        } else {
            order.setPauseEffectiveDate(previousPauseEffectiveDate);
        }
        if (parentPackageChanged(originalParentPackageId, dto.getParentPackageId())) {
            refreshCustomerCodeForParentPackageChange(order, dto.getParentPackageId());
        }
        order.setUpdateBy(getCurrentUsername());

        orderMapper.updateById(order);
        syncProfileDietaryInfo(dto);
        softDeleteRules(dto.getId());
        saveReplaceRules(dto.getId(), dto.getReplaceRules());
    }

    /**
     * 按白名单更新一个订单字段，校验页面提供的旧值并将实际变更状态追加到审计表。
     *
     * @param id 订单主键
     * @param dto 单字段更新请求，包含字段键、新值和预期旧值；地址字段含槽位类型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateInline(Long id, CustomerOrderInlineUpdateDto dto) {
        if (id == null) {
            throw new BadRequestException("订单ID不能为空");
        }
        if (dto == null || dto.getField() == null) {
            throw new BadRequestException("行内修改字段不能为空");
        }

        String field = dto.getField();
        if (!isOrderInlineField(field)) {
            throw new BadRequestException("不支持行内修改字段：" + field);
        }

        CustomerOrder order = orderMapper.selectInlineUpdateByIdForUpdate(id);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        if (!Integer.valueOf(CustomerOrderStatus.ACTIVE.getCode()).equals(order.getStatus())
                && !Integer.valueOf(CustomerOrderStatus.PAUSED.getCode()).equals(order.getStatus())) {
            throw new BadRequestException("只有进行中或暂停订单可以行内修改");
        }

        String addressType = inlineAddressType(field);
        CustomerProfile profile = null;
        if (isProfileInlineField(field) || "customerCode".equals(field) || addressType != null) {
            profile = profileMapper.selectByIdForInlineUpdate(order.getCustomerId());
            if (profile == null) {
                throw new BadRequestException("客户档案不存在");
            }
        }

        CustomerProfileAddress address = null;
        if (addressType != null) {
            address = profileAddressMapper.selectForInlineUpdate(order.getCustomerId(), addressType);
            if (address == null) {
                throw new BadRequestException("该类型地址不存在，请在客户档案中添加");
            }
        }

        Object actualValue = address == null ? getInlineValue(order, profile, field) : address.getAddressDetail();
        Object expectedValue;
        Object comparableCurrentValue;
        if (isDietInlineField(field)) {
            expectedValue = normalizeInlineDietIdentities(dto.getExpectedValue());
            comparableCurrentValue = normalizeInlineDietIdentities(actualValue);
        } else {
            expectedValue = normalizeInlineValue(field, dto.getExpectedValue(), true);
            comparableCurrentValue = normalizeInlineValue(field, actualValue, true);
        }
        if (!Objects.equals(comparableCurrentValue, expectedValue)) {
            throw new BadRequestException(org.springframework.http.HttpStatus.CONFLICT,
                    "字段已被其他操作修改，请刷新后重试");
        }
        Object requestedValue = isDietInlineField(field)
                ? normalizeInlineDietSelection(dto.getValue(), actualValue)
                : normalizeInlineValue(field, dto.getValue(), false);
        if (isInlineNoOp(order, profile, field, actualValue, requestedValue)) {
            return;
        }

        String operator = getCurrentUsername();
        LocalDateTime updateTime = LocalDateTime.now().withNano(0);
        LinkedHashMap<String, Object> orderBeforeState = new LinkedHashMap<>();
        LinkedHashMap<String, Object> orderAfterState = new LinkedHashMap<>();
        LinkedHashMap<String, Object> profileBeforeState = new LinkedHashMap<>();
        LinkedHashMap<String, Object> profileAfterState = new LinkedHashMap<>();
        LinkedHashMap<String, Object> addressBeforeState = new LinkedHashMap<>();
        LinkedHashMap<String, Object> addressAfterState = new LinkedHashMap<>();

        if (address != null) {
            addressBeforeState.put("addressType", addressType);
            addressAfterState.put("addressType", addressType);
            addressBeforeState.put("addressDetail", actualValue);
            addressAfterState.put("addressDetail", requestedValue);
            if (!Objects.equals(address.getUpdateTime(), updateTime)) {
                addressBeforeState.put("updateTime", address.getUpdateTime());
                addressAfterState.put("updateTime", updateTime);
            }
            if (profileAddressMapper.updateAddressDetailInline(address.getId(), (String) requestedValue, updateTime) != 1) {
                throw new BadRequestException(org.springframework.http.HttpStatus.CONFLICT,
                        "地址已被其他操作修改，请刷新后重试");
            }
        } else if ("customerCode".equals(field)) {
            updateInlineCustomerCode(order, profile, (String) requestedValue, operator,
                    updateTime,
                    orderBeforeState, orderAfterState, profileBeforeState, profileAfterState);
        } else if (isProfileInlineField(field)) {
            updateInlineProfileField(profile, field, actualValue, requestedValue, operator,
                    updateTime,
                    profileBeforeState, profileAfterState);
        } else {
            orderBeforeState.put(field, actualValue);
            orderAfterState.put(field, requestedValue);
            appendInlineUpdateMetadata(orderBeforeState, orderAfterState,
                    order.getUpdateBy(), order.getUpdateTime(), operator, updateTime);

            Integer integerValue = requestedValue instanceof Integer ? (Integer) requestedValue : null;
            String stringValue = requestedValue instanceof String ? (String) requestedValue : null;
            Integer remainingCount = null;
            LocalDate pauseEffectiveDate = order.getPauseEffectiveDate();
            if ("breakfastCount".equals(field) || "lunchDinnerCount".equals(field)) {
                remainingCount = applyMealCountUpdate(order, field, integerValue,
                        orderBeforeState, orderAfterState);
            } else if ("status".equals(field)) {
                pauseEffectiveDate = applyStatusUpdate(order, integerValue,
                        orderBeforeState, orderAfterState);
            }

            if (orderMapper.updateInlineField(id, field, integerValue, stringValue,
                    remainingCount, pauseEffectiveDate, operator, updateTime) != 1) {
                throw new BadRequestException(org.springframework.http.HttpStatus.CONFLICT,
                        "订单数据已被其他操作修改，请刷新后重试");
            }
        }

        CustomerOrderInlineAudit audit = new CustomerOrderInlineAudit();
        audit.setOrderId(order.getId());
        audit.setCustomerId(order.getCustomerId());
        audit.setFieldKey(field);
        audit.setOperator(operator);
        audit.setCreatedAt(LocalDateTime.now());
        audit.setBeforeState(buildInlineAuditState(order.getId(), orderBeforeState,
                order.getCustomerId(), profileBeforeState, address == null ? null : address.getId(), addressBeforeState));
        audit.setAfterState(buildInlineAuditState(order.getId(), orderAfterState,
                order.getCustomerId(), profileAfterState, address == null ? null : address.getId(), addressAfterState));
        if (inlineAuditMapper.insert(audit) != 1) {
            throw new IllegalStateException("订单行内修改审计写入失败");
        }
    }

    /**
     * 判断字段是否属于行内修改白名单。
     *
     * @param field 字段键
     * @return 支持时为 true
     */
    private boolean isOrderInlineField(String field) {
        return "mainDishCount".equals(field)
                || "sideDishCount".equals(field)
                || "vegCount".equals(field)
                || "soupCount".equals(field)
                || "scheduleMode".equals(field)
                || "customMenuImage".equals(field)
                || "breakfastCount".equals(field)
                || "lunchDinnerCount".equals(field)
                || "status".equals(field)
                || isProfileInlineField(field)
                || inlineAddressType(field) != null
                || "customerCode".equals(field);
    }

    /**
     * 判断字段是否归属于客户档案。
     *
     * @param field 字段键
     * @return 归属于客户档案时为 true
     */
    private boolean isProfileInlineField(String field) {
        return "allergyTags".equals(field) || "specialRequirements".equals(field)
                || "phone".equals(field) || isDietInlineField(field);
    }

    /**
     * 判断字段是否为饮食对象引用数组。
     *
     * @param field 字段键
     * @return 饮食对象字段时为 true
     */
    private boolean isDietInlineField(String field) {
        return "dishRequirements".equals(field) || "dietaryRestrictions".equals(field);
    }

    /**
     * 从地址行内字段键中识别现有地址槽位。
     *
     * @param field 形如 addressDetail:DEFAULT 的字段键
     * @return 合法槽位代码；其他字段返回 null
     */
    private String inlineAddressType(String field) {
        if (field == null || !field.startsWith("addressDetail:")) {
            return null;
        }
        String type = field.substring("addressDetail:".length());
        return Arrays.asList("DEFAULT", "WORKDAY", "WEEKEND").contains(type) ? type : null;
    }

    /**
     * 按字段类型校验行内请求值；预期旧值允许为 null，以支持数据库空值的并发比较。
     *
     * @param field 字段键
     * @param rawValue 请求中的原始 JSON 值
     * @param expected 是否为预期旧值
     * @return 已验证的字段值
     */
    private Object normalizeInlineValue(String field, Object rawValue, boolean expected) {
        if ("allergyTags".equals(field)) {
            return normalizeInlineAllergyTags(rawValue);
        }
        if (rawValue == null) {
            if (expected || "customMenuImage".equals(field) || "specialRequirements".equals(field)) {
                return null;
            }
            throw new BadRequestException("字段 " + field + " 不允许为空");
        }
        if ("mainDishCount".equals(field) || "sideDishCount".equals(field)
                || "vegCount".equals(field) || "soupCount".equals(field)
                || "breakfastCount".equals(field) || "lunchDinnerCount".equals(field)
                || "status".equals(field)) {
            Integer value = requireInlineInteger(field, rawValue);
            if (expected) {
                return value;
            }
            if ("soupCount".equals(field) && value != 0 && value != 1) {
                throw new BadRequestException("含汤字段只允许 0 或 1");
            }
            if ("status".equals(field) && value != CustomerOrderStatus.ACTIVE.getCode()
                    && value != CustomerOrderStatus.PAUSED.getCode()) {
                throw new BadRequestException("订单状态只允许进行中或暂停");
            }
            if (!"status".equals(field) && value < 0) {
                throw new BadRequestException("字段 " + field + " 不能小于 0");
            }
            return value;
        }
        if (!(rawValue instanceof String)) {
            throw new BadRequestException("字段 " + field + " 必须是字符串");
        }
        String value = (String) rawValue;
        if ("phone".equals(field)) {
            if (expected) {
                return value;
            }
            String normalized = value.trim();
            if (!normalized.matches("^1[3-9]\\d{9}$")) {
                throw new BadRequestException("手机号格式不正确");
            }
            return normalized;
        }
        if (inlineAddressType(field) != null) {
            if (expected) {
                return value;
            }
            String normalized = value.trim();
            if (normalized.isEmpty() || normalized.length() > 200) {
                throw new BadRequestException("地址不能为空且不能超过 200 个字符");
            }
            return normalized;
        }
        if ("specialRequirements".equals(field)) {
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        }
        if ("customerCode".equals(field)) {
            if (expected) {
                return value;
            }
            if (value.trim().isEmpty()) {
                throw new BadRequestException("客户编号不能为空");
            }
            return value;
        }
        if (expected) {
            return value;
        }
        if ("scheduleMode".equals(field)) {
            if (!Arrays.asList("SCHEDULE", "DAILY", "WEEKEND", "WEEKDAY").contains(value)) {
                throw new BadRequestException("排餐模式不合法");
            }
            return value;
        }
        if ("customMenuImage".equals(field)) {
            String[] pathParts = value.split("/", -1);
            if (value.length() > 500 || pathParts.length != 4 || !"".equals(pathParts[0])
                    || !"file".equals(pathParts[1]) || pathParts[2].isEmpty() || pathParts[3].isEmpty()
                    || value.contains("..") || value.contains("\\") || value.contains("?")
                    || value.contains("#") || value.trim().length() != value.length()) {
                throw new BadRequestException("自定义菜单图片必须使用已上传文件的 /file/ 路径");
            }
            return value;
        }
        throw new BadRequestException("不支持行内修改字段：" + field);
    }

    /**
     * 去除过敏标签首尾空白并去重，保留首次出现顺序。
     *
     * @param rawValue 请求值或数据库当前值
     * @return 规范化标签列表
     */
    private List<String> normalizeInlineAllergyTags(Object rawValue) {
        if (rawValue == null) {
            return Collections.emptyList();
        }
        if (!(rawValue instanceof List)) {
            throw new BadRequestException("过敏标签必须是字符串数组");
        }
        List<String> tags = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object item : (List<?>) rawValue) {
            if (item == null) {
                continue;
            }
            if (!(item instanceof String)) {
                throw new BadRequestException("过敏标签必须是字符串");
            }
            String tag = ((String) item).trim();
            if (!tag.isEmpty() && seen.add(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    /**
     * 按去重且排序后的 type:id 集合规范化饮食对象旧值，用于并发比较。
     *
     * @param rawValue 页面提交的预期值或数据库当前值
     * @return 用于稳定比较的对象身份键
     */
    private List<String> normalizeInlineDietIdentities(Object rawValue) {
        if (rawValue == null) {
            return Collections.emptyList();
        }
        if (!(rawValue instanceof List)) {
            throw new BadRequestException("饮食对象旧值必须是对象数组");
        }
        List<CustomerDietItemDto> items = parseInlineDietItems(rawValue);
        List<String> keys = new ArrayList<>();
        for (CustomerDietItemDto item : items) {
            if (item == null || !isSupportedDietType(item.getType())
                    || item.getId() == null || item.getId() <= 0) {
                continue;
            }
            String key = item.getType() + ":" + item.getId();
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        Collections.sort(keys);
        return keys;
    }

    /**
     * 将订单行内提交的饮食对象数组按当前档案和有效字典规范化。
     *
     * @param rawValue 页面提交的新对象数组；null 表示清空
     * @param existingValue 当前档案已保存的对象数组
     * @return 权威对象引用数组，不采用客户端名称
     */
    private List<CustomerDietItemDto> normalizeInlineDietSelection(Object rawValue, Object existingValue) {
        List<CustomerDietItemDto> requested = rawValue == null
                ? Collections.<CustomerDietItemDto>emptyList() : parseInlineDietItems(rawValue);
        List<CustomerDietItemDto> existing = existingValue == null
                ? Collections.<CustomerDietItemDto>emptyList() : parseInlineDietItems(existingValue);
        List<CustomerDietOptionDto> activeOptions = dietDictionaryService.listActiveOptions();
        return dietDictionaryService.normalizeSelections(requested, existing, activeOptions);
    }

    /**
     * 将 JSON 数组或 DTO 数组转换为饮食对象 DTO，并拒绝非数组值。
     *
     * @param rawValue 待解析对象数组
     * @return 已解析的对象 DTO 列表
     */
    private List<CustomerDietItemDto> parseInlineDietItems(Object rawValue) {
        if (!(rawValue instanceof List)) {
            throw new BadRequestException("饮食对象必须是对象数组");
        }
        try {
            return JSON.parseArray(JSON.toJSONString(rawValue), CustomerDietItemDto.class);
        } catch (RuntimeException e) {
            throw new BadRequestException("饮食对象格式不正确");
        }
    }

    /**
     * 检查饮食对象类型是否属于现有五类结构化字典。
     *
     * @param type 对象类型
     * @return 类型受支持时为 true
     */
    private boolean isSupportedDietType(String type) {
        return "DISH".equals(type) || "INGREDIENT".equals(type) || "INGREDIENT_TAG".equals(type)
                || "INGREDIENT_CATEGORY".equals(type) || "DISH_TAG".equals(type);
    }

    /**
     * 读取严格为 JSON 整数的行内字段值，拒绝字符串和小数转换。
     *
     * @param field 字段键
     * @param rawValue 请求值
     * @return 32 位整数
     */
    private Integer requireInlineInteger(String field, Object rawValue) {
        if (!(rawValue instanceof Integer) && !(rawValue instanceof Long)) {
            throw new BadRequestException("字段 " + field + " 必须是整数");
        }
        long value = ((Number) rawValue).longValue();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new BadRequestException("字段 " + field + " 超出允许范围");
        }
        return (int) value;
    }

    /**
     * 读取当前订单中与字段键对应的持久化值。
     *
     * @param order 已锁定订单
     * @param field 字段键
     * @return 当前字段值
     */
    private Object getInlineValue(CustomerOrder order, CustomerProfile profile, String field) {
        switch (field) {
            case "mainDishCount": return order.getMainDishCount();
            case "sideDishCount": return order.getSideDishCount();
            case "vegCount": return order.getVegCount();
            case "soupCount": return order.getSoupCount();
            case "scheduleMode": return order.getScheduleMode();
            case "customMenuImage": return order.getCustomMenuImage();
            case "breakfastCount": return order.getBreakfastCount();
            case "lunchDinnerCount": return order.getLunchDinnerCount();
            case "status": return order.getStatus();
            case "allergyTags": return profile == null ? null : profile.getAllergyTags();
            case "specialRequirements": return profile == null ? null : profile.getSpecialRequirements();
            case "dishRequirements": return profile == null ? null : profile.getDishRequirements();
            case "dietaryRestrictions": return profile == null ? null : profile.getDietaryRestrictions();
            case "phone": return profile == null ? null : profile.getPhone();
            case "customerCode": return order.getCustomerCode();
            default: throw new BadRequestException("不支持行内修改字段：" + field);
        }
    }

    /**
     * 判断更新是否没有需要持久化的业务变更。
     *
     * @param order 已锁定订单
     * @param profile 已锁定客户档案；订单自有字段时为空
     * @param field 字段键
     * @param actualValue 数据库当前原始值
     * @param requestedValue 已规范化的新值
     * @return 无变化时为 true
     */
    private boolean isInlineNoOp(CustomerOrder order, CustomerProfile profile, String field,
                                 Object actualValue, Object requestedValue) {
        if (isDietInlineField(field)) {
            return Objects.equals(normalizeInlineDietIdentities(actualValue),
                    normalizeInlineDietIdentities(requestedValue));
        }
        if ("customerCode".equals(field)) {
            return Objects.equals(order.getCustomerCode(), requestedValue)
                    && Objects.equals(profile.getCustomerCode(), requestedValue);
        }
        return Objects.equals(actualValue, requestedValue);
    }

    /**
     * 定向更新客户档案中的行内字段，并记录实际修改前后值。
     *
     * @param profile 已锁定客户档案
     * @param field 字段键
     * @param actualValue 数据库原始值
     * @param requestedValue 已规范化的新值
     * @param operator 最后修改人
     * @param beforeState 修改前审计字段
     * @param afterState 修改后审计字段
     */
    private void updateInlineProfileField(CustomerProfile profile, String field, Object actualValue,
                                          Object requestedValue, String operator,
                                          LocalDateTime updateTime,
                                          Map<String, Object> beforeState,
                                          Map<String, Object> afterState) {
        beforeState.put(field, actualValue);
        afterState.put(field, requestedValue);
        appendInlineUpdateMetadata(beforeState, afterState, profile.getUpdateBy(),
                profile.getUpdateTime(), operator, updateTime);

        int updated;
        if ("allergyTags".equals(field)) {
            updated = profileMapper.updateAllergyTagsInline(profile.getId(),
                    JSON.toJSONString(requestedValue), operator, updateTime);
        } else if ("phone".equals(field)) {
            updated = profileMapper.updatePhoneInline(profile.getId(), (String) requestedValue, operator, updateTime);
        } else if ("dishRequirements".equals(field)) {
            updated = profileMapper.updateDishRequirementsInline(profile.getId(),
                    JSON.toJSONString(requestedValue), operator, updateTime);
        } else if ("dietaryRestrictions".equals(field)) {
            updated = profileMapper.updateDietaryRestrictionsInline(profile.getId(),
                    JSON.toJSONString(requestedValue), operator, updateTime);
        } else {
            updated = profileMapper.updateSpecialRequirementsInline(profile.getId(),
                    (String) requestedValue, operator, updateTime);
        }
        if (updated != 1) {
            throw new BadRequestException(org.springframework.http.HttpStatus.CONFLICT,
                    "客户档案数据已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 校验当前父套餐编号池后，同步更新客户档案和当前订单的客户编号。
     *
     * @param order 已锁定订单
     * @param profile 已锁定客户档案
     * @param customerCode 新客户编号
     * @param operator 最后修改人
     * @param orderBeforeState 订单修改前审计字段
     * @param orderAfterState 订单修改后审计字段
     * @param profileBeforeState 档案修改前审计字段
     * @param profileAfterState 档案修改后审计字段
     */
    private void updateInlineCustomerCode(CustomerOrder order, CustomerProfile profile, String customerCode,
                                          String operator, LocalDateTime updateTime,
                                          Map<String, Object> orderBeforeState,
                                          Map<String, Object> orderAfterState,
                                          Map<String, Object> profileBeforeState,
                                          Map<String, Object> profileAfterState) {
        validateInlineCustomerCode(order, customerCode);
        if (profileMapper.countByCodeExcludeId(customerCode, profile.getId()) > 0) {
            throw new BadRequestException("客户编号「" + customerCode + "」已被占用，请换一个");
        }

        orderBeforeState.put("customerCode", order.getCustomerCode());
        orderAfterState.put("customerCode", customerCode);
        profileBeforeState.put("customerCode", profile.getCustomerCode());
        profileAfterState.put("customerCode", customerCode);
        boolean profileCodeChanged = !Objects.equals(profile.getCustomerCode(), customerCode);
        boolean orderCodeChanged = !Objects.equals(order.getCustomerCode(), customerCode);
        try {
            if (profileCodeChanged) {
                appendInlineUpdateMetadata(profileBeforeState, profileAfterState,
                        profile.getUpdateBy(), profile.getUpdateTime(), operator, updateTime);
                if (profileMapper.updateCustomerCodeInline(
                        profile.getId(), customerCode, operator, updateTime) != 1) {
                    throw new BadRequestException(org.springframework.http.HttpStatus.CONFLICT,
                            "客户档案数据已被其他操作修改，请刷新后重试");
                }
            }
            if (orderCodeChanged) {
                appendInlineUpdateMetadata(orderBeforeState, orderAfterState,
                        order.getUpdateBy(), order.getUpdateTime(), operator, updateTime);
                if (orderMapper.updateCustomerCodeInline(
                        order.getId(), customerCode, operator, updateTime) != 1) {
                    throw new BadRequestException(org.springframework.http.HttpStatus.CONFLICT,
                            "订单数据已被其他操作修改，请刷新后重试");
                }
            }
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("客户编号「" + customerCode + "」已被占用，请换一个");
        }
    }

    /**
     * 将随业务更新一起修改的操作人和更新时间加入前后状态。
     *
     * @param beforeState 修改前状态字段
     * @param afterState 修改后状态字段
     * @param previousOperator 当前记录的修改人
     * @param previousUpdateTime 当前记录的更新时间
     * @param operator 本次操作人
     * @param updateTime 本次更新时间
     */
    private void appendInlineUpdateMetadata(Map<String, Object> beforeState,
                                            Map<String, Object> afterState,
                                            String previousOperator,
                                            LocalDateTime previousUpdateTime,
                                            String operator,
                                            LocalDateTime updateTime) {
        if (!Objects.equals(previousOperator, operator)) {
            beforeState.put("updateBy", previousOperator);
            afterState.put("updateBy", operator);
        }
        if (!Objects.equals(previousUpdateTime, updateTime)) {
            beforeState.put("updateTime", previousUpdateTime);
            afterState.put("updateTime", updateTime);
        }
    }

    /**
     * 根据订单当前父套餐的编号池校验客户编号前缀和数字范围。
     *
     * @param order 当前订单
     * @param customerCode 待校验客户编号
     */
    private void validateInlineCustomerCode(CustomerOrder order, String customerCode) {
        if (customerCode == null || customerCode.length() > 16) {
            throw new BadRequestException("客户编号不能为空且不能超过 16 个字符");
        }
        if (order.getParentPackageId() == null) {
            throw new BadRequestException("当前订单未关联父套餐，无法校验客户编号");
        }
        ParentPackage parent = parentPackageMapper.selectById(order.getParentPackageId());
        if (parent == null) {
            throw new BadRequestException("当前订单父套餐不存在");
        }
        String poolPrefix = parent.getPoolPrefix();
        if (StringUtils.isBlank(poolPrefix) || parent.getPoolStart() == null || parent.getPoolEnd() == null) {
            throw new BadRequestException("当前父套餐未配置可用的客户编号池");
        }
        if (!customerCode.startsWith(poolPrefix)) {
            throw new BadRequestException("客户编号必须以「" + poolPrefix + "」开头");
        }
        String numberPart = customerCode.substring(poolPrefix.length());
        if (!numberPart.matches("[0-9]+")) {
            throw new BadRequestException("客户编号前缀后的内容必须是数字");
        }
        int sequence;
        try {
            sequence = Integer.parseInt(numberPart);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("客户编号数字部分超出允许范围");
        }
        if (sequence < parent.getPoolStart() || sequence > parent.getPoolEnd()) {
            throw new BadRequestException("客户编号不在当前父套餐编号池范围内");
        }
    }

    /**
     * 添加本次白名单字段及业务派生字段的定向数据库更新。
     *
     * @param order 已锁定订单
     * @param field 字段键
     * @param value 已验证的新值
     * @param update 定向更新包装器
     * @param beforeState 修改前审计字段
     * @param afterState 修改后审计字段
     */
    /**
     * 校验并更新早餐或午晚餐数，同时重算剩余餐数。
     *
     * @param order 已锁定订单
     * @param field 被修改餐数字段
     * @param requestedCount 新餐数
     * @param update 定向更新包装器
     * @param beforeState 修改前审计字段
     * @param afterState 修改后审计字段
     */
    private Integer applyMealCountUpdate(CustomerOrder order, String field, Integer requestedCount,
                                         Map<String, Object> beforeState,
                                         Map<String, Object> afterState) {
        int breakfastCount = "breakfastCount".equals(field) ? requestedCount
                : (order.getBreakfastCount() == null ? 0 : order.getBreakfastCount());
        int lunchDinnerCount = "lunchDinnerCount".equals(field) ? requestedCount
                : (order.getLunchDinnerCount() == null ? 0 : order.getLunchDinnerCount());
        long totalCount = (long) breakfastCount + lunchDinnerCount;
        int verifiedCount = order.getVerifiedCount() == null ? 0 : order.getVerifiedCount();
        int importedVerifiedCount = order.getImportedVerifiedCount() == null ? 0 : order.getImportedVerifiedCount();
        if (totalCount < verifiedCount || totalCount < importedVerifiedCount) {
            throw new BadRequestException("订单餐数不能小于已核销餐数（当前已核销：" + verifiedCount + "）");
        }
        if (totalCount > Integer.MAX_VALUE) {
            throw new BadRequestException("订单餐数合计超出允许范围");
        }

        int remainingCount = (int) totalCount - verifiedCount;
        if (!Objects.equals(order.getRemainingCount(), remainingCount)) {
            beforeState.put("remainingCount", order.getRemainingCount());
            afterState.put("remainingCount", remainingCount);
        }
        return remainingCount;
    }

    /**
     * 按现有订单规则在进行中与暂停之间切换，并维护暂停生效日。
     *
     * @param order 已锁定订单
     * @param requestedStatus 新状态
     * @param update 定向更新包装器
     * @param beforeState 修改前审计字段
     * @param afterState 修改后审计字段
     */
    private LocalDate applyStatusUpdate(CustomerOrder order, Integer requestedStatus,
                                        Map<String, Object> beforeState,
                                        Map<String, Object> afterState) {
        Integer previousStatus = order.getStatus();
        LocalDate pauseEffectiveDate;
        if (Integer.valueOf(CustomerOrderStatus.ACTIVE.getCode()).equals(previousStatus)
                && Integer.valueOf(CustomerOrderStatus.PAUSED.getCode()).equals(requestedStatus)) {
            pauseEffectiveDate = LocalDate.now();
        } else if (Integer.valueOf(CustomerOrderStatus.PAUSED.getCode()).equals(previousStatus)
                && Integer.valueOf(CustomerOrderStatus.ACTIVE.getCode()).equals(requestedStatus)) {
            if (order.getMealType() == null) {
                throw new BadRequestException("该订单餐次尚未指定，请先确认午餐或晚餐后再恢复");
            }
            pauseEffectiveDate = null;
        } else {
            throw new BadRequestException("订单状态只能在进行中与暂停之间切换");
        }
        if (!Objects.equals(order.getPauseEffectiveDate(), pauseEffectiveDate)) {
            beforeState.put("pauseEffectiveDate", order.getPauseEffectiveDate());
            afterState.put("pauseEffectiveDate", pauseEffectiveDate);
        }
        return pauseEffectiveDate;
    }

    /**
     * 以实体类型和主键为索引，将一组字段状态序列化为审计 JSON。
     *
     * @param orderId 订单主键
     * @param orderFields 订单字段状态
     * @param customerId 客户主键
     * @param profileFields 客户档案字段状态
     * @param addressId 地址主键；非地址修改时为空
     * @param addressFields 地址字段状态
     * @return 可供管理员核对和手动恢复的 JSON 文本
     */
    private String buildInlineAuditState(Long orderId, Map<String, Object> orderFields,
                                         Long customerId, Map<String, Object> profileFields,
                                         Long addressId, Map<String, Object> addressFields) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (!orderFields.isEmpty()) {
            state.put("customer_order:" + orderId, orderFields);
        }
        if (!profileFields.isEmpty()) {
            state.put("customer_profile:" + customerId, profileFields);
        }
        if (!addressFields.isEmpty()) {
            state.put("customer_profile_address:" + addressId, addressFields);
        }
        return JSON.toJSONString(state);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("请选择要删除的订单");
        }
        List<CustomerOrder> orders = orderMapper.selectBatchIds(ids);
        boolean hasUnfinishedOrder = orders.stream().anyMatch(order ->
                order.getStatus() != null && (order.getStatus() == CustomerOrderStatus.ACTIVE.getCode()
                        || order.getStatus() == CustomerOrderStatus.PAUSED.getCode()));
        if (hasUnfinishedOrder) {
            throw new BadRequestException("进行中或暂停的订单不能删除，请先完成或退餐");
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
        maskAmountFields(orders);
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
     * 校验订单字段、允许的状态转换并规范化餐次与金额。
     *
     * @param dto 订单保存参数
     * @param existingOrder 原订单；创建时为空
     */
    private void validateAndNormalize(CustomerOrderSaveDto dto, CustomerOrder existingOrder) {
        // 客户校验
        if (dto.getCustomerId() == null) {
            throw new BadRequestException("客户不能为空");
        }

        CustomerProfile profile = profileMapper.selectById(dto.getCustomerId());
        if (profile == null) {
            throw new BadRequestException("客户不存在");
        }

        normalizeAmountFields(dto, existingOrder);
        validateAmountFields(dto);

        // 日期校验：结束日期不再做硬性校验
        // if (dto.getStartDate() != null && dto.getEndDate() != null) {
        //     if (dto.getEndDate().isBefore(dto.getStartDate())) {
        //         throw new BadRequestException("订单结束日期不能早于开始日期");
        //     }
        // }

        // 核销数校验
        int totalCount = getTotalCount(dto);
        int importedVerified = existingOrder == null || existingOrder.getImportedVerifiedCount() == null
                ? 0 : existingOrder.getImportedVerifiedCount();
        int verifiedCount = dto.getVerifiedCount() != null ? dto.getVerifiedCount()
                : importedVerified > 0 && existingOrder != null && existingOrder.getVerifiedCount() != null
                ? existingOrder.getVerifiedCount() : 0;
        if (verifiedCount < importedVerified) {
            throw new BadRequestException("核销餐数不能小于导入前已核销餐数");
        }
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
        if (dto.getVerifiedCount() == null) dto.setVerifiedCount(verifiedCount);

        validateTrialConversion(dto);

        // 状态默认值
        if (dto.getStatus() == null) {
            dto.setStatus(CustomerOrderStatus.ACTIVE.getCode());
        }
        validateStatusChange(existingOrder, dto.getStatus());

        boolean keepUnspecifiedMealType = dto.getMealType() == null
                && (existingOrder != null && existingOrder.getMealType() == null
                || existingOrder == null && Integer.valueOf(CustomerOrderStatus.PAUSED.getCode()).equals(dto.getStatus()));
        if (keepUnspecifiedMealType) {
            if (existingOrder != null && Integer.valueOf(CustomerOrderStatus.PAUSED.getCode()).equals(existingOrder.getStatus())
                    && Integer.valueOf(CustomerOrderStatus.ACTIVE.getCode()).equals(dto.getStatus())) {
                throw new BadRequestException("该订单餐次尚未指定，请先确认午餐或晚餐后再恢复");
            }
            dto.setStartMealType(null);
        } else {
            dto.setMealType(OrderStartMealTypeUtil.normalizeOrderMealType(dto.getMealType()));
            dto.setStartMealType(OrderStartMealTypeUtil.normalizeStartMealType(dto.getMealType(), dto.getStartMealType()));
            if (!OrderStartMealTypeUtil.isStartMealTypeAllowed(dto.getMealType(), dto.getStartMealType())) {
                throw new BadRequestException("开始餐次与订单餐次类型不匹配，可选开始餐次：" +
                        String.join("、", toMealTypeDescList(OrderStartMealTypeUtil.allowedStartMealTypes(dto.getMealType()))));
            }
        }
    }

    /**
     * 校验创建和编辑允许的订单状态，暂停订单只能通过编辑从进行中暂停或恢复。
     *
     * @param existingOrder 原订单；创建时为空
     * @param requestedStatus 请求状态
     */
    private void validateStatusChange(CustomerOrder existingOrder, Integer requestedStatus) {
        if (existingOrder == null) {
            if (!Integer.valueOf(CustomerOrderStatus.ACTIVE.getCode()).equals(requestedStatus)
                    && !Integer.valueOf(CustomerOrderStatus.PAUSED.getCode()).equals(requestedStatus)) {
                throw new BadRequestException("新订单状态只能是进行中或暂停");
            }
            return;
        }
        Integer currentStatus = existingOrder.getStatus();
        boolean unchanged = requestedStatus.equals(currentStatus);
        boolean pause = Integer.valueOf(CustomerOrderStatus.ACTIVE.getCode()).equals(currentStatus)
                && Integer.valueOf(CustomerOrderStatus.PAUSED.getCode()).equals(requestedStatus);
        boolean resume = Integer.valueOf(CustomerOrderStatus.PAUSED.getCode()).equals(currentStatus)
                && Integer.valueOf(CustomerOrderStatus.ACTIVE.getCode()).equals(requestedStatus);
        if (!unchanged && !pause && !resume) {
            throw new BadRequestException("订单状态只能在进行中与暂停之间切换，已结束订单不能恢复");
        }
    }

    /**
     * 按金额编辑权限规范化订单金额字段，防止绕过前端直接改金额。
     *
     * @param dto 订单保存参数
     * @param existingOrder 已存在订单，新增时传 null
     */
    private void normalizeAmountFields(CustomerOrderSaveDto dto, CustomerOrder existingOrder) {
        if (CustomerOrderAmountPermissionUtil.canEditAmount()) {
            if (dto.getDepositAmount() == null) dto.setDepositAmount(BigDecimal.ZERO);
            if (dto.getBreakfastPrice() == null) dto.setBreakfastPrice(BigDecimal.ZERO);
            if (dto.getLunchDinnerPrice() == null) dto.setLunchDinnerPrice(BigDecimal.ZERO);
            if (dto.getVerifiedAmount() == null) dto.setVerifiedAmount(BigDecimal.ZERO);
            if (dto.getVerifiedCount() == null) dto.setVerifiedCount(0);
            return;
        }
        if (existingOrder == null) {
            dto.setDepositAmount(BigDecimal.ZERO);
            dto.setTotalAmount(BigDecimal.ZERO);
            dto.setFinalAmount(BigDecimal.ZERO);
            dto.setBreakfastPrice(BigDecimal.ZERO);
            dto.setLunchDinnerPrice(BigDecimal.ZERO);
            dto.setVerifiedAmount(BigDecimal.ZERO);
            dto.setVerifiedCount(0);
            return;
        }
        dto.setDepositAmount(existingOrder.getDepositAmount() != null ? existingOrder.getDepositAmount() : BigDecimal.ZERO);
        dto.setTotalAmount(existingOrder.getTotalAmount() != null ? existingOrder.getTotalAmount() : BigDecimal.ZERO);
        dto.setFinalAmount(existingOrder.getFinalAmount() != null ? existingOrder.getFinalAmount() : BigDecimal.ZERO);
        dto.setBreakfastPrice(existingOrder.getBreakfastPrice() != null ? existingOrder.getBreakfastPrice() : BigDecimal.ZERO);
        dto.setLunchDinnerPrice(existingOrder.getLunchDinnerPrice() != null ? existingOrder.getLunchDinnerPrice() : BigDecimal.ZERO);
        dto.setVerifiedAmount(existingOrder.getVerifiedAmount() != null ? existingOrder.getVerifiedAmount() : BigDecimal.ZERO);
        dto.setVerifiedCount(existingOrder.getVerifiedCount() != null ? existingOrder.getVerifiedCount() : 0);
    }

    /**
     * 校验金额字段关系，只有金额字段完成规范化后才执行。
     *
     * @param dto 订单保存参数
     */
    private void validateAmountFields(CustomerOrderSaveDto dto) {
        if (dto.getTotalAmount() == null) {
            throw new BadRequestException("总金额不能为空");
        }
        if (dto.getFinalAmount() == null) {
            throw new BadRequestException("成交金额不能为空");
        }
        if (dto.getFinalAmount().compareTo(dto.getTotalAmount()) > 0) {
            throw new BadRequestException("成交金额不能超过总金额");
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
            dto.setDishRequirements(profile.getDishRequirements());
            dto.setDietaryRestrictions(profile.getDietaryRestrictions());
            dto.setDishRequirementsRaw(profile.getDishRequirementsRaw());
            dto.setDietaryRestrictionsRaw(profile.getDietaryRestrictionsRaw());
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
        dto.setImportedVerifiedCount(order.getImportedVerifiedCount());
        dto.setVerifiedAmount(order.getVerifiedAmount());
        dto.setMealBalance(order.getMealBalance());
        dto.setRemainingCount(order.getRemainingCount());
        dto.setDealTime(order.getDealTime());
        dto.setFirstDeliveryTime(order.getFirstDeliveryTime());
        dto.setStartDate(order.getStartDate());
        dto.setStartMealType(order.getStartMealType());
        dto.setEndDate(order.getEndDate());
        dto.setPauseEffectiveDate(order.getPauseEffectiveDate());
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

    /**
     * 按金额查看权限脱敏订单列表金额字段。
     *
     * @param orders 订单列表
     */
    private void maskAmountFields(List<CustomerOrder> orders) {
        if (CustomerOrderAmountPermissionUtil.canViewAmount() || orders == null || orders.isEmpty()) {
            return;
        }
        for (CustomerOrder order : orders) {
            maskAmountFields(order);
        }
    }

    /**
     * 按金额查看权限脱敏单个订单实体金额字段。
     *
     * @param order 订单实体
     */
    private void maskAmountFields(CustomerOrder order) {
        if (CustomerOrderAmountPermissionUtil.canViewAmount() || order == null) {
            return;
        }
        order.setDepositAmount(null);
        order.setTotalAmount(null);
        order.setFinalAmount(null);
        order.setBreakfastPrice(null);
        order.setLunchDinnerPrice(null);
        order.setVerifiedAmount(null);
        order.setMealBalance(null);
    }

    /**
     * 按金额查看权限脱敏订单详情金额字段。
     *
     * @param dto 订单详情
     */
    private void maskAmountFields(CustomerOrderDetailDto dto) {
        if (CustomerOrderAmountPermissionUtil.canViewAmount() || dto == null) {
            return;
        }
        dto.setDepositAmount(null);
        dto.setTotalAmount(null);
        dto.setFinalAmount(null);
        dto.setBreakfastPrice(null);
        dto.setLunchDinnerPrice(null);
        dto.setVerifiedAmount(null);
        dto.setMealBalance(null);
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

    /**
     * 将订单状态码转换为页面展示名称。
     *
     * @param status 数据库存储状态码
     * @return 状态名称；未知状态返回「未知」
     */
    private String getStatusDesc(Integer status) {
        CustomerOrderStatus orderStatus = CustomerOrderStatus.fromCode(status);
        return orderStatus == null ? "未知" : orderStatus.getDescription();
    }

    /**
     * 将订单餐次转换为页面展示名称，空值保留待通知含义。
     *
     * @param mealType 订单餐次编码
     * @return 餐次名称；空值返回「待通知」
     */
    private String getMealTypeDesc(String mealType) {
        if (mealType == null) return "待通知";
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
