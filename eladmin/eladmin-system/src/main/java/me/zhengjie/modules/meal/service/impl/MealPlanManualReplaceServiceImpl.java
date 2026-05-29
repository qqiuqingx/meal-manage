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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.MealPlanManualReplace;
import me.zhengjie.modules.meal.domain.dto.MealPlanManualReplaceSaveRequest;
import me.zhengjie.modules.meal.domain.dto.MealPlanManualReplaceVO;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.mapper.MealPlanManualReplaceMapper;
import me.zhengjie.modules.meal.service.MealPlanManualReplaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 排餐单手工换菜服务实现
 * @author qqx
 * @date 2026-05-28
 **/
@Service
@RequiredArgsConstructor
@Slf4j
public class MealPlanManualReplaceServiceImpl implements MealPlanManualReplaceService {

    private final MealPlanMapper mealPlanMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;
    private final MealPlanManualReplaceMapper mealPlanManualReplaceMapper;
    private final DishMapper dishMapper;

    /**
     * 查询指定排餐计划的手工换菜关系列表
     */
    @Override
    public List<MealPlanManualReplaceVO> queryByMealPlanId(Long mealPlanId) {
        List<MealPlanManualReplace> records = mealPlanManualReplaceMapper.selectByMealPlanId(mealPlanId);
        return records.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 全量保存指定排餐计划的手工换菜关系。
     * 采用"先软删、再插入"的覆盖式写入，保证幂等。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveManualReplaces(Long mealPlanId, MealPlanManualReplaceSaveRequest request) {
        // 1. 校验排餐计划存在且未删除
        MealPlan mealPlan = mealPlanMapper.selectById(mealPlanId);
        if (mealPlan == null || Boolean.TRUE.equals(mealPlan.getDeleted())) {
            throw new BadRequestException("排餐计划不存在或已删除");
        }

        // 2. 查询当前排餐单所有客户，构建 customerPlanId -> MealPlanCustomer 映射
        List<MealPlanCustomer> customers = mealPlanCustomerMapper.selectByMealPlanId(mealPlanId);
        Map<Long, MealPlanCustomer> customerMap = customers.stream()
                .collect(Collectors.toMap(MealPlanCustomer::getId, c -> c, (a, b) -> a));

        // 3. 校验所有 customerPlanIds 属于当前排餐单
        List<Long> allCustomerPlanIds = request.getItems().stream()
                .flatMap(item -> item.getCustomerPlanIds().stream())
                .distinct()
                .collect(Collectors.toList());

        for (Long cpId : allCustomerPlanIds) {
            if (!customerMap.containsKey(cpId)) {
                throw new BadRequestException("客户排餐记录ID " + cpId + " 不属于当前排餐单");
            }
        }

        // 4. 校验所有菜品存在且启用
        Set<Integer> dishIds = request.getItems().stream()
                .map(MealPlanManualReplaceSaveRequest.ReplaceItem::getDishId)
                .collect(Collectors.toSet());
        Map<Integer, Dish> dishMap = new HashMap<>();
        for (Integer dishId : dishIds) {
            Dish dish = dishMapper.selectById(dishId);
            if (dish == null) {
                throw new BadRequestException("菜品ID " + dishId + " 不存在");
            }
            if (!Boolean.TRUE.equals(dish.getEnabled())) {
                throw new BadRequestException("菜品「" + dish.getName() + "」已停用，无法作为换菜菜品");
            }
            dishMap.put(dishId, dish);
        }

        // 5. 构建新换菜关系（去重）
        Set<String> dedupKeys = new HashSet<>();
        List<MealPlanManualReplace> newRecords = new ArrayList<>();
        for (MealPlanManualReplaceSaveRequest.ReplaceItem item : request.getItems()) {
            Dish dish = dishMap.get(item.getDishId());
            for (Long cpId : item.getCustomerPlanIds()) {
                MealPlanCustomer customer = customerMap.get(cpId);
                String dedupKey = mealPlanId + "_" + cpId + "_" + item.getDishId();
                if (!dedupKeys.add(dedupKey)) {
                    continue;
                }
                MealPlanManualReplace record = new MealPlanManualReplace();
                record.setMealPlanId(mealPlanId);
                record.setCustomerPlanId(cpId);
                record.setCustomerId(customer.getCustomerId());
                record.setCustomerCode(customer.getCustomerCode());
                record.setCustomerName(customer.getCustomerName());
                record.setDishId(item.getDishId());
                record.setDishName(dish.getName());
                record.setDishType(item.getDishType());
                record.setDeleted(false);
                newRecords.add(record);
            }
        }

        // 6. 全量覆盖：先清理历史软删记录，再软删当前记录，最后插入新记录。
        // 否则同一 (mealPlanId, customerPlanId, dishId) 历史上已存在 deleted=1 记录时，
        // 本轮软删 deleted=0 -> 1 会撞上唯一索引 (meal_plan_id, customer_plan_id, dish_id, deleted)。
        mealPlanManualReplaceMapper.deleteHistoryByMealPlanId(mealPlanId);
        mealPlanManualReplaceMapper.softDeleteByMealPlanId(mealPlanId);
        for (MealPlanManualReplace record : newRecords) {
            mealPlanManualReplaceMapper.insert(record);
        }

        log.info("手工换菜保存完成 - 排餐计划ID: {}, 新增记录数: {}", mealPlanId, newRecords.size());
    }

    /**
     * 将实体转换为VO
     */
    private MealPlanManualReplaceVO toVO(MealPlanManualReplace entity) {
        MealPlanManualReplaceVO vo = new MealPlanManualReplaceVO();
        vo.setId(entity.getId());
        vo.setMealPlanId(entity.getMealPlanId());
        vo.setCustomerPlanId(entity.getCustomerPlanId());
        vo.setCustomerId(entity.getCustomerId());
        vo.setCustomerCode(entity.getCustomerCode());
        vo.setCustomerName(entity.getCustomerName());
        vo.setDishId(entity.getDishId());
        vo.setDishName(entity.getDishName());
        vo.setDishType(entity.getDishType());
        return vo;
    }
}
