/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.modules.meal.service.impl;

import com.alibaba.fastjson2.JSON;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishIngredientRelation;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.MealPlanCustomerItem;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerItemMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.modules.meal.util.ScheduleKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 排餐计划服务实现
 * @author qqx
 * @date 2026-03-31
 **/
@Service
@RequiredArgsConstructor
@Slf4j
public class MealPlanServiceImpl implements MealPlanService {

    private static final String MEAL_TYPE_LUNCH = "LUNCH";
    private static final String MEAL_TYPE_DINNER = "DINNER";
    private static final String SCHEDULE_MODE_DAILY = "DAILY";
    private static final String SCHEDULE_MODE_WEEKDAY = "WEEKDAY";
    private static final String SCHEDULE_MODE_WEEKEND = "WEEKEND";
    private static final String SCHEDULE_MODE_SCHEDULE = "SCHEDULE";
    private static final String PLAN_STATUS_GENERATING = "GENERATING";
    private static final String PLAN_STATUS_SUCCESS = "SUCCESS";
    private static final String PLAN_STATUS_FAILED = "FAILED";
    private static final String DISH_TYPE_MAIN = "MAIN";
    private static final String DISH_TYPE_SIDE = "SIDE";
    private static final String DISH_TYPE_VEGETABLE = "VEGETABLE";
    private static final String DISH_TYPE_SOUP = "SOUP";
    private static final String DISH_TYPE_RICE = "RICE";
    private static final Map<String, ReentrantLock> GENERATE_LOCKS = new ConcurrentHashMap<>();

    private final MealPlanMapper mealPlanMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;
    private final MealPlanCustomerItemMapper mealPlanCustomerItemMapper;
    private final CustomerOrderMapper customerOrderMapper;
    private final CustomerProfileMapper customerProfileMapper;
    private final ParentPackageMapper parentPackageMapper;
    private final SubPackageMapper subPackageMapper;
    private final DishMapper dishMapper;
    private final DishIngredientMapper dishIngredientMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MealPlanGenerateResult generateMealPlan(String recordDate, String mealType) {
        LocalDate targetDate = ScheduleKeyUtil.parseDate(recordDate);
        String normalizedMealType = validateParams(mealType);
        String lockKey = buildGenerateLockKey(targetDate, normalizedMealType);
        Lock lock = GENERATE_LOCKS.computeIfAbsent(lockKey, key -> new ReentrantLock());
        lock.lock();
        registerLockCleanup(lockKey, lock);
        return doGenerateMealPlan(targetDate, normalizedMealType);
    }

    /**
     * 执行排餐生成主流程：清理旧计划、加载候选数据、逐个订单生成并汇总结果。
     */
    private MealPlanGenerateResult doGenerateMealPlan(LocalDate targetDate, String mealType) {
        softDeleteExistingPlan(targetDate, mealType);
        List<CustomerOrder> orders = loadValidOrders(targetDate, mealType);
        Map<Long, CustomerProfile> customerMap = loadCustomers(orders);
        Map<Long, SubPackage> subPackageMap = loadSubPackages(orders);
        Map<Long, ParentPackage> parentPackageMap = loadParentPackages(orders);
        List<Dish> scheduledDishes = loadScheduledDishes(targetDate, mealType);
        Map<Integer, Set<String>> dishIngredientMap = loadDishIngredients(scheduledDishes);
        Map<Long, Map<String, List<Dish>>> candidateDishMap = buildCandidateDishPool(scheduledDishes, orders, parentPackageMap);

        MealPlan mealPlan = createMealPlan(targetDate, mealType, orders.size());
        List<MealPlanGenerateResult.FailDetail> failDetails = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (CustomerOrder order : orders) {
            CustomerProfile customer = customerMap.get(order.getCustomerId());
            SubPackage subPackage = subPackageMap.get(order.getChildPackageId());
            if (customer == null) {
                failCount += saveFailPlan(mealPlan.getId(), order, null, null, "客户档案不存在", failDetails);
                continue;
            }
            if (subPackage == null) {
                failCount += saveFailPlan(mealPlan.getId(), order, customer, null, "子套餐不存在", failDetails);
                continue;
            }
            if (!parentPackageMap.containsKey(order.getParentPackageId())) {
                failCount += saveFailPlan(mealPlan.getId(), order, customer, subPackage, "父套餐不存在", failDetails);
                continue;
            }

            try {
                CustomerMealPlan customerPlan = buildCustomerPlan(order, customer, subPackage, candidateDishMap, dishIngredientMap);
                saveSuccessPlan(mealPlan.getId(), order, customer, subPackage, customerPlan);
                successCount++;
            } catch (BadRequestException e) {
                failCount += saveFailPlan(mealPlan.getId(), order, customer, subPackage, e.getMessage(), failDetails);
            }
        }

        updateMealPlanSummary(mealPlan, successCount, failCount);
        return buildResult(mealPlan, failDetails);
    }

