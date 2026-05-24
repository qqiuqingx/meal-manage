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
import me.zhengjie.modules.customer.order.util.OrderStartMealTypeUtil;
import me.zhengjie.modules.customer.orderReplaceRule.domain.CustomerOrderReplaceRule;
import me.zhengjie.modules.customer.orderReplaceRule.mapper.CustomerOrderReplaceRuleMapper;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerMealScheduleAdditionMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishIngredientRelation;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.MealPlanCustomerItem;
import me.zhengjie.modules.meal.domain.dto.CustomerGeneratedMealPlanDto;
import me.zhengjie.modules.meal.domain.dto.DishQueryCriteria;
import me.zhengjie.modules.meal.domain.enums.DishTypeEnum;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerAddressVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerItemVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanCustomerQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import me.zhengjie.modules.meal.domain.dto.OrderScheduledCountDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.domain.dto.MealPlanListDetailVO;
import me.zhengjie.modules.meal.domain.dto.MealPlanQueryCriteria;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealSchedulePlanMapper;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerItemMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.modules.meal.util.ScheduleKeyUtil;
import me.zhengjie.utils.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
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
    private static final String MEAL_TYPE_BREAKFAST = "BREAKFAST";
    private static final String SCHEDULE_MODE_DAILY = "DAILY";
    private static final String SCHEDULE_MODE_WEEKDAY = "WEEKDAY";
    private static final String SCHEDULE_MODE_WEEKEND = "WEEKEND";
    private static final String SCHEDULE_MODE_SCHEDULE = "SCHEDULE";
    private static final String PLAN_STATUS_GENERATING = "GENERATING";
    private static final String PLAN_STATUS_SUCCESS = "SUCCESS";
    private static final String PLAN_STATUS_FAILED = "FAILED";
    private static final String REPLACE_REASON_EXCLUDED = "EXCLUDED";
    private static final String DISH_TYPE_MAIN = "MAIN";
    private static final String DISH_TYPE_SIDE = "SIDE";
    private static final String DISH_TYPE_VEGETABLE = "VEGETABLE";
    private static final String DISH_TYPE_SOUP = "SOUP";
    private static final String DISH_TYPE_RICE = "RICE";
    private static final String DEFAULT_RICE_TYPE = "默认";
    private static final String REPLACE_REASON_CUSTOMER_SELECTED = "客户选择替换";
    private static final String REPLACE_REASON_ORDER_RULE = "ORDER_RULE";
    private static final Map<String, ReentrantLock> GENERATE_LOCKS = new ConcurrentHashMap<>();

    private final MealPlanMapper mealPlanMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;
    private final MealPlanCustomerItemMapper mealPlanCustomerItemMapper;
    private final CustomerOrderMapper customerOrderMapper;
    private final CustomerProfileMapper customerProfileMapper;
    private final ParentPackageMapper parentPackageMapper;
    private final DishMapper dishMapper;
    private final DishIngredientMapper dishIngredientMapper;
    private final MealSchedulePlanMapper mealSchedulePlanMapper;
    private final DishIngredientCategoryService dishIngredientCategoryService;
    private final CustomerOrderReplaceRuleMapper replaceRuleMapper;
    private final CustomerMealScheduleAdditionMapper customerMealScheduleAdditionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MealPlanGenerateResult generateMealPlan(String recordDate, String mealType, Long customerId,
                                                   Integer menuWeekNum, Integer menuDayOfWeek) {
        long startTime = System.currentTimeMillis();
        log.info("开始生成排餐计划 - 日期: {}, 餐次: {}, 客户ID: {}, 菜单周次: {}, 菜单星期: {}",
                recordDate, mealType, customerId, menuWeekNum, menuDayOfWeek);

        LocalDate targetDate = ScheduleKeyUtil.parseDate(recordDate);
        String normalizedMealType = validateParams(mealType);
        MenuSlot menuSlot = resolveMenuSlot(targetDate, menuWeekNum, menuDayOfWeek);
        String lockKey = buildGenerateLockKey(targetDate, normalizedMealType);
        log.debug("开始获取锁 - lockKey: {}", lockKey);

        Lock lock = GENERATE_LOCKS.computeIfAbsent(lockKey, key -> new ReentrantLock());
        lock.lock();
        log.debug("成功获取锁 - lockKey: {}", lockKey);
        registerLockCleanup(lockKey, lock);

        MealPlanGenerateResult result = doGenerateMealPlan(targetDate, normalizedMealType, customerId, menuSlot);

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
    private MealPlanGenerateResult doGenerateMealPlan(LocalDate targetDate, String mealType, Long customerId,
                                                      MenuSlot menuSlot) {
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

        Map<Long, ParentPackage> parentPackageMap = loadParentPackages(orders);
        log.debug("加载父套餐完成 - 套餐数量: {}", parentPackageMap.size());

        List<Dish> scheduledDishes = loadScheduledDishes(menuSlot, mealType);
        log.debug("加载排期菜品完成 - 菜品数量: {}", scheduledDishes.size());

        List<Dish> riceDishes = loadRiceDishes();
        log.debug("加载固定米饭菜品完成 - 菜品数量: {}", riceDishes.size());

        List<Dish> candidateDishes = mergeCandidateDishes(scheduledDishes, riceDishes);
        Map<Integer, Set<String>> dishIngredientMap = loadDishIngredients(candidateDishes);
        log.debug("加载菜品食材完成 - 菜品-食材关联数量: {}", dishIngredientMap.size());

        Map<Long, Map<String, List<Dish>>> candidateDishMap = buildCandidateDishPool(candidateDishes, orders, parentPackageMap);
        log.debug("构建候选菜池完成 - 父套餐数量: {}", candidateDishMap.size());

        Map<String, Set<String>> categoryIngredientMap = loadCategoryIngredientMapping();
        log.debug("加载分类→配料映射完成 - 分类数量: {}", categoryIngredientMap.size());

        Map<Long, Map<Long, CustomerOrderReplaceRule>> orderReplaceRuleMap = buildOrderReplaceRuleMap(orders);
        log.debug("加载订单换菜规则完成 - 有规则的订单数: {}", orderReplaceRuleMap.size());

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
            CustomerProfile customer = customerMap.get(order.getCustomerId());
            String customerCode = customer != null ? customer.getCustomerCode() : null;
            String customerName = customer != null ? customer.getCustomerName() : null;
            log.info("处理订单 {}/{} - 订单ID: {}, 客户: {}-{}, 父套餐ID: {},餐数:{}",
                    i + 1, orders.size(),
                    order.getId(),
                    customerCode,
                    customerName,
                    order.getParentPackageId(),order.getMainDishCount()+","+order.getSideDishCount()+","+order.getVegCount()+","+order.getRiceCount()+","+order.getSoupCount());

            if (customer == null) {
                log.warn("订单处理失败：客户档案不存在 - 订单ID: {}, 客户ID: {}",
                        order.getId(), order.getCustomerId());
                failCount += saveFailPlan(mealPlan.getId(), order, null, "客户档案不存在", failDetails, mealType);
                continue;
            }
            if (!parentPackageMap.containsKey(order.getParentPackageId())) {
                log.warn("订单处理失败：父套餐不存在 - 订单ID: {}, 父套餐ID: {}",
                        order.getId(), order.getParentPackageId());
                failCount += saveFailPlan(mealPlan.getId(), order, customer, "父套餐不存在", failDetails, mealType);
                continue;
            }

            try {
                CustomerMealPlan customerPlan = buildCustomerPlan(order, customer, candidateDishMap, dishIngredientMap,
                        targetDate, mealType, parentPackageMap, categoryIngredientMap);
                applyOrderReplaceRules(order.getId(), customerPlan, orderReplaceRuleMap);
                saveSuccessPlan(mealPlan.getId(), order, customer, customerPlan, mealType);
                successCount++;
                log.debug("订单处理成功 - 订单ID: {}", order.getId());
            } catch (MealPlanBuildException e) {
                log.warn("订单处理失败 - 订单ID: {}, 客户ID: {}, 失败原因: {}",
                        order.getId(), order.getCustomerId(), e.getMessage());
                failCount += saveFailPlan(mealPlan.getId(), order, customer, e.getMessage(), failDetails, e.getCustomerPlan(), mealType);
            } catch (BadRequestException e) {
                log.warn("订单处理失败 - 订单ID: {}, 客户ID: {}, 失败原因: {}",
                        order.getId(), order.getCustomerId(), e.getMessage());
                failCount += saveFailPlan(mealPlan.getId(), order, customer, e.getMessage(), failDetails, mealType);
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
     * 校验餐次参数，仅支持 BREAKFAST / LUNCH / DINNER。
     */
    private String validateParams(String mealType) {
        if (!MEAL_TYPE_LUNCH.equals(mealType) && !MEAL_TYPE_DINNER.equals(mealType) && !MEAL_TYPE_BREAKFAST.equals(mealType)) {
            throw new BadRequestException("餐次仅支持LUNCH、DINNER或BREAKFAST");
        }
        return mealType;
    }

    private MenuSlot resolveMenuSlot(LocalDate targetDate, Integer menuWeekNum, Integer menuDayOfWeek) {
        if ((menuWeekNum == null) != (menuDayOfWeek == null)) {
            throw new BadRequestException("menuWeekNum和menuDayOfWeek必须同时传入或同时不传");
        }
        if (menuWeekNum == null) {
            MenuSlot slot = new MenuSlot(ScheduleKeyUtil.calcWeek(targetDate), ScheduleKeyUtil.calcDay(targetDate), false);
            log.info("排餐菜单槽位已确定 - source: {}, weekNum: {}, dayOfWeek: {}", "AUTO", slot.getWeekNum(), slot.getDayOfWeek());
            return slot;
        }
        if (menuWeekNum < 1 || menuWeekNum > 4) {
            throw new BadRequestException("menuWeekNum范围仅支持1-4");
        }
        if (menuDayOfWeek < 1 || menuDayOfWeek > 7) {
            throw new BadRequestException("menuDayOfWeek范围仅支持1-7");
        }
        MenuSlot slot = new MenuSlot(menuWeekNum, menuDayOfWeek, true);
        log.info("排餐菜单槽位已确定 - source: {}, weekNum: {}, dayOfWeek: {}", "MANUAL", slot.getWeekNum(), slot.getDayOfWeek());
        return slot;
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
        log.info("开始查询有效订单 - 日期: {}, 餐次: {}, 客户ID: {}", targetDate, mealType, customerId);

        // 先查询所有符合日期范围的订单，用于对比过滤情况
        List<CustomerOrder> allOrdersByDate = customerOrderMapper.findByDateRangeAndMealType(targetDate, mealType);
        log.info("符合日期范围的订单总数 - 日期: {}, 餐次: {}, 订单数: {}", targetDate, mealType, allOrdersByDate.size());

        // 记录被基础条件过滤的订单
        for (CustomerOrder order : allOrdersByDate) {
            if (order.getStatus() == null || order.getStatus() != 1) {
                log.info("订单被过滤 - 订单ID: {}, 客户ID: {}, 状态: {} (非有效状态)",
                        order.getId(), order.getCustomerId(), order.getStatus());
            } else if (order.getRemainingCount() == null || order.getRemainingCount() <= 0) {
                log.info("订单被过滤 - 订单ID: {}, 客户ID: {}, 剩余餐数: {} (餐数用完)",
                        order.getId(), order.getCustomerId(), order.getRemainingCount());
            }
        }

        List<CustomerOrder> candidateOrders = customerOrderMapper.findMealPlanOrders(targetDate, mealType);
        candidateOrders = mergeManualAdditionOrders(candidateOrders, targetDate, mealType);
        log.info("基础条件过滤后的候选订单 - 数量: {}", candidateOrders.size());

        // 批量查询各订单的已排餐数量
        Map<Long, Integer> scheduledCountMap = new HashMap<>();
        if (!candidateOrders.isEmpty()) {
            List<Long> orderIds = candidateOrders.stream().map(CustomerOrder::getId).collect(Collectors.toList());
            List<OrderScheduledCountDto> scheduledCounts =
                    mealPlanCustomerMapper.countScheduledByOrderIds(orderIds, mealType);
            for (OrderScheduledCountDto dto : scheduledCounts) {
                scheduledCountMap.put(dto.getOrderId(), dto.getScheduledCount());
            }
            log.debug("已排餐数量查询完成 - 订单数: {}, 有已排记录的订单数: {}", orderIds.size(), scheduledCountMap.size());
        }

        // 批量加载客户档案（用于排除日期检查）
        Map<Long, CustomerProfile> customerMap = new HashMap<>();
        if (!candidateOrders.isEmpty()) {
            Set<Long> customerIds = candidateOrders.stream().map(CustomerOrder::getCustomerId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!customerIds.isEmpty()) {
                for (CustomerProfile c : customerProfileMapper.findByIds(customerIds)) {
                    customerMap.put(c.getId(), c);
                }
            }
        }

        List<CustomerOrder> validOrders = new ArrayList<>();
        for (CustomerOrder order : candidateOrders) {
            if (customerId != null && !Objects.equals(order.getCustomerId(), customerId)) {
                continue;
            }
            String startMealTypeReason = getStartMealTypeMismatchReason(order, mealType, targetDate);
            if (startMealTypeReason != null) {
                log.info("订单被过滤 - 订单ID: {}, 客户名称+编号: {}, 餐次: {}, 原因: {}",
                        order.getId(), order.getCustomerName()+"-"+order.getCustomerCode(), mealType, startMealTypeReason);
                continue;
            }
            boolean manualAddition = hasManualAddition(order.getId(), targetDate, mealType);
            String matchReason = manualAddition ? null : getScheduleModeMatchReason(order, mealType,targetDate);
            if (matchReason != null) {
                log.info("订单被过滤 - 订单ID: {}, 客户名称+编号: {}, 餐次: {}, 配送模式: {}, 原因: {}",
                        order.getId(), order.getCustomerName()+"-"+order.getCustomerCode(), order.getMealType(), order.getScheduleMode(), matchReason);
                continue;
            }
            // 检查客户是否排除了该日期+餐次
            CustomerProfile customer = customerMap.get(order.getCustomerId());
            if (customer != null && customer.isExcluded(targetDate, mealType)) {
                log.info("订单被过滤 - 订单ID: {}, 客户名称+编号: {}, 日期: {}, 餐次: {}, 原因: 排除日期",
                        order.getId(), order.getCustomerName()+"-"+order.getCustomerCode(), targetDate, mealType);
                continue;
            }
            // 检查已排餐数量是否已达到订单对应餐次的餐数上限
            Integer scheduledCount = scheduledCountMap.get(order.getId());
            int currentScheduled = scheduledCount != null ? scheduledCount : 0;
            int maxCount;
            if (MEAL_TYPE_BREAKFAST.equals(mealType)) {
                maxCount = order.getBreakfastCount() != null ? order.getBreakfastCount() : 0;
            } else {
                maxCount = order.getLunchDinnerCount() != null ? order.getLunchDinnerCount() : 0;
            }
            if (currentScheduled >= maxCount) {
                log.info("订单被过滤 - 订单ID: {}, 客户名称+编号: {}, 餐次: {}, 餐数上限: {}, 已排未核销: {}, 原因: 已排餐数量已达上限",
                        order.getId(), order.getCustomerName()+"-"+order.getCustomerCode(),
                        mealType, maxCount, currentScheduled);
                continue;
            }
            validOrders.add(order);
        }

        log.info("有效订单过滤完成 - 日期: {}, 餐次: {}, 日期范围内订单: {}, 基础过滤后: {}, 最终有效数: {}, 耗时: {}ms",
                targetDate, mealType, allOrdersByDate.size(), candidateOrders.size(), validOrders.size(),
                System.currentTimeMillis() - startTime);

        return validOrders;
    }

    /**
     * 加载指定日期餐次的人工新增订单，并合并进候选订单列表。
     */
    private List<CustomerOrder> mergeManualAdditionOrders(List<CustomerOrder> candidateOrders,
                                                          LocalDate targetDate,
                                                          String mealType) {
        List<CustomerMealScheduleAddition> additions =
                customerMealScheduleAdditionMapper.selectActiveByDateMeal(targetDate, mealType);
        if (additions == null || additions.isEmpty()) {
            return candidateOrders;
        }
        Set<Long> existingOrderIds = candidateOrders.stream()
                .map(CustomerOrder::getId)
                .collect(Collectors.toSet());
        List<CustomerOrder> merged = new ArrayList<>(candidateOrders);
        for (CustomerMealScheduleAddition addition : additions) {
            if (addition == null || existingOrderIds.contains(addition.getOrderId())) {
                continue;
            }
            CustomerOrder order = customerOrderMapper.selectById(addition.getOrderId());
            if (order != null && order.getStatus() != null && order.getStatus() == 1) {
                merged.add(order);
                existingOrderIds.add(order.getId());
            }
        }
        return merged;
    }

    /**
     * 判断订单指定日期餐次是否存在人工新增记录。
     */
    private boolean hasManualAddition(Long orderId, LocalDate targetDate, String mealType) {
        return customerMealScheduleAdditionMapper.selectActiveByOrderDateMeal(orderId, targetDate, mealType) != null;
    }

    private String getStartMealTypeMismatchReason(CustomerOrder order, String targetMealType, LocalDate targetDate) {
        String normalizedStartMealType = OrderStartMealTypeUtil.normalizeStartMealType(order.getMealType(), order.getStartMealType());
        if (OrderStartMealTypeUtil.hasStartedForMeal(order.getStartDate(), normalizedStartMealType, targetDate, targetMealType)) {
            return null;
        }
        return String.format("开始餐次为%s，当前餐次%s尚未开始",
                OrderStartMealTypeUtil.mealTypeDesc(normalizedStartMealType),
                OrderStartMealTypeUtil.mealTypeDesc(targetMealType));
    }

    /**
     * 获取配送模式匹配失败的原因，如果匹配则返回 null。
     */
    private String getScheduleModeMatchReason(CustomerOrder order,String targetMealType, LocalDate targetDate) {
        String scheduleMode = order.getScheduleMode();
        if (scheduleMode == null || SCHEDULE_MODE_DAILY.equals(scheduleMode)) {
            return null; // 匹配
        }
        if (SCHEDULE_MODE_SCHEDULE.equals(scheduleMode)) {
            List<String> deliveryDates = parseDeliveryDatesWithMealTypes(order.getDeliveryDates());
            if (deliveryDates.contains(targetDate.toString())) {
                // 旧格式仅记录日期，不区分餐次；命中日期时沿用旧逻辑直接通过
                DeliveryDateWithMealTypes deliveryDateWithMealTypes = parseDElivery(order.getDeliveryDates(), targetDate.toString(), targetMealType);
                if (deliveryDateWithMealTypes == null) {
                    return null;
                }
                if (deliveryDateWithMealTypes.getMealTypes().contains(targetMealType)){
                    return null;
                }


                return  String.format("SCHEDULE模式 - 今日餐次(%s)不在配送日餐次中: %s", targetMealType, JSONUtil.toJsonStr(deliveryDateWithMealTypes.getMealTypes()));
            }


            return String.format("SCHEDULE模式 - 今日(%s)不在配送日列表中: %s", targetDate, deliveryDates);
        }
        if (SCHEDULE_MODE_WEEKDAY.equals(scheduleMode)) {
            if (ScheduleKeyUtil.isWeekday(targetDate)) {
                return null; // 匹配
            }
            return String.format("WEEKDAY模式 - 今日(%s)是周末", targetDate);
        }
        if (SCHEDULE_MODE_WEEKEND.equals(scheduleMode)) {
            if (ScheduleKeyUtil.isWeekend(targetDate)) {
                return null; // 匹配
            }
            return String.format("WEEKEND模式 - 今日(%s)是工作日", targetDate);
        }
        return String.format("未知的配送模式: %s", scheduleMode);
    }

    /**
     * 判断订单配送模式是否命中目标日期。
     */
    private boolean scheduleModeMatches(CustomerOrder order, LocalDate targetDate) {
        return getScheduleModeMatchReason(order,order.getMealType(), targetDate) == null;
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
    private Map<Long, Map<String, List<Dish>>> buildCandidateDishPool(List<Dish> candidateDishes, List<CustomerOrder> orders,
                                                                     Map<Long, ParentPackage> parentPackageMap) {
        Set<Long> parentPackageIds = orders.stream().map(CustomerOrder::getParentPackageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (parentPackageIds.isEmpty() || candidateDishes.isEmpty()) {
            log.debug("父套餐ID为空或候选菜品为空，跳过构建候选菜池");
            return Collections.emptyMap();
        }

        long startTime = System.currentTimeMillis();
        Map<Long, Map<String, List<Dish>>> candidateDishMap = new HashMap<>();

        log.debug("开始构建候选菜池 - 父套餐ID数量: {}, 候选菜品数量: {}",
                parentPackageIds.size(), candidateDishes.size());

        for (Long parentPackageId : parentPackageIds) {
            ParentPackage parentPackage = parentPackageMap.get(parentPackageId);
            String packageCode = parentPackage != null ? parentPackage.getPackageCode() : null;
            Map<String, List<Dish>> dishTypeMap = new HashMap<>();

            int matchedDishes = 0;
            for (Dish dish : candidateDishes) {
                // RICE_TYPE 米饭不绑定任何套餐，是全局通用菜品，每个父套餐都应包含
                if (DishTypeEnum.RICE_TYPE.getCode().equals(dish.getDishType())) {
                    dishTypeMap.computeIfAbsent(normalizeCandidateDishType(dish.getDishType()), key -> new ArrayList<>()).add(dish);
                    matchedDishes++;
                    continue;
                }
                if (!matchesParentPackage(dish.getMealPackages(), parentPackageId, packageCode)) {
                    continue;
                }
                dishTypeMap.computeIfAbsent(normalizeCandidateDishType(dish.getDishType()), key -> new ArrayList<>()).add(dish);
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
    private List<Dish> loadScheduledDishes(MenuSlot menuSlot, String mealType) {
        long startTime = System.currentTimeMillis();
        log.debug("查询排期菜品 - 周序号: {}, 星期: {}, 餐次: {}",
                menuSlot.getWeekNum(),
                menuSlot.getDayOfWeek(),
                mealType);

        List<Dish> scheduledDishes = mealSchedulePlanMapper.findBySchedule(
                menuSlot.getWeekNum(),
                menuSlot.getDayOfWeek(),
                mealType);
        if (scheduledDishes == null || scheduledDishes.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("排期菜品查询完成 - 数量: {}, 耗时: {}ms",
                scheduledDishes.size(), System.currentTimeMillis() - startTime);
        return scheduledDishes;
    }

    /**
     * 查询固定米饭菜品。RICE 不依赖 meal_schedule_plan，而是直接从 dish 主档读取。
     */
    private List<Dish> loadRiceDishes() {
        DishQueryCriteria criteria = new DishQueryCriteria();
        criteria.setDishType(DishTypeEnum.RICE_TYPE.getCode());

        criteria.setEnabled(true);
        List<Dish> riceDishes = dishMapper.findAll(criteria);
        if (riceDishes == null || riceDishes.isEmpty()) {
            return Collections.emptyList();
        }
        return riceDishes;
    }

    /**
     * 特殊米饭类型只用于主档和订单选择，进入排餐后统一按 RICE 处理。
     */
    private String normalizeCandidateDishType(String dishType) {
        if (DishTypeEnum.RICE_TYPE.getCode().equals(dishType)) {
            return DISH_TYPE_RICE;
        }
        return dishType;
    }

    /**
     * 合并排期菜品与固定米饭菜品，按菜品ID去重，供后续统一构建候选池和加载食材。
     */
    private List<Dish> mergeCandidateDishes(List<Dish> scheduledDishes, List<Dish> riceDishes) {
        Map<Integer, Dish> merged = new LinkedHashMap<>();
        if (scheduledDishes != null) {
            for (Dish dish : scheduledDishes) {
                if (dish != null && dish.getId() != null) {
                    merged.put(dish.getId(), dish);
                }
            }
        }
        if (riceDishes != null) {
            for (Dish dish : riceDishes) {
                if (dish != null && dish.getId() != null) {
                    merged.putIfAbsent(dish.getId(), dish);
                }
            }
        }
        return new ArrayList<>(merged.values());
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
     * 加载分类名称→配料名称集合的映射，用于三级过敏词展开。
     */
    private Map<String, Set<String>> loadCategoryIngredientMapping() {
        return dishIngredientCategoryService.getCategoryIngredientMapping();
    }

    /**
     * 为单个订单生成具体菜品组合。
     */
    private CustomerMealPlan buildCustomerPlan(CustomerOrder order, CustomerProfile customer,
                                               Map<Long, Map<String, List<Dish>>> candidateDishMap,
                                               Map<Integer, Set<String>> dishIngredientMap,
                                               LocalDate targetDate, String mealType,
                                               Map<Long, ParentPackage> parentPackageMap,
                                               Map<String, Set<String>> categoryIngredientMap) {
        // BREAKFAST 餐次不生成菜品明细，只创建客户排餐记录
        if (MEAL_TYPE_BREAKFAST.equals(mealType)) {
            return new CustomerMealPlan();
        }

        DishQuantityConfig config = new DishQuantityConfig(order);

        Map<String, List<Dish>> dishTypeMap = candidateDishMap.get(order.getParentPackageId());
        if (dishTypeMap == null || dishTypeMap.isEmpty()) {
            throw new BadRequestException("当前父套餐没有可用候选菜");
        }
        List<String> allergyTags = customer.getAllergyTags() == null ? Collections.emptyList() : customer.getAllergyTags();
        Set<Integer> selectedDishIds = new HashSet<>();
        List<SelectedDish> selectedDishes = new ArrayList<>();
        List<String> failureReasons = new ArrayList<>();
        CustomerMealPlan customerMealPlan = new CustomerMealPlan();

        // 使用 DishQuantityConfig 获取菜品数量
        pickRequiredDishes(config.getMainDishCount(), config.getSideDishCount(), selectedDishes, selectedDishIds, dishTypeMap, allergyTags,
                dishIngredientMap, targetDate, mealType, order.getParentPackageId(), parentPackageMap, customerMealPlan, failureReasons, customer, categoryIngredientMap);
        pickVegetables(config.getVegCount(), selectedDishes, selectedDishIds, dishTypeMap, allergyTags,
                dishIngredientMap, targetDate, mealType, order.getParentPackageId(), parentPackageMap, customerMealPlan, failureReasons, customer, categoryIngredientMap);
        pickRiceDish(config.getRiceCount(), config.getRiceType(), selectedDishes, selectedDishIds,
                dishTypeMap, allergyTags, dishIngredientMap,  customerMealPlan, failureReasons, customer, categoryIngredientMap);
        pickOptionalDish(config.getSoupCount(), DISH_TYPE_SOUP, selectedDishes, selectedDishIds,
                dishTypeMap, allergyTags, dishIngredientMap, targetDate, mealType, order.getParentPackageId(), parentPackageMap, customerMealPlan, failureReasons, customer, categoryIngredientMap);

        // 处理客户需要显示排除的菜品
        List<SkippedAllergyDish> skippedAllergyDishes = customerMealPlan.getSkippedAllergyDishes();
        List<SelectedDish> selectedDishes1 = markExcludedDishes(selectedDishes, skippedAllergyDishes, customer);

        customerMealPlan.setSelectedDishes(selectedDishes1);

        if (!failureReasons.isEmpty()) {
            throw new MealPlanBuildException(String.join("；", failureReasons), customerMealPlan);
        }
        return customerMealPlan;
    }

    /**
     * 客户显式排除的菜品。
     */
    private List<SelectedDish> markExcludedDishes(List<SelectedDish> selectedDishes,List<SkippedAllergyDish> skippedAllergyDishes, CustomerProfile customerProfile) {
        List<Integer> excludedDishIds = customerProfile.getExcludedDishIds();
        if (selectedDishes == null || selectedDishes.isEmpty() || excludedDishIds == null || excludedDishIds.isEmpty()) {
            return selectedDishes;
        }

        Set<Integer> excludedDishIdSet = new HashSet<>(excludedDishIds);
        List<SelectedDish> result = new ArrayList<>(selectedDishes.size());
        for (SelectedDish selectedDish : selectedDishes) {
            Dish dish = selectedDish.getDish();
            if (dish == null || dish.getId() == null || !excludedDishIdSet.contains(dish.getId())) {
                result.add(selectedDish);
                continue;
            }
            if (selectedDish.isReplaced&& !CollectionUtils.isEmpty(selectedDish.getMatchedAllergyTags())){
                log.info("已被过敏排除的菜品：{},不再校验 排除菜品ID列表", dish.getName());
                result.add(selectedDish);
                continue;
            }



            String replaceReason = selectedDish.getReplaceReason() != null
                    ? selectedDish.getReplaceReason()
                    : REPLACE_REASON_EXCLUDED;
            Set<String> strings = new LinkedHashSet<>(selectedDish.getMatchedAllergyTags());
            strings.add("排除菜品");
            log.info("客户{} 编号{}被排除的菜品：{}，id:{}。原因：{}",
                    customerProfile.getCustomerName(), customerProfile.getCustomerCode(),
                    dish.getName(),dish.getId(), replaceReason);
            skippedAllergyDishes.add(new SkippedAllergyDish(

                    dish,


                    strings, replaceReason));
        }
        return result;
    }

    /**
     * 固定日菜单规则：主菜/副菜每天每类最多提供 1 份，超出部分通过补菜数量记录。
     */
    private void pickRequiredDishes(Integer mainCount, Integer sideCount, List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                                    Map<String, List<Dish>> dishTypeMap, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap,
                                    LocalDate targetDate, String mealType, Long parentPackageId, Map<Long, ParentPackage> parentPackageMap,
                                    CustomerMealPlan customerMealPlan, List<String> failureReasons, CustomerProfile customerProfile,
                                    Map<String, Set<String>> categoryIngredientMap) {
        pickSingleDishIfRequired(mainCount, DISH_TYPE_MAIN, "主菜不足", selectedDishes, selectedDishIds,
                dishTypeMap, allergyTags, dishIngredientMap, targetDate, mealType, parentPackageId, parentPackageMap,
                customerMealPlan, failureReasons, customerProfile, categoryIngredientMap);
        pickSingleDishIfRequired(sideCount, DISH_TYPE_SIDE, "副菜不足", selectedDishes, selectedDishIds,
                dishTypeMap, allergyTags, dishIngredientMap, targetDate, mealType, parentPackageId, parentPackageMap,
                customerMealPlan, failureReasons, customerProfile, categoryIngredientMap);
    }

    /**
     * 固定日菜单规则：素菜每天最多提供 1 份，超出部分通过补菜数量记录。
     */
    private void pickVegetables(Integer vegCount, List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                                Map<String, List<Dish>> dishTypeMap, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap,
                                LocalDate targetDate, String mealType, Long parentPackageId, Map<Long, ParentPackage> parentPackageMap,
                                CustomerMealPlan customerMealPlan, List<String> failureReasons, CustomerProfile customerProfile,
                                Map<String, Set<String>> categoryIngredientMap) {
        pickSingleDishIfRequired(vegCount, DISH_TYPE_VEGETABLE, "素菜不足", selectedDishes, selectedDishIds,
                dishTypeMap, allergyTags, dishIngredientMap, targetDate, mealType, parentPackageId, parentPackageMap,
                customerMealPlan, failureReasons, customerProfile, categoryIngredientMap);
    }

    /**
     * 固定日菜单规则：汤或米饭每天每类最多提供 1 份，超出部分通过补菜数量记录。
     */
    private void pickOptionalDish(Integer count, String dishType, List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                                  Map<String, List<Dish>> dishTypeMap, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap,
                                  LocalDate targetDate, String mealType, Long parentPackageId, Map<Long, ParentPackage> parentPackageMap,
                                  CustomerMealPlan customerMealPlan, List<String> failureReasons, CustomerProfile customerProfile,
                                  Map<String, Set<String>> categoryIngredientMap) {
        pickSingleDishIfRequired(count, dishType, dishType + "类型菜品不足", selectedDishes, selectedDishIds,
                dishTypeMap, allergyTags, dishIngredientMap, targetDate, mealType, parentPackageId, parentPackageMap,
                customerMealPlan, failureReasons, customerProfile, categoryIngredientMap);
    }

    /**
     * 米饭选择：优先匹配订单指定的米饭类型（riceType），匹配不到则回退到任意米饭。
     */
    private void pickRiceDish(Integer riceCount, String riceType, List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                             Map<String, List<Dish>> dishTypeMap, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap,
                             CustomerMealPlan customerMealPlan, List<String> failureReasons, CustomerProfile customerProfile,
                             Map<String, Set<String>> categoryIngredientMap) {
        if (riceCount == null || riceCount <= 0) {
            return;
        }
        List<Dish> riceDishes = new ArrayList<>(dishTypeMap.getOrDefault(DISH_TYPE_RICE, Collections.emptyList()));
        List<Dish> riceTypeDishes = dishTypeMap.get(DishTypeEnum.RICE_TYPE.getCode());
        if (riceTypeDishes != null) {
            riceDishes.addAll(riceTypeDishes);
        }
        if (riceDishes.isEmpty()) {
            failureReasons.add("米饭类型菜品不足");
            return;
        }
        // 如果指定了米饭类型，优先从名称匹配的米饭中选择
        DishSelectResult result = null;
        Dish originalRiceDish = isSpecifiedRiceType(riceType) ? riceDishes.get(0) : null;
        if (isSpecifiedRiceType(riceType)) {
            List<Dish> matched = new ArrayList<>();
            for (Dish d : riceDishes) {
                if (riceType.equals(d.getName())) {
                    matched.add(d);
                }
            }
            if (!matched.isEmpty()) {
                result = selectDish(matched, selectedDishIds, allergyTags, dishIngredientMap, categoryIngredientMap);
            }
            // 匹配不到指定米饭类型，回退到白米饭默认
            if (result == null || result.getDish() == null) {
                List<Dish> defaultRice = new ArrayList<>();
                for (Dish d : riceDishes) {
                    if ("白米饭".equals(d.getName())) {
                        defaultRice.add(d);
                    }
                }
                if (!defaultRice.isEmpty()) {
                    result = selectDish(defaultRice, selectedDishIds, allergyTags, dishIngredientMap, categoryIngredientMap);
                }
            }
        }
        // 未指定类型（默认），选菜单中的米饭（排除RICE_TYPE特殊米饭）
        if (result == null || result.getDish() == null) {
            List<Dish> menuRice = new ArrayList<>();
            for (Dish d : riceDishes) {
                if (!DishTypeEnum.RICE_TYPE.getCode().equals(d.getDishType())) {
                    menuRice.add(d);
                }
            }
            if (!menuRice.isEmpty()) {
                result = selectDish(menuRice, selectedDishIds, allergyTags, dishIngredientMap, categoryIngredientMap);
            }
        }
        // 菜单米饭选不到，回退到全部米饭
        if (result == null || result.getDish() == null) {
            result = selectDish(riceDishes, selectedDishIds, allergyTags, dishIngredientMap, categoryIngredientMap);
        }
        for (SkippedAllergyDish sad : result.getSkippedAllergyDishes()) {
            customerMealPlan.addSkippedAllergyDish(sad);
        }
        if (result.getDish() == null) {
            if (!isAllergyFilteredOut(result)) {
                failureReasons.add("米饭类型菜品不足");
            }
            return;
        }
        if (isSpecifiedRiceType(riceType)) {
            selectedDishes.add(new SelectedDish(DISH_TYPE_RICE, result.getDish(),
                    true, originalRiceDish, REPLACE_REASON_CUSTOMER_SELECTED, result.getMatchedAllergyTags()));
        } else {
            selectedDishes.add(new SelectedDish(DISH_TYPE_RICE, result.getDish(),
                    result.isReplaced(), result.getOriginalDish(), result.getReplaceReason(), result.getMatchedAllergyTags()));
        }
        selectedDishIds.add(result.getDish().getId());
    }

    private boolean isSpecifiedRiceType(String riceType) {
        return StringUtils.isNotBlank(riceType) && !DEFAULT_RICE_TYPE.equals(riceType);
    }

    /**
     * 固定日菜单规则：某一类型只要客户需求大于 0，就从当日菜单中选 1 份。
     * 超出的份数通过补菜数量字段记录，不在排餐明细里重复选第二道同类型菜。
     */
    private void pickSingleDishIfRequired(Integer requiredCount, String dishType, String failReason,
                                          List<SelectedDish> selectedDishes, Set<Integer> selectedDishIds,
                                          Map<String, List<Dish>> dishTypeMap, List<String> allergyTags,
                                          Map<Integer, Set<String>> dishIngredientMap,
                                          LocalDate targetDate, String mealType, Long parentPackageId,
                                          Map<Long, ParentPackage> parentPackageMap,
                                          CustomerMealPlan customerMealPlan, List<String> failureReasons,
                                          CustomerProfile customerProfile,
                                          Map<String, Set<String>> categoryIngredientMap) {
        if (requiredCount == null || requiredCount <= 0) {
            log.debug("客户{}(编号{}) 不需要 {} 类型菜品（需求数量：{}），跳过选菜",
                    customerProfile.getCustomerName(), customerProfile.getCustomerCode(), dishType, requiredCount);
            return;
        }
        DishSelectResult result = selectDishWithFallback(dishType, null, selectedDishIds, allergyTags,
                dishTypeMap, dishIngredientMap, targetDate, mealType, parentPackageId, parentPackageMap, categoryIngredientMap);
        for (SkippedAllergyDish sad : result.getSkippedAllergyDishes()) {
            customerMealPlan.addSkippedAllergyDish(sad);
        }
        if (result.getDish() == null) {
            if (!isAllergyFilteredOut(result)) {
                failureReasons.add(failReason);
            }
            return;
        }
        selectedDishes.add(new SelectedDish(dishType, result.getDish(),
                result.isReplaced(), result.getOriginalDish(), result.getReplaceReason(), result.getMatchedAllergyTags()));
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
                                       Map<Long, ParentPackage> parentPackageMap,
                                       Map<String, Set<String>> categoryIngredientMap) {
        // 尝试首选类型
        DishSelectResult result = selectDish(dishTypeMap.get(primaryType), selectedDishIds, allergyTags, dishIngredientMap, categoryIngredientMap);
        if (result.getDish() != null) {
            return result;
        }

        // 收集首选类型中被过敏过滤的菜品
        List<SkippedAllergyDish> allSkipped = new ArrayList<>(result.getSkippedAllergyDishes());

        if (fallbackType != null) {
            log.info("当前未启用类型替换 - primaryType={}, fallbackType={}", primaryType, fallbackType);
        }

        // 尝试次日菜品（暂不启用）
        if (allSkipped.isEmpty()) {
            log.warn("【暂跳过替换菜品】当日{}类型菜品不足，未尝试次日菜品", primaryType);
        } else {
            log.info("当日{}类型菜品因过敏被过滤，未尝试次日菜品", primaryType);
        }
        // Map<String, List<Dish>> nextDayDishTypeMap = loadNextDayDishTypeMap(targetDate, mealType, parentPackageId, parentPackageMap);
        // ... (注释中的备用代码保持不变)

        // 返回空结果，附带收集到的所有过敏菜品
        if (!allSkipped.isEmpty()) {
            return new DishSelectResult(null, false, null, null, Collections.emptySet(), allSkipped);
        }
        return DishSelectResult.noDish();
    }

    private boolean isAllergyFilteredOut(DishSelectResult result) {
        return result != null && result.getDish() == null && !result.getSkippedAllergyDishes().isEmpty();
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

        List<Dish> nextDayDishes = loadScheduledDishes(
                new MenuSlot(ScheduleKeyUtil.calcWeek(nextDate), ScheduleKeyUtil.calcDay(nextDate), false),
                mealType);
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
            dishTypeMap.computeIfAbsent(normalizeCandidateDishType(dish.getDishType()), k -> new ArrayList<>()).add(dish);
        }
        sortDishTypeMap(dishTypeMap);

        log.debug("次日菜品加载完成 - 匹配菜品数: {}, 类型数: {}", nextDayDishes.size(), dishTypeMap.size());
        return dishTypeMap;
    }

    /**
     * 从候选列表中挑选首个未重复且不过敏的菜品，返回选择结果（含匹配的过敏标签和被过滤的过敏菜品）。
     */
    private DishSelectResult selectDish(List<Dish> dishes, Set<Integer> selectedDishIds, List<String> allergyTags, Map<Integer, Set<String>> dishIngredientMap, Map<String, Set<String>> categoryIngredientMap) {
        if (dishes == null || dishes.isEmpty()) {
            return DishSelectResult.noDish();
        }
        List<SkippedAllergyDish> skippedAllergies = new ArrayList<>();
        for (Dish dish : dishes) {
            if (selectedDishIds.contains(dish.getId())) {
                continue;
            }
            Set<String> matchedAllergies = getMatchedAllergyTags(allergyTags, dishIngredientMap.get(dish.getId()), categoryIngredientMap);
            if (!matchedAllergies.isEmpty()) {
                log.warn("过敏菜品：{}", JSONUtil.toJsonStr(dish));
                skippedAllergies.add(new SkippedAllergyDish(dish, matchedAllergies,"ALLERGY"));
                continue;
            }
            return DishSelectResult.noReplace(dish, Collections.emptySet(), skippedAllergies);
        }
        // 所有候选都过敏或无候选，返回空结果并附上被过滤的过敏菜品
        if (!skippedAllergies.isEmpty()) {
            return new DishSelectResult(null, false, null, null, Collections.emptySet(), skippedAllergies);
        }
        return DishSelectResult.noDish();
    }

    /**
     * 判断菜品食材是否与客户过敏标签命中，支持三级过敏词：
     * 1. 配料名称：直接匹配
     * 2. 二级分类名称：展开该分类下所有配料后匹配
     * 3. 一级分类名称：展开该分类下所有配料后匹配
     * 返回匹配到的原始过敏标签集合（用于展示过敏原因）。
     */
    private Set<String> getMatchedAllergyTags(List<String> allergyTags, Set<String> ingredientNames,
                                               Map<String, Set<String>> categoryIngredientMap) {
        Set<String> matched = new HashSet<>();
        if (allergyTags == null || allergyTags.isEmpty() || ingredientNames == null || ingredientNames.isEmpty()) {
            return matched;
        }
        for (String allergyTag : allergyTags) {
            if (allergyTag == null) {
                continue;
            }
            // 优先级1：配料名称精确匹配
            if (ingredientNames.contains(allergyTag)) {
                log.debug("过敏匹配-配料名：{}", allergyTag);
                matched.add(allergyTag);
                continue;
            }
            // 优先级2/3：分类名称匹配（一级或二级分类）
            Set<String> categoryIngredients = categoryIngredientMap.get(allergyTag);
            if (categoryIngredients != null) {
                for (String catIngredient : categoryIngredients) {
                    if (ingredientNames.contains(catIngredient)) {
                        log.debug("过敏匹配-分类({})：菜品包含 {}", allergyTag, catIngredient);
                        matched.add(allergyTag);
                        break;
                    }
                }
            }
        }
        return matched;
    }

    /**
     * 将成功生成的客户排餐结果落库到主表和明细表。
     */
    private void saveSuccessPlan(Long mealPlanId, CustomerOrder order, CustomerProfile customer,
                                 CustomerMealPlan customerPlan, String mealType) {
        log.debug("保存成功排餐计划 - 计划ID: {}, 订单ID: {}, 客户ID: {}",
                mealPlanId, order.getId(), order.getCustomerId());

        MealPlanCustomer entity = buildCustomerEntity(mealPlanId, order, customer, 1, "", mealType);
        mealPlanCustomerMapper.insert(entity);

        saveSelectedItems(entity.getId(), entity.getCustomerName(), customerPlan);
        saveAllergyFilteredItems(entity.getId(), entity.getCustomerName(), customerPlan);

        log.debug("成功排餐计划保存完成 - 客户计划ID: {}, 选菜数量: {}, 过敏菜品数量: {}",
                entity.getId(), customerPlan.getSelectedDishes().size(), customerPlan.getSkippedAllergyDishes().size());
    }

    /**
     * 记录单个订单的失败结果，并追加失败原因到返回结果。
     */
    private int saveFailPlan(Long mealPlanId, CustomerOrder order, CustomerProfile customer,
                             String failReason, List<MealPlanGenerateResult.FailDetail> failDetails, String mealType) {
        return saveFailPlan(mealPlanId, order, customer, failReason, failDetails, null, mealType);
    }

    private int saveFailPlan(Long mealPlanId, CustomerOrder order, CustomerProfile customer,
                             String failReason, List<MealPlanGenerateResult.FailDetail> failDetails,
                             CustomerMealPlan customerPlan, String mealType) {
        log.debug("保存失败排餐计划 - 计划ID: {}, 订单ID: {}, 客户ID: {}, 失败原因: {}",
                mealPlanId, order.getId(),
                customer != null ? customer.getId() : order.getCustomerId(),
                failReason);

        MealPlanCustomer entity = buildCustomerEntity(mealPlanId, order, customer, 0, failReason, mealType);
        mealPlanCustomerMapper.insert(entity);
        saveSelectedItems(entity.getId(), entity.getCustomerName(), customerPlan);
        saveAllergyFilteredItems(entity.getId(), entity.getCustomerName(), customerPlan);

        MealPlanGenerateResult.FailDetail failDetail = new MealPlanGenerateResult.FailDetail();
        failDetail.setFailReason(failReason);
        failDetails.add(failDetail);

        log.warn("排餐计划保存失败 - 客户计划ID: {}, 失败原因: {}", entity.getId(), failReason);
        return 1;
    }

    private void saveSelectedItems(Long customerPlanId, String customerName, CustomerMealPlan customerPlan) {
        if (customerPlan == null || customerPlan.getSelectedDishes() == null || customerPlan.getSelectedDishes().isEmpty()) {
            return;
        }
        int seq = 1;
        for (SelectedDish selectedDish : customerPlan.getSelectedDishes()) {
            MealPlanCustomerItem item = new MealPlanCustomerItem();
            item.setCustomerPlanId(customerPlanId);
            item.setDishType(selectedDish.getDishType());
            item.setDishId(selectedDish.getDish().getId());
            item.setDishName(selectedDish.getDish().getName());
            item.setSeq(seq++);
            item.setDeleted(false);
            if (selectedDish.isReplaced()) {
                item.setIsReplaced(true);
                if (selectedDish.getOriginalDish() != null) {
                    item.setOriginalDishId(selectedDish.getOriginalDish().getId());
                    item.setOriginalDishName(selectedDish.getOriginalDish().getName());
                }
                item.setOriginalDishType(selectedDish.getOriginalDishType());
                item.setReplaceReason(selectedDish.getReplaceReason());
            }
            Set<String> matchedAllergies = selectedDish.getMatchedAllergyTags();
            if (matchedAllergies != null && !matchedAllergies.isEmpty()) {
                item.setIsAllergyFiltered(true);
                item.setAllergyReasons(String.join(",", matchedAllergies));
                log.info("【过敏记录】客户: {}, 菜品: {}, 过敏标签: {}",
                        customerName, selectedDish.getDish().getName(), String.join(",", matchedAllergies));
            }
            mealPlanCustomerItemMapper.insert(item);
            log.debug("保存客户菜品明细 - 客户: {}, 菜品ID: {}, 菜品类型: {}, 菜品名称: {}, 是否替换: {}, 替换原因: {}",
                    customerName, selectedDish.getDish().getId(),
                    selectedDish.getDishType(), selectedDish.getDish().getName(),
                    selectedDish.isReplaced(), selectedDish.getReplaceReason());
        }
    }

    private void saveAllergyFilteredItems(Long customerPlanId, String customerName, CustomerMealPlan customerPlan) {
        if (customerPlan == null || customerPlan.getSkippedAllergyDishes() == null || customerPlan.getSkippedAllergyDishes().isEmpty()) {
            return;
        }
        int allergySeq = 100; // 用较大序号区分
        for (SkippedAllergyDish sad : customerPlan.getSkippedAllergyDishes()) {
            MealPlanCustomerItem allergyItem = new MealPlanCustomerItem();
            allergyItem.setCustomerPlanId(customerPlanId);
            allergyItem.setDishType(sad.getDish().getDishType());
            allergyItem.setDishId(sad.getDish().getId());
            allergyItem.setDishName(sad.getDish().getName());
            allergyItem.setOriginalDishId(sad.getDish().getId());
            allergyItem.setOriginalDishName(sad.getDish().getName());
            allergyItem.setSeq(allergySeq++);
            allergyItem.setDeleted(false);
            allergyItem.setIsAllergyFiltered(true);
            allergyItem.setIsReplaced(false);
            allergyItem.setAllergyReasons(String.join(",", sad.getAllergyReasons()));
            allergyItem.setReplaceReason(sad.getReplaceReason());

            mealPlanCustomerItemMapper.insert(allergyItem);
            String replaceReason = sad.getReplaceReason();

            if (org.apache.commons.lang3.StringUtils.equals(replaceReason, "ALLERGY")) {
                log.info("【过敏记录-写入】客户: {}, 菜品: {},  allergy-过敏",
                        customerName, sad.getDish().getName());
            }else if (org.apache.commons.lang3.StringUtils.equals(replaceReason, REPLACE_REASON_EXCLUDED)) {
                log.info("【客户过滤菜品-写入】客户: {}, 菜品: {}",
                        customerName, sad.getDish().getName());
            }

        }
    }

    /**
     * 组装客户排餐记录实体，统一填充订单、客户和套餐快照字段。
     */
    private MealPlanCustomer buildCustomerEntity(Long mealPlanId, CustomerOrder order, CustomerProfile customer,
                                                 int status, String failReason, String mealType) {
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
        if (MEAL_TYPE_BREAKFAST.equals(mealType)) {
            entity.setBreakfastCount(order.getBreakfastCount() != null ? order.getBreakfastCount() : 0);
        } else {
            entity.setMeatRequiredCount(order.getMainDishCount() != null ? order.getMainDishCount() : 0);
            entity.setVegRequiredCount(order.getVegCount() != null ? order.getVegCount() : 0);
            entity.setIncludeSoup(order.getSoupCount() != null && order.getSoupCount() > 0 ? 1 : 0);
            entity.setIncludeRice(order.getRiceCount() != null && order.getRiceCount() > 0 ? 1 : 0);
            // 补菜数量 = max(0, 需求数 - 每日固定提供1个)，基础数量从 customer_order 关联获取，无需冗余存储
            DishQuantityConfig cfg = new DishQuantityConfig(order);
            entity.setSupplementaryMainCount(cfg.getSupplementaryMainCount());
            entity.setSupplementarySideCount(cfg.getSupplementarySideCount());
            entity.setSupplementaryVegCount(cfg.getSupplementaryVegCount());
            entity.setSupplementaryRiceCount(cfg.getSupplementaryRiceCount());
            entity.setSupplementarySoupCount(cfg.getSupplementarySoupCount());
        }
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
        List<MealPlanGenerateResult.FailDetail> effectiveFailDetails = failDetails == null
                ? Collections.emptyList()
                : failDetails;
        int failCount = mealPlan.getFailCount() == null ? 0 : mealPlan.getFailCount();
        if (failCount != effectiveFailDetails.size()) {
            log.info("排餐失败汇总与明细数量不一致，开始按当前有效失败记录重建 - 计划ID: {}, 汇总失败数: {}, 当前明细数: {}",
                    mealPlan.getId(), failCount, effectiveFailDetails.size());
            effectiveFailDetails = rebuildFailDetails(mealPlan.getId());
            failCount = effectiveFailDetails.size();
        }

        MealPlanGenerateResult result = new MealPlanGenerateResult();
        result.setMealPlanId(mealPlan.getId());
        result.setRecordDate(mealPlan.getRecordDate().toString());
        result.setMealType(mealPlan.getMealType());
        result.setTotalCount(mealPlan.getTotalCount());
        result.setSuccessCount(mealPlan.getSuccessCount());
        result.setFailCount(failCount);
        result.setFailDetails(effectiveFailDetails);
        return result;
    }

    private List<MealPlanGenerateResult.FailDetail> rebuildFailDetails(Long mealPlanId) {
        if (mealPlanId == null) {
            return Collections.emptyList();
        }

        List<MealPlanCustomer> customerPlans = mealPlanCustomerMapper.selectByMealPlanId(mealPlanId);
        if (CollectionUtils.isEmpty(customerPlans)) {
            return Collections.emptyList();
        }

        return customerPlans.stream()
                .filter(plan -> Integer.valueOf(0).equals(plan.getStatus()))
                .map(plan -> {
                    MealPlanGenerateResult.FailDetail failDetail = new MealPlanGenerateResult.FailDetail();
                    failDetail.setFailReason(plan.getFailReason());
                    return failDetail;
                })
                .collect(Collectors.toList());
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

    /**
     * 解析带餐次信息的配送日期JSON数组。
     * 支持新旧两种格式：
     * - 新格式: [{"date": "2026-04-01", "mealTypes": ["BREAKFAST", "LUNCH"]}]
     * - 旧格式: ["2026-04-01", "2026-04-02"]
     */
    private List<String> parseDeliveryDatesWithMealTypes(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String trimmed = json.trim();
            // 检查是否是对象数组（新格式以 [{ 开头）
            if (trimmed.startsWith("[{")) {
                // 新格式: [{"date": "...", "mealTypes": [...]}]
                List<DeliveryDateWithMealTypes> dates = JSON.parseArray(json, DeliveryDateWithMealTypes.class);
                return dates.stream()
                        .filter(d -> d.getDate() != null)
                        .map(DeliveryDateWithMealTypes::getDate)
                        .collect(Collectors.toList());
            } else {
                // 旧格式: ["2026-04-01", "2026-04-02"]
                return JSON.parseArray(json, String.class);
            }
        } catch (Exception e) {
            log.warn("解析deliveryDates失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 校验客户的餐次是否在排期餐次中
     *
     * @param json 新格式: [{"date": "2026-04-01", "mealTypes": ["BREAKFAST", "LUNCH"]}]
     * @return true 匹配成功
     */
    public DeliveryDateWithMealTypes parseDElivery(String json,String targetDate,String targetMealType) {
        String trimmed = json.trim();
        if (trimmed.startsWith("[{")) {
            List<DeliveryDateWithMealTypes> deliveryDateWithMealTypes = JSON.parseArray(json, DeliveryDateWithMealTypes.class);
            return deliveryDateWithMealTypes.stream()
                    .filter(d -> d.getDate() != null)
                    .filter(d -> d.getDate().equals(targetDate))

                    .findFirst().orElse(null);
        } else {
            // 旧格式: ["2026-04-01", "2026-04-02"]
            return  null;
        }

    }

    /**
     * 内部类：配送日期及餐次信息
     */
    private static class DeliveryDateWithMealTypes {
        private String date;
        private List<String> mealTypes;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public List<String> getMealTypes() {
            return mealTypes;
        }

        public void setMealTypes(List<String> mealTypes) {
            this.mealTypes = mealTypes;
        }
    }

    private static class MenuSlot {
        private final int weekNum;
        private final int dayOfWeek;
        private final boolean manual;

        private MenuSlot(int weekNum, int dayOfWeek, boolean manual) {
            this.weekNum = weekNum;
            this.dayOfWeek = dayOfWeek;
            this.manual = manual;
        }

        private int getWeekNum() {
            return weekNum;
        }

        private int getDayOfWeek() {
            return dayOfWeek;
        }

        private boolean isManual() {
            return manual;
        }
    }

    /**
     * 菜品数量配置封装：从 CustomerOrder 直接读取 5 个新字段，null 值自动转换为 0。
     * 废弃 SubPackage 字段，排餐逻辑完全从订单配置读取。
     */
    private static class DishQuantityConfig {
        private final int mainDishCount;
        private final int sideDishCount;
        private final int vegCount;
        private final int riceCount;
        private final String riceType;
        private final int soupCount;

        public DishQuantityConfig(CustomerOrder order) {
            this.mainDishCount = order.getMainDishCount() != null ? order.getMainDishCount() : 0;
            this.sideDishCount = order.getSideDishCount() != null ? order.getSideDishCount() : 0;
            this.vegCount = order.getVegCount() != null ? order.getVegCount() : 0;
            this.riceCount = order.getRiceCount() != null ? order.getRiceCount() : 0;
            this.riceType = order.getRiceType();
            this.soupCount = order.getSoupCount() != null ? order.getSoupCount() : 0;
        }

        public int getMainDishCount() { return mainDishCount; }
        public int getSideDishCount() { return sideDishCount; }
        public int getVegCount() { return vegCount; }
        public int getRiceCount() { return riceCount; }
        public String getRiceType() { return riceType; }
        public int getSoupCount() { return soupCount; }
        /** 补主菜数量 = max(0, 主菜需求数 - 每日固定1个) */
        public int getSupplementaryMainCount() { return Math.max(0, mainDishCount - 1); }
        /** 补副菜数量 = max(0, 副菜需求数 - 每日固定1个) */
        public int getSupplementarySideCount() { return Math.max(0, sideDishCount - 1); }
        /** 补素菜数量 = max(0, 素菜需求数 - 每日固定1个) */
        public int getSupplementaryVegCount() { return Math.max(0, vegCount - 1); }
        /** 补米饭数量 = max(0, 米饭需求数 - 每日固定1个) */
        public int getSupplementaryRiceCount() { return Math.max(0, riceCount - 1); }
        /** 补汤数量 = max(0, 汤需求数 - 每日固定1个) */
        public int getSupplementarySoupCount() { return Math.max(0, soupCount - 1); }
    }

    private static class CustomerMealPlan {
        private List<SelectedDish> selectedDishes = new ArrayList<>();
        private List<SkippedAllergyDish> skippedAllergyDishes = new ArrayList<>();

        public List<SelectedDish> getSelectedDishes() {
            return selectedDishes;
        }

        public void setSelectedDishes(List<SelectedDish> selectedDishes) {
            this.selectedDishes = selectedDishes;
        }

        public List<SkippedAllergyDish> getSkippedAllergyDishes() {
            return skippedAllergyDishes;
        }

        public void addSkippedAllergyDish(SkippedAllergyDish dish) {
            this.skippedAllergyDishes.add(dish);
        }

        public void addSkippedAllergyDishes(List<SkippedAllergyDish> dishes) {
            this.skippedAllergyDishes.addAll(dishes);
        }
    }

    private static class MealPlanBuildException extends BadRequestException {
        private final CustomerMealPlan customerPlan;

        private MealPlanBuildException(String msg, CustomerMealPlan customerPlan) {
            super(msg);
            this.customerPlan = customerPlan;
        }

        public CustomerMealPlan getCustomerPlan() {
            return customerPlan;
        }
    }

    /**
     * 被过敏过滤掉的菜品信息
     */
    private static class SkippedAllergyDish {
        private final Dish dish;
        private final Set<String> allergyReasons;
        private  final String replaceReason;

        private SkippedAllergyDish(Dish dish, Set<String> allergyReasons,String replaceReason) {
            this.dish = dish;
            this.allergyReasons = allergyReasons;
            this.replaceReason = replaceReason;
        }

        public String getReplaceReason() {
            return replaceReason;
        }


        public Dish getDish() { return dish; }
        public Set<String> getAllergyReasons() { return allergyReasons; }
    }

    /**
     * 菜品选择结果，包含选中的菜品、替换信息和匹配的过敏标签
     */
    private static class DishSelectResult {
        private final Dish dish;
        private final boolean isReplaced;
        private final Dish originalDish;
        private final String replaceReason;
        private final Set<String> matchedAllergyTags;
        private final List<SkippedAllergyDish> skippedAllergyDishes;

        private DishSelectResult(Dish dish, boolean isReplaced, Dish originalDish, String replaceReason,
                                Set<String> matchedAllergyTags, List<SkippedAllergyDish> skippedAllergyDishes) {
            this.dish = dish;
            this.isReplaced = isReplaced;
            this.originalDish = originalDish;
            this.replaceReason = replaceReason;
            this.matchedAllergyTags = matchedAllergyTags;
            this.skippedAllergyDishes = skippedAllergyDishes;
        }

        public static DishSelectResult noDish() {
            return new DishSelectResult(null, false, null, null, Collections.emptySet(), Collections.emptyList());
        }

        public static DishSelectResult noReplace(Dish dish, Set<String> matchedAllergyTags) {
            return new DishSelectResult(dish, false, null, null, matchedAllergyTags, Collections.emptyList());
        }

        public static DishSelectResult noReplace(Dish dish, Set<String> matchedAllergyTags, List<SkippedAllergyDish> skippedAllergyDishes) {
            return new DishSelectResult(dish, false, null, null, matchedAllergyTags, skippedAllergyDishes);
        }

        public static DishSelectResult noDishWithSkipped(List<SkippedAllergyDish> skippedAllergyDishes) {
            return new DishSelectResult(null, false, null, null, Collections.emptySet(), skippedAllergyDishes);
        }

        public static DishSelectResult withReplace(Dish dish, Dish originalDish, String reason) {
            return new DishSelectResult(dish, true, originalDish, reason, Collections.emptySet(), Collections.emptyList());
        }

        public Dish getDish() { return dish; }
        public boolean isReplaced() { return isReplaced; }
        public Dish getOriginalDish() { return originalDish; }
        public String getReplaceReason() { return replaceReason; }
        public Set<String> getMatchedAllergyTags() { return matchedAllergyTags; }
        public List<SkippedAllergyDish> getSkippedAllergyDishes() {
            return skippedAllergyDishes != null ? skippedAllergyDishes : Collections.emptyList();
        }
        public boolean hasMatchedAllergies() {
            return matchedAllergyTags != null && !matchedAllergyTags.isEmpty();
        }
    }

    // ========== 订单换菜规则 ==========

    /**
     * 批量加载订单换菜规则，按 orderId -> sourceDishId 建立内存 Map。
     */
    private Map<Long, Map<Long, CustomerOrderReplaceRule>> buildOrderReplaceRuleMap(List<CustomerOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> orderIds = orders.stream()
                .map(CustomerOrder::getId)
                .collect(Collectors.toSet());

        List<CustomerOrderReplaceRule> rules = replaceRuleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CustomerOrderReplaceRule>()
                        .in("order_id", orderIds)
                        .eq("deleted", false)
                        .eq("enabled", true));

        if (rules.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Map<Long, CustomerOrderReplaceRule>> result = new HashMap<>();
        for (CustomerOrderReplaceRule rule : rules) {
            result.computeIfAbsent(rule.getOrderId(), k -> new HashMap<>())
                    .put(rule.getSourceDishId(), rule);
        }
        return result;
    }

    /**
     * 对已选菜品应用订单换菜规则。
     * 若命中规则：替换为目标菜、记录原菜信息、清空过敏标记。
     * 若目标菜已停用或已删除：跳过该规则、保留原始选菜、记录 WARN 日志。
     */
    private void applyOrderReplaceRules(Long orderId, CustomerMealPlan customerPlan,
                                        Map<Long, Map<Long, CustomerOrderReplaceRule>> ruleMap) {
        if (customerPlan == null || customerPlan.getSelectedDishes() == null || ruleMap == null) {
            return;
        }
        Map<Long, CustomerOrderReplaceRule> sourceMap = ruleMap.get(orderId);
        if (sourceMap == null || sourceMap.isEmpty()) {
            return;
        }

        List<SelectedDish> newSelected = new ArrayList<>();
        for (SelectedDish sd : customerPlan.getSelectedDishes()) {
            if (sd.getDish() == null) {
                newSelected.add(sd);
                continue;
            }
            CustomerOrderReplaceRule rule = sourceMap.get(Long.valueOf(sd.getDish().getId()));
            if (rule == null) {
                newSelected.add(sd);
                continue;
            }

            // 查找目标菜品
            Dish targetDish = dishMapper.selectById(rule.getTargetDishId().intValue());
            if (targetDish == null || !Boolean.TRUE.equals(targetDish.getEnabled())) {
                log.warn("订单换菜规则跳过 - 规则ID: {}, 订单ID: {}, 原菜ID: {}, 目标菜ID: {} ({}), 原因: {}",
                        rule.getId(), orderId, rule.getSourceDishId(), rule.getTargetDishId(),
                        rule.getTargetDishName(),
                        targetDish == null ? "菜品已删除" : "菜品已停用");
                newSelected.add(sd);
                continue;
            }

            log.info("订单换菜规则命中 - 订单ID: {}, 原菜: {}({}), 替换为: {}({})",
                    orderId, sd.getDish().getName(), sd.getDish().getId(),
                    targetDish.getName(), targetDish.getId());

            // 用新构造器创建替换后的 SelectedDish，记录 originalDishType
            SelectedDish replaced = new SelectedDish(
                    targetDish.getDishType(),
                    targetDish,
                    true,
                    sd.getDish(),
                    sd.getDishType(),
                    REPLACE_REASON_ORDER_RULE,
                    Collections.emptySet()
            );
            newSelected.add(replaced);
        }
        customerPlan.setSelectedDishes(newSelected);
    }

    private static class SelectedDish {
        private final String dishType;
        private final Dish dish;
        private final boolean isReplaced;
        private final Dish originalDish;
        private final String originalDishType;
        private final String replaceReason;
        private final Set<String> matchedAllergyTags;

        private SelectedDish(String dishType, Dish dish) {
            this.dishType = dishType;
            this.dish = dish;
            this.isReplaced = false;
            this.originalDish = null;
            this.originalDishType = null;
            this.replaceReason = null;
            this.matchedAllergyTags = Collections.emptySet();
        }

        private SelectedDish(String dishType, Dish dish, boolean isReplaced, Dish originalDish, String replaceReason, Set<String> matchedAllergyTags) {
            this.dishType = dishType;
            this.dish = dish;
            this.isReplaced = isReplaced;
            this.originalDish = originalDish;
            this.originalDishType = originalDish != null ? originalDish.getDishType() : null;
            this.replaceReason = replaceReason;
            this.matchedAllergyTags = matchedAllergyTags != null ? matchedAllergyTags : Collections.emptySet();
        }

        private SelectedDish(String dishType, Dish dish, boolean isReplaced, Dish originalDish,
                             String originalDishType, String replaceReason, Set<String> matchedAllergyTags) {
            this.dishType = dishType;
            this.dish = dish;
            this.isReplaced = isReplaced;
            this.originalDish = originalDish;
            this.originalDishType = originalDishType;
            this.replaceReason = replaceReason;
            this.matchedAllergyTags = matchedAllergyTags != null ? matchedAllergyTags : Collections.emptySet();
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

        public String getOriginalDishType() {
            return originalDishType;
        }

        public String getReplaceReason() {
            return replaceReason;
        }

        public Set<String> getMatchedAllergyTags() {
            return matchedAllergyTags;
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
    public PageResult<MealPlanListDetailVO> queryAllWithDetail(MealPlanQueryCriteria criteria) {
        Page<MealPlan> page = new Page<>(criteria.getPage(), criteria.getSize());
        Page<MealPlan> result = mealPlanMapper.selectPageByCriteria(criteria, page);
        List<MealPlan> plans = result.getRecords();
        if (plans.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), result.getTotal());
        }

        List<Long> mealPlanIds = plans.stream()
                .map(MealPlan::getId)
                .collect(Collectors.toList());
        List<MealPlanCustomer> allCustomers = mealPlanCustomerMapper.selectByMealPlanIds(mealPlanIds);
        Map<Long, List<MealPlanCustomer>> customersByPlanId = allCustomers.stream()
                .collect(Collectors.groupingBy(MealPlanCustomer::getMealPlanId));
        Map<Long, List<MealPlanCustomerItem>> itemsByCustomerPlanId = buildItemsByCustomerPlanId(allCustomers);
        Map<Integer, List<me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO>> ingredientsMap =
                buildIngredientsMapForItems(itemsByCustomerPlanId);

        List<MealPlanListDetailVO> content = plans.stream()
                .map(plan -> assembleMealPlanListDetail(
                        plan,
                        customersByPlanId.getOrDefault(plan.getId(), Collections.emptyList()),
                        itemsByCustomerPlanId,
                        ingredientsMap))
                .collect(Collectors.toList());
        return new PageResult<>(content, result.getTotal());
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

    private Map<Long, List<MealPlanCustomerItem>> buildItemsByCustomerPlanId(List<MealPlanCustomer> customers) {
        if (customers == null || customers.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> customerPlanIds = customers.stream()
                .map(MealPlanCustomer::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (customerPlanIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MealPlanCustomerItem> allItems = mealPlanCustomerItemMapper.selectByCustomerPlanIds(customerPlanIds);
        if (allItems.isEmpty()) {
            return Collections.emptyMap();
        }
        return allItems.stream()
                .collect(Collectors.groupingBy(MealPlanCustomerItem::getCustomerPlanId));
    }

    private Map<Integer, List<me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO>> buildIngredientsMapForItems(
            Map<Long, List<MealPlanCustomerItem>> itemsByCustomerPlanId) {
        if (itemsByCustomerPlanId == null || itemsByCustomerPlanId.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Integer> dishIds = itemsByCustomerPlanId.values().stream()
                .flatMap(List::stream)
                .map(MealPlanCustomerItem::getDishId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        return buildDishIngredientsMap(dishIds);
    }

    private MealPlanDetailVO.CustomerPlanDetail assembleCustomerDetail(
            MealPlanCustomer customer,
            Map<Long, List<MealPlanCustomerItem>> itemsByCustomerPlanId,
            Map<Integer, List<me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO>> ingredientsMap,
            Set<Long> firstMealCustomerPlanIds) {
        MealPlanDetailVO.CustomerPlanDetail detail = new MealPlanDetailVO.CustomerPlanDetail();
        detail.setId(customer.getId());
        detail.setCustomerId(customer.getCustomerId());
        detail.setCustomerName(customer.getCustomerName());
        detail.setPhone(customer.getPhone());
        detail.setCustomerCode(customer.getCustomerCode());
        detail.setFirstMealOfOrder(customer.getStatus() != null
                && customer.getStatus() == 1
                && firstMealCustomerPlanIds.contains(customer.getId()));
        detail.setOrderId(customer.getOrderId());
        detail.setParentPackageId(customer.getParentPackageId());
        detail.setChildPackageId(customer.getChildPackageId());
        detail.setStatus(customer.getStatus());
        detail.setFailReason(customer.getFailReason());
        detail.setMeatRequiredCount(customer.getMeatRequiredCount());
        detail.setVegRequiredCount(customer.getVegRequiredCount());
        detail.setIncludeSoup(customer.getIncludeSoup());
        detail.setIncludeRice(customer.getIncludeRice());
        detail.setSupplementaryMainCount(customer.getSupplementaryMainCount());
        detail.setSupplementarySideCount(customer.getSupplementarySideCount());
        detail.setSupplementaryVegCount(customer.getSupplementaryVegCount());
        detail.setSupplementaryRiceCount(customer.getSupplementaryRiceCount());
        detail.setSupplementarySoupCount(customer.getSupplementarySoupCount());
        detail.setIsVerified(customer.getIsVerified());
        detail.setVerificationTime(customer.getVerificationTime() != null ? customer.getVerificationTime().toString() : null);
        detail.setVerificationOperator(customer.getVerificationOperator());
        detail.setSpecialRequirements(customer.getSpecialRequirements());

        List<MealPlanCustomerItem> items = itemsByCustomerPlanId.getOrDefault(customer.getId(), Collections.emptyList());
        List<MealPlanCustomerItemVO> itemVOs = items.stream()
                .map(item -> convertToVO(item, ingredientsMap))
                .collect(Collectors.toList());
        detail.setItems(itemVOs);
        return detail;
    }

    private Set<Long> buildFirstMealCustomerPlanIdSet(List<MealPlanCustomer> customers) {
        if (customers == null || customers.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> customerPlanIds = customers.stream()
                .map(MealPlanCustomer::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (customerPlanIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(mealPlanCustomerMapper.selectFirstSuccessfulCustomerPlanIds(customerPlanIds));
    }

    private MealPlanListDetailVO assembleMealPlanListDetail(
            MealPlan plan,
            List<MealPlanCustomer> customers,
            Map<Long, List<MealPlanCustomerItem>> itemsByCustomerPlanId,
            Map<Integer, List<me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO>> ingredientsMap) {
        MealPlanListDetailVO vo = new MealPlanListDetailVO();
        vo.setId(plan.getId());
        vo.setRecordDate(plan.getRecordDate() != null ? plan.getRecordDate().toString() : null);
        vo.setMealType(plan.getMealType());
        vo.setTotalCount(plan.getTotalCount());
        vo.setSuccessCount(plan.getSuccessCount());
        vo.setFailCount(plan.getFailCount());
        vo.setStatus(plan.getStatus());
        vo.setGenerateTime(plan.getGenerateTime() != null ? plan.getGenerateTime().toString() : null);

        Set<Long> firstMealCustomerPlanIds = buildFirstMealCustomerPlanIdSet(customers);
        List<MealPlanDetailVO.CustomerPlanDetail> customerDetails = customers.stream()
                .map(customer -> assembleCustomerDetail(customer, itemsByCustomerPlanId, ingredientsMap, firstMealCustomerPlanIds))
                .collect(Collectors.toList());
        vo.setCustomers(customerDetails);
        vo.setTotalCustomers(customerDetails.size());
        return vo;
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
        vo.setIsAllergyFiltered(item.getIsAllergyFiltered());
        vo.setAllergyReasons(item.getAllergyReasons());
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

    /**
     * 删除客户指定日期餐次的未核销排餐记录；若存在已核销记录则拒绝日历取消操作。
     *
     * @param customerId 客户ID
     * @param recordDate 排餐日期，格式 yyyy-MM-dd
     * @param mealType 餐次，支持 BREAKFAST / LUNCH / DINNER
     * @return 删除的客户排餐记录数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUnverifiedCustomerMealForCalendarAdjustment(Long customerId, String recordDate, String mealType) {
        LocalDate targetDate = ScheduleKeyUtil.parseDate(recordDate);
        List<CustomerGeneratedMealPlanDto> generatedRecords =
                mealPlanCustomerMapper.selectGeneratedByCustomerDateMeal(customerId, targetDate, mealType);
        if (generatedRecords == null || generatedRecords.isEmpty()) {
            return 0;
        }
        for (CustomerGeneratedMealPlanDto record : generatedRecords) {
            if (Boolean.TRUE.equals(record.getVerified())) {
                throw new BadRequestException("该日期餐次已核销，不能取消排餐");
            }
        }
        List<Long> customerPlanIds = generatedRecords.stream()
                .map(CustomerGeneratedMealPlanDto::getCustomerPlanId)
                .collect(Collectors.toList());
        mealPlanCustomerItemMapper.softDeleteByCustomerPlanIds(customerPlanIds);
        mealPlanCustomerMapper.softDeleteByIds(customerPlanIds);
        Set<Long> mealPlanIds = generatedRecords.stream()
                .map(CustomerGeneratedMealPlanDto::getMealPlanId)
                .collect(Collectors.toSet());
        for (Long mealPlanId : mealPlanIds) {
            MealPlan mealPlan = mealPlanMapper.selectById(mealPlanId);
            if (mealPlan != null && !Boolean.TRUE.equals(mealPlan.getDeleted())) {
                refreshMealPlanSummary(mealPlan, true);
            }
        }
        return customerPlanIds.size();
    }

    // ========== 聚合查询接口实现 ==========

    @Override
    public MealPlanDetailVO queryMealPlanDetail(Long mealPlanId) {
      

        // 查询排餐计划主表
        MealPlan mealPlan = mealPlanMapper.selectById(mealPlanId);
        if (mealPlan == null) {
            log.warn("未找到排餐计划 - ID: {}", mealPlanId);
            return null;
        }

        // 查询所有客户列表
        List<MealPlanCustomer> customers = mealPlanCustomerMapper.selectByMealPlanId(mealPlanId);
        Map<Long, List<MealPlanCustomerItem>> itemsMap = buildItemsByCustomerPlanId(customers);
        Map<Integer, List<me.zhengjie.modules.meal.domain.dto.DishIngredientItemVO>> finalIngredientsMap =
                buildIngredientsMapForItems(itemsMap);

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
        Set<Long> firstMealCustomerPlanIds = buildFirstMealCustomerPlanIdSet(customers);
        List<MealPlanDetailVO.CustomerPlanDetail> customerDetails = customers.stream()
                .map(customer -> assembleCustomerDetail(customer, itemsMap, finalIngredientsMap, firstMealCustomerPlanIds))
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

    @Override
    public List<MealPlanCustomerAddressVO> queryCustomerAddresses(Long mealPlanId) {
        return mealPlanCustomerMapper.selectCustomerAddresses(mealPlanId);
    }
}
