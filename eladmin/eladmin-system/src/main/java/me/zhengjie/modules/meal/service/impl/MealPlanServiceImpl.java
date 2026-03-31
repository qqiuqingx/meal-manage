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

    private String validateParams(String mealType) {
        if (!MEAL_TYPE_LUNCH.equals(mealType) && !MEAL_TYPE_DINNER.equals(mealType)) {
            throw new BadRequestException("餐次仅支持LUNCH或DINNER");
        }
        return mealType;
    }

    private void softDeleteExistingPlan(LocalDate recordDate, String mealType) {
        MealPlan existingPlan = mealPlanMapper.findActiveByDateAndMealTypeForUpdate(recordDate, mealType);
        if (existingPlan == null) {
            return;
        }
        mealPlanMapper.softDeleteItemsByMealPlanId(existingPlan.getId());
        mealPlanMapper.softDeleteCustomersByMealPlanId(existingPlan.getId());
        mealPlanMapper.softDeletePlanById(existingPlan.getId());
    }

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

    private List<Dish> loadScheduledDishes(LocalDate targetDate, String mealType) {
        return dishMapper.findBySchedule(ScheduleKeyUtil.calcWeek(targetDate), ScheduleKeyUtil.calcDay(targetDate), mealType);
    }

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

    private int saveFailPlan(Long mealPlanId, CustomerOrder order, CustomerProfile customer, SubPackage subPackage,
                             String failReason, List<MealPlanGenerateResult.FailDetail> failDetails) {
        MealPlanCustomer entity = buildCustomerEntity(mealPlanId, order, customer, subPackage, 0, failReason);
        mealPlanCustomerMapper.insert(entity);
        MealPlanGenerateResult.FailDetail failDetail = new MealPlanGenerateResult.FailDetail();
        failDetail.setFailReason(failReason);
        failDetails.add(failDetail);
        return 1;
    }

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

    private void updateMealPlanSummary(MealPlan mealPlan, int successCount, int failCount) {
        mealPlan.setSuccessCount(successCount);
        mealPlan.setFailCount(failCount);
        mealPlan.setStatus(failCount > 0 ? PLAN_STATUS_FAILED : PLAN_STATUS_SUCCESS);
        mealPlanMapper.updateById(mealPlan);
    }

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

    private String buildGenerateLockKey(LocalDate recordDate, String mealType) {
        return recordDate + "#" + mealType;
    }

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