    /**
     * 校验餐次参数，只允许午餐和晚餐。
     */
    private String validateParams(String mealType) {
        if (!MEAL_TYPE_LUNCH.equals(mealType) && !MEAL_TYPE_DINNER.equals(mealType)) {
            throw new BadRequestException("餐次仅支持LUNCH或DINNER");
        }
        return mealType;
    }

    /**
     * 对同日期同餐次的历史有效排餐做软删除，保证重新生成时只保留最新计划。
     */
    private void softDeleteExistingPlan(LocalDate recordDate, String mealType) {
        MealPlan existingPlan = mealPlanMapper.findActiveByDateAndMealTypeForUpdate(recordDate, mealType);
        if (existingPlan == null) {
            return;
        }
        mealPlanMapper.softDeleteItemsByMealPlanId(existingPlan.getId());
        mealPlanMapper.softDeleteCustomersByMealPlanId(existingPlan.getId());
        mealPlanMapper.softDeletePlanById(existingPlan.getId());
    }

    /**
     * 查询满足日期、餐次和配送规则的订单候选。
     */
    private List<CustomerOrder> loadValidOrders(LocalDate targetDate, String mealType) {
        List<CustomerOrder> candidateOrders = customerOrderMapper.findMealPlanOrders(targetDate, mealType);
        List<CustomerOrder> validOrders = new ArrayList<>();
        for (CustomerOrder order : candidateOrders) {
            if (!scheduleModeMatches(order, targetDate)) {
                continue;
            }
            validOrders.add(order);
        }
        return validOrders;
    }

    /**
     * 判断订单配送模式是否命中目标日期。
     */
    private boolean scheduleModeMatches(CustomerOrder order, LocalDate targetDate) {
        String scheduleMode = order.getScheduleMode();
        if (scheduleMode == null || SCHEDULE_MODE_DAILY.equals(scheduleMode)) {
            return true;
        }
        if (SCHEDULE_MODE_SCHEDULE.equals(scheduleMode)) {
            List<String> deliveryDates = parseJsonArray(order.getDeliveryDates());
            return deliveryDates.contains(targetDate.toString());
        }
        if (SCHEDULE_MODE_WEEKDAY.equals(scheduleMode)) {
            return ScheduleKeyUtil.isWeekday(targetDate);
        }
        if (SCHEDULE_MODE_WEEKEND.equals(scheduleMode)) {
            return ScheduleKeyUtil.isWeekend(targetDate);
        }
        return false;
    }

