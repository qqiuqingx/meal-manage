package me.zhengjie.modules.customer.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealVerifiedCountDto;
import me.zhengjie.modules.customer.order.domain.dto.OrderVerifiedCountDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.util.OrderStartMealTypeUtil;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRule;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRuleDto;
import me.zhengjie.modules.customer.orderReplaceRule.mapper.CustomerOrderReplaceRuleMapper;
import me.zhengjie.modules.meal.mapper.MealVerificationLogMapper;
import me.zhengjie.modules.meal.service.DishService;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdditionDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdjustmentRequest;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleAdjustmentResult;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerScheduledMealDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsRowDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfilePackageMapper;
import me.zhengjie.modules.customer.profile.service.CustomerProfileService;
import me.zhengjie.modules.customer.profile.util.CustomerMealStatsScheduleUtil;
import me.zhengjie.modules.customer.numberpool.domain.NumberPoolConfig;
import me.zhengjie.modules.customer.numberpool.service.NumberPoolService;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.PageUtil;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 客户档案服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl implements CustomerProfileService {

    private final CustomerProfileMapper profileMapper;
    private final CustomerProfileAddressMapper addressMapper;
    private final ParentPackageMapper parentPackageMapper;
    private final SubPackageMapper subPackageMapper;
    private final CustomerOrderMapper customerOrderMapper;
    private final CustomerProfilePackageMapper profilePackageMapper;
    private final MealVerificationLogMapper verificationLogMapper;
    private final NumberPoolService numberPoolService;
    private final DishService dishService;
    private final CustomerOrderReplaceRuleMapper replaceRuleMapper;
    private final DishMapper dishMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;
    private final CustomerMealScheduleAdditionMapper customerMealScheduleAdditionMapper;
    private final MealPlanService mealPlanService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ORDER_CODE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("M.d");

    @Override
    public PageResult<CustomerProfile> queryAll(CustomerProfileQueryCriteria criteria, Page<Object> page) {
        page.setCountId("findAll_COUNT");
        IPage<CustomerProfile> pageResult = profileMapper.findAll(criteria, page);


        for (CustomerProfile profile : pageResult.getRecords()) {
            fillDefaultAddress(profile);
            fillLatestOrderInfo(profile);
        }
        PageResult<CustomerProfile> result = PageUtil.toPage(pageResult);
        return result;
    }

    @Override
    public PageResult<CustomerMealStatsRowDto> queryMealStats(CustomerMealStatsQueryCriteria criteria, Integer page, Integer size) {
        CustomerProfileQueryCriteria profileCriteria = new CustomerProfileQueryCriteria();
        profileCriteria.setCustomerCode(criteria.getCustomerCode());
        profileCriteria.setCustomerName(criteria.getCustomerName());
        profileCriteria.setPhone(criteria.getPhone());

        List<CustomerProfile> profiles = profileMapper.findAll(profileCriteria);
        if (profiles == null || profiles.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L);
        }

        profiles.sort(Comparator.comparing(CustomerProfile::getCustomerCode, Comparator.nullsLast(String::compareTo)));
        List<Long> customerIds = profiles.stream().map(CustomerProfile::getId).collect(Collectors.toList());

        Map<Long, List<CustomerProfileAddress>> addressMap = addressMapper.selectList(
                new QueryWrapper<CustomerProfileAddress>().in("customer_id", customerIds).orderByAsc("address_type", "id")
        ).stream().collect(Collectors.groupingBy(CustomerProfileAddress::getCustomerId));

        LocalDate startedBeforeDate = parseStatsMonthExclusiveEnd(criteria.getStatsMonth());
        List<CustomerOrder> activeOrders = customerOrderMapper.findActiveOrdersByCustomerIds(customerIds, startedBeforeDate);
        if (activeOrders == null || activeOrders.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L);
        }
        Map<Long, List<CustomerOrder>> ordersByCustomerId = activeOrders.stream()
                .collect(Collectors.groupingBy(CustomerOrder::getCustomerId));

        List<Long> orderIds = activeOrders.stream().map(CustomerOrder::getId).collect(Collectors.toList());
        Map<Long, Map<String, Integer>> verifiedCountMap = buildVerifiedCountMap(orderIds);
        LocalDate statsMonthStart = parseStatsMonthStart(criteria.getStatsMonth());
        Map<Long, Map<String, List<String>>> scheduledMealMap = buildScheduledMealMap(
                customerIds,
                statsMonthStart,
                statsMonthStart.plusMonths(1).minusDays(1)
        );
        Map<Long, Map<String, List<CustomerMealScheduleAddition>>> additionMap = buildManualAdditionMap(
                customerIds,
                statsMonthStart,
                statsMonthStart.plusMonths(1).minusDays(1)
        );

        List<CustomerMealStatsRowDto> rows = new ArrayList<>();
        for (CustomerProfile profile : profiles) {
            List<CustomerOrder> customerOrders = ordersByCustomerId.get(profile.getId());
            if (customerOrders == null || customerOrders.isEmpty()) {
                continue;
            }

            List<CustomerOrder> breakfastOrders = customerOrders.stream()
                    .filter(order -> safeInt(order.getBreakfastCount()) > 0)
                    .collect(Collectors.toList());
            List<CustomerOrder> lunchDinnerOrders = customerOrders.stream()
                    .filter(order -> safeInt(order.getLunchDinnerCount()) > 0)
                    .collect(Collectors.toList());

            List<CustomerMealStatsRowDto> customerRows = new ArrayList<>();
            if (!breakfastOrders.isEmpty()) {
                customerRows.add(buildMealStatsRow(profile, addressMap.get(profile.getId()), breakfastOrders,
                        verifiedCountMap, "BREAKFAST", criteria.getStatsMonth()));
            }
            if (!lunchDinnerOrders.isEmpty()) {
                customerRows.add(buildMealStatsRow(profile, addressMap.get(profile.getId()), lunchDinnerOrders,
                        verifiedCountMap, "LUNCH_DINNER", criteria.getStatsMonth()));
            }
            List<CustomerMealStatsScheduleUtil.ScheduleDay> customerScheduleDays = mergeScheduleDays(customerRows);
            applyBaseAndExcludedMealTypes(customerScheduleDays, customerRows, profile.getExcludedDates());
            applyManualAdditions(customerScheduleDays, additionMap.get(profile.getId()));
            applyScheduledMealTypes(customerScheduleDays, scheduledMealMap.get(profile.getId()));
            for (CustomerMealStatsRowDto row : customerRows) {
                row.setCustomerScheduleDays(customerScheduleDays);
            }

            for (int i = 0; i < customerRows.size(); i++) {
                CustomerMealStatsRowDto row = customerRows.get(i);
                row.setFirstRowInGroup(i == 0);
                row.setGroupRowSpan(i == 0 ? customerRows.size() : 0);
            }
            rows.addAll(customerRows);
        }

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        int fromIndex = Math.min((safePage - 1) * safeSize, rows.size());
        int toIndex = Math.min(fromIndex + safeSize, rows.size());

        List<CustomerMealStatsRowDto> pageRows = new ArrayList<>(rows.subList(fromIndex, toIndex));
        resetRowGroupSpan(pageRows);
        return new PageResult<>(pageRows, rows.size());
    }

    /**
     * 保存客户排餐日历调整，取消餐次写入排除日期，人工新增餐次写入订单级新增表。
     *
     * @param request 调整请求
     * @return 调整结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerMealScheduleAdjustmentResult saveMealScheduleAdjustments(CustomerMealScheduleAdjustmentRequest request) {
        long startTime = System.currentTimeMillis();
        if (request == null || request.getCustomerId() == null) {
            log.warn("客户排餐日历调整失败 - 请求为空或客户ID为空");
            throw new BadRequestException("客户ID不能为空");
        }
        int requestExcludedCount = request.getExcludedDates() == null ? 0 : request.getExcludedDates().size();
        int requestAdditionCount = request.getAdditions() == null ? 0 : request.getAdditions().size();
        log.info("开始保存客户排餐日历调整 - 客户ID: {}, 请求排除日期项: {}, 请求人工新增项: {}",
                request.getCustomerId(), requestExcludedCount, requestAdditionCount);

        CustomerProfile profile = profileMapper.selectById(request.getCustomerId());
        if (profile == null) {
            log.warn("客户排餐日历调整失败 - 客户不存在，客户ID: {}", request.getCustomerId());
            throw new BadRequestException("客户不存在");
        }
        log.info("客户排餐日历调整客户信息 - 客户ID: {}, 客户编号: {}, 客户姓名: {}",
                profile.getId(), profile.getCustomerCode(), profile.getCustomerName());

        Set<String> oldExcludedKeys = buildExcludedKeys(profile.getExcludedDates());
        List<ExcludedDateDto> normalizedExcludedDates = normalizeExcludedDates(request.getExcludedDates());
        Set<String> normalizedExcludedKeys = buildExcludedKeys(normalizedExcludedDates);
        log.info("客户排餐日历调整排除日期标准化完成 - 客户ID: {}, 原排除餐次数: {}, 新排除餐次数: {}, 新增排除餐次: {}",
                profile.getId(), oldExcludedKeys.size(), normalizedExcludedKeys.size(),
                diffKeys(normalizedExcludedKeys, oldExcludedKeys));

        int deletedPlanCount = deleteGeneratedMealsForNewExclusions(profile.getId(), profile.getExcludedDates(), normalizedExcludedDates);
        log.info("客户排餐日历调整已生成排餐清理完成 - 客户ID: {}, 删除未核销客户排餐数: {}",
                profile.getId(), deletedPlanCount);

        profile.setExcludedDates(normalizedExcludedDates);
        profile.setUpdateBy(getCurrentUsername());
        profileMapper.updateById(profile);
        log.info("客户排餐日历调整排除日期保存完成 - 客户ID: {}, 排除餐次数: {}",
                profile.getId(), normalizedExcludedKeys.size());

        List<Long> keepAdditionIds = saveManualAdditions(profile.getId(), request.getAdditions(), normalizedExcludedDates);
        int softDeletedAdditionCount = customerMealScheduleAdditionMapper.softDeleteMissingByCustomerId(profile.getId(), keepAdditionIds);
        log.info("客户排餐日历调整人工新增同步完成 - 客户ID: {}, 保留人工新增数: {}, 软删除人工新增数: {}, 保留ID: {}",
                profile.getId(), keepAdditionIds.size(), softDeletedAdditionCount, keepAdditionIds);

        CustomerMealScheduleAdjustmentResult result = new CustomerMealScheduleAdjustmentResult();
        result.setCustomerId(profile.getId());
        result.setExcludedMealCount(countExcludedMeals(normalizedExcludedDates));
        result.setAdditionMealCount(keepAdditionIds.size());
        result.setDeletedUnverifiedPlanCount(deletedPlanCount);
        log.info("客户排餐日历调整保存完成 - 客户ID: {}, 客户编号: {}, 排除餐次数: {}, 人工新增餐次数: {}, 删除未核销排餐数: {}, 耗时: {}ms",
                profile.getId(), profile.getCustomerCode(), result.getExcludedMealCount(), result.getAdditionMealCount(),
                result.getDeletedUnverifiedPlanCount(), System.currentTimeMillis() - startTime);
        return result;
    }

    private List<CustomerMealStatsScheduleUtil.ScheduleDay> mergeScheduleDays(List<CustomerMealStatsRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, CustomerMealStatsScheduleUtil.ScheduleDay> dayMap = new java.util.TreeMap<>();
        for (CustomerMealStatsRowDto row : rows) {
            if (row.getScheduleDays() == null) {
                continue;
            }
            for (CustomerMealStatsScheduleUtil.ScheduleDay sourceDay : row.getScheduleDays()) {
                if (sourceDay == null || StringUtils.isBlank(sourceDay.getDate())) {
                    continue;
                }
                CustomerMealStatsScheduleUtil.ScheduleDay targetDay = dayMap.computeIfAbsent(
                        sourceDay.getDate(),
                        CustomerMealStatsScheduleUtil.ScheduleDay::new
                );
                if (sourceDay.getMealTypes() == null) {
                    continue;
                }
                for (String mealType : sourceDay.getMealTypes()) {
                    if (!targetDay.getMealTypes().contains(mealType)) {
                        targetDay.getMealTypes().add(mealType);
                    }
                }
            }
        }
        return new ArrayList<>(dayMap.values());
    }

    private void applyScheduledMealTypes(List<CustomerMealStatsScheduleUtil.ScheduleDay> scheduleDays,
                                         Map<String, List<String>> scheduledMealsByDate) {
        if (scheduleDays == null || scheduleDays.isEmpty() || scheduledMealsByDate == null || scheduledMealsByDate.isEmpty()) {
            return;
        }
        for (CustomerMealStatsScheduleUtil.ScheduleDay day : scheduleDays) {
            List<String> scheduledMealTypes = scheduledMealsByDate.get(day.getDate());
            if (scheduledMealTypes == null || scheduledMealTypes.isEmpty()) {
                continue;
            }
            for (String mealType : scheduledMealTypes) {
                if (day.getMealTypes() != null && day.getMealTypes().contains(mealType)) {
                    day.addScheduledMealType(mealType);
                }
            }
        }
    }

    /**
     * 标记基础应排餐次和被人工排除的餐次。
     */
    private void applyBaseAndExcludedMealTypes(List<CustomerMealStatsScheduleUtil.ScheduleDay> customerScheduleDays,
                                               List<CustomerMealStatsRowDto> rows,
                                               List<ExcludedDateDto> excludedDates) {
        Map<String, CustomerMealStatsScheduleUtil.ScheduleDay> dayMap = customerScheduleDays.stream()
                .collect(Collectors.toMap(CustomerMealStatsScheduleUtil.ScheduleDay::getDate, day -> day, (left, right) -> left, TreeMap::new));
        for (CustomerMealStatsRowDto row : rows) {
            if (row.getBaseScheduleDays() == null) {
                continue;
            }
            for (CustomerMealStatsScheduleUtil.ScheduleDay baseDay : row.getBaseScheduleDays()) {
                CustomerMealStatsScheduleUtil.ScheduleDay target = dayMap.computeIfAbsent(
                        baseDay.getDate(),
                        CustomerMealStatsScheduleUtil.ScheduleDay::new
                );
                target.addBaseMealTypes(baseDay.getMealTypes());
            }
        }
        Set<String> excludedKeys = buildExcludedKeys(excludedDates);
        for (String key : excludedKeys) {
            String[] parts = key.split("#");
            CustomerMealStatsScheduleUtil.ScheduleDay target = dayMap.computeIfAbsent(
                    parts[0],
                    CustomerMealStatsScheduleUtil.ScheduleDay::new
            );
            target.addExcludedMealType(parts[1]);
        }
        customerScheduleDays.clear();
        customerScheduleDays.addAll(dayMap.values());
    }

    /**
     * 将人工新增餐次合并进日历。
     */
    private void applyManualAdditions(List<CustomerMealStatsScheduleUtil.ScheduleDay> customerScheduleDays,
                                      Map<String, List<CustomerMealScheduleAddition>> additionsByDate) {
        if (additionsByDate == null || additionsByDate.isEmpty()) {
            return;
        }
        Map<String, CustomerMealStatsScheduleUtil.ScheduleDay> dayMap = customerScheduleDays.stream()
                .collect(Collectors.toMap(CustomerMealStatsScheduleUtil.ScheduleDay::getDate, day -> day, (left, right) -> left, TreeMap::new));
        for (Map.Entry<String, List<CustomerMealScheduleAddition>> entry : additionsByDate.entrySet()) {
            CustomerMealStatsScheduleUtil.ScheduleDay target = dayMap.computeIfAbsent(
                    entry.getKey(),
                    CustomerMealStatsScheduleUtil.ScheduleDay::new
            );
            for (CustomerMealScheduleAddition addition : entry.getValue()) {
                target.addAddedMealType(addition.getMealType());
                if (target.getMealTypes() != null && !target.getMealTypes().contains(addition.getMealType())) {
                    target.getMealTypes().add(addition.getMealType());
                }
            }
        }
        customerScheduleDays.clear();
        customerScheduleDays.addAll(dayMap.values());
    }

    private Map<Long, Map<String, List<String>>> buildScheduledMealMap(List<Long> customerIds, LocalDate monthStart, LocalDate monthEnd) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CustomerScheduledMealDto> scheduledMeals = mealPlanCustomerMapper.selectScheduledMealsByCustomerIdsAndDateRange(
                customerIds, monthStart, monthEnd);
        if (scheduledMeals == null || scheduledMeals.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<String, List<String>>> result = new HashMap<>();
        for (CustomerScheduledMealDto scheduledMeal : scheduledMeals) {
            if (scheduledMeal == null || scheduledMeal.getCustomerId() == null
                    || scheduledMeal.getRecordDate() == null || StringUtils.isBlank(scheduledMeal.getMealType())) {
                continue;
            }
            result.computeIfAbsent(scheduledMeal.getCustomerId(), key -> new HashMap<>())
                    .computeIfAbsent(scheduledMeal.getRecordDate().toString(), key -> new ArrayList<>());
            List<String> mealTypes = result.get(scheduledMeal.getCustomerId()).get(scheduledMeal.getRecordDate().toString());
            if (!mealTypes.contains(scheduledMeal.getMealType())) {
                mealTypes.add(scheduledMeal.getMealType());
            }
        }
        return result;
    }

    /**
     * 查询客户月份内的人工新增餐次。
     */
    private Map<Long, Map<String, List<CustomerMealScheduleAddition>>> buildManualAdditionMap(List<Long> customerIds,
                                                                                             LocalDate monthStart,
                                                                                             LocalDate monthEnd) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CustomerMealScheduleAddition> additions =
                customerMealScheduleAdditionMapper.selectActiveByCustomerIdsAndDateRange(customerIds, monthStart, monthEnd);
        if (additions == null || additions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<String, List<CustomerMealScheduleAddition>>> result = new HashMap<>();
        for (CustomerMealScheduleAddition addition : additions) {
            if (addition == null || addition.getCustomerId() == null || addition.getRecordDate() == null) {
                continue;
            }
            result.computeIfAbsent(addition.getCustomerId(), key -> new HashMap<>())
                    .computeIfAbsent(addition.getRecordDate().toString(), key -> new ArrayList<>())
                    .add(addition);
        }
        return result;
    }

    private LocalDate parseStatsMonthStart(String statsMonth) {
        if (StringUtils.isBlank(statsMonth)) {
            return LocalDate.now().withDayOfMonth(1);
        }
        try {
            return LocalDate.parse(statsMonth + "-01", DATE_FORMATTER);
        } catch (Exception e) {
            throw new BadRequestException("统计月份格式错误，请使用 yyyy-MM 格式");
        }
    }

    private LocalDate parseStatsMonthExclusiveEnd(String statsMonth) {
        if (StringUtils.isBlank(statsMonth)) {
            return null;
        }
        try {
            return LocalDate.parse(statsMonth + "-01", DATE_FORMATTER).plusMonths(1);
        } catch (Exception e) {
            throw new BadRequestException("统计月份格式错误，请使用 yyyy-MM 格式");
        }
    }

    @Override
    public CustomerProfileDetailDto getDetail(Long id) {
        CustomerProfile profile = profileMapper.selectByIdWithJson(id);
        if (profile == null) {
            throw new BadRequestException("客户档案不存在");
        }

        List<CustomerProfileAddress> addresses = addressMapper.selectList(
            new QueryWrapper<CustomerProfileAddress>().eq("customer_id", id)
        );

        CustomerProfileDetailDto detail = new CustomerProfileDetailDto();
        detail.setId(profile.getId());
        detail.setCustomerCode(profile.getCustomerCode());
        detail.setCustomerName(profile.getCustomerName());
        detail.setPhone(profile.getPhone());
        detail.setGestationalWeek(profile.getGestationalWeek());
        detail.setAllergyTags(profile.getAllergyTags());
        detail.setExcludedDishIds(profile.getExcludedDishIds());
        detail.setExcludedDishNames(convertDishIdsToNames(profile.getExcludedDishIds()));
        detail.setExcludedDates(profile.getExcludedDates());
        detail.setMedicalRequirements(profile.getMedicalRequirements());
        detail.setSpecialRequirements(profile.getSpecialRequirements());
        detail.setProductionDate(profile.getProductionDate());
        //
        detail.setCreateTime(profile.getCreateTime() != null ? profile.getCreateTime().toLocalDate() : null);
        detail.setUpdateTime(profile.getUpdateTime() != null ? profile.getUpdateTime().toLocalDate() : null);

        List<CustomerProfileDetailDto.AddressDto> addressDtos = addresses.stream()
            .map(addr -> {
                CustomerProfileDetailDto.AddressDto dto = new CustomerProfileDetailDto.AddressDto();
                dto.setAddressType(addr.getAddressType());
                dto.setAddressDetail(addr.getAddressDetail());
                dto.setContactName(addr.getContactName());
                dto.setContactPhone(addr.getContactPhone());
                return dto;
            })
            .collect(Collectors.toList());
        detail.setAddresses(addressDtos);

        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CustomerProfileSaveDto dto) {
        CustomerProfileSaveDto.OrderInfoDto orderInfo = normalizeAndValidate(dto, true);
        String customerCode = resolveCustomerCode(dto.getCustomerCode(), orderInfo.getParentPackageId());

        CustomerProfile profile = new CustomerProfile();
        profile.setCustomerCode(customerCode);
        profile.setCustomerName(dto.getCustomerName());
        profile.setPhone(dto.getPhone());
        profile.setGestationalWeek(dto.getGestationalWeek());
        profile.setAllergyTags(dto.getAllergyTags());
        profile.setExcludedDishIds(dto.getExcludedDishIds());
        profile.setExcludedDates(dto.getExcludedDates());
        profile.setMedicalRequirements(dto.getMedicalRequirements());
        profile.setSpecialRequirements(dto.getSpecialRequirements());
        if (StringUtils.isNotBlank(dto.getProductionDate())) {
            profile.setProductionDate(LocalDate.parse(dto.getProductionDate().substring(0, 10), DATE_FORMATTER));
        }
        //
        profile.setCreateBy(getCurrentUsername());
        profileMapper.insert(profile);

        saveAddresses(profile.getId(), dto.getAddresses());
        saveFirstOrder(profile, orderInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerProfileSaveDto dto) {
        if (dto.getId() == null) {
            throw new BadRequestException("客户ID不能为空");
        }

        normalizeAndValidate(dto, false);

        CustomerProfile profile = profileMapper.selectById(dto.getId());
        if (profile == null) {
            throw new BadRequestException("客户档案不存在");
        }

        profile.setCustomerName(dto.getCustomerName());
        profile.setPhone(dto.getPhone());
        profile.setGestationalWeek(dto.getGestationalWeek());
        profile.setAllergyTags(dto.getAllergyTags());
        profile.setExcludedDishIds(dto.getExcludedDishIds());
        profile.setExcludedDates(dto.getExcludedDates());
        profile.setMedicalRequirements(dto.getMedicalRequirements());
        profile.setSpecialRequirements(dto.getSpecialRequirements());
        if (StringUtils.isNotBlank(dto.getProductionDate())) {
            profile.setProductionDate(LocalDate.parse(dto.getProductionDate().substring(0, 10), DATE_FORMATTER));
        }
        profile.setRemark(dto.getRemark());
        profile.setUpdateBy(getCurrentUsername());
        profileMapper.updateById(profile);

        updateAddresses(profile.getId(), dto.getAddresses());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("请选择要删除的客户");
        }

        // Step 1: 校验有效订单
        List<Map<String, Object>> activeOrderCounts = customerOrderMapper.countActiveOrdersByCustomerIds(ids);
        Map<Long, Integer> activeOrderMap = new HashMap<>();
        for (Map<String, Object> row : activeOrderCounts) {
            Object customerIdObj = row.get("customerId");
            Object countObj = row.get("orderCount");
            if (customerIdObj != null && countObj != null) {
                activeOrderMap.put(((Number) customerIdObj).longValue(), ((Number) countObj).intValue());
            }
        }

        List<String> blockedCustomers = new ArrayList<>();
        for (Long customerId : ids) {
            Integer count = activeOrderMap.getOrDefault(customerId, 0);
            if (count > 0) {
                CustomerProfile profile = profileMapper.selectById(customerId);
                if (profile != null) {
                    blockedCustomers.add(profile.getCustomerName());
                }
            }
        }

        if (!blockedCustomers.isEmpty()) {
            throw new BadRequestException("以下客户存在进行中的订单，无法删除：" + String.join("、", blockedCustomers));
        }

        // Step 2: 级联删除
        // 2a: 删除地址
        addressMapper.delete(new QueryWrapper<me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress>().in("customer_id", ids));
        // 2b: 删除签约记录
        profilePackageMapper.delete(new QueryWrapper<me.zhengjie.modules.customer.profile.domain.CustomerProfilePackage>().in("customer_id", ids));
        // 2c: 删除客户档案
        profileMapper.deleteBatchIds(ids);
    }

    //

    @Override
    public String generateCode(Long parentPackageId) {
        if (parentPackageId == null) {
            throw new BadRequestException("父套餐ID不能为空");
        }

        ParentPackage parent = parentPackageMapper.selectById(parentPackageId);
        if (parent == null) {
            throw new BadRequestException("父套餐不存在");
        }
        if (!Boolean.TRUE.equals(parent.getStatus())) {
            throw new BadRequestException("父套餐已禁用");
        }
//        if (StringUtils.isBlank(parent.getPrefix())) {
//            throw new BadRequestException("父套餐未配置编号前缀");
//        }

        // --- Backward compatibility: pool fields NULL → fall back to sequential allocation ---
        // Phase 5 migration will populate pool fields for all packages.
        // Before migration, packages with NULL pool fields use the original sequential logic.
        if (StringUtils.isBlank(parent.getPoolPrefix())
                || parent.getPoolStart() == null
                || parent.getPoolEnd() == null) {
            return generateCodeSequential(parent);
        }

        // --- Pool-based allocation (per POOL-05, POOL-06, POOL-07, POOL-08) ---
        // Delegates to NumberPoolService which holds SELECT FOR UPDATE on the package row.
        NumberPoolConfig config = new NumberPoolConfig();
        config.setPackageId(parent.getId());
        config.setPoolPrefix(parent.getPoolPrefix());
        config.setPoolStart(parent.getPoolStart());
        config.setPoolEnd(parent.getPoolEnd());

        return numberPoolService.allocate(config);
    }

    /**
     * 解析客户编号：优先使用前端传入值，否则自动生成。
     *
     * 前端传入时校验：
     * 1. 必须以父套餐 poolPrefix 开头
     * 2. 前缀后的数字必须在 [poolStart, poolEnd] 范围内
     * 3. 零填充宽度必须与 resolveCodeWidth() 一致（自动生成时保持一致）
     * 4. 编号必须在数据库中唯一
     *
     * @param manualCode 前端传入手动编号（可能为 null 或 blank）
     * @param parentPackageId 父套餐ID
     * @return 合规的客户编号
     */
    private String resolveCustomerCode(String manualCode, Long parentPackageId) {
        if (StringUtils.isNotBlank(manualCode)) {
            // --- 手动编号校验 ---
            ParentPackage parent = parentPackageMapper.selectById(parentPackageId);
            if (parent == null) {
                throw new BadRequestException("父套餐不存在");
            }
            String poolPrefix = parent.getPoolPrefix();
            if (StringUtils.isBlank(poolPrefix)) {
                throw new BadRequestException("该套餐未配置编号池，无法使用手动编号");
            }
            int poolStart = parent.getPoolStart();
            int poolEnd = parent.getPoolEnd();
            int codeWidth = resolveCodeWidth(poolStart, poolEnd);

            // 校验 1：必须以 poolPrefix 开头
            if (!manualCode.startsWith(poolPrefix)) {
                throw new BadRequestException("客户编号必须以「" + poolPrefix + "」开头，当前编号格式不符合");
            }

            // 校验 2：截取数字部分，校验范围
            String numPart = manualCode.substring(poolPrefix.length());
            if (numPart.length() != codeWidth) {
                throw new BadRequestException("客户编号数字部分必须为" + codeWidth + "位，如「" + poolPrefix + String.format("%0" + codeWidth + "d", poolStart) + "」");
            }
            int seq;
            try {
                seq = Integer.parseInt(numPart);
            } catch (NumberFormatException e) {
                throw new BadRequestException("客户编号数字部分必须为纯数字，如「" + poolPrefix + String.format("%0" + codeWidth + "d", poolStart) + "」");
            }
            if (seq < poolStart || seq > poolEnd) {
                throw new BadRequestException("客户编号必须在「" + poolPrefix + String.format("%0" + codeWidth + "d", poolStart) + "」～「" + poolPrefix + String.format("%0" + codeWidth + "d", poolEnd) + "」范围内");
            }

            // 校验 3：唯一性
            Long existingCount = profileMapper.selectCount(
                new QueryWrapper<CustomerProfile>().eq("customer_code", manualCode)
            );
            if (existingCount > 0) {
                throw new BadRequestException("客户编号「" + manualCode + "」已被占用，请换一个");
            }

            return manualCode;
        }

        // --- 自动生成 ---
        return generateCode(parentPackageId);
    }

    /**
     * 计算编号数字部分的固定宽度，保持与 NumberPoolServiceImpl.buildCode() 一致。
     * 宽度 = max(3, max(len(poolStart), len(poolEnd)))
     */
    private int resolveCodeWidth(int poolStart, int poolEnd) {
        int startWidth = String.valueOf(poolStart).length();
        int endWidth = String.valueOf(poolEnd).length();
        return Math.max(3, Math.max(startWidth, endWidth));
    }

    /**
     * Fallback: sequential allocation without pool (for packages with NULL pool fields).
     * Preserves existing behavior for packages migrated in Phase 5.
     * No locking — only used for packages without pool configuration.
     */
    private String generateCodeSequential(ParentPackage parent) {
        QueryWrapper<CustomerProfile> wrapper = new QueryWrapper<>();
        wrapper.likeRight("customer_code", parent.getPrefix())
            .orderByDesc("customer_code")
            .last("LIMIT 1");

        CustomerProfile lastProfile = profileMapper.selectOne(wrapper);
        int nextNum = 1;
        if (lastProfile != null) {
            String code = lastProfile.getCustomerCode();
            String numPart = code.substring(parent.getPrefix().length());
            try {
                nextNum = Integer.parseInt(numPart) + 1;
            } catch (NumberFormatException e) {
                nextNum = 1;
            }
        }
        return parent.getPrefix() + String.format("%03d", nextNum);
    }

    private CustomerProfileSaveDto.OrderInfoDto normalizeAndValidate(CustomerProfileSaveDto dto, boolean createMode) {
        if (StringUtils.isBlank(dto.getCustomerName())) {
            throw new BadRequestException("客户姓名不能为空");
        }
        if (StringUtils.isBlank(dto.getPhone())) {
            throw new BadRequestException("手机号不能为空");
        }
        if (!dto.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BadRequestException("手机号格式不正确");
        }
        if (dto.getGestationalWeek() != null && dto.getGestationalWeek() <= 0) {
            throw new BadRequestException("孕周必须为正整数");
        }
        if (dto.getAddresses() == null || dto.getAddresses().isEmpty()) {
            throw new BadRequestException("地址信息不能为空");
        }

        boolean hasValidAddress = false;
        Set<String> addressTypes = new HashSet<>();
        for (CustomerProfileSaveDto.AddressDto addr : dto.getAddresses()) {
            if (StringUtils.isNotBlank(addr.getAddressDetail())) {
                hasValidAddress = true;
            }
            if (StringUtils.isNotBlank(addr.getAddressType())) {
                if (addressTypes.contains(addr.getAddressType())) {
                    throw new BadRequestException("地址类型不能重复");
                }
                addressTypes.add(addr.getAddressType());
            }
        }
        if (!hasValidAddress) {
            throw new BadRequestException("至少需要一个有效地址");
        }
        for (CustomerProfileSaveDto.AddressDto addr : dto.getAddresses()) {
            if (StringUtils.isNotBlank(addr.getAddressType())) {
                if (!"DEFAULT".equals(addr.getAddressType())
                    && !"WORKDAY".equals(addr.getAddressType())
                    && !"WEEKEND".equals(addr.getAddressType())) {
                    throw new BadRequestException("地址类型必须是 DEFAULT、WORKDAY 或 WEEKEND");
                }
            }
        }

        CustomerProfileSaveDto.OrderInfoDto validatedOrderInfo = null;
        if (createMode) {
            validatedOrderInfo = normalizeAndValidateOrderInfo(dto.getOrderInfo());
        }

        if (dto.getAllergyTags() != null && !dto.getAllergyTags().isEmpty()) {
            List<String> normalizedTags = dto.getAllergyTags().stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
            dto.setAllergyTags(normalizedTags);
        }

        // 验证排除菜品ID列表
        validateExcludedDishes(dto.getExcludedDishIds());

        // 验证排除日期格式
        validateExcludedDates(dto.getExcludedDates());

        return validatedOrderInfo;
    }

    private CustomerProfileSaveDto.OrderInfoDto normalizeAndValidateOrderInfo(CustomerProfileSaveDto.OrderInfoDto orderInfo) {
        if (orderInfo == null) {
            throw new BadRequestException("首单信息不能为空");
        }
        if (orderInfo.getParentPackageId() == null) {
            throw new BadRequestException("首单父套餐不能为空");
        }

        ParentPackage parent = parentPackageMapper.selectById(orderInfo.getParentPackageId());
        if (parent == null) {
            throw new BadRequestException("父套餐不存在");
        }
        if (!Boolean.TRUE.equals(parent.getStatus())) {
            throw new BadRequestException("父套餐已禁用");
        }
//        if (StringUtils.isBlank(parent.getPrefix())) {
//            throw new BadRequestException("父套餐未配置编号前缀");
//        }

        if (orderInfo.getChildPackageId() != null) {
            SubPackage child = subPackageMapper.selectById(orderInfo.getChildPackageId());
            if (child == null) {
                throw new BadRequestException("子套餐不存在");
            }
        }

        if (orderInfo.getBreakfastCount() == null && orderInfo.getLunchDinnerCount() == null) {
            throw new BadRequestException("早餐数与午餐+晚餐数至少填写一个");
        }
        if (StringUtils.isBlank(orderInfo.getStartDate()) ) {
            throw new BadRequestException("首单开始日期不能为空");
        }

        normalizeDishCount(orderInfo.getMainDishCount(), "主菜", false);
        normalizeDishCount(orderInfo.getSideDishCount(), "副菜", false);
        normalizeDishCount(orderInfo.getVegCount(), "素菜", false);
        normalizeDishCount(orderInfo.getRiceCount(), "米饭", true);
        normalizeDishCount(orderInfo.getSoupCount(), "汤", false);



        int breakfastCount = orderInfo.getBreakfastCount() != null ? orderInfo.getBreakfastCount() : 0;
        int lunchDinnerCount = orderInfo.getLunchDinnerCount() != null ? orderInfo.getLunchDinnerCount() : 0;
        if (breakfastCount < 0 || lunchDinnerCount < 0) {
            throw new BadRequestException("首单份数不能为负数");
        }
        int totalCount = breakfastCount + lunchDinnerCount;
        if (totalCount <= 0) {
            throw new BadRequestException("首单份数必须大于0");
        }
        orderInfo.setMealType(OrderStartMealTypeUtil.normalizeOrderMealType(orderInfo.getMealType()));
        orderInfo.setStartMealType(OrderStartMealTypeUtil.normalizeStartMealType(orderInfo.getMealType(), orderInfo.getStartMealType()));
        if (!OrderStartMealTypeUtil.isStartMealTypeAllowed(orderInfo.getMealType(), orderInfo.getStartMealType())) {
            throw new BadRequestException("首单开始餐次与订单餐次类型不匹配");
        }
        orderInfo.setMainDishCount(orderInfo.getMainDishCount() != null ? orderInfo.getMainDishCount() : 0);
        orderInfo.setSideDishCount(orderInfo.getSideDishCount() != null ? orderInfo.getSideDishCount() : 0);
        orderInfo.setVegCount(orderInfo.getVegCount() != null ? orderInfo.getVegCount() : 0);
        orderInfo.setRiceCount(orderInfo.getRiceCount() != null ? orderInfo.getRiceCount() : 1);
        orderInfo.setSoupCount(orderInfo.getSoupCount() != null ? orderInfo.getSoupCount() : 0);
        orderInfo.setTotalCount(totalCount);
        return orderInfo;
    }

    private void normalizeDishCount(Integer count, String label, boolean enforceMaxTen) {
        if (count == null) {
            return;
        }
        if (count < 0) {
            throw new BadRequestException(label + "数量不能为负数");
        }
        if (enforceMaxTen && count > 10) {
            throw new BadRequestException(label + "数量不能超过10份");
        }
    }

    private void saveAddresses(Long customerId, List<CustomerProfileSaveDto.AddressDto> addresses) {
        if (addresses == null) {
            return;
        }

        for (CustomerProfileSaveDto.AddressDto addr : addresses) {
            if (StringUtils.isBlank(addr.getAddressDetail())) {
                continue;
            }
            CustomerProfileAddress entity = new CustomerProfileAddress();
            entity.setCustomerId(customerId);
            entity.setAddressType(addr.getAddressType());
            entity.setAddressDetail(addr.getAddressDetail());
            entity.setContactName(addr.getContactName());
            entity.setContactPhone(addr.getContactPhone());
            addressMapper.insert(entity);
        }
    }

    private void updateAddresses(Long customerId, List<CustomerProfileSaveDto.AddressDto> addresses) {
        addressMapper.delete(new QueryWrapper<CustomerProfileAddress>().eq("customer_id", customerId));
        saveAddresses(customerId, addresses);
    }

    private void saveFirstOrder(CustomerProfile profile, CustomerProfileSaveDto.OrderInfoDto orderInfo) {
        CustomerOrder order = new CustomerOrder();
        order.setCustomerId(profile.getId());
        order.setCustomerCode(profile.getCustomerCode());
        order.setOrderCode(generateFirstOrderCode());
        order.setParentPackageId(orderInfo.getParentPackageId());
        order.setChildPackageId(orderInfo.getChildPackageId());
        order.setDepositAmount(orderInfo.getDepositAmount() != null ? orderInfo.getDepositAmount() : BigDecimal.ZERO);
        order.setTotalAmount(orderInfo.getTotalAmount() != null ? orderInfo.getTotalAmount() : BigDecimal.ZERO);
        order.setFinalAmount(orderInfo.getFinalAmount() != null ? orderInfo.getFinalAmount() : BigDecimal.ZERO);
        order.setBreakfastCount(orderInfo.getBreakfastCount() != null ? orderInfo.getBreakfastCount() : 0);
        order.setLunchDinnerCount(orderInfo.getLunchDinnerCount() != null ? orderInfo.getLunchDinnerCount() : 0);
        order.setTotalCount(orderInfo.getTotalCount());
        order.setBreakfastPrice(orderInfo.getBreakfastPrice() != null ? orderInfo.getBreakfastPrice() : BigDecimal.ZERO);
        order.setLunchDinnerPrice(orderInfo.getLunchDinnerPrice() != null ? orderInfo.getLunchDinnerPrice() : BigDecimal.ZERO);
        order.setVerifiedCount(0);
        order.setVerifiedAmount(BigDecimal.ZERO);
        order.setMealBalance(orderInfo.getFinalAmount() != null ? orderInfo.getFinalAmount() : BigDecimal.ZERO);
        order.setRemainingCount(orderInfo.getTotalCount());
        order.setStartDate(LocalDate.parse(orderInfo.getStartDate(), DATE_FORMATTER));
        order.setStartMealType(orderInfo.getStartMealType());
        if (!StringUtils.isBlank(orderInfo.getEndDate())){
            order.setEndDate(LocalDate.parse(orderInfo.getEndDate(), DATE_FORMATTER));
        }

        order.setFirstDeliveryTime(null);
        order.setDealTime(java.time.LocalDateTime.now());
        order.setStatus(1);
        order.setScheduleMode(orderInfo.getScheduleMode() != null ? orderInfo.getScheduleMode() : "SCHEDULE");
        order.setMealType(orderInfo.getMealType() != null ? orderInfo.getMealType() : "ALL");
        order.setCustomerSource(orderInfo.getCustomerSource());
        order.setDeliveryDates(orderInfo.getDeliveryDates());
        order.setMainDishCount(orderInfo.getMainDishCount() != null ? orderInfo.getMainDishCount() : 0);
        order.setSideDishCount(orderInfo.getSideDishCount() != null ? orderInfo.getSideDishCount() : 0);
        order.setVegCount(orderInfo.getVegCount() != null ? orderInfo.getVegCount() : 0);
        order.setRiceCount(orderInfo.getRiceCount() != null ? orderInfo.getRiceCount() : 1);
        order.setRiceType(orderInfo.getRiceType() != null ? orderInfo.getRiceType() : "白米饭");
        order.setSoupCount(orderInfo.getSoupCount() != null ? orderInfo.getSoupCount() : 0);
        order.setRemark(profile.getRemark());
        order.setCreateBy(getCurrentUsername());
        customerOrderMapper.insert(order);
        saveReplaceRules(order.getId(), orderInfo.getReplaceRules());
    }

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

    private String generateFirstOrderCode() {
        String datePrefix = "ORD" + LocalDate.now().format(ORDER_CODE_DATE_FORMATTER);
        String maxCode = customerOrderMapper.findTodayMaxOrderCode(datePrefix);
        int nextNum = 1;
        if (StringUtils.isNotBlank(maxCode) && maxCode.length() > datePrefix.length()) {
            String numPart = maxCode.substring(datePrefix.length());
            try {
                nextNum = Integer.parseInt(numPart) + 1;
            } catch (NumberFormatException e) {
                nextNum = 1;
            }
        }
        return datePrefix + String.format("%03d", nextNum);
    }

    private void fillDefaultAddress(CustomerProfile profile) {
        List<CustomerProfileAddress> addresses = addressMapper.selectList(
            new QueryWrapper<CustomerProfileAddress>()
                .eq("customer_id", profile.getId())
                .orderByAsc("address_type")
        );

        if (addresses == null) {
            addresses = Collections.emptyList();
        }

        StringBuilder allAddresses = new StringBuilder();
        for (CustomerProfileAddress addr : addresses) {
            if (StringUtils.isNotBlank(addr.getAddressDetail())) {
                if (allAddresses.length() > 0) {
                    allAddresses.append(", ");
                }
                allAddresses.append("[").append(getAddressTypeName(addr.getAddressType())).append("] ")
                          .append(addr.getAddressDetail());
            }
        }
        profile.setDefaultAddress(allAddresses.toString());
    }

    private String getAddressTypeName(String addressType) {
        if ("DEFAULT".equals(addressType)) {
            return "默认";
        } else if ("WORKDAY".equals(addressType)) {
            return "工作日";
        } else if ("WEEKEND".equals(addressType)) {
            return "周末";
        }
        return addressType;
    }

    private Map<Long, Map<String, Integer>> buildVerifiedCountMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderMealVerifiedCountDto> verifiedCounts = customerOrderMapper.sumVerifiedCountByOrderIds(orderIds);
        if (verifiedCounts == null || verifiedCounts.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Map<String, Integer>> result = new HashMap<>();
        for (OrderMealVerifiedCountDto item : verifiedCounts) {
            result.computeIfAbsent(item.getOrderId(), key -> new HashMap<>())
                    .merge(item.getMealType(), safeInt(item.getVerifiedCount()), Integer::sum);
        }
        return result;
    }

    private void resetRowGroupSpan(List<CustomerMealStatsRowDto> rows) {
        int index = 0;
        while (index < rows.size()) {
            CustomerMealStatsRowDto current = rows.get(index);
            int next = index + 1;
            while (next < rows.size() && java.util.Objects.equals(rows.get(next).getCustomerId(), current.getCustomerId())) {
                next++;
            }
            int span = next - index;
            for (int i = index; i < next; i++) {
                CustomerMealStatsRowDto row = rows.get(i);
                row.setFirstRowInGroup(i == index);
                row.setGroupRowSpan(i == index ? span : 0);
            }
            index = next;
        }
    }

    private CustomerMealStatsRowDto buildMealStatsRow(CustomerProfile profile,
                                                      List<CustomerProfileAddress> addresses,
                                                      List<CustomerOrder> orders,
                                                      Map<Long, Map<String, Integer>> verifiedCountMap,
                                                      String mealBucket,
                                                      String statsMonth) {
        CustomerMealStatsRowDto row = new CustomerMealStatsRowDto();
        row.setRowKey(profile.getId() + "-" + mealBucket);
        row.setCustomerId(profile.getId());
        row.setOrderId(orders.stream()
                .map(CustomerOrder::getId)
                .filter(Objects::nonNull)
                .min(Long::compareTo)
                .orElse(null));
        row.setCustomerCode(profile.getCustomerCode());
        row.setPhone(profile.getPhone());
        row.setAddressText(buildAddressText(profile, addresses));
        row.setRemarkInfo(defaultString(profile.getRemark()));
        row.setSpecialRequirementText(buildSpecialRequirementText(profile));
        row.setMealBucket(mealBucket);
        row.setScheduleDays(CustomerMealStatsScheduleUtil.buildMonthScheduleDays(orders, profile.getExcludedDates(), statsMonth, mealBucket));
        row.setBaseScheduleDays(CustomerMealStatsScheduleUtil.buildMonthBaseScheduleDays(orders, statsMonth, mealBucket));

        if ("BREAKFAST".equals(mealBucket)) {
            int totalCount = orders.stream().mapToInt(order -> safeInt(order.getBreakfastCount())).sum();
            int verifiedCount = orders.stream()
                    .mapToInt(order -> getVerifiedCount(verifiedCountMap, order.getId(), "BREAKFAST"))
                    .sum();
            row.setSoupLabel("");
            row.setDeliveryInfo("早餐");
            row.setMealCount(totalCount);
            row.setRemainingMealCount(Math.max(totalCount - verifiedCount, 0));
        } else {
            int totalCount = orders.stream().mapToInt(order -> safeInt(order.getLunchDinnerCount())).sum();
            int verifiedCount = orders.stream()
                    .mapToInt(order -> getVerifiedCount(verifiedCountMap, order.getId(), "LUNCH")
                            + getVerifiedCount(verifiedCountMap, order.getId(), "DINNER"))
                    .sum();
            row.setSoupLabel(orders.stream().anyMatch(order -> safeInt(order.getSoupCount()) > 0) ? "含汤" : "");
            row.setDeliveryInfo(buildLunchDinnerDeliveryInfo(orders));
            row.setMealCount(totalCount);
            row.setRemainingMealCount(Math.max(totalCount - verifiedCount, 0));
        }

        row.setPurchaseDateText(formatDisplayDate(orders.stream()
                .map(CustomerOrder::getDealTime)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null)));
        row.setStartDateText(formatDisplayDate(orders.stream()
                .map(CustomerOrder::getStartDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null)));
        return row;
    }

    private String buildAddressText(CustomerProfile profile, List<CustomerProfileAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return "-";
        }
        CustomerProfileAddress address = pickPreferredAddress(addresses);
        String contactName = StringUtils.isNotBlank(address.getContactName()) ? address.getContactName() : profile.getCustomerName();
        String contactPhone = StringUtils.isNotBlank(address.getContactPhone()) ? address.getContactPhone() : profile.getPhone();

        List<String> lines = new ArrayList<>();
        if (StringUtils.isNotBlank(contactName)) {
            lines.add("联系人：" + contactName);
        }
        if (StringUtils.isNotBlank(contactPhone)) {
            lines.add("电话：" + contactPhone);
        }
        if (StringUtils.isNotBlank(address.getAddressDetail())) {
            lines.add("地址：" + address.getAddressDetail());
        }
        return lines.isEmpty() ? "-" : String.join("\n", lines);
    }

    private CustomerProfileAddress pickPreferredAddress(List<CustomerProfileAddress> addresses) {
        return addresses.stream()
                .sorted(Comparator.comparingInt(address -> addressTypePriority(address.getAddressType())))
                .findFirst()
                .orElse(addresses.get(0));
    }

    private int addressTypePriority(String addressType) {
        if ("DEFAULT".equals(addressType)) {
            return 0;
        }
        if ("WORKDAY".equals(addressType)) {
            return 1;
        }
        if ("WEEKEND".equals(addressType)) {
            return 2;
        }
        return 99;
    }

    private String buildSpecialRequirementText(CustomerProfile profile) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(profile.getSpecialRequirements())) {
            parts.add(profile.getSpecialRequirements().trim());
        }
        if (StringUtils.isNotBlank(profile.getMedicalRequirements())) {
            parts.add(profile.getMedicalRequirements().trim());
        }
        return parts.isEmpty() ? "-" : String.join("/", parts);
    }

    private String buildLunchDinnerDeliveryInfo(List<CustomerOrder> orders) {
        List<String> values = orders.stream()
                .map(this::buildOrderDeliveryInfo)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        return values.isEmpty() ? "-" : String.join("；", values);
    }

    private String buildOrderDeliveryInfo(CustomerOrder order) {
        String schedulePart = mapScheduleModeLabel(order.getScheduleMode());
        String mealTypePart = mapLunchDinnerMealTypeLabel(order.getMealType());
        if (StringUtils.isBlank(schedulePart)) {
            return mealTypePart;
        }
        if (StringUtils.isBlank(mealTypePart)) {
            return schedulePart;
        }
        return schedulePart + "/" + mealTypePart;
    }

    private String mapScheduleModeLabel(String scheduleMode) {
        if (StringUtils.isBlank(scheduleMode)) {
            return "";
        }
        switch (scheduleMode) {
            case "DAILY":
                return "每日";
            case "WEEKDAY":
                return "工作日";
            case "WEEKEND":
                return "周末";
            case "SCHEDULE":
                return "指定日期";
            default:
                return scheduleMode;
        }
    }

    private String mapLunchDinnerMealTypeLabel(String mealType) {
        if (StringUtils.isBlank(mealType)) {
            return "";
        }
        switch (mealType) {
            case "ALL":
            case "LUNCH_DINNER":
                return "午餐/晚餐";
            case "LUNCH":
                return "午餐";
            case "DINNER":
                return "晚餐";
            default:
                return mealType;
        }
    }

    private int getVerifiedCount(Map<Long, Map<String, Integer>> verifiedCountMap, Long orderId, String mealType) {
        if (verifiedCountMap == null) {
            return 0;
        }
        Map<String, Integer> orderMap = verifiedCountMap.get(orderId);
        if (orderMap == null) {
            return 0;
        }
        return safeInt(orderMap.get(mealType));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 标准化排除日期，去除空日期、空餐次和重复餐次。
     */
    private List<ExcludedDateDto> normalizeExcludedDates(List<ExcludedDateDto> excludedDates) {
        if (excludedDates == null || excludedDates.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<String>> map = new TreeMap<>();
        for (ExcludedDateDto dto : excludedDates) {
            if (dto == null || StringUtils.isBlank(dto.getDate()) || dto.getMealTypes() == null) {
                log.debug("客户排餐日历调整排除日期标准化跳过空项 - item: {}", dto);
                continue;
            }
            parseLocalDate(dto.getDate(), "排除日期格式错误");
            for (String mealType : dto.getMealTypes()) {
                if (!isSupportedMealType(mealType)) {
                    throw new BadRequestException("不支持的餐次：" + mealType);
                }
                List<String> values = map.computeIfAbsent(dto.getDate(), key -> new ArrayList<>());
                if (!values.contains(mealType)) {
                    values.add(mealType);
                } else {
                    log.debug("客户排餐日历调整排除日期标准化跳过重复餐次 - 日期: {}, 餐次: {}",
                            dto.getDate(), mealType);
                }
            }
        }
        List<ExcludedDateDto> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            ExcludedDateDto dto = new ExcludedDateDto();
            dto.setDate(entry.getKey());
            dto.setMealTypes(entry.getValue());
            result.add(dto);
        }
        return result;
    }

    /**
     * 对新增的排除餐次执行已生成排餐校验和未核销记录清理。
     */
    private int deleteGeneratedMealsForNewExclusions(Long customerId,
                                                     List<ExcludedDateDto> oldExcludedDates,
                                                     List<ExcludedDateDto> newExcludedDates) {
        Set<String> oldKeys = buildExcludedKeys(oldExcludedDates);
        Set<String> newKeys = buildExcludedKeys(newExcludedDates);
        int deletedCount = 0;
        log.info("客户排餐日历调整开始校验新增排除餐次 - 客户ID: {}, 原排除餐次: {}, 新排除餐次: {}",
                customerId, oldKeys, newKeys);
        for (String key : newKeys) {
            if (oldKeys.contains(key)) {
                log.debug("客户排餐日历调整跳过已存在排除餐次 - 客户ID: {}, key: {}", customerId, key);
                continue;
            }
            String[] parts = key.split("#");
            log.info("客户排餐日历调整新增排除餐次，开始清理已生成排餐 - 客户ID: {}, 日期: {}, 餐次: {}",
                    customerId, parts[0], parts[1]);
            int currentDeleted = mealPlanService.deleteUnverifiedCustomerMealForCalendarAdjustment(customerId, parts[0], parts[1]);
            log.info("客户排餐日历调整新增排除餐次清理完成 - 客户ID: {}, 日期: {}, 餐次: {}, 删除未核销排餐数: {}",
                    customerId, parts[0], parts[1], currentDeleted);
            deletedCount += currentDeleted;
        }
        return deletedCount;
    }

    /**
     * 保存页面保留的人工新增餐次，返回有效记录ID。
     */
    private List<Long> saveManualAdditions(Long customerId,
                                           List<CustomerMealScheduleAdditionDto> additions,
                                           List<ExcludedDateDto> excludedDates) {
        if (additions == null || additions.isEmpty()) {
            log.info("客户排餐日历调整人工新增为空 - 客户ID: {}", customerId);
            return Collections.emptyList();
        }
        Set<String> excludedKeys = buildExcludedKeys(excludedDates);
        List<Long> keepIds = new ArrayList<>();
        log.info("客户排餐日历调整开始同步人工新增 - 客户ID: {}, 请求人工新增数: {}, 排除餐次: {}",
                customerId, additions.size(), excludedKeys);
        for (CustomerMealScheduleAdditionDto dto : additions) {
            if (dto == null) {
                log.debug("客户排餐日历调整人工新增跳过空项 - 客户ID: {}", customerId);
                continue;
            }
            if (!isSupportedMealType(dto.getMealType())) {
                log.warn("客户排餐日历调整人工新增餐次不支持 - 客户ID: {}, 订单ID: {}, 日期: {}, 餐次: {}",
                        customerId, dto.getOrderId(), dto.getDate(), dto.getMealType());
                throw new BadRequestException("不支持的餐次：" + dto.getMealType());
            }
            LocalDate recordDate = parseLocalDate(dto.getDate(), "人工新增日期格式错误");
            if (excludedKeys.contains(recordDate + "#" + dto.getMealType())) {
                log.info("客户排餐日历调整人工新增被排除日期覆盖，跳过保存 - 客户ID: {}, 订单ID: {}, 日期: {}, 餐次: {}",
                        customerId, dto.getOrderId(), recordDate, dto.getMealType());
                continue;
            }
            log.info("客户排餐日历调整人工新增校验开始 - 客户ID: {}, 订单ID: {}, 日期: {}, 餐次: {}",
                    customerId, dto.getOrderId(), recordDate, dto.getMealType());
            validateManualAdditionOrder(customerId, dto.getOrderId(), recordDate, dto.getMealType());
            CustomerMealScheduleAddition existing = customerMealScheduleAdditionMapper
                    .selectActiveByOrderDateMeal(dto.getOrderId(), recordDate, dto.getMealType());
            if (existing == null) {
                existing = new CustomerMealScheduleAddition();
                existing.setCustomerId(customerId);
                existing.setOrderId(dto.getOrderId());
                existing.setRecordDate(recordDate);
                existing.setMealType(dto.getMealType());
                existing.setRemark(dto.getRemark());
                existing.setDeleted(false);
                existing.setCreateBy(getCurrentUsername());
                customerMealScheduleAdditionMapper.insert(existing);
                log.info("客户排餐日历调整人工新增创建完成 - 客户ID: {}, 订单ID: {}, 日期: {}, 餐次: {}, 新增ID: {}",
                        customerId, dto.getOrderId(), recordDate, dto.getMealType(), existing.getId());
            } else {
                existing.setRemark(dto.getRemark());
                existing.setUpdateBy(getCurrentUsername());
                customerMealScheduleAdditionMapper.updateById(existing);
                log.info("客户排餐日历调整人工新增更新完成 - 客户ID: {}, 订单ID: {}, 日期: {}, 餐次: {}, 记录ID: {}",
                        customerId, dto.getOrderId(), recordDate, dto.getMealType(), existing.getId());
            }
            keepIds.add(existing.getId());
        }
        return keepIds;
    }

    /**
     * 校验人工新增绑定订单是否属于当前客户且餐次类型可用。
     */
    private void validateManualAdditionOrder(Long customerId, Long orderId, LocalDate recordDate, String mealType) {
        if (orderId == null) {
            log.warn("客户排餐日历调整人工新增校验失败 - 客户ID: {}, 日期: {}, 餐次: {}, 原因: 订单ID为空",
                    customerId, recordDate, mealType);
            throw new BadRequestException("人工新增餐次必须选择订单");
        }
        CustomerOrder order = customerOrderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getCustomerId(), customerId)) {
            log.warn("客户排餐日历调整人工新增校验失败 - 客户ID: {}, 订单ID: {}, 日期: {}, 餐次: {}, 原因: 订单不存在或不属于当前客户",
                    customerId, orderId, recordDate, mealType);
            throw new BadRequestException("人工新增餐次选择的订单不存在或不属于当前客户");
        }
        if (order.getStatus() == null || order.getStatus() != 1) {
            log.warn("客户排餐日历调整人工新增校验失败 - 客户ID: {}, 订单ID: {}, 日期: {}, 餐次: {}, 订单状态: {}, 原因: 非进行中订单",
                    customerId, orderId, recordDate, mealType, order.getStatus());
            throw new BadRequestException("人工新增餐次只能选择进行中的订单");
        }
        if (!customerOrderContainsMealType(order, mealType)) {
            log.warn("客户排餐日历调整人工新增校验失败 - 客户ID: {}, 订单ID: {}, 日期: {}, 餐次: {}, 订单餐次: {}, 早餐数: {}, 午晚餐数: {}, 原因: 餐次类型不匹配",
                    customerId, orderId, recordDate, mealType, order.getMealType(), order.getBreakfastCount(), order.getLunchDinnerCount());
            throw new BadRequestException("人工新增餐次与订单餐次类型不匹配");
        }
        String startMealType = OrderStartMealTypeUtil.normalizeStartMealType(order.getMealType(), order.getStartMealType());
        if (!OrderStartMealTypeUtil.hasStartedForMeal(order.getStartDate(), startMealType, recordDate, mealType)) {
            log.warn("客户排餐日历调整人工新增校验失败 - 客户ID: {}, 订单ID: {}, 日期: {}, 餐次: {}, 开始日期: {}, 开始餐次: {}, 原因: 早于订单开始餐次",
                    customerId, orderId, recordDate, mealType, order.getStartDate(), startMealType);
            throw new BadRequestException("人工新增餐次早于订单开始餐次");
        }
        log.info("客户排餐日历调整人工新增校验通过 - 客户ID: {}, 订单ID: {}, 日期: {}, 餐次: {}, 订单餐次: {}, 开始日期: {}, 开始餐次: {}",
                customerId, orderId, recordDate, mealType, order.getMealType(), order.getStartDate(), startMealType);
    }

    /**
     * 判断订单是否包含指定餐次。
     */
    private boolean customerOrderContainsMealType(CustomerOrder order, String mealType) {
        String orderMealType = OrderStartMealTypeUtil.normalizeOrderMealType(order.getMealType());
        if ("BREAKFAST".equals(mealType)) {
            return "ALL".equals(orderMealType) && safeInt(order.getBreakfastCount()) > 0;
        }
        if ("LUNCH".equals(mealType)) {
            return ("ALL".equals(orderMealType) || "LUNCH_DINNER".equals(orderMealType) || "LUNCH".equals(orderMealType))
                    && safeInt(order.getLunchDinnerCount()) > 0;
        }
        if ("DINNER".equals(mealType)) {
            return ("ALL".equals(orderMealType) || "LUNCH_DINNER".equals(orderMealType) || "DINNER".equals(orderMealType))
                    && safeInt(order.getLunchDinnerCount()) > 0;
        }
        return false;
    }

    /**
     * 构造排除日期键集合。
     */
    private Set<String> buildExcludedKeys(List<ExcludedDateDto> excludedDates) {
        Set<String> keys = new HashSet<>();
        if (excludedDates == null) {
            return keys;
        }
        for (ExcludedDateDto dto : excludedDates) {
            if (dto == null || StringUtils.isBlank(dto.getDate()) || dto.getMealTypes() == null) {
                continue;
            }
            for (String mealType : dto.getMealTypes()) {
                keys.add(dto.getDate() + "#" + mealType);
            }
        }
        return keys;
    }

    /**
     * 统计排除日期餐次数。
     */
    private int countExcludedMeals(List<ExcludedDateDto> excludedDates) {
        return buildExcludedKeys(excludedDates).size();
    }

    /**
     * 计算左侧集合相对右侧集合新增的键，用于调整日志输出。
     */
    private Set<String> diffKeys(Set<String> left, Set<String> right) {
        Set<String> diff = new HashSet<>();
        if (left == null || left.isEmpty()) {
            return diff;
        }
        for (String key : left) {
            if (right == null || !right.contains(key)) {
                diff.add(key);
            }
        }
        return diff;
    }

    /**
     * 校验餐次是否支持。
     */
    private boolean isSupportedMealType(String mealType) {
        return "BREAKFAST".equals(mealType) || "LUNCH".equals(mealType) || "DINNER".equals(mealType);
    }

    /**
     * 解析 yyyy-MM-dd 日期。
     */
    private LocalDate parseLocalDate(String value, String message) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new BadRequestException(message);
        }
    }

    private String defaultString(String value) {
        return StringUtils.isBlank(value) ? "-" : value.trim();
    }

    private String formatDisplayDate(LocalDateTime value) {
        return value == null ? "" : value.toLocalDate().format(DISPLAY_DATE_FORMATTER);
    }

    private String formatDisplayDate(LocalDate value) {
        return value == null ? "" : value.format(DISPLAY_DATE_FORMATTER);
    }

    /**
     * 验证排除日期格式
     */
    private void validateExcludedDates(List<me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto> excludedDates) {
        if (excludedDates == null || excludedDates.isEmpty()) {
            return;  // 字段可选，null或空列表合法
        }

        Set<String> validMealTypes = new HashSet<>(Arrays.asList("BREAKFAST", "LUNCH", "DINNER"));

        for (me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto dto : excludedDates) {
            if (dto == null) {
                throw new BadRequestException("排除日期列表不能包含空项");
            }

            // 校验日期格式
            try {
                LocalDate.parse(dto.getDate());  // 必须为 yyyy-MM-dd
            } catch (Exception e) {
                throw new BadRequestException("排除日期格式错误: " + dto.getDate());
            }

            // 校验餐次列表
            if (dto.getMealTypes() == null || dto.getMealTypes().isEmpty()) {
                throw new BadRequestException("排除日期必须指定至少一个餐次");
            }

            // 校验餐次值
            for (String mealType : dto.getMealTypes()) {
                if (!validMealTypes.contains(mealType)) {
                    throw new BadRequestException("无效的餐次类型: " + mealType);
                }
            }
        }
    }

    /**
     * 验证排除菜品ID列表
     */
    private void validateExcludedDishes(List<Integer> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return; // 空列表是允许的
        }

        // 去重检查
        Set<Integer> uniqueIds = new HashSet<>(dishIds);
        if (uniqueIds.size() != dishIds.size()) {
            throw new BadRequestException("排除菜品ID列表包含重复值");
        }

        // 检查菜品ID是否有效（正数）
        for (Integer dishId : dishIds) {
            if (dishId == null || dishId <= 0) {
                throw new BadRequestException("排除菜品ID必须为正整数");
            }
        }
    }

    private void fillLatestOrderInfo(CustomerProfile profile) {
        List<CustomerOrder> activeOrders = customerOrderMapper.findActiveOrdersByCustomerId(profile.getId());
        if (activeOrders != null && !activeOrders.isEmpty()) {
            // 汇总所有有效订单的总餐数
            int totalBreakfast = 0;
            int totalLunchDinner = 0;
            for (CustomerOrder order : activeOrders) {
                int breakfastCount = order.getBreakfastCount() != null ? order.getBreakfastCount() : 0;
                int lunchDinnerCount = order.getLunchDinnerCount() != null ? order.getLunchDinnerCount() : 0;
                totalBreakfast += breakfastCount;
                totalLunchDinner += lunchDinnerCount;
            }
            profile.setBreakfastCount(totalBreakfast);
            profile.setLunchDinnerCount(totalLunchDinner);

            // 汇总所有有效订单的核销数据
            int breakfastVerified = 0;
            int lunchDinnerVerified = 0;
            for (CustomerOrder order : activeOrders) {
                List<OrderVerifiedCountDto> verifiedList = customerOrderMapper.sumVerifiedCountByOrderId(order.getId());
                if (verifiedList != null) {
                    for (OrderVerifiedCountDto item : verifiedList) {
                        String mealType = item.getMealType();
                        int verifiedCount = item.getVerifiedCount() != null ? item.getVerifiedCount() : 0;
                        if ("BREAKFAST".equals(mealType)) {
                            breakfastVerified += verifiedCount;
                        } else if ("LUNCH".equals(mealType) || "DINNER".equals(mealType)) {
                            lunchDinnerVerified += verifiedCount;
                        }
                    }
                }
            }
            profile.setRemainingBreakfastCount(Math.max(totalBreakfast - breakfastVerified, 0));
            profile.setRemainingLunchDinnerCount(Math.max(totalLunchDinner - lunchDinnerVerified, 0));

            // 填充送餐模式（从最新订单获取）
            CustomerOrder latestOrder = customerOrderMapper.findLatestByCustomerId(profile.getId());
            if (latestOrder != null && latestOrder.getScheduleMode() != null) {
                profile.setScheduleMode(mapScheduleModeToChinese(latestOrder.getScheduleMode()));
            } else {
                profile.setScheduleMode("-");
            }
        } else {
            profile.setScheduleMode("-");
        }
    }

    /**
     * 映射排餐模式为中文标签
     * @param scheduleMode 排餐模式代码（SCHEDULE/DAILY/WEEKEND/WEEKDAY）
     * @return 中文标签
     */
    private String mapScheduleModeToChinese(String scheduleMode) {
        if (scheduleMode == null) {
            return "-";
        }
        switch (scheduleMode) {
            case "SCHEDULE": return "指定日期";
            case "DAILY": return "每天";
            case "WEEKEND": return "周末";
            case "WEEKDAY": return "工作日";
            default: return "-";
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
     * 将菜品ID列表转换为菜品名称列表
     * @param dishIds 排除菜品ID列表
     * @return 排除菜品名称列表
     */
    private List<String> convertDishIdsToNames(List<Integer> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return null;
        }
        List<me.zhengjie.modules.meal.domain.Dish> dishes = dishService.listByIds(dishIds);
        if (dishes == null || dishes.isEmpty()) {
            return null;
        }
        Map<Integer, String> dishNameMap = dishes.stream()
            .collect(Collectors.toMap(
                me.zhengjie.modules.meal.domain.Dish::getId,
                me.zhengjie.modules.meal.domain.Dish::getName,
                (existing, replacement) -> existing
            ));
        return dishIds.stream()
            .map(dishNameMap::get)
            .collect(Collectors.toList());
    }
}
