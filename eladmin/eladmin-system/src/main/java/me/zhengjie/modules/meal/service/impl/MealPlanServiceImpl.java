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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.hutool.json.JSONUtil;
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
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerItemVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.domain.dto.MealPlanQueryCriteria;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerItemMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.modules.meal.util.ScheduleKeyUtil;
import me.zhengjie.utils.PageResult;
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
    public MealPlanGenerateResult generateMealPlan(String recordDate, String mealType, Long customerId) {
        long startTime = System.currentTimeMillis();
        log.info("开始生成排餐计划 - 日期: {}, 餐次: {}, 客户ID: {}", recordDate, mealType, customerId);

        LocalDate targetDate = ScheduleKeyUtil.parseDate(recordDate);
        String normalizedMealType = validateParams(mealType);
        String lockKey = buildGenerateLockKey(targetDate, normalizedMealType);
        log.debug("开始获取锁 - lockKey: {}", lockKey);

        Lock lock = GENERATE_LOCKS.computeIfAbsent(lockKey, key -> new ReentrantLock());
        lock.lock();
        log.debug("成功获取锁 - lockKey: {}", lockKey);
        registerLockCleanup(lockKey, lock);

        MealPlanGenerateResult result = doGenerateMealPlan(targetDate, normalizedMealType, customerId);

        log.info("排餐计划生成完成 - 耗时: {}ms, 计划ID: {}, 成功数: {}, 失败数: {}",
                System.currentTimeMillis() - startTime,
                result.getMealPlanId(),
                result.getSuccessCount(),
                result.getFailCount());

        return result;
    }

    /**
     * 执行排餐生成主流程：清理旧计划、加载候选数据、逐个订单生成并汇总结果。
     */
    private MealPlanGenerateResult doGenerateMealPlan(LocalDate targetDate, String mealType, Long customerId) {
        log.debug("开始执行排餐生成主流程 - 日期: {}, 餐次: {}", targetDate, mealType);

        long startTime = System.currentTimeMillis();

        // 步骤1: 清理旧计划
        log.debug("开始清理旧排餐计划");
        MealPlan existingPlan = softDeleteExistingPlan(targetDate, mealType, customerId);
        log.debug("旧排餐计划清理完成");

        // 步骤2: 加载各种数据
        log.debug("开始加载数据");
        long dataLoadStart = System.currentTimeMillis();

        List<CustomerOrder> orders = loadValidOrders(targetDate, mealType, customerId);
        log.debug("加载有效订单完成 - 订单数量: {}", orders.size());

        Map<Long, CustomerProfile> customerMap = loadCustomers(orders);
        log.debug("加载客户档案完成 - 客户数量: {}", customerMap.size());

        Map<Long, SubPackage> subPackageMap = loadSubPackages(orders);
        log.debug("加载子套餐完成 - 套餐数量: {}", subPackageMap.size());

        Map<Long, ParentPackage> parentPackageMap = loadParentPackages(orders);
        log.debug("加载父套餐完成 - 套餐数量: {}", parentPackageMap.size());

        List<Dish> scheduledDishes = loadScheduledDishes(targetDate, mealType);
        log.debug("加载排期菜品完成 - 菜品数量: {}", scheduledDishes.size());

        Map<Integer, Set<String>> dishIngredientMap = loadDishIngredients(scheduledDishes);
        log.debug("加载菜品食材完成 - 菜品-食材关联数量: {}", dishIngredientMap.size());

        Map<Long, Map<String, List<Dish>>> candidateDishMap = buildCandidateDishPool(scheduledDishes, orders, parentPackageMap);
        log.debug("构建候选菜池完成 - 父套餐数量: {}", candidateDishMap.size());

        log.debug("数据加载总耗时: {}ms", System.currentTimeMillis() - dataLoadStart);

        // 步骤3: 获取或创建排餐计划主记录（复用模式）
        log.debug("获取或创建排餐计划主记录");
        MealPlan mealPlan = getOrCreateMealPlan(targetDate, mealType, orders.size(), existingPlan);
        log.info("排餐计划主记录获取成功 - ID: {}, 总订单数: {}", mealPlan.getId(), orders.size());

        // 步骤4: 逐个处理订单
        List<MealPlanGenerateResult.FailDetail> failDetails = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        log.debug("开始处理订单，共{}个订单", orders.size());

        for (int i = 0; i < orders.size(); i++) {
            CustomerOrder order = orders.get(i);
            log.info("处理订单 {}/{} - 订单ID: {}, 客户ID: {},客户code{}, 父套餐ID: {}, 子套餐ID: {}",
                    i + 1, orders.size(),
                    order.getId(),
                    customerMap.get(order.getCustomerId()).getCustomerCode(),
                    customerMap.get(order.getCustomerId()).getCustomerName(),
                    order.getParentPackageName(),
                    subPackageMap.get(order.getChildPackageId()).getSubPackageName());

            CustomerProfile customer = customerMap.get(order.getCustomerId());
            SubPackage subPackage = subPackageMap.get(order.getChildPackageId());
            if (customer == null) {
                log.warn("订单处理失败：客户档案不存在 - 订单ID: {}, 客户ID: {}",
                        order.getId(), order.getCustomerId());
                failCount += saveFailPlan(mealPlan.getId(), order, null, null, "客户档案不存在", failDetails);
                continue;
            }
            if (subPackage == null) {
                log.warn("订单处理失败：子套餐不存在 - 订单ID: {}, 子套餐ID: {}",
                        order.getId(), order.getChildPackageId());
                failCount += saveFailPlan(mealPlan.getId(), order, customer, null, "子套餐不存在", failDetails);
                continue;
            }
            if (!parentPackageMap.containsKey(order.getParentPackageId())) {
                log.warn("订单处理失败：父套餐不存在 - 订单ID: {}, 父套餐ID: {}",
                        order.getId(), order.getParentPackageId());
                failCount += saveFailPlan(mealPlan.getId(), order, customer, subPackage, "父套餐不存在", failDetails);
                continue;
            }

            try {
                CustomerMealPlan customerPlan = buildCustomerPlan(order, customer, subPackage, candidateDishMap, dishIngredientMap,
                        targetDate, mealType, parentPackageMap);
                saveSuccessPlan(mealPlan.getId(), order, customer, subPackage, customerPlan);
                successCount++;
                log.debug("订单处理成功 - 订单ID: {}", order.getId());
            } catch (BadRequestException e) {
                log.warn("订单处理失败 - 订单ID: {}, 客户ID: {}, 失败原因: {}",
                        order.getId(), order.getCustomerId(), e.getMessage());
                failCount += saveFailPlan(mealPlan.getId(), order, customer, subPackage, e.getMessage(), failDetails);
            }
        }

        log.debug("订单处理完成 - 成功: {}, 失败: {}", successCount, failCount);

        // 步骤5: 更新汇总信息
        log.debug("更新排餐计划汇总信息");
        MealPlan latestPlan = updateMealPlanSummary(mealPlan, successCount, failCount);

        log.debug("排餐生成主流程完成 - 总耗时: {}ms", System.currentTimeMillis() - startTime);

        return buildResult(latestPlan, failDetails);
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
     * 如果指定了customerId，则只删除该客户的排餐计划详情。
     * 返回已存在的排餐计划（如果删除全部则返回null，否则返回被清理但保留的plan）
     */
    private MealPlan softDeleteExistingPlan(LocalDate recordDate, String mealType, Long customerId) {
        log.debug("查询现有排餐计划 - 日期: {}, 餐次: {}, 客户ID: {}", recordDate, mealType, customerId);
        MealPlan existingPlan = mealPlanMapper.findActiveByDateAndMealTypeForUpdate(recordDate, mealType);
        if (existingPlan == null) {
            log.info("未找到现有排餐计划，跳过清理 - 日期: {}, 餐次: {}", recordDate, mealType);
            return null;
        }

        log.info("找到现有排餐计划，开始软删除 - 计划ID: {}, 日期: {}, 餐次: {}",
                existingPlan.getId(), recordDate, mealType);

        // 如果指定了客户ID，只删除该客户的排餐计划详情，保留主记录
        if (customerId != null) {
            List<Long> customerPlanIds = mealPlanMapper.findCustomerPlanIdsByMealPlanIdAndCustomerId(existingPlan.getId(), customerId);
            if (!customerPlanIds.isEmpty()) {
                log.debug("软删除指定客户的排餐计划明细 - 客户计划ID数量: {}", customerPlanIds.size());
                mealPlanCustomerItemMapper.softDeleteByCustomerPlanIds(customerPlanIds);
                log.debug("软删除指定客户的排餐计划 - 客户计划ID数量: {}", customerPlanIds.size());
                mealPlanCustomerMapper.softDeleteByIds(customerPlanIds);
                log.info("指定客户排餐计划软删除完成 - 客户计划ID数量: {}", customerPlanIds.size());
            } else {
                log.info("未找到指定客户的排餐计划，跳过清理 - 客户ID: {}", customerId);
            }
            refreshMealPlanSummary(existingPlan, false);
            // 保留主记录，返回给后续复用
            return existingPlan;
        } else {
            // 未指定客户ID，删除全部
            log.debug("软删除排餐计划明细表 - 计划ID: {}", existingPlan.getId());
            mealPlanMapper.softDeleteItemsByMealPlanId(existingPlan.getId());

            log.debug("软删除排餐计划客户表 - 计划ID: {}", existingPlan.getId());
            mealPlanMapper.softDeleteCustomersByMealPlanId(existingPlan.getId());

            log.debug("软删除排餐计划主表 - 计划ID: {}", existingPlan.getId());
            mealPlanMapper.softDeletePlanById(existingPlan.getId());

            log.info("现有排餐计划软删除完成 - 计划ID: {}", existingPlan.getId());
            // 主记录被删除，返回null
            return null;
        }
    }

    /**
     * 获取或创建排餐计划主记录。
     * 如果已有排餐计划则复用，否则创建新的。
     */
    private MealPlan getOrCreateMealPlan(LocalDate recordDate, String mealType, int totalCount, MealPlan existingPlan) {
        if (existingPlan != null) {
            // 复用已有排餐计划，保留剩余客户的统计信息
            log.info("复用已有排餐计划 - ID: {}", existingPlan.getId());
            existingPlan.setStatus(PLAN_STATUS_GENERATING);
            existingPlan.setGenerateTime(new Timestamp(System.currentTimeMillis()));
            mealPlanMapper.updateById(existingPlan);
            return existingPlan;
        } else {
            // 创建新的排餐计划
            return createMealPlan(recordDate, mealType, totalCount);
        }
    }

    /**
     * 查询满足日期、餐次和配送规则的订单候选。
     */
    private List<CustomerOrder> loadValidOrders(LocalDate targetDate, String mealType, Long customerId) {
        long startTime = System.currentTimeMillis();
        log.debug("查询候选订单 - 日期: {}, 餐次: {}, 客户ID: {}", targetDate, mealType, customerId);

        List<CustomerOrder> candidateOrders = customerOrderMapper.findMealPlanOrders(targetDate, mealType);
        log.debug("查询到候选订单 - 数量: {}", candidateOrders.size());

        List<CustomerOrder> validOrders = new ArrayList<>();
        for (CustomerOrder order : candidateOrders) {
            if (customerId != null && !Objects.equals(order.getCustomerId(), customerId)) {
                continue;
            }
            if (!scheduleModeMatches(order, targetDate)) {
                log.debug("订单不匹配配送模式 - 订单ID: {}, 配送模式: {}",
                        order.getId(), order.getScheduleMode());
                continue;
            }
            validOrders.add(order);
        }

        log.debug("有效订单过滤完成 - 候选数: {}, 有效数: {}, 耗时: {}ms",
                candidateOrders.size(), validOrders.size(),
                System.currentTimeMillis() - startTime);

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
            log.debug("订单列表为空，无需加载客户档案");
            return Collections.emptyMap();
        }

        long startTime = System.currentTimeMillis();
        Set<Long> customerIds = orders.stream().map(CustomerOrder::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());

        if (customerIds.isEmpty()) {
            log.debug("订单中没有有效的客户ID，无需加载客户档案");
            return Collections.emptyMap();
        }

        log.debug("批量查询客户档案 - 客户ID数量: {}", customerIds.size());
        Map<Long, CustomerProfile> customerMap = new HashMap<>();
        for (CustomerProfile customer : customerProfileMapper.findByIds(customerIds)) {
            customerMap.put(customer.getId(), customer);
        }

        log.debug("客户档案加载完成 - 加载数: {}, 耗时: {}ms",
                customerMap.size(), System.currentTimeMillis() - startTime);
        return customerMap;
    }

    /**
     * 按订单子套餐ID批量加载套餐数据。
     */
    private Map<Long, SubPackage> loadSubPackages(List<CustomerOrder> orders) {
        Set<Long> ids = orders.stream().map(CustomerOrder::getChildPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            log.debug("订单中没有有效的子套餐ID，无需加载子套餐数据");
            return Collections.emptyMap();
        }

        long startTime = System.currentTimeMillis();
        Map<Long, SubPackage> packageMap = new HashMap<>();
        for (SubPackage subPackage : subPackageMapper.selectBatchIds(ids)) {
            packageMap.put(subPackage.getId(), subPackage);
        }

        log.debug("子套餐加载完成 - 套餐ID数量: {}, 加载数: {}, 耗时: {}ms",
                ids.size(), packageMap.size(), System.currentTimeMillis() - startTime);
        return packageMap;
    }

    /**
     * 按订单父套餐ID批量加载套餐数据。
     */
    private Map<Long, ParentPackage> loadParentPackages(List<CustomerOrder> orders) {
        Set<Long> ids = orders.stream().map(CustomerOrder::getParentPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            log.debug("订单中没有有效的父套餐ID，无需加载父套餐数据");
            return Collections.emptyMap();
        }

        long startTime = System.currentTimeMillis();
        Map<Long, ParentPackage> packageMap = new HashMap<>();
        for (ParentPackage parentPackage : parentPackageMapper.selectBatchIds(ids)) {
            packageMap.put(parentPackage.getId(), parentPackage);
        }

        log.debug("父套餐加载完成 - 套餐ID数量: {}, 加载数: {}, 耗时: {}ms",
                ids.size(), packageMap.size(), System.currentTimeMillis() - startTime);
        return packageMap;
    }

    /**
     * 按父套餐归类候选菜池，供后续客户排餐复用。
     */
    private Map<Long, Map<String, List<Dish>>> buildCandidateDishPool(List<Dish> scheduledDishes, List<CustomerOrder> orders,
                                                                     Map<Long, ParentPackage> parentPackageMap) {
        Set<Long> parentPackageIds = orders.stream().map(CustomerOrder::getParentPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (parentPackageIds.isEmpty() || scheduledDishes.isEmpty()) {
            log.debug("父套餐ID为空或排期菜品为空，跳过构建候选菜池");
            return Collections.emptyMap();
        }

        long startTime = System.currentTimeMillis();
        Map<Long, Map<String, List<Dish>>> candidateDishMap = new HashMap<>();

        log.debug("开始构建候选菜池 - 父套餐ID数量: {}, 排期菜品数量: {}",
                parentPackageIds.size(), scheduledDishes.size());

        for (Long parentPackageId : parentPackageIds) {
            ParentPackage parentPackage = parentPackageMap.get(parentPackageId);
            String packageCode = parentPackage != null ? parentPackage.getPackageCode() : null;
            Map<String, List<Dish>> dishTypeMap = new HashMap<>();

            int matchedDishes = 0;
            for (Dish dish : scheduledDishes) {
                if (!matchesParentPackage(dish.getMealPackages(), parentPackageId, packageCode)) {
                    continue;
                }
                dishTypeMap.computeIfAbsent(dish.getDishType(), key -> new ArrayList<>()).add(dish);
                matchedDishes++;
            }

            sortDishTypeMap(dishTypeMap);
            candidateDishMap.put(parentPackageId, dishTypeMap);

            log.debug("父套餐 {} 的候选菜池构建完成 - 匹配菜品数: {}, 菜品类型数: {}",
                    parentPackageId, matchedDishes, dishTypeMap.size());
        }

        log.debug("候选菜池构建完成 - 父套餐数: {}, 总耗时: {}ms",
                candidateDishMap.size(), System.currentTimeMillis() - startTime);
        return candidateDishMap;
    }

    /**
     * 按排期和餐次查询当日候选菜品。
     */
    private List<Dish> loadScheduledDishes(LocalDate targetDate, String mealType) {
        long startTime = System.currentTimeMillis();
        log.debug("查询排期菜品 - 周序号: {}, 星期: {}, 餐次: {}",
                ScheduleKeyUtil.calcWeek(targetDate),
                ScheduleKeyUtil.calcDay(targetDate),
                mealType);

        List<Dish> scheduledDishes = dishMapper.findBySchedule(
                ScheduleKeyUtil.calcWeek(targetDate),
                ScheduleKeyUtil.calcDay(targetDate),
                mealType);

        log.debug("排期菜品查询完成 - 数量: {}, 耗时: {}ms",
                scheduledDishes.size(), System.currentTimeMillis() - startTime);
        return scheduledDishes;
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
            log.debug("菜品列表为空，无需加载食材信息");
            return Collections.emptyMap();
        }

        long startTime = System.currentTimeMillis();
        List<DishIngredientRelation> relations = dishIngredientMapper.findRelationsByDishIds(dishIds);
       

        Map<Integer, Set<String>> dishIngredients = new HashMap<>();
        for (DishIngredientRelation relation : relations) {
            dishIngredients.computeIfAbsent(relation.getDishId(), key -> new LinkedHashSet<>()).add(relation.getIngredientName());
        }

        log.debug("菜品食材加载完成 - 菜品数: {}, 食材关联数: {}, 耗时: {}ms",
                dishIds.size(), dishIngredients.size(), System.currentTimeMillis() - startTime);
        return dishIngredients;
    }

    /**
     * 为单个订单生成具体菜品组合。
     */
    private CustomerMealPlan buildCustomerPlan(CustomerOrder order, CustomerProfile customer, SubPackage subPackage,
                                               Map<Long, Map<String, List<Dish>>> candidateDishMap,
                                               Map<Integer, Set<String>> dishIngredientMap,
                                               LocalDate targetDate, String mealType,
                                               Map<Long, ParentPackage> parentPackageMap) {
        Map<String, List<Dish>> dishTypeMap = candidateDishMap.get(order.getParentPackageId());
        if (dishTypeMap == null || dishTypeMap.isEmpty()) {
            throw new BadRequestException("当前父套餐没有可用候选菜");
        }
        // log.info("buildCustomerPlan_dishTypeMap:"+JSONUtil.toJsonStr(dishTypeMap));
        List<String> allergyTags = customer.getAllergyTags() == null ? Collections.emptyList() : customer.getAllergyTags();
        Set<Integer> selectedDishIds = new HashSet<>();
        List<SelectedDish> selectedDishes = new ArrayList<>();
        pickRequiredDishes(subPackage.getMeatCount(), selectedDishes, selectedDishIds, dishTypeMap, allergyTags,
                dishIngredientMap, targetDate, mealType, order.getParentPackageId(), parentPackageMap);
        pickVegetables(subPackage.getVegCount(), selectedDishes, selectedDishIds, dishTypeMap, allergyTags,
                dishIngredientMap, targetDate, mealType, order.getParentPackageId(), parentPackageMap);
        pickOptionalDish(Boolean.TRUE.equals(subPackage.getIncludeSoup()), DISH_TYPE_SOUP, selectedDishes, selectedDishIds,
                dishTypeMap, allergyTags, dishIngredientMap, targetDate, mealType, order.getParentPackageId(), parentPackageMap);
        pickOptionalDish(Boolean.TRUE.equals(subPackage.getIncludeRice()), DISH_TYPE_RICE, selectedDishes, selectedDishIds,
                dishTypeMap, allergyTags, dishIngredientMap, targetDate, mealType, order.getParentPackageId(), parentPackageMap);
        CustomerMealPlan customerMealPlan = new CustomerMealPlan();
        customerMealPlan.setSelectedDishes(selectedDishes);
        return customerMealPlan;
    }

    /**
     * 选择荤菜，支持单荤和主副菜组合两种配置，带过敏回退逻辑。
     */
    private void pickRequiredDishes(Integer meatCount, List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                                    Map<String, List<Dish>> dishTypeMap, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap,
                                    LocalDate targetDate, String mealType, Long parentPackageId, Map<Long, ParentPackage> parentPackageMap) {
        int requiredCount = meatCount == null ? 0 : meatCount;
        if (requiredCount <= 0) {
            return;
        }
        if (requiredCount == 1) {
            // 一荤: MAIN -> SIDE -> 次日MAIN -> 次日SIDE
            DishSelectResult result = selectDishWithFallback(DISH_TYPE_MAIN, DISH_TYPE_SIDE, selectedDishIds, allergyTags,
                    dishTypeMap, dishIngredientMap, targetDate, mealType, parentPackageId, parentPackageMap);
            if (result == null || result.getDish() == null) {
                throw new BadRequestException("荤菜不足");
            }
            selectedDishes.add(new SelectedDish(result.getDish().getDishType(), result.getDish(),
                    result.isReplaced(), result.getOriginalDish(), result.getReplaceReason()));
            selectedDishIds.add(result.getDish().getId());
            return;
        }
        if (requiredCount != 2) {
            throw new BadRequestException("荤菜数量配置不支持");
        }
        // 二荤一素: 需要同时有MAIN和SIDE
        // 尝试从当日选择MAIN和SIDE
        Dish mainDish = selectDish(dishTypeMap.get(DISH_TYPE_MAIN), selectedDishIds, allergyTags, dishIngredientMap);
        Dish sideDish = selectDish(dishTypeMap.get(DISH_TYPE_SIDE), selectedDishIds, allergyTags, dishIngredientMap);

        // 记录原本想选的菜品
        Dish originalMainDish = mainDish;
        Dish originalSideDish = sideDish;
        String mainReplaceReason = null;
        String sideReplaceReason = null;

        // 回退1: MAIN过敏，用SIDE作为MAIN
        if (mainDish == null && sideDish != null) {
            log.info("主菜全部过敏，改用副菜作为主菜");
            originalMainDish = getFirstAvailableDish(dishTypeMap.get(DISH_TYPE_MAIN), selectedDishIds, dishIngredientMap);
            mainReplaceReason = "ALLERGY";
            mainDish = sideDish;
            sideDish = null; // 重新选择SIDE
        }

        // 回退2: 重新选择SIDE
        if (sideDish == null) {
            sideDish = selectDish(dishTypeMap.get(DISH_TYPE_SIDE), selectedDishIds, allergyTags, dishIngredientMap);
        }

        // 回退3: 当日不足，尝试次日菜品
        if (mainDish == null || sideDish == null) {
            log.warn("当日荤菜不足，尝试次日菜品");
            Map<String, List<Dish>> nextDayDishTypeMap = loadNextDayDishTypeMap(targetDate, mealType, parentPackageId, parentPackageMap);
            Map<Integer, Set<String>> nextDayIngredients = loadDishIngredients(
                    nextDayDishTypeMap.values().stream().flatMap(List::stream).collect(Collectors.toList()));

            if (mainDish == null) {
                if (originalMainDish == null) {
                    originalMainDish = getFirstAvailableDish(dishTypeMap.get(DISH_TYPE_MAIN), selectedDishIds, dishIngredientMap);
                }
                mainDish = selectDish(nextDayDishTypeMap.get(DISH_TYPE_MAIN), selectedDishIds, allergyTags, nextDayIngredients);
                if (mainDish != null) {
                    log.info("次日主菜选择成功 - 菜品: {}", mainDish.getName());
                    mainReplaceReason = mainReplaceReason == null ? "NEXT_DAY" : mainReplaceReason;
                }
            }
            if (sideDish == null) {
                if (originalSideDish == null) {
                    originalSideDish = getFirstAvailableDish(dishTypeMap.get(DISH_TYPE_SIDE), selectedDishIds, dishIngredientMap);
                }
                sideDish = selectDish(nextDayDishTypeMap.get(DISH_TYPE_SIDE), selectedDishIds, allergyTags, nextDayIngredients);
                if (sideDish != null) {
                    log.info("次日副菜选择成功 - 菜品: {}", sideDish.getName());
                    sideReplaceReason = sideReplaceReason == null ? "NEXT_DAY" : sideReplaceReason;
                }
            }
        }

        if (mainDish == null || sideDish == null) {
            throw new BadRequestException("荤菜不足，必须同时选出主菜和副菜");
        }
        selectedDishes.add(new SelectedDish(DISH_TYPE_MAIN, mainDish,
                mainReplaceReason != null, originalMainDish, mainReplaceReason));
        selectedDishes.add(new SelectedDish(DISH_TYPE_SIDE, sideDish,
                sideReplaceReason != null, originalSideDish, sideReplaceReason));
        selectedDishIds.add(mainDish.getId());
        selectedDishIds.add(sideDish.getId());
    }

    /**
     * 选择素菜，带过敏回退逻辑。
     */
    private void pickVegetables(Integer vegCount, List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                                Map<String, List<Dish>> dishTypeMap, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap,
                                LocalDate targetDate, String mealType, Long parentPackageId, Map<Long, ParentPackage> parentPackageMap) {
        int requiredCount = vegCount == null ? 0 : vegCount;
        for (int i = 0; i < requiredCount; i++) {
            DishSelectResult result = selectDishWithFallback(DISH_TYPE_VEGETABLE, null, selectedDishIds, allergyTags,
                    dishTypeMap, dishIngredientMap, targetDate, mealType, parentPackageId, parentPackageMap);
            if (result == null || result.getDish() == null) {
                throw new BadRequestException("素菜不足");
            }
            selectedDishes.add(new SelectedDish(DISH_TYPE_VEGETABLE, result.getDish(),
                    result.isReplaced(), result.getOriginalDish(), result.getReplaceReason()));
            selectedDishIds.add(result.getDish().getId());
        }
    }

    /**
     * 在套餐配置要求时补选汤或米饭等可选菜品，带过敏回退逻辑。
     */
    private void pickOptionalDish(boolean required, String dishType, List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                                  Map<String, List<Dish>> dishTypeMap, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap,
                                  LocalDate targetDate, String mealType, Long parentPackageId, Map<Long, ParentPackage> parentPackageMap) {
        if (!required) {
            return;
        }
        // log.info("pickOptionalDish_dishTypeMap:"+JSONUtil.toJsonStr(dishTypeMap));
        DishSelectResult result = selectDishWithFallback(dishType, null, selectedDishIds, allergyTags,
                dishTypeMap, dishIngredientMap, targetDate, mealType, parentPackageId, parentPackageMap);
        if (result == null || result.getDish() == null) {
            throw new BadRequestException(dishType + "类型菜品不足");
        }
        selectedDishes.add(new SelectedDish(dishType, result.getDish(),
                result.isReplaced(), result.getOriginalDish(), result.getReplaceReason()));
        selectedDishIds.add(result.getDish().getId());
    }

    /**
     * 带回退的菜品选择：优先从首选类型选择，如果全部过敏则从次选类型选择，再不行则尝试次日菜品。
     * @param primaryType 首选菜品类型 (如 MAIN, VEGETABLE, SOUP 等)
     * @param fallbackType 次选菜品类型 (如 SIDE)，可以为 null
     * @param selectedDishIds 已选中的菜品ID集合
     * @param allergyTags 客户过敏标签
     * @param dishTypeMap 当日菜品类型映射
     * @param dishIngredientMap 菜品食材映射
     * @param targetDate 目标日期
     * @param mealType 餐次
     * @param parentPackageId 父套餐ID
     * @param parentPackageMap 父套餐映射
     * @return 菜品选择结果，包含选中的菜品和替换信息
     */
    private DishSelectResult selectDishWithFallback(String primaryType, String fallbackType, Set<Integer> selectedDishIds,
                                       List<String> allergyTags, Map<String, List<Dish>> dishTypeMap,
                                       Map<Integer, Set<String>> dishIngredientMap,
                                       LocalDate targetDate, String mealType, Long parentPackageId,
                                       Map<Long, ParentPackage> parentPackageMap) {
        // 尝试首选类型
        Dish dish = selectDish(dishTypeMap.get(primaryType), selectedDishIds, allergyTags, dishIngredientMap);
        if (dish != null) {
            return DishSelectResult.noReplace(dish);
        }

        // 尝试次选类型
        if (fallbackType != null) {
            // 记录原本想选的菜品（首选类型的第一个候选）
            Dish originalDish = getFirstAvailableDish(dishTypeMap.get(primaryType), selectedDishIds, dishIngredientMap);
            log.info("{}类型菜品全部过敏，改用{}类型", primaryType, fallbackType);
            dish = selectDish(dishTypeMap.get(fallbackType), selectedDishIds, allergyTags, dishIngredientMap);
            if (dish != null) {
                return DishSelectResult.withReplace(dish, originalDish, "ALLERGY");
            }
        }

        // 尝试次日菜品
        log.warn("当日{}类型菜品不足，尝试次日菜品", primaryType);
        Map<String, List<Dish>> nextDayDishTypeMap = loadNextDayDishTypeMap(targetDate, mealType, parentPackageId, parentPackageMap);
        if (nextDayDishTypeMap.isEmpty()) {
            return null;
        }

        Map<Integer, Set<String>> nextDayIngredients = loadDishIngredients(
                nextDayDishTypeMap.values().stream().flatMap(List::stream).collect(Collectors.toList()));

        // 记录原本想选的菜品
        Dish originalDish = getFirstAvailableDish(dishTypeMap.get(primaryType), selectedDishIds, dishIngredientMap);
        if (originalDish == null && fallbackType != null) {
            originalDish = getFirstAvailableDish(dishTypeMap.get(fallbackType), selectedDishIds, dishIngredientMap);
        }

        // 优先尝试次日首选类型
        dish = selectDish(nextDayDishTypeMap.get(primaryType), selectedDishIds, allergyTags, nextDayIngredients);
        if (dish != null) {
            log.info("次日{}类型选择成功 - 菜品: {}", primaryType, dish.getName());
            return DishSelectResult.withReplace(dish, originalDish, "NEXT_DAY");
        }

        // 如果次选类型存在，尝试次日次选类型
        if (fallbackType != null) {
            dish = selectDish(nextDayDishTypeMap.get(fallbackType), selectedDishIds, allergyTags, nextDayIngredients);
            if (dish != null) {
                log.info("次日{}类型选择成功 - 菜品: {}", fallbackType, dish.getName());
                return DishSelectResult.withReplace(dish, originalDish, "NEXT_DAY");
            }
        }

        return null;
    }

    /**
     * 获取候选列表中第一个可用的菜品（未选中且不过敏）
     */
    private Dish getFirstAvailableDish(List<Dish> dishes, Set<Integer> selectedDishIds, Map<Integer, Set<String>> dishIngredientMap) {
        if (dishes == null || dishes.isEmpty()) {
            return null;
        }
        for (Dish dish : dishes) {
            if (!selectedDishIds.contains(dish.getId())) {
                return dish;
            }
        }
        return null;
    }

    /**
     * 加载次日菜品并按父套餐过滤，构建菜品类型映射。
     */
    private Map<String, List<Dish>> loadNextDayDishTypeMap(LocalDate targetDate, String mealType,
                                                            Long parentPackageId, Map<Long, ParentPackage> parentPackageMap) {
        LocalDate nextDate = targetDate.plusDays(1);
        log.debug("加载次日排期菜品 - 日期: {}, 餐次: {}", nextDate, mealType);

        List<Dish> nextDayDishes = loadScheduledDishes(nextDate, mealType);
        if (nextDayDishes.isEmpty()) {
            return Collections.emptyMap();
        }

        ParentPackage parentPackage = parentPackageMap.get(parentPackageId);
        String packageCode = parentPackage != null ? parentPackage.getPackageCode() : null;

        Map<String, List<Dish>> dishTypeMap = new HashMap<>();
        for (Dish dish : nextDayDishes) {
            if (!matchesParentPackage(dish.getMealPackages(), parentPackageId, packageCode)) {
                continue;
            }
            dishTypeMap.computeIfAbsent(dish.getDishType(), k -> new ArrayList<>()).add(dish);
        }
        sortDishTypeMap(dishTypeMap);

        log.debug("次日菜品加载完成 - 匹配菜品数: {}, 类型数: {}", nextDayDishes.size(), dishTypeMap.size());
        return dishTypeMap;
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
                log.warn("过敏菜品：{}",JSONUtil.toJsonStr(dish));
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
                    log.warn("过敏--{}",allergyTag);
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
        log.debug("保存成功排餐计划 - 计划ID: {}, 订单ID: {}, 客户ID: {}",
                mealPlanId, order.getId(), order.getCustomerId());

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
            // 记录替换信息
            if (selectedDish.isReplaced()) {
                item.setIsReplaced(true);
                if (selectedDish.getOriginalDish() != null) {
                    item.setOriginalDishId(selectedDish.getOriginalDish().getId());
                    item.setOriginalDishName(selectedDish.getOriginalDish().getName());
                }
                item.setReplaceReason(selectedDish.getReplaceReason());
            }
            mealPlanCustomerItemMapper.insert(item);

            log.debug("保存客户菜品明细 - 客户计划ID: {}, 菜品ID: {}, 菜品类型: {}, 菜品名称: {}, 是否替换: {}, 替换原因: {}",
                    entity.getCustomerName(), selectedDish.getDish().getId(),
                    selectedDish.getDishType(), selectedDish.getDish().getName(),
                    selectedDish.isReplaced(), selectedDish.getReplaceReason());
        }

        log.debug("成功排餐计划保存完成 - 客户计划ID: {}, 选菜数量: {}",
                entity.getId(), customerPlan.getSelectedDishes().size());
    }

    /**
     * 记录单个订单的失败结果，并追加失败原因到返回结果。
     */
    private int saveFailPlan(Long mealPlanId, CustomerOrder order, CustomerProfile customer, SubPackage subPackage,
                             String failReason, List<MealPlanGenerateResult.FailDetail> failDetails) {
        log.debug("保存失败排餐计划 - 计划ID: {}, 订单ID: {}, 客户ID: {}, 失败原因: {}",
                mealPlanId, order.getId(),
                customer != null ? customer.getId() : order.getCustomerId(),
                failReason);

        MealPlanCustomer entity = buildCustomerEntity(mealPlanId, order, customer, subPackage, 0, failReason);
        mealPlanCustomerMapper.insert(entity);

        MealPlanGenerateResult.FailDetail failDetail = new MealPlanGenerateResult.FailDetail();
        failDetail.setFailReason(failReason);
        failDetails.add(failDetail);

        log.warn("排餐计划保存失败 - 客户计划ID: {}, 失败原因: {}", entity.getId(), failReason);
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
        log.info("创建排餐计划主记录 - 日期: {}, 餐次: {}, 订单总数: {}", recordDate, mealType, totalCount);

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

        log.info("排餐计划主记录创建成功 - 计划ID: {}", mealPlan.getId());
        return mealPlan;
    }

    /**
     * 回填排餐统计信息并更新最终状态。
     */
    private MealPlan updateMealPlanSummary(MealPlan mealPlan, int successCount, int failCount) {
        log.debug("更新排餐计划汇总信息 - 计划ID: {}, 成功数: {}, 失败数: {}", mealPlan.getId(), successCount, failCount);

        // 重新查询最新的排餐计划，获取现有统计（因为可能是复用模式）
        MealPlan latestPlan = mealPlanMapper.selectById(mealPlan.getId());
        if (latestPlan == null) {
            log.error("排餐计划不存在 - ID: {}", mealPlan.getId());
            return mealPlan;
        }

        int baseSuccess = latestPlan.getSuccessCount() == null ? 0 : latestPlan.getSuccessCount();
        int baseFail = latestPlan.getFailCount() == null ? 0 : latestPlan.getFailCount();
        int totalSuccess = baseSuccess + successCount;
        int totalFail = baseFail + failCount;

        latestPlan.setTotalCount(totalSuccess + totalFail);
        latestPlan.setSuccessCount(totalSuccess);
        latestPlan.setFailCount(totalFail);
        latestPlan.setStatus(totalFail > 0 ? PLAN_STATUS_FAILED : PLAN_STATUS_SUCCESS);
        mealPlanMapper.updateById(latestPlan);

        String statusText = totalFail > 0 ? "部分成功" : "全部成功";
        log.info("排餐计划汇总信息更新完成 - 计划ID: {}, 最终状态: {}, 成功: {}, 失败: {}",
                latestPlan.getId(), statusText, totalSuccess, totalFail);
        return latestPlan;
    }

    /**
     * 基于当前有效客户计划重算父计划统计信息。
     */
    private MealPlan refreshMealPlanSummary(MealPlan mealPlan, boolean deletePlanWhenEmpty) {
        MealPlan latestPlan = mealPlanMapper.selectById(mealPlan.getId());
        if (latestPlan == null) {
            log.warn("排餐计划不存在，跳过汇总重算 - 计划ID: {}", mealPlan.getId());
            return null;
        }

        List<MealPlanCustomer> customerPlans = mealPlanCustomerMapper.selectByMealPlanId(latestPlan.getId());
        if (customerPlans.isEmpty()) {
            if (deletePlanWhenEmpty) {
                mealPlanMapper.softDeletePlanById(latestPlan.getId());
                log.info("排餐计划下已无客户记录，软删除主计划 - 计划ID: {}", latestPlan.getId());
                return null;
            }
            latestPlan.setTotalCount(0);
            latestPlan.setSuccessCount(0);
            latestPlan.setFailCount(0);
            latestPlan.setStatus(PLAN_STATUS_GENERATING);
            mealPlanMapper.updateById(latestPlan);
            return latestPlan;
        }

        int totalCount = customerPlans.size();
        int successCount = (int) customerPlans.stream()
                .filter(plan -> Integer.valueOf(1).equals(plan.getStatus()))
                .count();
        int failCount = totalCount - successCount;

        latestPlan.setTotalCount(totalCount);
        latestPlan.setSuccessCount(successCount);
        latestPlan.setFailCount(failCount);
        latestPlan.setStatus(failCount > 0 ? PLAN_STATUS_FAILED : PLAN_STATUS_SUCCESS);
        mealPlanMapper.updateById(latestPlan);
        log.info("排餐计划汇总重算完成 - 计划ID: {}, 总数: {}, 成功: {}, 失败: {}",
                latestPlan.getId(), totalCount, successCount, failCount);
        return latestPlan;
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

    /**
     * 菜品选择结果，包含选中的菜品和替换信息
     */
    private static class DishSelectResult {
        private final Dish dish;
        private final boolean isReplaced;
        private final Dish originalDish;
        private final String replaceReason;

        private DishSelectResult(Dish dish, boolean isReplaced, Dish originalDish, String replaceReason) {
            this.dish = dish;
            this.isReplaced = isReplaced;
            this.originalDish = originalDish;
            this.replaceReason = replaceReason;
        }

        public static DishSelectResult noReplace(Dish dish) {
            return new DishSelectResult(dish, false, null, null);
        }

        public static DishSelectResult withReplace(Dish dish, Dish originalDish, String reason) {
            return new DishSelectResult(dish, true, originalDish, reason);
        }

        public Dish getDish() {
            return dish;
        }

        public boolean isReplaced() {
            return isReplaced;
        }

        public Dish getOriginalDish() {
            return originalDish;
        }

        public String getReplaceReason() {
            return replaceReason;
        }
    }

    private static class SelectedDish {
        private final String dishType;
        private final Dish dish;
        private final boolean isReplaced;
        private final Dish originalDish;
        private final String replaceReason;

        private SelectedDish(String dishType, Dish dish) {
            this.dishType = dishType;
            this.dish = dish;
            this.isReplaced = false;
            this.originalDish = null;
            this.replaceReason = null;
        }

        private SelectedDish(String dishType, Dish dish, boolean isReplaced, Dish originalDish, String replaceReason) {
            this.dishType = dishType;
            this.dish = dish;
            this.isReplaced = isReplaced;
            this.originalDish = originalDish;
            this.replaceReason = replaceReason;
        }

        public String getDishType() {
            return dishType;
        }

        public Dish getDish() {
            return dish;
        }

        public boolean isReplaced() {
            return isReplaced;
        }

        public Dish getOriginalDish() {
            return originalDish;
        }

        public String getReplaceReason() {
            return replaceReason;
        }
    }

    // ========== 查询接口实现 ==========

    @Override
    public PageResult<MealPlan> queryAll(MealPlanQueryCriteria criteria) {
        Page<MealPlan> page = new Page<>(criteria.getPage() , criteria.getSize());
        Page<MealPlan> result = mealPlanMapper.selectPageByCriteria(criteria, page);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public MealPlan queryById(Long id) {
        return mealPlanMapper.selectById(id);
    }

    @Override
    public PageResult<MealPlanCustomer> queryCustomers(MealPlanCustomerQueryCriteria criteria) {
        Page<MealPlanCustomer> page = new Page<>(criteria.getPage() , criteria.getSize());
        Page<MealPlanCustomer> result = mealPlanCustomerMapper.selectPageByCriteria(criteria, page);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public MealPlanCustomer queryCustomerById(Long id) {
        return mealPlanCustomerMapper.selectById(id);
    }

    @Override
    public List<MealPlanCustomerItemVO> queryCustomerItems(Long customerPlanId) {
        List<MealPlanCustomerItem> items = mealPlanCustomerItemMapper.selectByCustomerPlanId(customerPlanId);
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集所有 dishId，批量查询配料
        List<Integer> dishIds = items.stream()
                .map(MealPlanCustomerItem::getDishId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Integer, List<me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO>> finalIngredientsMap =
                buildDishIngredientsMap(dishIds);

        return items.stream()
                .map(item -> convertToVO(item, finalIngredientsMap))
                .collect(Collectors.toList());
    }

    private Map<Integer, List<me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO>> buildDishIngredientsMap(List<Integer> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<me.zhengjie.modules.meal.domain.DishIngredientRelation> relations =
                dishIngredientMapper.findRelationsByDishIds(dishIds);

        Map<Integer, List<me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO>> result = new HashMap<>();
        for (me.zhengjie.modules.meal.domain.DishIngredientRelation relation : relations) {
            me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO vo =
                    new me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO();
            vo.setIngredientId(relation.getIngredientId());
            vo.setIngredientName(relation.getIngredientName());
            vo.setUnit(relation.getUnit());
            vo.setQuantity(relation.getQuantity());
            vo.setRemark(relation.getRemark());
            result.computeIfAbsent(relation.getDishId(), k -> new ArrayList<>()).add(vo);
        }
        return result;
    }

    private MealPlanCustomerItemVO convertToVO(MealPlanCustomerItem item) {
        MealPlanCustomerItemVO vo = new MealPlanCustomerItemVO();
        vo.setId(item.getId());
        vo.setDishType(item.getDishType());
        vo.setDishId(item.getDishId());
        vo.setDishName(item.getDishName());
        vo.setSeq(item.getSeq());
        vo.setIsReplaced(item.getIsReplaced());
        vo.setOriginalDishId(item.getOriginalDishId());
        vo.setOriginalDishName(item.getOriginalDishName());
        vo.setReplaceReason(item.getReplaceReason());
        return vo;
    }

    private MealPlanCustomerItemVO convertToVO(
            MealPlanCustomerItem item,
            Map<Integer, List<me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO>> ingredientsMap) {
        MealPlanCustomerItemVO vo = convertToVO(item);
        vo.setIngredients(ingredientsMap.getOrDefault(item.getDishId(), Collections.emptyList()));
        return vo;
    }

    // ========== 删除接口实现 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMealPlan(String recordDate, String mealType, Long customerId) {
        log.info("删除排餐计划 - 日期: {}, 餐次: {}, 客户ID: {}", recordDate, mealType, customerId);
        LocalDate targetDate = ScheduleKeyUtil.parseDate(recordDate);

        // 查询要删除的计划
        MealPlan mealPlan = mealPlanMapper.selectByDateAndMealType(targetDate, mealType);
        if (mealPlan == null) {
            log.warn("未找到要删除的排餐计划 - 日期: {}, 餐次: {}", recordDate, mealType);
            return;
        }

        // 如果指定了客户ID，只删除该客户的排餐计划详情
        if (customerId != null) {
            // 查询该客户在当前计划下的客户计划ID
            List<Long> customerPlanIds = mealPlanMapper.findCustomerPlanIdsByMealPlanIdAndCustomerId(mealPlan.getId(), customerId);
            if (!customerPlanIds.isEmpty()) {
                // 级联删除该客户的明细
                mealPlanCustomerItemMapper.softDeleteByCustomerPlanIds(customerPlanIds);
                // 软删除该客户的计划
                mealPlanCustomerMapper.softDeleteByIds(customerPlanIds);
                refreshMealPlanSummary(mealPlan, true);
                log.info("删除指定客户排餐计划完成 - 客户计划ID数量: {}", customerPlanIds.size());
            } else {
                log.warn("未找到指定客户的排餐计划 - 客户ID: {}", customerId);
            }
        } else {
            // 未指定客户ID，删除全部
            // 级联删除明细
            mealPlanMapper.softDeleteItemsByMealPlanId(mealPlan.getId());
            // 级联删除客户
            mealPlanMapper.softDeleteCustomersByMealPlanId(mealPlan.getId());
            // 删除主表
            mealPlanMapper.softDeletePlanById(mealPlan.getId());
            log.info("删除排餐计划完成 - 计划ID: {}", mealPlan.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMealPlans(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            log.warn("排餐计划ID列表为空，跳过删除");
            return;
        }
        log.info("批量删除排餐计划 - 数量: {}", ids.size());

        for (Long id : ids) {
            // 级联删除明细
            mealPlanMapper.softDeleteItemsByMealPlanId(id);
            // 级联删除客户
            mealPlanMapper.softDeleteCustomersByMealPlanId(id);
            // 删除主表
            mealPlanMapper.softDeletePlanById(id);
        }

        log.info("批量删除排餐计划完成 - 数量: {}", ids.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMealPlanCustomers(List<Long> customerPlanIds) {
        if (customerPlanIds == null || customerPlanIds.isEmpty()) {
            log.warn("客户计划ID列表为空，跳过删除");
            return;
        }
        log.info("批量删除客户排餐计划 - 数量: {}", customerPlanIds.size());

        // 级联删除明细
        mealPlanCustomerItemMapper.softDeleteByCustomerPlanIds(customerPlanIds);
        // 软删除客户计划
        mealPlanCustomerMapper.softDeleteByIds(customerPlanIds);

        log.info("批量删除客户排餐计划完成 - 数量: {}", customerPlanIds.size());
    }

    // ========== 聚合查询接口实现 ==========

    @Override
    public MealPlanDetailVO queryMealPlanDetail(Long mealPlanId) {
        log.info("查询排餐计划完整详情 - 计划ID: {}", mealPlanId);

        // 查询排餐计划主表
        MealPlan mealPlan = mealPlanMapper.selectById(mealPlanId);
        if (mealPlan == null) {
            log.warn("未找到排餐计划 - ID: {}", mealPlanId);
            return null;
        }

        // 查询所有客户列表
        List<MealPlanCustomer> customers = mealPlanCustomerMapper.selectByMealPlanId(mealPlanId);
        List<Long> customerPlanIds = customers.stream()
                .map(MealPlanCustomer::getId)
                .collect(Collectors.toList());

        // 批量查询所有客户的菜品明细
        Map<Long, List<MealPlanCustomerItem>> itemsMap;
        if (!customerPlanIds.isEmpty()) {
            List<MealPlanCustomerItem> allItems = mealPlanCustomerItemMapper.selectByCustomerPlanIds(customerPlanIds);
            itemsMap = allItems.stream()
                    .collect(Collectors.groupingBy(MealPlanCustomerItem::getCustomerPlanId));
        } else {
            itemsMap = Collections.emptyMap();
        }

        // 批量查询所有菜品的配料
        List<Integer> allDishIds = Collections.emptyList();
        if (!itemsMap.isEmpty()) {
            Set<Integer> dishIds = itemsMap.values().stream()
                    .flatMap(List::stream)
                    .map(MealPlanCustomerItem::getDishId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!dishIds.isEmpty()) {
                allDishIds = new ArrayList<>(dishIds);
            }
        }
        Map<Integer, List<me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO>> finalIngredientsMap =
                buildDishIngredientsMap(allDishIds);

        // 组装返回结果
        MealPlanDetailVO result = new MealPlanDetailVO();

        // 设置主表信息
        MealPlanDetailVO.MealPlanVO planVO = new MealPlanDetailVO.MealPlanVO();
        planVO.setId(mealPlan.getId());
        planVO.setRecordDate(mealPlan.getRecordDate() != null ? mealPlan.getRecordDate().toString() : null);
        planVO.setMealType(mealPlan.getMealType());
        planVO.setTotalCount(mealPlan.getTotalCount());
        planVO.setStatus(mealPlan.getStatus());
        planVO.setGenerateTime(mealPlan.getGenerateTime() != null ? mealPlan.getGenerateTime().toString() : null);
        result.setMealPlan(planVO);

        // 设置客户列表
        List<MealPlanDetailVO.CustomerPlanDetail> customerDetails = customers.stream()
                .map(customer -> {
                    Long customerId = customer.getId();
                    MealPlanDetailVO.CustomerPlanDetail detail = new MealPlanDetailVO.CustomerPlanDetail();
                    detail.setId(customerId);
                    detail.setCustomerId(customer.getCustomerId());
                    detail.setCustomerName(customer.getCustomerName());
                    detail.setPhone(customer.getPhone());
                    detail.setCustomerCode(customer.getCustomerCode());
                    detail.setOrderId(customer.getOrderId());
                    detail.setParentPackageId(customer.getParentPackageId());
                    detail.setChildPackageId(customer.getChildPackageId());
                    detail.setStatus(customer.getStatus());
                    detail.setFailReason(customer.getFailReason());
                    detail.setMeatRequiredCount(customer.getMeatRequiredCount());
                    detail.setVegRequiredCount(customer.getVegRequiredCount());
                    detail.setIncludeSoup(customer.getIncludeSoup());
                    detail.setIncludeRice(customer.getIncludeRice());

                    // 设置菜品明细
                    List<MealPlanCustomerItem> items = itemsMap.getOrDefault(customerId, Collections.emptyList());
                    List<MealPlanCustomerItemVO> itemVOs = items.stream()
                            .map(item -> convertToVO(item, finalIngredientsMap))
                            .collect(Collectors.toList());
                    detail.setItems(itemVOs);

                    return detail;
                })
                .collect(Collectors.toList());

        result.setCustomers(customerDetails);
        result.setTotalCustomers(customerDetails.size());
        result.setSuccessCount((int) customerDetails.stream().filter(c -> c.getStatus() != null && c.getStatus() == 1).count());
        result.setFailCount((int) customerDetails.stream().filter(c -> c.getStatus() != null && c.getStatus() == 0).count());

        log.info("查询排餐计划完整详情完成 - 计划ID: {}, 客户数: {}", mealPlanId, customerDetails.size());
        return result;
    }

    @Override
    public List<MealPackageStatDto> statByDate(String date) {
        log.info("按日期统计各父套餐餐数 - 日期: {}", date);
        List<MealPackageStatDto> result = mealPlanCustomerMapper.statByDate(date);
        log.info("按日期统计各父套餐餐数完成 - 日期: {}, 结果数量: {}", date, result.size());
        return result;
    }
}
