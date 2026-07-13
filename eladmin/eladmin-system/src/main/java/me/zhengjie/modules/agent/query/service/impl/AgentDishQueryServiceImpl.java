package me.zhengjie.modules.agent.query.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidateItemDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishCandidatePreviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentListResultDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerOverviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentScheduledMenuGroupDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentScheduledMenuResponseDto;
import me.zhengjie.modules.agent.query.service.AgentDishQueryService;
import me.zhengjie.modules.agent.query.service.AgentCustomerQueryService;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishIngredientRelation;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealSchedulePlanMapper;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import me.zhengjie.modules.meal.util.ScheduleKeyUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 主系统 Agent 菜品查询实现，限制 ID 和配料返回量以避免大结果集进入模型上下文。 */
@Service
@RequiredArgsConstructor
public class AgentDishQueryServiceImpl implements AgentDishQueryService {
    private static final int MAX_DISHES = 20;
    private static final int MAX_INGREDIENTS = 20;
    private final DishMapper dishMapper;
    private final DishIngredientMapper dishIngredientMapper;
    private final MealSchedulePlanMapper mealSchedulePlanMapper;
    private final ParentPackageMapper parentPackageMapper;
    private final AgentCustomerQueryService customerQueryService;
    private final DishIngredientCategoryService dishIngredientCategoryService;