    /**
     * 批量加载订单关联的客户档案，避免逐条查询。
     */
    private Map<Long, CustomerProfile> loadCustomers(List<CustomerOrder> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> customerIds = orders.stream().map(CustomerOrder::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (customerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, CustomerProfile> customerMap = new HashMap<>();
        for (CustomerProfile customer : customerProfileMapper.findByIds(customerIds)) {
            customerMap.put(customer.getId(), customer);
        }
        return customerMap;
    }

    /**
     * 按订单子套餐ID批量加载套餐数据。
     */
    private Map<Long, SubPackage> loadSubPackages(List<CustomerOrder> orders) {
        Set<Long> ids = orders.stream().map(CustomerOrder::getChildPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, SubPackage> packageMap = new HashMap<>();
        for (SubPackage subPackage : subPackageMapper.selectBatchIds(ids)) {
            packageMap.put(subPackage.getId(), subPackage);
        }
        return packageMap;
    }

    /**
     * 按订单父套餐ID批量加载套餐数据。
     */
    private Map<Long, ParentPackage> loadParentPackages(List<CustomerOrder> orders) {
        Set<Long> ids = orders.stream().map(CustomerOrder::getParentPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ParentPackage> packageMap = new HashMap<>();
        for (ParentPackage parentPackage : parentPackageMapper.selectBatchIds(ids)) {
            packageMap.put(parentPackage.getId(), parentPackage);
        }
        return packageMap;
    }

    /**
     * 按父套餐归类候选菜池，供后续客户排餐复用。
     */
    private Map<Long, Map<String, List<Dish>>> buildCandidateDishPool(List<Dish> scheduledDishes, List<CustomerOrder> orders,
                                                                       Map<Long, ParentPackage> parentPackageMap) {
        Set<Long> parentPackageIds = orders.stream().map(CustomerOrder::getParentPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (parentPackageIds.isEmpty() || scheduledDishes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<String, List<Dish>>> candidateDishMap = new HashMap<>();
        for (Long parentPackageId : parentPackageIds) {
            ParentPackage parentPackage = parentPackageMap.get(parentPackageId);
            String packageCode = parentPackage != null ? parentPackage.getPackageCode() : null;
            Map<String, List<Dish>> dishTypeMap = new HashMap<>();
            for (Dish dish : scheduledDishes) {
                if (!matchesParentPackage(dish.getMealPackages(), parentPackageId, packageCode)) {
                    continue;
                }
                dishTypeMap.computeIfAbsent(dish.getDishType(), key -> new ArrayList<>()).add(dish);
            }
            sortDishTypeMap(dishTypeMap);
            candidateDishMap.put(parentPackageId, dishTypeMap);
        }
        return candidateDishMap;
    }

    /**
     * 按排期和餐次查询当日候选菜品。
     */
    private List<Dish> loadScheduledDishes(LocalDate targetDate, String mealType) {
        return dishMapper.findBySchedule(ScheduleKeyUtil.calcWeek(targetDate), ScheduleKeyUtil.calcDay(targetDate), mealType);
    }

    /**
     * 对候选菜池按排序号和ID稳定排序，保证选菜结果可预期。
     */
    private void sortDishTypeMap(Map<String, List<Dish>> dishTypeMap) {
        for (List<Dish> dishList : dishTypeMap.values()) {
            dishList.sort((a, b) -> {
                int sortCompare = Integer.compare(a.getSort() == null ? Integer.MAX_VALUE : a.getSort(), b.getSort() == null ? Integer.MAX_VALUE : b.getSort());
                if (sortCompare != 0) {
                    return sortCompare;
                }
                return Integer.compare(a.getId(), b.getId());
            });
        }
    }

    /**
     * 加载候选菜品的食材清单，用于过敏过滤。
     */
    private Map<Integer, Set<String>> loadDishIngredients(List<Dish> dishes) {
        List<Integer> dishIds = dishes.stream().map(Dish::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (dishIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DishIngredientRelation> relations = dishIngredientMapper.findRelationsByDishIds(dishIds);
        Map<Integer, Set<String>> dishIngredients = new HashMap<>();
        for (DishIngredientRelation relation : relations) {
            dishIngredients.computeIfAbsent(relation.getDishId(), key -> new LinkedHashSet<>()).add(relation.getIngredientName());
        }
        return dishIngredients;
    }

    /**
     * 为单个订单生成具体菜品组合。
     */
    private CustomerMealPlan buildCustomerPlan(CustomerOrder order, CustomerProfile customer, SubPackage subPackage,
                                               Map<Long, Map<String, List<Dish>>> candidateDishMap,
                                               Map<Integer, Set<String>> dishIngredientMap) {
        Map<String, List<Dish>> dishTypeMap = candidateDishMap.get(order.getParentPackageId());
        if (dishTypeMap == null || dishTypeMap.isEmpty()) {
            throw new BadRequestException("当前父套餐没有可用候选菜");
        }
        List<String> allergyTags = customer.getAllergyTags() == null ? Collections.emptyList() : customer.getAllergyTags();
        Set<Integer> selectedDishIds = new HashSet<>();
        List<SelectedDish> selectedDishes = new ArrayList<>();
        pickRequiredDishes(subPackage.getMeatCount(), selectedDishes, selectedDishIds, dishTypeMap, allergyTags, dishIngredientMap);
        pickVegetables(subPackage.getVegCount(), selectedDishes, selectedDishIds, dishTypeMap, allergyTags, dishIngredientMap);
        pickOptionalDish(Boolean.TRUE.equals(subPackage.getIncludeSoup()), DISH_TYPE_SOUP, selectedDishes, selectedDishIds, dishTypeMap, allergyTags, dishIngredientMap);
        pickOptionalDish(Boolean.TRUE.equals(subPackage.getIncludeRice()), DISH_TYPE_RICE, selectedDishes, selectedDishIds, dishTypeMap, allergyTags, dishIngredientMap);
        CustomerMealPlan customerMealPlan = new CustomerMealPlan();
        customerMealPlan.setSelectedDishes(selectedDishes);
        return customerMealPlan;
    }

    /**
     * 选择荤菜，支持单荤和主副菜组合两种配置。
     */
    private void pickRequiredDishes(Integer meatCount, List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                                    Map<String, List<Dish>> dishTypeMap, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap) {
        int requiredCount = meatCount == null ? 0 : meatCount;
        if (requiredCount <= 0) {
            return;
        }
        if (requiredCount == 1) {
            Dish dish = selectDish(dishTypeMap.get(DISH_TYPE_MAIN), selectedDishIds, allergyTags, dishIngredientMap);
            if (dish == null) {
                dish = selectDish(dishTypeMap.get(DISH_TYPE_SIDE), selectedDishIds, allergyTags, dishIngredientMap);
            }
            if (dish == null) {
                throw new BadRequestException("荤菜不足");
            }
            selectedDishes.add(new SelectedDish(dish.getDishType(), dish));
            selectedDishIds.add(dish.getId());
            return;
        }
        if (requiredCount != 2) {
            throw new BadRequestException("荤菜数量配置不支持");
        }
        Dish mainDish = selectDish(dishTypeMap.get(DISH_TYPE_MAIN), selectedDishIds, allergyTags, dishIngredientMap);
        Dish sideDish = selectDish(dishTypeMap.get(DISH_TYPE_SIDE), selectedDishIds, allergyTags, dishIngredientMap);
        if (mainDish == null || sideDish == null) {
            throw new BadRequestException("荤菜不足，必须同时选出主菜和副菜");
        }
        selectedDishes.add(new SelectedDish(DISH_TYPE_MAIN, mainDish));
        selectedDishes.add(new SelectedDish(DISH_TYPE_SIDE, sideDish));
        selectedDishIds.add(mainDish.getId());
        selectedDishIds.add(sideDish.getId());
    }

    /**
     * 按套餐要求补齐素菜数量。
     */
    private void pickVegetables(Integer vegCount, List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                                Map<String, List<Dish>> dishTypeMap, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap) {
        int requiredCount = vegCount == null ? 0 : vegCount;
        for (int i = 0; i < requiredCount; i++) {
            Dish dish = selectDish(dishTypeMap.get(DISH_TYPE_VEGETABLE), selectedDishIds, allergyTags, dishIngredientMap);
            if (dish == null) {
                throw new BadRequestException("素菜不足");
            }
            selectedDishes.add(new SelectedDish(DISH_TYPE_VEGETABLE, dish));
            selectedDishIds.add(dish.getId());
        }
    }

    /**
     * 在套餐配置要求时补选汤或米饭等可选菜品。
     */
    private void pickOptionalDish(boolean required, String dishType, List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                                  Map<String, List<Dish>> dishTypeMap, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap) {
        if (!required) {
            return;
        }
        Dish dish = selectDish(dishTypeMap.get(dishType), selectedDishIds, allergyTags, dishIngredientMap);
        if (dish == null) {
            throw new BadRequestException(dishType + "类型菜品不足");
        }
        selectedDishes.add(new SelectedDish(dishType, dish));
        selectedDishIds.add(dish.getId());
    }

    /**
     * 从候选列表中挑选首个未重复且不过敏的菜品。
     */
    private Dish selectDish(List<Dish> dishes, Set<Integer> selectedDishIds, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap) {
        if (dishes == null || dishes.isEmpty()) {
            return null;
        }
        for (Dish dish : dishes) {
            if (selectedDishIds.contains(dish.getId())) {
                continue;
            }
            if (containsAllergy(allergyTags, dishIngredientMap.get(dish.getId()))) {
                continue;
            }
            return dish;
        }
        return null;
    }

    /**
     * 判断菜品食材是否与客户过敏标签精确命中。
     */
    private boolean containsAllergy(List<String> allergyTags, Set<String> ingredientNames) {
        if (allergyTags == null || allergyTags.isEmpty() || ingredientNames == null || ingredientNames.isEmpty()) {
            return false;
        }
        for (String allergyTag : allergyTags) {
            if (allergyTag == null) {
                continue;
            }
            for (String ingredientName : ingredientNames) {
                if (allergyTag.equals(ingredientName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 将成功生成的客户排餐结果落库到主表和明细表。
     */
    private void saveSuccessPlan(Long mealPlanId, CustomerOrder order, CustomerProfile customer, SubPackage subPackage,
                                 CustomerMealPlan customerPlan) {
        MealPlanCustomer entity = buildCustomerEntity(mealPlanId, order, customer, subPackage, 1, "");
        mealPlanCustomerMapper.insert(entity);
        int seq = 1;
        for (SelectedDish selectedDish : customerPlan.getSelectedDishes()) {
            MealPlanCustomerItem item = new MealPlanCustomerItem();
            item.setCustomerPlanId(entity.getId());
            item.setDishType(selectedDish.getDishType());
            item.setDishId(selectedDish.getDish().getId());
            item.setDishName(selectedDish.getDish().getName());
            item.setSeq(seq++);
            item.setDeleted(false);
            mealPlanCustomerItemMapper.insert(item);
        }
    }

    /**
     * 记录单个订单的失败结果，并追加失败原因到返回结果。
     */
    private int saveFailPlan(Long mealPlanId, CustomerOrder order, CustomerProfile customer, SubPackage subPackage,
                             String failReason, List<MealPlanGenerateResult.FailDetail> failDetails) {
        MealPlanCustomer entity = buildCustomerEntity(mealPlanId, order, customer, subPackage, 0, failReason);
        mealPlanCustomerMapper.insert(entity);
        MealPlanGenerateResult.FailDetail failDetail = new MealPlanGenerateResult.FailDetail();
        failDetail.setFailReason(failReason);
        failDetails.add(failDetail);
        return 1;
    }

    /**
     * 组装客户排餐记录实体，统一填充订单、客户和套餐快照字段。
     */
    private MealPlanCustomer buildCustomerEntity(Long mealPlanId, CustomerOrder order, CustomerProfile customer,
                                                 SubPackage subPackage, int status, String failReason) {
        MealPlanCustomer entity = new MealPlanCustomer();
        entity.setMealPlanId(mealPlanId);
        entity.setCustomerId(customer != null ? customer.getId() : order.getCustomerId());
        entity.setCustomerName(customer != null ? customer.getCustomerName() : "");
        entity.setPhone(customer != null ? customer.getPhone() : "");
        entity.setOrderId(order.getId());
        entity.setParentPackageId(order.getParentPackageId());
        entity.setChildPackageId(order.getChildPackageId());
        entity.setStatus(status);
        entity.setFailReason(failReason);
        entity.setMeatRequiredCount(subPackage != null && subPackage.getMeatCount() != null ? subPackage.getMeatCount() : 0);
        entity.setVegRequiredCount(subPackage != null && subPackage.getVegCount() != null ? subPackage.getVegCount() : 0);
        entity.setIncludeSoup(subPackage != null && Boolean.TRUE.equals(subPackage.getIncludeSoup()) ? 1 : 0);
        entity.setIncludeRice(subPackage != null && Boolean.TRUE.equals(subPackage.getIncludeRice()) ? 1 : 0);
        entity.setDeleted(false);
        return entity;
    }

    /**
     * 创建单次排餐主记录，初始状态为生成中。
     */
    private MealPlan createMealPlan(LocalDate recordDate, String mealType, int totalCount) {
        MealPlan mealPlan = new MealPlan();
        mealPlan.setRecordDate(recordDate);
        mealPlan.setMealType(mealType);
        mealPlan.setTotalCount(totalCount);
        mealPlan.setSuccessCount(0);
        mealPlan.setFailCount(0);
        mealPlan.setStatus(PLAN_STATUS_GENERATING);
        mealPlan.setGenerateTime(new Timestamp(System.currentTimeMillis()));
        mealPlan.setDeleted(false);
        mealPlanMapper.insert(mealPlan);
        return mealPlan;
    }

    /**
     * 回填排餐统计信息并更新最终状态。
     */
    private void updateMealPlanSummary(MealPlan mealPlan, int successCount, int failCount) {
        mealPlan.setSuccessCount(successCount);
        mealPlan.setFailCount(failCount);
        mealPlan.setStatus(failCount > 0 ? PLAN_STATUS_FAILED : PLAN_STATUS_SUCCESS);
        mealPlanMapper.updateById(mealPlan);
    }

    /**
     * 将内部排餐结果转换为接口返回对象。
     */
    private MealPlanGenerateResult buildResult(MealPlan mealPlan, List<MealPlanGenerateResult.FailDetail> failDetails) {
        MealPlanGenerateResult result = new MealPlanGenerateResult();
        result.setMealPlanId(mealPlan.getId());
        result.setRecordDate(mealPlan.getRecordDate().toString());
        result.setMealType(mealPlan.getMealType());
        result.setTotalCount(mealPlan.getTotalCount());
        result.setSuccessCount(mealPlan.getSuccessCount());
        result.setFailCount(mealPlan.getFailCount());
        result.setFailDetails(failDetails);
        return result;
    }

    /**
     * 注册事务完成后的锁释放逻辑，避免在事务提交前释放同 key 生成锁。
     */
    private void registerLockCleanup(String lockKey, Lock lock) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            lock.unlock();
            GENERATE_LOCKS.remove(lockKey, lock);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                lock.unlock();
                if (lock instanceof ReentrantLock && !((ReentrantLock) lock).hasQueuedThreads()) {
                    GENERATE_LOCKS.remove(lockKey, lock);
                }
            }
        });
    }

    /**
     * 构造同日期同餐次的本地锁 key。
     */
    private String buildGenerateLockKey(LocalDate recordDate, String mealType) {
        return recordDate + "#" + mealType;
    }

    /**
     * 判断菜品配置的套餐标识是否匹配当前父套餐ID或套餐编码。
     */
    private boolean matchesParentPackage(List<String> mealPackages, Long parentPackageId, String packageCode) {
        if (mealPackages == null || mealPackages.isEmpty()) {
            return false;
        }
        String numericId = String.valueOf(parentPackageId);
        for (String mealPackage : mealPackages) {
            if (numericId.equals(mealPackage)) {
                return true;
            }
            if (packageCode != null && packageCode.equals(mealPackage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析订单中的配送日期JSON数组，异常时降级为空列表。
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return JSON.parseArray(json, String.class);
        } catch (Exception e) {
            log.warn("解析deliveryDates失败: {}", json);
            return Collections.emptyList();
        }
    }

    private static class CustomerMealPlan {
        private List<SelectedDish> selectedDishes = new ArrayList<>();

        public List<SelectedDish> getSelectedDishes() {
            return selectedDishes;
        }

        public void setSelectedDishes(List<SelectedDish> selectedDishes) {
            this.selectedDishes = selectedDishes;
        }
    }

    private static class SelectedDish {
        private final String dishType;
        private final Dish dish;

        private SelectedDish(String dishType, Dish dish) {
            this.dishType = dishType;
            this.dish = dish;
        }

        public String getDishType() {
            return dishType;
        }

        public Dish getDish() {
            return dish;
        }
    }
}
