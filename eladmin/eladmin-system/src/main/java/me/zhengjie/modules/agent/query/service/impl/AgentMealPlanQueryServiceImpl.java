package me.zhengjie.modules.agent.query.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.query.domain.dto.*;
import me.zhengjie.modules.agent.query.service.AgentMealPlanQueryService;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.MealPlanCustomerItem;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerItemMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.mapper.MealPlanManualReplaceMapper;
import me.zhengjie.modules.meal.domain.MealPlanManualReplace;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerAddressVO;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Agent 排餐三层数据受控聚合实现。 */
@Service
@RequiredArgsConstructor
public class AgentMealPlanQueryServiceImpl implements AgentMealPlanQueryService {
    private static final int MAX_DISHES = 30;
    private static final int MAX_PAGE_SIZE = 50;
    private final MealPlanMapper mealPlanMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;
    private final MealPlanCustomerItemMapper mealPlanCustomerItemMapper;
    private final MealPlanManualReplaceMapper mealPlanManualReplaceMapper;
    private final CustomerMealScheduleAdditionMapper customerMealScheduleAdditionMapper;
    private final CustomerProfileMapper customerProfileMapper;

    /** {@inheritDoc} */
    @Override
    public AgentListResultDto<AgentMealPlanSummaryDto> query(AgentMealPlanQueryRequest request) {
        if (request == null) throw new IllegalArgumentException("排餐查询请求不能为空");
        List<MealPlanCustomer> customers;
        Map<Long, MealPlan> plansById = new LinkedHashMap<>();
        if (request.getCustomerMealPlanId() != null) {
            MealPlanCustomer customer = mealPlanCustomerMapper.selectById(request.getCustomerMealPlanId());
            if (customer != null && request.getCustomerId() != null && !request.getCustomerId().equals(customer.getCustomerId())) {
                customer = null;
            }
            if (customer != null && !AgentCustomerDataScopeContext.allows(customer.getCustomerId())) customer = null;
            customers = customer == null ? Collections.emptyList() : List.of(customer);
            if (customer != null) plansById.put(customer.getMealPlanId(), mealPlanMapper.selectById(customer.getMealPlanId()));
        } else {
            if (AgentCustomerDataScopeContext.status() == AgentCustomerDataScopeContext.ScopeStatus.UNBOUND) return emptyResult(request);
            LocalDate[] range = resolveDateRange(request);
            if (request.getCustomerId() == null && (!hasText(request.getRecordDate()) || !hasText(request.getMealType()))) {
                throw new IllegalArgumentException("范围排餐查询必须提供单日日期和餐次");
            }
            if (request.getCustomerId() != null && !AgentCustomerDataScopeContext.allows(request.getCustomerId())) return emptyResult(request);
            List<MealPlan> plans = mealPlanMapper.selectList(new LambdaQueryWrapper<MealPlan>()
                    .eq(MealPlan::getDeleted, false)
                    .ge(MealPlan::getRecordDate, range[0])
                    .le(MealPlan::getRecordDate, range[1])
                    .eq(hasText(request.getMealType()), MealPlan::getMealType, request.getMealType())
                    .orderByAsc(MealPlan::getRecordDate).orderByAsc(MealPlan::getMealType));
            if (plans == null) plans = Collections.emptyList();
            for (MealPlan plan : plans) plansById.put(plan.getId(), plan);
            if (plansById.isEmpty()) return emptyResult(request);
            LambdaQueryWrapper<MealPlanCustomer> customerQuery = new LambdaQueryWrapper<MealPlanCustomer>()
                .in(MealPlanCustomer::getMealPlanId, plansById.keySet())
                .eq(MealPlanCustomer::getDeleted, false)
                .eq(request.getCustomerId() != null, MealPlanCustomer::getCustomerId, request.getCustomerId())
                .orderByAsc(MealPlanCustomer::getMealPlanId).orderByAsc(MealPlanCustomer::getId);
            Set<Long> scopedIds = AgentCustomerDataScopeContext.customerIds();
            if (scopedIds != null && scopedIds.isEmpty()) return emptyResult(request);
            if (scopedIds != null) customerQuery.in(MealPlanCustomer::getCustomerId, scopedIds);
            customers = mealPlanCustomerMapper.selectList(customerQuery);
        }
        AgentListResultDto<AgentMealPlanSummaryDto> result = new AgentListResultDto<>();
        int page = request.getPage() == null ? 1 : request.getPage();
        int size = request.getSize() == null ? MAX_PAGE_SIZE : request.getSize();
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("排餐范围查询分页参数无效");
        long total = customers.size();
        int from = Math.min((page - 1) * size, customers.size());
        int to = Math.min(from + size, customers.size());
        customers = customers.subList(from, to);
        result.setTotal(total);
        result.setPage(page); result.setSize(size); result.setTruncated(to < total);
        result.setQueriedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(8)).toString());
        Map<Long, String> addressByCustomer = addressesByCustomer(plansById);
        Map<Long, String> customerCodes = customerCodes(customers);
        Map<Long, Integer> manualReplaceCounts = manualReplaceCounts(customers);
        java.util.Set<String> manualAdditionKeys = manualAdditionKeys(plansById);
        result.setItems(customers.stream().map(customer -> summary(plansById.get(customer.getMealPlanId()), customer,
            customerCodes.get(customer.getCustomerId()), addressByCustomer.get(customer.getCustomerId()), manualReplaceCounts.getOrDefault(customer.getId(), 0), manualAdditionKeys)).collect(Collectors.toList()));
        return result;
    }

    /** 构造无权限、空范围或无命中时的稳定分页空结果。 */
    private AgentListResultDto<AgentMealPlanSummaryDto> emptyResult(AgentMealPlanQueryRequest request) {
        AgentListResultDto<AgentMealPlanSummaryDto> result = new AgentListResultDto<>();
        result.setPage(request.getPage() == null ? 1 : request.getPage());
        result.setSize(request.getSize() == null ? MAX_PAGE_SIZE : request.getSize());
        result.setQueriedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.ofHours(8)).toString());
        return result;
    }

    /**
     * 解析单日或有限日期范围。未传日期时拒绝，避免客服查询无界历史排餐。
     *
     * @param request 已校验的受控请求
     * @return 起止日期，均包含在查询范围内
     */
    private LocalDate[] resolveDateRange(AgentMealPlanQueryRequest request) {
        try {
            if (hasText(request.getRecordDate())) {
                LocalDate date = LocalDate.parse(request.getRecordDate());
                return new LocalDate[]{date, date};
            }
            if (!hasText(request.getStartDate()) || !hasText(request.getEndDate())) {
                throw new IllegalArgumentException("排餐查询必须提供日期或日期范围");
            }
            LocalDate start = LocalDate.parse(request.getStartDate());
            LocalDate end = LocalDate.parse(request.getEndDate());
            long days = ChronoUnit.DAYS.between(start, end);
            if (days < 0 || days > 30) throw new IllegalArgumentException("排餐日期范围不能超过 31 天");
            return new LocalDate[]{start, end};
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("排餐日期必须使用 yyyy-MM-dd 格式", exception);
        }
    }

    /** 将真实排餐三层记录转换为脱敏 Agent 摘要。 */
    private AgentMealPlanSummaryDto summary(MealPlan plan, MealPlanCustomer customer, String customerCode, String maskedAddress, int manualReplaceCount,
                                            java.util.Set<String> manualAdditionKeys) {
        AgentMealPlanSummaryDto dto = new AgentMealPlanSummaryDto();
        dto.setMealPlanId(customer.getMealPlanId()); dto.setCustomerMealPlanId(customer.getId()); dto.setCustomerId(customer.getCustomerId()); dto.setOrderId(customer.getOrderId());
        dto.setCustomerCode(customerCode);
        dto.setParentPackageId(customer.getParentPackageId()); dto.setChildPackageId(customer.getChildPackageId());
        if (plan != null) { dto.setRecordDate(plan.getRecordDate()); dto.setMealTypeCode(plan.getMealType()); dto.setGenerationStatus(plan.getStatus()); dto.setGenerateTime(plan.getGenerateTime()); }
        dto.setCustomerPlanStatus(customer.getStatus()); dto.setVerified(Integer.valueOf(1).equals(customer.getIsVerified()));
        dto.setMaskedDeliveryAddress(maskedAddress); dto.setManualReplaceCount(manualReplaceCount);
        dto.setManualAddition(plan != null && manualAdditionKeys.contains(additionKey(customer.getCustomerId(), customer.getOrderId(), plan.getRecordDate(), plan.getMealType())));
        List<Long> firstSuccessfulIds = mealPlanCustomerMapper.selectFirstSuccessfulCustomerPlanIds(List.of(customer.getId()));
        dto.setFirstSuccessful(firstSuccessfulIds != null && firstSuccessfulIds.contains(customer.getId()));
        dto.setFailureReason(truncate(customer.getFailReason()));
        List<MealPlanCustomerItem> items = mealPlanCustomerItemMapper.selectByCustomerPlanId(customer.getId());
        if (items == null) items = Collections.emptyList();
        dto.setDishesTruncated(items.size() > MAX_DISHES);
        dto.setDishes(items.stream().filter(item -> !Boolean.TRUE.equals(item.getDeleted())).limit(MAX_DISHES).map(this::dish).collect(Collectors.toList()));
        return dto;
    }

    /** 批量装载客户编号，禁止从排餐实体中的姓名或手机号推断客户身份。 */
    private Map<Long, String> customerCodes(List<MealPlanCustomer> customers) {
        Set<Long> ids = customers.stream().map(MealPlanCustomer::getCustomerId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        List<CustomerProfile> profiles = customerProfileMapper.selectList(new LambdaQueryWrapper<CustomerProfile>().in(CustomerProfile::getId, ids));
        if (profiles == null) return Collections.emptyMap();
        return profiles.stream()
            .collect(Collectors.toMap(CustomerProfile::getId, CustomerProfile::getCustomerCode, (left, right) -> left, LinkedHashMap::new));
    }
    /** 查询本轮排餐日期餐次的人工新增规则，并以客户/订单维度建立受控命中索引。 */
    private java.util.Set<String> manualAdditionKeys(Map<Long, MealPlan> plansById) {
        java.util.Set<String> result = new java.util.HashSet<>();
        for (MealPlan plan : plansById.values()) {
            if (plan == null || plan.getRecordDate() == null || !hasText(plan.getMealType())) continue;
            List<CustomerMealScheduleAddition> additions = customerMealScheduleAdditionMapper.selectActiveByDateMeal(plan.getRecordDate(), plan.getMealType());
            if (additions == null) continue;
            for (CustomerMealScheduleAddition addition : additions) {
                if (addition != null) result.add(additionKey(addition.getCustomerId(), addition.getOrderId(), plan.getRecordDate(), plan.getMealType()));
            }
        }
        return result;
    }
    /** 构造不含敏感文本的人工新增匹配键。 */
    private String additionKey(Long customerId, Long orderId, LocalDate recordDate, String mealType) {
        return String.valueOf(customerId) + "|" + String.valueOf(orderId) + "|" + recordDate + "|" + mealType;
    }
    /** 按排餐主单读取实际配送地址并立即脱敏，避免地址全文进入 Agent。 */
    private Map<Long, String> addressesByCustomer(Map<Long, MealPlan> plansById) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long mealPlanId : plansById.keySet()) {
            List<MealPlanCustomerAddressVO> addresses = mealPlanCustomerMapper.selectCustomerAddresses(mealPlanId);
            if (addresses == null) continue;
            for (MealPlanCustomerAddressVO address : addresses) {
                if (address != null && address.getCustomerId() != null) result.put(address.getCustomerId(), maskAddress(address.getAddressDetail()));
            }
        }
        return result;
    }
    /** 聚合客户排餐记录的手工换菜数量，避免暴露原始人工操作明细。 */
    private Map<Long, Integer> manualReplaceCounts(List<MealPlanCustomer> customers) {
        List<Long> ids = customers.stream().map(MealPlanCustomer::getId).collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<Long, Integer> result = new LinkedHashMap<>();
        List<MealPlanManualReplace> records = mealPlanManualReplaceMapper.selectByCustomerPlanIds(ids);
        if (records != null) for (MealPlanManualReplace record : records) {
            if (record != null && record.getCustomerPlanId() != null) result.merge(record.getCustomerPlanId(), 1, Integer::sum);
        }
        return result;
    }
    /** 转换排餐菜品的可审计过滤信息；只有 replaceReason=ALLERGY 才代表实际过敏换菜。 */
    private AgentMealPlanDishItemDto dish(MealPlanCustomerItem item) {
        AgentMealPlanDishItemDto dto = new AgentMealPlanDishItemDto();
        dto.setDishId(item.getDishId()); dto.setDishName(item.getDishName()); dto.setDishType(item.getDishType());
        dto.setReplaced(Boolean.TRUE.equals(item.getIsReplaced())); dto.setReplaceReason(item.getReplaceReason());
        dto.setAllergyFiltered(Boolean.TRUE.equals(item.getIsAllergyFiltered()) && "ALLERGY".equals(item.getReplaceReason()));
        dto.setAllergyReasons(dto.isAllergyFiltered() && hasText(item.getAllergyReasons())
            ? java.util.Arrays.stream(item.getAllergyReasons().split(",")).map(String::trim).filter(this::hasText).collect(Collectors.toList()) : Collections.emptyList());
        dto.setOriginalDishId(item.getOriginalDishId()); dto.setOriginalDishName(item.getOriginalDishName());
        return dto;
    }
    private String truncate(String value) { return value == null ? null : value.length() <= 200 ? value : value.substring(0, 200) + "…"; }
    /** 对配送地址保留前六位摘要，禁止将完整地址发送到 Agent。 */
    private String maskAddress(String value) { return !hasText(value) ? null : value.trim().length() <= 6 ? "***" : value.trim().substring(0, 6) + "***"; }
    /** 判断字符串是否包含有效业务值。 */
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