    /** {@inheritDoc} */
    @Override
    public AgentListResultDto<AgentDishSummaryDto> listByIds(List<Integer> dishIds) {
        AgentListResultDto<AgentDishSummaryDto> result = new AgentListResultDto<>();
        if (dishIds == null || dishIds.isEmpty()) return result;
        List<Integer> safeIds = dishIds.stream().filter(id -> id != null && id > 0).distinct().limit(MAX_DISHES).collect(Collectors.toList());
        result.setTruncated(safeIds.size() < dishIds.stream().filter(id -> id != null && id > 0).distinct().count());
        if (safeIds.isEmpty()) return result;
        List<Dish> dishes = dishMapper.selectBatchIds(safeIds);
        Map<Integer, List<DishIngredientRelation>> relations = dishIngredientMapper.findRelationsByDishIds(safeIds).stream()
            .collect(Collectors.groupingBy(DishIngredientRelation::getDishId));
        result.setTotal(dishes.size());
        result.setItems(dishes.stream().map(dish -> toSummary(dish, relations.getOrDefault(dish.getId(), Collections.emptyList()))).collect(Collectors.toList()));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentScheduledMenuResponseDto listScheduled(String recordDate, List<String> mealTypes) {
        LocalDate date = LocalDate.parse(recordDate);
        List<String> safeMealTypes = normalizeScheduledMealTypes(mealTypes);
        List<Dish> scheduled = mealSchedulePlanMapper.findByScheduleMealTimes(ScheduleKeyUtil.calcWeek(date),
            ScheduleKeyUtil.calcDay(date), safeMealTypes);
        if (scheduled == null) scheduled = Collections.emptyList();
        List<Integer> ids = scheduled.stream().map(Dish::getId).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        Map<Integer, List<DishIngredientRelation>> relations = ids.isEmpty() ? Collections.emptyMap()
            : dishIngredientMapper.findRelationsByDishIds(ids).stream().collect(Collectors.groupingBy(DishIngredientRelation::getDishId));
        AgentScheduledMenuResponseDto result = new AgentScheduledMenuResponseDto();
        result.setRecordDate(recordDate); result.setTotal(scheduled.size()); result.setTruncated(scheduled.size() > MAX_DISHES);
        int[] remaining = {MAX_DISHES};
        for (String mealType : safeMealTypes) {
            List<Dish> groupDishes = scheduled.stream().filter(dish -> mealType.equals(dish.getScheduleMealTime())).collect(Collectors.toList());
            AgentScheduledMenuGroupDto group = new AgentScheduledMenuGroupDto();
            group.setMealTypeCode(mealType); group.setMealTypeName(mealTypeName(mealType)); group.setTotal(groupDishes.size());
            group.setItems(groupDishes.stream().limit(remaining[0]).map(dish -> toSummary(dish,
                relations.getOrDefault(dish.getId(), Collections.emptyList()))).collect(Collectors.toList()));
            remaining[0] -= group.getItems().size();
            result.getGroups().add(group);
        }
        return result;
    }

    /** 将公共菜单餐次收敛为午餐、晚餐白名单和固定排序，拒绝空集合及未登记餐次。 */
    private List<String> normalizeScheduledMealTypes(List<String> mealTypes) {
        if (mealTypes == null || mealTypes.isEmpty()) throw new IllegalArgumentException("公共菜单餐次不能为空");
        List<String> result = new ArrayList<>();
        for (String mealType : List.of("LUNCH", "DINNER")) {
            if (mealTypes.contains(mealType)) result.add(mealType);
        }
        if (result.size() != mealTypes.stream().filter(java.util.Objects::nonNull).distinct().count() || result.isEmpty()) {
            throw new IllegalArgumentException("公共菜单仅支持午餐和晚餐");
        }
        return result;
    }

    /** 返回公共菜单餐次的固定中文名称。 */
    private String mealTypeName(String mealType) {
        return "LUNCH".equals(mealType) ? "午餐" : "DINNER".equals(mealType) ? "晚餐" : mealType;
    }

    /** {@inheritDoc} */
    @Override
    public AgentDishCandidatePreviewDto previewCandidates(Long customerId, String recordDate, String mealType) {
        AgentDishCandidatePreviewDto result = new AgentDishCandidatePreviewDto();
        result.setCustomerId(customerId); result.setRecordDate(recordDate); result.setMealTypeCode(mealType);
        AgentCustomerOverviewDto customer = customerQueryService.getOverview(customerId, null);
        if (!customer.isPresent()) return result;
        result.setPresent(true); result.setCustomerCode(customer.getCustomerCode());
        List<Long> parentPackageIds = customer.getPackages().stream().filter(item -> item.isActive())
            .map(item -> item.getParentPackageId()).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        result.setParentPackageIds(parentPackageIds);
        LocalDate date = LocalDate.parse(recordDate);
        List<Dish> candidates = mergeCandidates(date, mealType);
        List<Integer> candidateIds = candidates.stream().map(Dish::getId).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        Map<Integer, List<DishIngredientRelation>> ingredients = candidateIds.isEmpty() ? Collections.emptyMap()
            : dishIngredientMapper.findRelationsByDishIds(candidateIds).stream().collect(Collectors.groupingBy(DishIngredientRelation::getDishId));
        Map<Long, ParentPackage> packages = parentPackageIds.isEmpty() ? Collections.emptyMap() : parentPackageMapper.selectBatchIds(parentPackageIds)
            .stream().collect(Collectors.toMap(ParentPackage::getId, Function.identity()));
        Map<String, Set<String>> categoryIngredients = dishIngredientCategoryService.getCategoryIngredientMapping();
        result.setTotalCandidateCount(candidates.size());
        int available = 0;
        List<AgentDishCandidateItemDto> items = new ArrayList<>();
        for (Dish dish : candidates) {
            List<String> reasons = filterReasons(dish, parentPackageIds, packages, customer.getExcludedDishIds(), customer.getAllergyTags(),
                ingredients.getOrDefault(dish.getId(), Collections.emptyList()), categoryIngredients);
            if (reasons.isEmpty()) available++;
            if (items.size() < MAX_DISHES) items.add(candidateItem(dish, ingredients.getOrDefault(dish.getId(), Collections.emptyList()), reasons));
        }
        result.setAvailableCandidateCount(available); result.setFilteredCandidateCount(candidates.size() - available);
        result.setItems(items); result.setTruncated(candidates.size() > MAX_DISHES);
        return result;
    }

    /** 合并当日排期菜和全局固定米饭，并按菜品 ID 去重以保持与排餐候选来源一致。 */
    private List<Dish> mergeCandidates(LocalDate date, String mealType) {
        List<Dish> scheduled = mealSchedulePlanMapper.findBySchedule(ScheduleKeyUtil.calcWeek(date), ScheduleKeyUtil.calcDay(date), mealType);
        List<Dish> rice = dishMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Dish>()
            .eq(Dish::getDishType, "RICE").eq(Dish::getEnabled, true));
        Map<Integer, Dish> unique = new LinkedHashMap<>();
        if (scheduled != null) scheduled.forEach(item -> { if (item != null && item.getId() != null) unique.put(item.getId(), item); });
        if (rice != null) rice.forEach(item -> { if (item != null && item.getId() != null) unique.putIfAbsent(item.getId(), item); });
        return new ArrayList<>(unique.values());
    }

