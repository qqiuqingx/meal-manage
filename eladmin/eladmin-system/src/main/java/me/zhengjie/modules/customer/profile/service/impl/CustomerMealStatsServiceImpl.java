package me.zhengjie.modules.customer.profile.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.CustomerOrderStatus;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealVerifiedCountDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.util.OrderStartMealTypeUtil;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.domain.CustomerProfileAddress;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleCellDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsQueryCriteria;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealStatsRowDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarOverrideDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarSaveDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerOrderMealCalendarSaveResult;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerScheduledMealDto;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileAddressMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.customer.profile.service.CustomerMealStatsService;
import me.zhengjie.modules.customer.profile.util.CustomerMealStatsScheduleUtil;
import me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.StringUtils;
import me.zhengjie.utils.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 为客户用餐统计页提供单订单日历查询与事务化保存。
 */
@Service
@RequiredArgsConstructor
public class CustomerMealStatsServiceImpl implements CustomerMealStatsService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM");
    private static final LocalDate EARLIEST_ORDER_CALENDAR_DATE = LocalDate.of(1000, 1, 1);
    private static final List<String> MEAL_TYPES = Arrays.asList("BREAKFAST", "LUNCH", "DINNER");

    private final CustomerOrderMapper customerOrderMapper;
    private final CustomerProfileMapper customerProfileMapper;
    private final CustomerProfileAddressMapper addressMapper;
    private final CustomerMealScheduleAdditionMapper additionMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;
    private final MealPlanService mealPlanService;

    /**
     * 按剩余订单或同月历史导入订单分页，批量补充当前页客户资料、地址和统计。
     *
     * @param criteria 客户编号、姓名、手机号和统计月份筛选
     * @param page 从 1 开始的页码
     * @param size 每页订单数
     * @return 当前页单订单行及符合条件的订单总数
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<CustomerMealStatsRowDto> queryMealStats(CustomerMealStatsQueryCriteria criteria,
                                                              Integer page,
                                                              Integer size) {
        CustomerMealStatsQueryCriteria query = criteria == null ? new CustomerMealStatsQueryCriteria() : criteria;
        YearMonth statsMonth = StringUtils.isBlank(query.getStatsMonth()) ? null : parseMonth(query.getStatsMonth());
        LocalDate monthStartDate = statsMonth == null ? null : statsMonth.atDay(1);
        LocalDate startedBeforeDate = statsMonth == null ? null : statsMonth.plusMonths(1).atDay(1);
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : size;
        Page<CustomerOrder> orderPage = new Page<>(currentPage, pageSize);
        Page<CustomerOrder> pageResult = customerOrderMapper.findMealStatsOrders(query, monthStartDate, startedBeforeDate,
                CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK, orderPage);
        if (pageResult == null) {
            return new PageResult<>(Collections.emptyList(), 0L);
        }
        List<CustomerOrder> orders = pageResult.getRecords();
        if (orders == null || orders.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), pageResult.getTotal());
        }

        List<Long> orderIds = orders.stream().map(CustomerOrder::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Set<Long> customerIds = orders.stream().map(CustomerOrder::getCustomerId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, CustomerProfile> profilesById = new HashMap<>();
        Map<Long, List<CustomerProfileAddress>> addressesByCustomer = new HashMap<>();
        if (!customerIds.isEmpty()) {
            List<CustomerProfile> profiles = customerProfileMapper.findByIds(customerIds);
            if (profiles != null) {
                for (CustomerProfile profile : profiles) {
                    if (profile != null && profile.getId() != null) {
                        profilesById.put(profile.getId(), profile);
                    }
                }
            }
            List<CustomerProfileAddress> addresses = addressMapper.selectList(
                    new QueryWrapper<CustomerProfileAddress>().in("customer_id", customerIds)
                            .orderByAsc("address_type", "id"));
            if (addresses != null) {
                for (CustomerProfileAddress address : addresses) {
                    if (address != null && address.getCustomerId() != null) {
                        addressesByCustomer.computeIfAbsent(address.getCustomerId(), key -> new ArrayList<>()).add(address);
                    }
                }
            }
        }

        Map<Long, Integer> scheduledCounts = countByOrder(
                mealPlanCustomerMapper.countAllScheduledByOrderIds(orderIds));
        Map<Long, Integer> todayUnverifiedCounts = countByOrder(
                mealPlanCustomerMapper.countTodayUnverifiedScheduledByOrderIds(orderIds, LocalDate.now()));
        List<CustomerMealStatsRowDto> rows = new ArrayList<>(orders.size());
        for (CustomerOrder order : orders) {
            CustomerProfile profile = profilesById.get(order.getCustomerId());
            rows.add(buildMealStatsOrderRow(order, profile,
                    addressesByCustomer.getOrDefault(order.getCustomerId(), Collections.emptyList()),
                    scheduledCounts.getOrDefault(order.getId(), 0),
                    todayUnverifiedCounts.getOrDefault(order.getId(), 0)));
        }
        return new PageResult<>(rows, pageResult.getTotal());
    }

    /**
     * 将订单统计查询计数整理为订单主键索引。
     *
     * @param counts 当前页订单的批量统计结果
     * @return 订单ID到统计数量的映射
     */
    private Map<Long, Integer> countByOrder(List<OrderScheduledCountDto> counts) {
        if (counts == null || counts.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (OrderScheduledCountDto count : counts) {
            if (count != null && count.getOrderId() != null) {
                result.put(count.getOrderId(), safeInt(count.getScheduledCount()));
            }
        }
        return result;
    }

    /**
     * 映射当前订单的 24 列业务数据及该客户当前共享资料。
     *
     * @param order 当前订单
     * @param profile 当前客户档案；档案缺失时仍保留订单行
     * @param addresses 当前客户地址列表
     * @param scheduledCount 当前订单全部有效已排结果数
     * @param todayUnverifiedCount 今日成功且未核销结果数
     * @return 单笔订单统计行
     */
    private CustomerMealStatsRowDto buildMealStatsOrderRow(CustomerOrder order,
                                                           CustomerProfile profile,
                                                           List<CustomerProfileAddress> addresses,
                                                           int scheduledCount,
                                                           int todayUnverifiedCount) {
        CustomerMealStatsRowDto row = new CustomerMealStatsRowDto();
        row.setOrderId(order.getId());
        row.setCustomerId(order.getCustomerId());
        row.setOrderCode(order.getOrderCode());
        row.setPhone(profile == null ? null : profile.getPhone());
        row.setAddressText(buildAddressText(profile, addresses));
        row.setCustomerCode(order.getCustomerCode());
        row.setCustomerName(profile == null ? null : profile.getCustomerName());
        row.setSpecialRequirements(profile == null ? null : profile.getSpecialRequirements());
        row.setScheduleMode(order.getScheduleMode());
        row.setScheduleModeText(scheduleModeLabel(order.getScheduleMode()));
        row.setMealType(order.getMealType());
        row.setMealTypeText(mealTypeLabel(order));
        row.setSpecification(specificationLabel(order));
        row.setSoupCount(order.getSoupCount());
        row.setBreakfastCount(order.getBreakfastCount());
        row.setLunchDinnerCount(order.getLunchDinnerCount());
        row.setTotalCount(safeInt(order.getBreakfastCount()) + safeInt(order.getLunchDinnerCount()));
        row.setVerifiedCount(safeInt(order.getVerifiedCount()));
        row.setScheduledCount(scheduledCount);
        row.setRemainingCount(safeInt(order.getRemainingCount()));
        row.setEstimatedRemainingCount(Math.max(safeInt(order.getRemainingCount()) - todayUnverifiedCount, 0));
        row.setStatus(order.getStatus());
        row.setStatusLabel(statusLabel(order.getStatus()));
        row.setDealTime(formatDealTime(order.getDealTime()));
        row.setPostoperativeInfo(profile == null ? null : profile.getPostoperativeInfo());
        row.setDishRequirements(profile == null ? Collections.emptyList() : profile.getDishRequirements());
        row.setDishRequirementsRaw(profile == null ? Collections.emptyList() : profile.getDishRequirementsRaw());
        row.setAllergyTags(profile == null ? Collections.emptyList() : profile.getAllergyTags());
        row.setDietaryRestrictions(profile == null ? Collections.emptyList() : profile.getDietaryRestrictions());
        row.setDietaryRestrictionsRaw(profile == null ? Collections.emptyList() : profile.getDietaryRestrictionsRaw());
        row.setMedicalRequirements(profile == null ? null : profile.getMedicalRequirements());
        row.setCustomMenuImage(order.getCustomMenuImage());
        return row;
    }

    /**
     * 复用客户默认、工作日、周末地址优先级生成多行地址文本。
     */
    private String buildAddressText(CustomerProfile profile, List<CustomerProfileAddress> addresses) {
        if (profile == null || addresses == null || addresses.isEmpty()) {
            return "-";
        }
        CustomerProfileAddress address = addresses.stream()
                .min(Comparator.comparingInt(item -> addressTypePriority(item.getAddressType())))
                .orElse(null);
        if (address == null) {
            return "-";
        }
        List<String> lines = new ArrayList<>();
        String contactName = StringUtils.isNotBlank(address.getContactName())
                ? address.getContactName() : profile.getCustomerName();
        String contactPhone = StringUtils.isNotBlank(address.getContactPhone())
                ? address.getContactPhone() : profile.getPhone();
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

    private String scheduleModeLabel(String scheduleMode) {
        if (StringUtils.isBlank(scheduleMode)) {
            return "-";
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

    private String mealTypeLabel(CustomerOrder order) {
        String mealType = order.getMealType();
        if (StringUtils.isBlank(mealType)) {
            return "待确认";
        }
        switch (mealType) {
            case "ALL":
                boolean breakfast = safeInt(order.getBreakfastCount()) > 0;
                boolean lunchDinner = safeInt(order.getLunchDinnerCount()) > 0;
                return breakfast && lunchDinner ? "早餐、午晚餐" : (breakfast ? "早餐" : (lunchDinner ? "午晚餐" : "待确认"));
            case "LUNCH_DINNER":
                return "午晚餐";
            case "LUNCH":
                return "午餐";
            case "DINNER":
                return "晚餐";
            default:
                return "待确认";
        }
    }

    private String specificationLabel(CustomerOrder order) {
        return "主" + safeInt(order.getMainDishCount()) + " / 副" + safeInt(order.getSideDishCount())
                + " / 素" + safeInt(order.getVegCount());
    }

    /**
     * 使用统一订单状态名称，包含因历史导入记录可见的已完成或已退餐订单。
     *
     * @param status 订单状态码
     * @return 状态名称；未知状态显示不可排餐
     */
    private String statusLabel(Integer status) {
        CustomerOrderStatus orderStatus = CustomerOrderStatus.fromCode(status);
        return orderStatus == null ? "不可排餐" : orderStatus.getDescription();
    }

    private String formatDealTime(LocalDateTime dealTime) {
        return dealTime == null ? null : dealTime.format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
    }

    /**
     * 查询单笔订单指定月份的排餐日历及并发修订标记。
     *
     * @param orderId 订单ID
     * @param statsMonth 查询月份，格式 yyyy-MM
     * @return 当前订单的日历、可编辑覆盖、只读历史导入数量、进度和编辑状态
     */
    @Override
    @Transactional(readOnly = true)
    public CustomerOrderMealCalendarDto getOrderCalendar(Long orderId, String statsMonth) {
        requireOrderId(orderId);
        YearMonth month = parseMonth(statsMonth);
        CustomerOrder order = customerOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BadRequestException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        CustomerProfile profile = loadProfile(order.getCustomerId(), false);
        CalendarFacts facts = loadCalendarFacts(order, profile, month);
        return buildCalendar(order, profile, month, facts);
    }

    /**
     * 事务化保存单笔订单某月的完整可编辑数量覆盖快照，保留只读历史导入并清理超额未核销排餐。
     *
     * @param orderId URL 指定的订单ID
     * @param request 当前月份、修订标记和完整覆盖快照
     * @return 保存后的修订标记及清理排餐数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerOrderMealCalendarSaveResult saveOrderCalendar(Long orderId,
                                                                 CustomerOrderMealCalendarSaveDto request) {
        requireOrderId(orderId);
        if (request == null || request.getOverrides() == null) {
            throw new BadRequestException("日历保存请求及 overrides 不能为空");
        }
        YearMonth month = parseMonth(request.getStatsMonth());
        if (StringUtils.isBlank(request.getExpectedRevision())) {
            throw new BadRequestException("日历保存缺少修订标记，请重新加载");
        }

        // 锁序固定为订单→客户档案→排餐计划/结果，与订单行内修改、核销及排餐生成保持一致。
        CustomerOrder order = customerOrderMapper.selectInlineUpdateByIdForUpdate(orderId);
        if (order == null) {
            throw new BadRequestException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        CustomerProfile profile = loadProfile(order.getCustomerId(), true);
        validateEditableOrder(order);

        CalendarFacts facts = loadCalendarFacts(order, profile, month);
        String currentRevision = buildRevision(order, profile, month, facts);
        if (!Objects.equals(currentRevision, request.getExpectedRevision())) {
            throw new BadRequestException(HttpStatus.CONFLICT, "排餐日历或订单状态已变化，请重新加载");
        }

        Map<String, CustomerMealScheduleAddition> savedMonthOverrides = indexMonthOverrides(facts.additions, month);
        for (CustomerOrderMealCalendarOverrideDto override : request.getOverrides()) {
            if (override != null) {
                CustomerMealScheduleAddition saved = savedMonthOverrides.get(cellKey(override.getDate(), override.getMealType()));
                if (saved != null && isReadOnlyAddition(order, saved)) {
                    throw new BadRequestException(saved.isImportedHistory() ? "历史导入数量只读，不能通过排餐日历修改"
                            : "该日期数量已归档为只读，请重新加载日历");
                }
            }
        }
        Map<String, CustomerOrderMealCalendarOverrideDto> editableOverrides = validateAndNormalizeOverrides(
                order, month, request.getOverrides());
        Map<String, CustomerOrderMealCalendarOverrideDto> requestedOverrides = new LinkedHashMap<>(editableOverrides);
        for (Map.Entry<String, CustomerMealScheduleAddition> entry : savedMonthOverrides.entrySet()) {
            if (isReadOnlyAddition(order, entry.getValue())) {
                requestedOverrides.put(entry.getKey(), toOverrideDto(entry.getValue()));
            }
        }
        validateGlobalExclusionPreservation(profile, savedMonthOverrides, requestedOverrides);

        List<CustomerMealScheduleAddition> nextAdditions = replaceMonthOverrides(
                order, facts.additions, month, requestedOverrides);
        Map<String, CustomerMealScheduleCellDto> currentCells = buildCellMap(order, profile, month,
                facts.additions, facts.monthProgress);
        Map<String, CustomerMealScheduleCellDto> nextCells = buildCellMap(order, profile, month,
                nextAdditions, facts.monthProgress);
        validateRequestedQuantities(profile, editableOverrides, nextCells);
        validateVerifiedMinimum(currentCells, nextCells, facts.monthProgress);
        validateMonthBudgets(order, currentCells, nextCells, facts);

        int deletedPlanCount = 0;
        if (!sameOverrideSnapshot(savedMonthOverrides, requestedOverrides)) {
            List<Long> keepIds = saveMonthOverrides(order, month, savedMonthOverrides, requestedOverrides);
            additionMapper.softDeleteMissingByOrderIdAndDateRange(
                    orderId, month.atDay(1), month.atEndOfMonth(), keepIds);
            deletedPlanCount = deleteExcessGeneratedPlans(order, nextCells, facts.monthProgress);
        }

        CalendarFacts savedFacts = loadCalendarFacts(order, profile, month);
        CustomerOrderMealCalendarSaveResult result = new CustomerOrderMealCalendarSaveResult();
        result.setOrderId(orderId);
        result.setStatsMonth(month.toString());
        result.setRevision(buildRevision(order, profile, month, savedFacts));
        result.setDeletedUnverifiedPlanCount(deletedPlanCount);
        return result;
    }

    /**
     * 批量读取订单当前数量、真实进度与规划区间前的已使用/预占额度。
     * @param order 查询订单
     * @param profile 当前客户档案
     * @param month 查询月份
     * @return 日历数量、核销、生成事实，已使用额度不会重新分配到未来
     */
    private CalendarFacts loadCalendarFacts(CustomerOrder order, CustomerProfile profile, YearMonth month) {
        CalendarFacts facts = new CalendarFacts();
        if (order.getImportDate() != null) {
            List<OrderScheduledCountDto> occupied = customerOrderMapper.countAllocatedBeforeImport(Collections.singletonList(order.getId()));
            order.setQuantityAllocatedBeforeImport(occupied == null ? 0 : occupied.stream()
                    .filter(value -> Objects.equals(value.getOrderId(), order.getId()))
                    .mapToInt(value -> safeInt(value.getScheduledCount())).sum());
        }
        facts.additions = additionMapper.selectActiveByOrderIdAndDateRange(
                order.getId(), EARLIEST_ORDER_CALENDAR_DATE, month.atEndOfMonth());
        if (facts.additions == null) {
            facts.additions = Collections.emptyList();
        }
        facts.monthProgress = mealPlanCustomerMapper.selectScheduledMealsByOrderIdAndDateRange(
                order.getId(), month.atDay(1), month.atEndOfMonth());
        if (facts.monthProgress == null) {
            facts.monthProgress = Collections.emptyList();
        }
        facts.poolScheduledCounts = mealPlanCustomerMapper
                .countSuccessfulScheduledByOrderIdGroupedByMealType(order.getId());
        if (facts.poolScheduledCounts == null) {
            facts.poolScheduledCounts = Collections.emptyList();
        }
        List<OrderMealVerifiedCountDto> verifiedCounts = customerOrderMapper.sumVerifiedCountByOrderIds(
                Collections.singletonList(order.getId()));
        facts.verifiedCountsByMealType = new HashMap<>();
        if (verifiedCounts != null) {
            for (OrderMealVerifiedCountDto count : verifiedCounts) {
                if (count != null && StringUtils.isNotBlank(count.getMealType())) {
                    facts.verifiedCountsByMealType.merge(count.getMealType(), safeInt(count.getVerifiedCount()), Integer::sum);
                }
            }
        }
        return facts;
    }

    private CustomerOrderMealCalendarDto buildCalendar(CustomerOrder order,
                                                       CustomerProfile profile,
                                                       YearMonth month,
                                                       CalendarFacts facts) {
        List<CustomerMealScheduleAddition> monthOverrides = facts.additions.stream()
                .filter(addition -> addition.getRecordDate() != null
                        && YearMonth.from(addition.getRecordDate()).equals(month))
                .sorted(Comparator.comparing(CustomerMealScheduleAddition::getRecordDate)
                        .thenComparing(CustomerMealScheduleAddition::getMealType))
                .collect(Collectors.toList());
        List<CustomerMealScheduleCellDto> cells = CustomerMealStatsScheduleUtil.buildMonthMealScheduleCells(
                order, profile.getExcludedDates(), month.toString(), facts.additions);
        Map<String, CustomerMealScheduleCellDto> cellsByKey = new LinkedHashMap<>();
        for (CustomerMealScheduleCellDto cell : cells) {
            cellsByKey.put(cellKey(cell.getDate(), cell.getMealType()), cell);
        }
        mergeProgressCells(order, profile, facts.monthProgress, cellsByKey);
        mergeImportedHistoryCells(order, monthOverrides, cellsByKey);
        LocalDate scheduleStart = OrderStartMealTypeUtil.resolveScheduleStartDate(order);
        cellsByKey.values().forEach(cell -> cell.setReadOnly(Boolean.TRUE.equals(cell.getImportedHistory())
                || scheduleStart == null || LocalDate.parse(cell.getDate()).isBefore(scheduleStart)));
        cells = new ArrayList<>(cellsByKey.values());
        cells.sort(Comparator.comparing(CustomerMealScheduleCellDto::getDate)
                .thenComparingInt(cell -> mealTypeOrder(cell.getMealType())));

        CustomerOrderMealCalendarDto response = new CustomerOrderMealCalendarDto();
        response.setOrderId(order.getId());
        response.setCustomerId(order.getCustomerId());
        response.setOrderCode(order.getOrderCode());
        response.setCustomerCode(order.getCustomerCode());
        response.setCustomerName(profile.getCustomerName());
        response.setStatsMonth(month.toString());
        response.setStatus(order.getStatus());
        response.setMealType(order.getMealType());
        response.setStartMealType(OrderStartMealTypeUtil.normalizeStartMealType(order.getMealType(), order.getStartMealType()));
        response.setStartDate(order.getStartDate());
        response.setEndDate(order.getEndDate());
        response.setBreakfastCount(order.getBreakfastCount());
        response.setLunchDinnerCount(order.getLunchDinnerCount());
        response.setAvailableBreakfastCount(availableForPool(order, "BREAKFAST", facts));
        response.setAvailableLunchDinnerCount(availableForPool(order, "LUNCH", facts));
        response.setDefaultIncludesSoup(safeInt(order.getSoupCount()) > 0);
        response.setEditable(isEditableOrder(order));
        response.setReadOnlyReason(readOnlyReason(order));
        response.setRevision(buildRevision(order, profile, month, facts));
        response.setCells(cells);
        response.setOverrides(monthOverrides.stream()
                .filter(addition -> !isReadOnlyAddition(order, addition))
                .map(this::toOverrideDto).collect(Collectors.toList()));
        return response;
    }

    /**
     * 将历史导入数量加入只读日历，保留同格已存在的真实生成与核销进度。
     *
     * @param order 当前订单
     * @param additions 本月已保存的数量记录
     * @param cellsByKey 按日期餐次聚合的日历格
     */
    private void mergeImportedHistoryCells(CustomerOrder order,
                                           List<CustomerMealScheduleAddition> additions,
                                           Map<String, CustomerMealScheduleCellDto> cellsByKey) {
        for (CustomerMealScheduleAddition addition : additions) {
            if (!addition.isImportedHistory()) {
                continue;
            }
            CustomerMealScheduleCellDto previous = cellsByKey.get(cellKey(addition.getRecordDate().toString(), addition.getMealType()));
            CustomerMealScheduleCellDto cell = new CustomerMealScheduleCellDto();
            cell.setOrderId(order.getId());
            cell.setDate(addition.getRecordDate().toString());
            cell.setMealType(addition.getMealType());
            cell.setBaseQuantity(0);
            cell.setQuantity(addition.getQuantity() == null ? 1 : addition.getQuantity());
            cell.setSoupQuantity(addition.getSoupQuantity());
            cell.setDefaultIncludesSoup(safeInt(order.getSoupCount()) > 0);
            cell.setGeneratedCount(previous == null ? 0 : safeInt(previous.getGeneratedCount()));
            cell.setFailedCount(previous == null ? 0 : safeInt(previous.getFailedCount()));
            cell.setVerifiedCount(previous == null ? 0 : safeInt(previous.getVerifiedCount()));
            cell.setManualOverride(false);
            cell.setCustomerExcluded(false);
            cell.setOrderExcluded(false);
            cell.setImportedHistory(true);
            cellsByKey.put(cellKey(cell.getDate(), cell.getMealType()), cell);
        }
    }

    private void mergeProgressCells(CustomerOrder order,
                                    CustomerProfile profile,
                                    List<CustomerScheduledMealDto> progressRows,
                                    Map<String, CustomerMealScheduleCellDto> cellsByKey) {
        for (CustomerScheduledMealDto progress : progressRows) {
            if (progress == null || progress.getRecordDate() == null || StringUtils.isBlank(progress.getMealType())) {
                continue;
            }
            String key = cellKey(progress.getRecordDate().toString(), progress.getMealType());
            CustomerMealScheduleCellDto cell = cellsByKey.get(key);
            if (cell == null) {
                cell = new CustomerMealScheduleCellDto();
                cell.setOrderId(order.getId());
                cell.setDate(progress.getRecordDate().toString());
                cell.setMealType(progress.getMealType());
                cell.setBaseQuantity(0);
                cell.setQuantity(0);
                cell.setSoupQuantity(null);
                cell.setDefaultIncludesSoup(!"BREAKFAST".equals(progress.getMealType())
                        && safeInt(order.getSoupCount()) > 0);
                boolean customerExcluded = profile.isExcluded(progress.getRecordDate(), progress.getMealType());
                cell.setCustomerExcluded(customerExcluded);
                cell.setOrderExcluded(false);
                cell.setManualOverride(false);
                cellsByKey.put(key, cell);
            }
            cell.setGeneratedCount(safeInt(progress.getGeneratedCount()));
            cell.setFailedCount(safeInt(progress.getFailedCount()));
            cell.setVerifiedCount(safeInt(progress.getVerifiedCount()));
        }
    }

    /** 保留导入历史及有效排餐区间之前的覆盖，不能被当前月份快照误删。 */
    private boolean isReadOnlyAddition(CustomerOrder order, CustomerMealScheduleAddition addition) {
        LocalDate start = OrderStartMealTypeUtil.resolveScheduleStartDate(order);
        return addition.isImportedHistory() || start == null || addition.getRecordDate().isBefore(start);
    }

    private CustomerOrderMealCalendarOverrideDto toOverrideDto(CustomerMealScheduleAddition addition) {
        CustomerOrderMealCalendarOverrideDto dto = new CustomerOrderMealCalendarOverrideDto();
        dto.setDate(addition.getRecordDate().toString());
        dto.setMealType(addition.getMealType());
        int quantity = addition.getQuantity() == null ? 1 : addition.getQuantity();
        dto.setQuantity(quantity);
        dto.setSoupQuantity(quantity == 0 ? null : addition.getSoupQuantity());
        dto.setRemark(addition.getRemark());
        return dto;
    }

    private Map<String, CustomerMealScheduleAddition> indexMonthOverrides(List<CustomerMealScheduleAddition> additions,
                                                                           YearMonth month) {
        Map<String, CustomerMealScheduleAddition> result = new LinkedHashMap<>();
        for (CustomerMealScheduleAddition addition : additions) {
            if (addition != null && addition.getRecordDate() != null
                    && YearMonth.from(addition.getRecordDate()).equals(month)) {
                result.put(cellKey(addition.getRecordDate().toString(), addition.getMealType()), addition);
            }
        }
        return result;
    }

    /**
     * 按有效排餐边界校验可编辑覆盖的日期、餐次和数量，不允许伪造历史导入来源标识。
     *
     * @param order 当前订单
     * @param month 编辑月份
     * @param overrides 客户端提交的完整可编辑覆盖
     * @return 按日期餐次索引的规范化覆盖；历史记录由保存流程独立保留
     */
    private Map<String, CustomerOrderMealCalendarOverrideDto> validateAndNormalizeOverrides(
            CustomerOrder order,
            YearMonth month,
            List<CustomerOrderMealCalendarOverrideDto> overrides) {
        Map<String, CustomerOrderMealCalendarOverrideDto> result = new LinkedHashMap<>();
        for (CustomerOrderMealCalendarOverrideDto source : overrides) {
            if (source == null || source.getDate() == null || source.getMealType() == null
                    || source.getQuantity() == null) {
                throw new BadRequestException("排餐日历覆盖项缺少日期、餐次或目标份数");
            }
            LocalDate date = parseDate(source.getDate());
            if (!YearMonth.from(date).equals(month)) {
                throw new BadRequestException("排餐日历覆盖日期不在当前编辑月份");
            }
            if (CustomerMealScheduleAddition.IMPORTED_HISTORY_REMARK.equals(source.getRemark())) {
                throw new BadRequestException("历史导入来源标识不能用于可编辑排餐覆盖");
            }
            String mealType = source.getMealType();
            if (!MEAL_TYPES.contains(mealType)) {
                throw new BadRequestException("不支持的餐次：" + mealType);
            }
            if (!isOrderMealTypeSupported(order, mealType)) {
                throw new BadRequestException("该餐次不属于当前订单购买餐池");
            }
            LocalDate scheduleStartDate = OrderStartMealTypeUtil.resolveScheduleStartDate(order);
            if (scheduleStartDate == null || date.isBefore(scheduleStartDate)
                    || order.getEndDate() != null && date.isAfter(order.getEndDate())) {
                throw new BadRequestException("排餐日期不在当前订单有效期内");
            }
            String startMealType = OrderStartMealTypeUtil.normalizeStartMealType(order.getMealType(), order.getStartMealType());
            if (!OrderStartMealTypeUtil.hasStartedForMeal(order.getStartDate(), startMealType, date, mealType)) {
                throw new BadRequestException("排餐日期早于当前订单的开始餐次");
            }
            int quantity = source.getQuantity();
            if (quantity < 0) {
                throw new BadRequestException("目标份数不能小于0");
            }
            Integer soupQuantity = source.getSoupQuantity();
            if (quantity == 0) {
                soupQuantity = null;
            } else if ("BREAKFAST".equals(mealType)) {
                if (soupQuantity != null && soupQuantity > 0) {
                    throw new BadRequestException("早餐不支持含汤份数调整");
                }
                soupQuantity = null;
            } else if (soupQuantity != null && (soupQuantity < 0 || soupQuantity > quantity)) {
                throw new BadRequestException("含汤份数必须在0到目标份数之间");
            }

            CustomerOrderMealCalendarOverrideDto normalized = new CustomerOrderMealCalendarOverrideDto();
            normalized.setDate(date.toString());
            normalized.setMealType(mealType);
            normalized.setQuantity(quantity);
            normalized.setSoupQuantity(soupQuantity);
            normalized.setRemark(source.getRemark());
            String key = cellKey(normalized.getDate(), mealType);
            if (result.putIfAbsent(key, normalized) != null) {
                throw new BadRequestException("同一订单、日期和餐次不能重复设置覆盖");
            }
        }
        return result;
    }

    private void validateGlobalExclusionPreservation(CustomerProfile profile,
                                                      Map<String, CustomerMealScheduleAddition> savedOverrides,
                                                      Map<String, CustomerOrderMealCalendarOverrideDto> requestedOverrides) {
        for (Map.Entry<String, CustomerMealScheduleAddition> entry : savedOverrides.entrySet()) {
            String[] keyParts = entry.getKey().split("#", 2);
            LocalDate date = LocalDate.parse(keyParts[0]);
            String mealType = keyParts[1];
            if (!profile.isExcluded(date, mealType)) {
                continue;
            }
            CustomerOrderMealCalendarOverrideDto requested = requestedOverrides.get(entry.getKey());
            if (requested == null || !sameOverride(entry.getValue(), requested)) {
                throw new BadRequestException("客户档案已统一停餐，该单元格覆盖必须原样保留；请到客户档案恢复");
            }
        }
        for (Map.Entry<String, CustomerOrderMealCalendarOverrideDto> entry : requestedOverrides.entrySet()) {
            String[] keyParts = entry.getKey().split("#", 2);
            LocalDate date = LocalDate.parse(keyParts[0]);
            if (profile.isExcluded(date, keyParts[1]) && !savedOverrides.containsKey(entry.getKey())) {
                throw new BadRequestException("客户档案已统一停餐，该单元格不能新增订单覆盖");
            }
        }
    }

    private List<CustomerMealScheduleAddition> replaceMonthOverrides(
            CustomerOrder order,
            List<CustomerMealScheduleAddition> savedAdditions,
            YearMonth month,
            Map<String, CustomerOrderMealCalendarOverrideDto> requestedOverrides) {
        List<CustomerMealScheduleAddition> result = savedAdditions.stream()
                .filter(addition -> addition.getRecordDate() == null
                        || !YearMonth.from(addition.getRecordDate()).equals(month))
                .collect(Collectors.toCollection(ArrayList::new));
        for (CustomerOrderMealCalendarOverrideDto requested : requestedOverrides.values()) {
            CustomerMealScheduleAddition addition = new CustomerMealScheduleAddition();
            addition.setCustomerId(order.getCustomerId());
            addition.setOrderId(order.getId());
            addition.setRecordDate(LocalDate.parse(requested.getDate()));
            addition.setMealType(requested.getMealType());
            addition.setQuantity(requested.getQuantity());
            addition.setSoupQuantity(requested.getSoupQuantity());
            addition.setRemark(requested.getRemark());
            result.add(addition);
        }
        return result;
    }

    private Map<String, CustomerMealScheduleCellDto> buildCellMap(CustomerOrder order,
                                                                  CustomerProfile profile,
                                                                  YearMonth month,
                                                                  List<CustomerMealScheduleAddition> additions,
                                                                  List<CustomerScheduledMealDto> progress) {
        Map<String, CustomerMealScheduleCellDto> result = new LinkedHashMap<>();
        List<CustomerMealScheduleCellDto> cells = CustomerMealStatsScheduleUtil.buildMonthMealScheduleCells(
                order, profile.getExcludedDates(), month.toString(), additions);
        for (CustomerMealScheduleCellDto cell : cells) {
            result.put(cellKey(cell.getDate(), cell.getMealType()), cell);
        }
        mergeProgressCells(order, profile, progress, result);
        return result;
    }

    private void validateVerifiedMinimum(Map<String, CustomerMealScheduleCellDto> currentCells,
                                         Map<String, CustomerMealScheduleCellDto> nextCells,
                                         List<CustomerScheduledMealDto> progressRows) {
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(currentCells.keySet());
        allKeys.addAll(nextCells.keySet());
        for (String key : allKeys) {
            CustomerMealScheduleCellDto next = nextCells.get(key);
            int target = next == null ? 0 : safeInt(next.getQuantity());
            CustomerMealScheduleCellDto current = currentCells.get(key);
            int verified = current == null ? 0 : safeInt(current.getVerifiedCount());
            if (verified > target) {
                String[] parts = key.split("#", 2);
                throw new BadRequestException(parts[0] + " " + mealTypeName(parts[1])
                        + "已核销" + verified + "份，计划份数不能调低");
            }
        }
        if (progressRows != null) {
            for (CustomerScheduledMealDto progress : progressRows) {
                if (progress == null || progress.getRecordDate() == null || StringUtils.isBlank(progress.getMealType())) {
                    continue;
                }
                String key = cellKey(progress.getRecordDate().toString(), progress.getMealType());
                int target = nextCells.containsKey(key) ? safeInt(nextCells.get(key).getQuantity()) : 0;
                int verified = safeInt(progress.getVerifiedCount());
                if (verified > target) {
                    throw new BadRequestException(progress.getRecordDate() + " " + mealTypeName(progress.getMealType())
                            + "已核销" + verified + "份，计划份数不能调低");
                }
            }
        }
    }

    private void validateRequestedQuantities(CustomerProfile profile,
                                             Map<String, CustomerOrderMealCalendarOverrideDto> requested,
                                             Map<String, CustomerMealScheduleCellDto> nextCells) {
        for (Map.Entry<String, CustomerOrderMealCalendarOverrideDto> entry : requested.entrySet()) {
            String[] parts = entry.getKey().split("#", 2);
            if (profile.isExcluded(LocalDate.parse(parts[0]), parts[1])) {
                continue;
            }
            CustomerMealScheduleCellDto cell = nextCells.get(entry.getKey());
            if (cell == null) {
                throw new BadRequestException("排餐日期不在当前订单可编辑范围内");
            }
            if (!Objects.equals(cell.getQuantity(), entry.getValue().getQuantity())) {
                throw new BadRequestException("订单 " + cell.getOrderId() + " 本月计划份数超过当前可用餐数");
            }
        }
    }

    private void validateMonthBudgets(CustomerOrder order,
                                      Map<String, CustomerMealScheduleCellDto> currentCells,
                                      Map<String, CustomerMealScheduleCellDto> nextCells,
                                      CalendarFacts facts) {
        for (String poolMealType : Arrays.asList("BREAKFAST", "LUNCH")) {
            int currentQuantity = sumCellPoolQuantity(currentCells, poolMealType);
            int nextQuantity = sumCellPoolQuantity(nextCells, poolMealType);
            int available = availableForPool(order, poolMealType, facts);
            if (nextQuantity > available && nextQuantity > currentQuantity) {
                String poolName = "BREAKFAST".equals(poolMealType) ? "早餐" : "午晚餐";
                throw new BadRequestException("订单 " + order.getId() + " 本月" + poolName + "计划 " + nextQuantity
                        + " 份，超过当前可用餐数 " + available + " 份");
            }
        }
    }

    private int availableForPool(CustomerOrder order, String poolMealType, CalendarFacts facts) {
        boolean breakfastPool = "BREAKFAST".equals(poolMealType);
        int poolTotal = breakfastPool ? safeInt(order.getBreakfastCount()) : safeInt(order.getLunchDinnerCount());
        int actualVerified = breakfastPool
                ? safeInt(facts.verifiedCountsByMealType.get("BREAKFAST"))
                : safeInt(facts.verifiedCountsByMealType.get("LUNCH"))
                        + safeInt(facts.verifiedCountsByMealType.get("DINNER"));
        int monthVerified = sumProgressPool(facts.monthProgress, poolMealType, true);
        int monthGenerated = sumProgressPool(facts.monthProgress, poolMealType, false);
        int successfulScheduled = facts.poolScheduledCounts.stream()
                .filter(count -> count != null && isSamePool(count.getMealType(), poolMealType))
                .mapToInt(count -> safeInt(count.getScheduledCount())).sum();
        int verifiedOutsideMonth = Math.max(actualVerified - monthVerified, 0);
        if (!breakfastPool) {
            verifiedOutsideMonth += safeInt(order.getImportedVerifiedCount());
        }
        int unverifiedScheduledOutsideMonth = Math.max(
                successfulScheduled - actualVerified - Math.max(monthGenerated - monthVerified, 0), 0);
        return Math.max(poolTotal - verifiedOutsideMonth - unverifiedScheduledOutsideMonth, 0);
    }

    private int sumCellPoolQuantity(Map<String, CustomerMealScheduleCellDto> cells, String poolMealType) {
        if (cells == null || cells.isEmpty()) {
            return 0;
        }
        return cells.values().stream()
                .filter(cell -> isSamePool(cell.getMealType(), poolMealType))
                .mapToInt(cell -> safeInt(cell.getQuantity()))
                .sum();
    }

    private int sumProgressPool(List<CustomerScheduledMealDto> progress, String poolMealType, boolean verifiedOnly) {
        if (progress == null || progress.isEmpty()) {
            return 0;
        }
        return progress.stream().filter(Objects::nonNull)
                .filter(row -> isSamePool(row.getMealType(), poolMealType))
                .mapToInt(row -> safeInt(verifiedOnly ? row.getVerifiedCount() : row.getGeneratedCount()))
                .sum();
    }

    private int deleteExcessGeneratedPlans(CustomerOrder order,
                                           Map<String, CustomerMealScheduleCellDto> nextCells,
                                           List<CustomerScheduledMealDto> progressRows) {
        Map<String, CustomerScheduledMealDto> progressByCell = new HashMap<>();
        if (progressRows != null) {
            for (CustomerScheduledMealDto progress : progressRows) {
                if (progress != null && progress.getRecordDate() != null && StringUtils.isNotBlank(progress.getMealType())) {
                    progressByCell.put(cellKey(progress.getRecordDate().toString(), progress.getMealType()), progress);
                }
            }
        }
        List<String> keys = new ArrayList<>(progressByCell.keySet());
        keys.sort(Comparator.comparing((String key) -> key.split("#", 2)[0])
                .thenComparingInt(key -> mealTypeOrder(key.split("#", 2)[1])));
        int deleted = 0;
        for (String key : keys) {
            CustomerScheduledMealDto progress = progressByCell.get(key);
            int existing = safeInt(progress.getGeneratedCount()) + safeInt(progress.getFailedCount());
            CustomerMealScheduleCellDto targetCell = nextCells.get(key);
            int target = targetCell == null ? 0 : safeInt(targetCell.getQuantity());
            if (existing > target) {
                String[] parts = key.split("#", 2);
                deleted += mealPlanService.deleteExcessUnverifiedCustomerServingsForCalendarAdjustment(
                        order.getCustomerId(), order.getId(), parts[0], parts[1], target);
            }
        }
        return deleted;
    }

    private List<Long> saveMonthOverrides(CustomerOrder order,
                                         YearMonth month,
                                         Map<String, CustomerMealScheduleAddition> saved,
                                         Map<String, CustomerOrderMealCalendarOverrideDto> requested) {
        String operator = currentOperator();
        List<Long> keepIds = new ArrayList<>();
        List<Map.Entry<String, CustomerOrderMealCalendarOverrideDto>> entries = new ArrayList<>(requested.entrySet());
        entries.sort(Comparator.comparing((Map.Entry<String, CustomerOrderMealCalendarOverrideDto> entry) ->
                        entry.getValue().getDate())
                .thenComparingInt(entry -> mealTypeOrder(entry.getValue().getMealType())));
        for (Map.Entry<String, CustomerOrderMealCalendarOverrideDto> entry : entries) {
            CustomerOrderMealCalendarOverrideDto dto = entry.getValue();
            LocalDate date = LocalDate.parse(dto.getDate());
            CustomerMealScheduleAddition existing = saved.get(entry.getKey());
            if (existing != null) {
                if (!sameOverride(existing, dto)) {
                    int updated = additionMapper.updateOrderCalendarOverride(existing.getId(), order.getId(),
                            dto.getQuantity(), dto.getSoupQuantity(), dto.getRemark(), operator);
                    if (updated != 1) {
                        throw new BadRequestException(HttpStatus.CONFLICT, "订单日历覆盖已变化，请重新加载");
                    }
                }
                keepIds.add(existing.getId());
                continue;
            }

            CustomerMealScheduleAddition deleted = additionMapper.selectAnyByOrderDateMeal(
                    order.getId(), date, dto.getMealType());
            if (deleted != null) {
                int revived = additionMapper.reviveOrderCalendarOverride(deleted.getId(), order.getId(), date,
                        dto.getMealType(), dto.getQuantity(), dto.getSoupQuantity(), dto.getRemark(), operator);
                if (revived != 1) {
                    throw new BadRequestException(HttpStatus.CONFLICT, "订单日历覆盖已变化，请重新加载");
                }
                keepIds.add(deleted.getId());
                continue;
            }

            CustomerMealScheduleAddition addition = new CustomerMealScheduleAddition();
            addition.setCustomerId(order.getCustomerId());
            addition.setOrderId(order.getId());
            addition.setRecordDate(date);
            addition.setMealType(dto.getMealType());
            addition.setQuantity(dto.getQuantity());
            addition.setSoupQuantity(dto.getSoupQuantity());
            addition.setRemark(dto.getRemark());
            addition.setDeleted(false);
            addition.setCreateBy(operator);
            if (additionMapper.insert(addition) != 1 || addition.getId() == null) {
                throw new BadRequestException(HttpStatus.CONFLICT, "订单日历覆盖保存失败，请重试");
            }
            keepIds.add(addition.getId());
        }
        return keepIds;
    }

    private boolean sameOverrideSnapshot(Map<String, CustomerMealScheduleAddition> saved,
                                         Map<String, CustomerOrderMealCalendarOverrideDto> requested) {
        if (!saved.keySet().equals(requested.keySet())) {
            return false;
        }
        for (String key : saved.keySet()) {
            if (!sameOverride(saved.get(key), requested.get(key))) {
                return false;
            }
        }
        return true;
    }

    private boolean sameOverride(CustomerMealScheduleAddition saved,
                                 CustomerOrderMealCalendarOverrideDto requested) {
        if (saved == null || requested == null) {
            return false;
        }
        int quantity = saved.getQuantity() == null ? 1 : saved.getQuantity();
        Integer soupQuantity = quantity == 0 ? null : saved.getSoupQuantity();
        return quantity == safeInt(requested.getQuantity())
                && Objects.equals(soupQuantity, requested.getSoupQuantity())
                && Objects.equals(saved.getRemark(), requested.getRemark());
    }

    private String buildRevision(CustomerOrder order,
                                 CustomerProfile profile,
                                 YearMonth month,
                                 CalendarFacts facts) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("order", orderRevisionFields(order));
        canonical.put("month", month.toString());
        canonical.put("customerExcluded", canonicalExcludedDates(profile.getExcludedDates()));
        canonical.put("overrides", facts.additions.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(CustomerMealScheduleAddition::getRecordDate,
                                Comparator.nullsLast(LocalDate::compareTo))
                        .thenComparing(CustomerMealScheduleAddition::getMealType,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(CustomerMealScheduleAddition::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::revisionOverrideFields).collect(Collectors.toList()));
        canonical.put("monthProgress", facts.monthProgress.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(CustomerScheduledMealDto::getRecordDate,
                                Comparator.nullsLast(LocalDate::compareTo))
                        .thenComparing(CustomerScheduledMealDto::getMealType,
                                Comparator.nullsLast(String::compareTo)))
                .map(this::revisionProgressFields).collect(Collectors.toList()));
        canonical.put("successfulScheduled", facts.poolScheduledCounts.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(OrderScheduledCountDto::getMealType,
                        Comparator.nullsLast(String::compareTo)))
                .map(count -> Arrays.asList(count.getMealType(), safeInt(count.getScheduledCount())))
                .collect(Collectors.toList()));
        canonical.put("verified", new TreeMap<>(facts.verifiedCountsByMealType));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(JSON.toJSONString(canonical).getBytes(StandardCharsets.UTF_8));
            StringBuilder revision = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                revision.append(String.format("%02x", value & 0xff));
            }
            return revision.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法计算排餐日历修订标记", e);
        }
    }

    private Map<String, Object> orderRevisionFields(CustomerOrder order) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", order.getId());
        fields.put("customerId", order.getCustomerId());
        fields.put("parentPackageId", order.getParentPackageId());
        fields.put("childPackageId", order.getChildPackageId());
        fields.put("status", order.getStatus());
        fields.put("mealType", order.getMealType());
        fields.put("scheduleMode", order.getScheduleMode());
        fields.put("deliveryDates", order.getDeliveryDates());
        fields.put("startDate", order.getStartDate());
        fields.put("importDate", order.getImportDate());
        fields.put("importMonth", order.getImportMonth());
        fields.put("startMealType", order.getStartMealType());
        fields.put("endDate", order.getEndDate());
        fields.put("pauseEffectiveDate", order.getPauseEffectiveDate());
        fields.put("breakfastCount", order.getBreakfastCount());
        fields.put("lunchDinnerCount", order.getLunchDinnerCount());
        fields.put("remainingCount", order.getRemainingCount());
        fields.put("importedVerifiedCount", order.getImportedVerifiedCount());
        fields.put("mainDishCount", order.getMainDishCount());
        fields.put("sideDishCount", order.getSideDishCount());
        fields.put("vegCount", order.getVegCount());
        fields.put("riceCount", order.getRiceCount());
        fields.put("riceType", order.getRiceType());
        fields.put("soupCount", order.getSoupCount());
        return fields;
    }

    private Map<String, Object> revisionOverrideFields(CustomerMealScheduleAddition addition) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", addition.getId());
        fields.put("orderId", addition.getOrderId());
        fields.put("recordDate", addition.getRecordDate());
        fields.put("mealType", addition.getMealType());
        fields.put("quantity", addition.getQuantity());
        fields.put("soupQuantity", addition.getSoupQuantity());
        fields.put("remark", addition.getRemark());
        return fields;
    }

    private List<Map<String, Object>> canonicalExcludedDates(List<ExcludedDateDto> excludedDates) {
        if (excludedDates == null || excludedDates.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        excludedDates.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(ExcludedDateDto::getDate, Comparator.nullsLast(String::compareTo)))
                .forEach(excluded -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", excluded.getDate());
                    item.put("mealTypes", excluded.getMealTypes() == null ? Collections.emptyList()
                            : excluded.getMealTypes().stream().filter(Objects::nonNull).sorted().collect(Collectors.toList()));
                    result.add(item);
                });
        return result;
    }

    private Map<String, Object> revisionProgressFields(CustomerScheduledMealDto progress) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("recordDate", progress.getRecordDate());
        fields.put("mealType", progress.getMealType());
        fields.put("generatedCount", progress.getGeneratedCount());
        fields.put("failedCount", progress.getFailedCount());
        fields.put("verifiedCount", progress.getVerifiedCount());
        return fields;
    }

    private CustomerProfile loadProfile(Long customerId, boolean lock) {
        if (customerId == null) {
            throw new BadRequestException("订单未关联客户档案");
        }
        CustomerProfile profile = lock
                ? customerProfileMapper.selectByIdForInlineUpdate(customerId)
                : customerProfileMapper.selectByIdWithJson(customerId);
        if (profile == null) {
            throw new BadRequestException(HttpStatus.NOT_FOUND, "客户档案不存在");
        }
        return profile;
    }

    private void validateEditableOrder(CustomerOrder order) {
        if (!isEditableOrder(order)) {
            throw new BadRequestException(readOnlyReason(order));
        }
    }

    private boolean isEditableOrder(CustomerOrder order) {
        return Integer.valueOf(1).equals(order.getStatus())
                && StringUtils.isNotBlank(order.getMealType())
                && safeInt(order.getRemainingCount()) > 0;
    }

    private String readOnlyReason(CustomerOrder order) {
        if (Integer.valueOf(4).equals(order.getStatus())) {
            return "订单处于暂停状态，只能查看来源计划";
        }
        if (StringUtils.isBlank(order.getMealType())) {
            return "订单餐次尚未指定，只能查看来源计划";
        }
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            return "当前订单状态不支持调整排餐计划";
        }
        if (safeInt(order.getRemainingCount()) <= 0) {
            return "订单没有剩余餐数，只能查看已有排餐";
        }
        return null;
    }

    private boolean isOrderMealTypeSupported(CustomerOrder order, String mealType) {
        String orderMealType = order.getMealType();
        if ("BREAKFAST".equals(mealType)) {
            return "ALL".equals(orderMealType) && safeInt(order.getBreakfastCount()) > 0;
        }
        if ("LUNCH".equals(mealType)) {
            return ("ALL".equals(orderMealType) || "LUNCH_DINNER".equals(orderMealType)
                    || "LUNCH".equals(orderMealType)) && safeInt(order.getLunchDinnerCount()) > 0;
        }
        return "DINNER".equals(mealType)
                && ("ALL".equals(orderMealType) || "LUNCH_DINNER".equals(orderMealType)
                || "DINNER".equals(orderMealType)) && safeInt(order.getLunchDinnerCount()) > 0;
    }

    private YearMonth parseMonth(String statsMonth) {
        if (StringUtils.isBlank(statsMonth)) {
            throw new BadRequestException("统计月份不能为空，格式应为 yyyy-MM");
        }
        try {
            YearMonth month = YearMonth.parse(statsMonth, MONTH_FORMAT);
            if (!month.toString().equals(statsMonth)) {
                throw new DateTimeParseException("not canonical", statsMonth, 0);
            }
            return month;
        } catch (DateTimeParseException e) {
            throw new BadRequestException("统计月份格式错误，请使用 yyyy-MM 格式");
        }
    }

    private LocalDate parseDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            if (!date.toString().equals(value)) {
                throw new DateTimeParseException("not canonical", value, 0);
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new BadRequestException("排餐日期格式错误，请使用 yyyy-MM-dd 格式");
        }
    }

    private void requireOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BadRequestException("订单ID必须为正整数");
        }
    }

    private String mealTypeName(String mealType) {
        if ("BREAKFAST".equals(mealType)) {
            return "早餐";
        }
        if ("LUNCH".equals(mealType)) {
            return "午餐";
        }
        if ("DINNER".equals(mealType)) {
            return "晚餐";
        }
        return mealType;
    }

    private String currentOperator() {
        try {
            return SecurityUtils.getCurrentUsername();
        } catch (Exception e) {
            return "system";
        }
    }

    private int mealTypeOrder(String mealType) {
        int index = MEAL_TYPES.indexOf(mealType);
        return index < 0 ? MEAL_TYPES.size() : index;
    }

    private boolean isSamePool(String mealType, String poolMealType) {
        return "BREAKFAST".equals(poolMealType)
                ? "BREAKFAST".equals(mealType)
                : "LUNCH".equals(mealType) || "DINNER".equals(mealType);
    }

    private String cellKey(String date, String mealType) {
        return date + "#" + mealType;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static final class CalendarFacts {
        private List<CustomerMealScheduleAddition> additions = Collections.emptyList();
        private List<CustomerScheduledMealDto> monthProgress = Collections.emptyList();
        private List<OrderScheduledCountDto> poolScheduledCounts = Collections.emptyList();
        private Map<String, Integer> verifiedCountsByMealType = Collections.emptyMap();
    }
}