    /** 计算套餐、客户排除菜和三级过敏标签对单个候选菜的稳定过滤摘要。 */
    private List<String> filterReasons(Dish dish, List<Long> parentPackageIds, Map<Long, ParentPackage> packages,
                                       List<Integer> excludedDishIds, List<String> allergyTags,
                                       List<DishIngredientRelation> relations, Map<String, Set<String>> categoryIngredients) {
        List<String> reasons = new ArrayList<>();
        if (!"RICE".equals(dish.getDishType()) && !matchesAnyPackage(dish, parentPackageIds, packages)) reasons.add("PACKAGE_NOT_MATCHED");
        if (excludedDishIds != null && excludedDishIds.contains(dish.getId())) reasons.add("CUSTOMER_EXCLUDED_DISH");
        Set<String> ingredientNames = relations.stream().map(DishIngredientRelation::getIngredientName)
            .filter(name -> name != null && !name.trim().isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        if (allergyTags != null) for (String tag : allergyTags) {
            if (tag == null || tag.trim().isEmpty()) continue;
            Set<String> categoryValues = categoryIngredients == null ? null : categoryIngredients.get(tag);
            if (ingredientNames.contains(tag) || categoryValues != null && categoryValues.stream().anyMatch(ingredientNames::contains)) reasons.add("ALLERGY:" + tag);
        }
        return reasons;
    }

    /** 按父套餐 ID 或套餐编码校验菜品套餐配置，复用正式排餐的匹配口径。 */
    private boolean matchesAnyPackage(Dish dish, List<Long> parentPackageIds, Map<Long, ParentPackage> packages) {
        if (parentPackageIds == null || parentPackageIds.isEmpty() || dish.getMealPackages() == null) return false;
        for (Long packageId : parentPackageIds) {
            ParentPackage parentPackage = packages.get(packageId);
            if (dish.getMealPackages().contains(String.valueOf(packageId)) || parentPackage != null && dish.getMealPackages().contains(parentPackage.getPackageCode())) return true;
        }
        return false;
    }

    /** 将候选菜转换为限量的可展示预览对象。 */
    private AgentDishCandidateItemDto candidateItem(Dish dish, List<DishIngredientRelation> relations, List<String> reasons) {
        AgentDishCandidateItemDto dto = new AgentDishCandidateItemDto();
        dto.setDishId(dish.getId()); dto.setDishName(dish.getName()); dto.setDishTypeCode(dish.getDishType());
        dto.setIngredientNames(relations.stream().map(DishIngredientRelation::getIngredientName).filter(name -> name != null && !name.trim().isEmpty())
            .limit(MAX_INGREDIENTS).collect(Collectors.toList()));
        dto.setIngredientsTruncated(relations.size() > MAX_INGREDIENTS); dto.setAvailable(reasons.isEmpty()); dto.setFilterReasons(reasons);
        return dto;
    }

    /** 将菜品和配料关联转换为受控的 Agent DTO。 */
    private AgentDishSummaryDto toSummary(Dish dish, List<DishIngredientRelation> relations) {
        AgentDishSummaryDto dto = new AgentDishSummaryDto();
        dto.setDishId(dish.getId()); dto.setDishName(dish.getName()); dto.setDishTypeCode(dish.getDishType());
        dto.setDishTypeName(dish.getDishType()); dto.setMealTypes(dish.getMealTypes() == null ? Collections.emptyList() : dish.getMealTypes()); dto.setEnabled(dish.getEnabled());
        dto.setIngredientsTruncated(relations.size() > MAX_INGREDIENTS);
        dto.setIngredientNames(relations.stream().map(DishIngredientRelation::getIngredientName).filter(name -> name != null && !name.trim().isEmpty()).limit(MAX_INGREDIENTS).collect(Collectors.toList()));
        return dto;
    }
}
