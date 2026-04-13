package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.MealSchedulePlan;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanBatchResult;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanCopyRequest;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanSaveRequest;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanUpdateRequest;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanWeekVO;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealSchedulePlanMapper;
import me.zhengjie.modules.meal.service.MealSchedulePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

/**
 * 排餐坑位服务实现
 * @author qqx
 * @date 2026-04-13
 **/
@Service
@RequiredArgsConstructor
public class MealSchedulePlanServiceImpl implements MealSchedulePlanService {

    private static final List<String> ALLOWED_MEAL_TIMES = java.util.Arrays.asList("BREAKFAST", "LUNCH", "DINNER");
    private static final List<String> ALLOWED_DISH_CATEGORIES = java.util.Arrays.asList("MAIN", "SIDE", "SOUP", "VEGETABLE", "RICE");

    private final MealSchedulePlanMapper mealSchedulePlanMapper;
    private final DishMapper dishMapper;

    @Override
    public MealSchedulePlanWeekVO queryWeek(MealSchedulePlanQueryCriteria criteria) {
        validateWeek(criteria.getWeekNum());
        MealSchedulePlanWeekVO result = new MealSchedulePlanWeekVO();
        result.setWeekNum(criteria.getWeekNum());
        result.setSlots(mealSchedulePlanMapper.findByWeek(criteria.getWeekNum()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MealSchedulePlan create(MealSchedulePlanSaveRequest request) {
        validateSaveRequest(request);
        assertDishExists(request.getDishId());
        ensureSlotAvailable(request.getWeekNum(), request.getDayOfWeek(), request.getMealTime(), request.getDishCategory(), null);

        MealSchedulePlan entity = new MealSchedulePlan();
        entity.setWeekNum(request.getWeekNum());
        entity.setDayOfWeek(request.getDayOfWeek());
        entity.setMealTime(request.getMealTime());
        entity.setDishCategory(request.getDishCategory());
        entity.setDishId(request.getDishId());
        entity.setEnabled(Boolean.TRUE);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        mealSchedulePlanMapper.insert(entity);
        return mealSchedulePlanMapper.selectById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MealSchedulePlan update(Long id, MealSchedulePlanUpdateRequest request) {
        MealSchedulePlan entity = getById(id);
        assertDishExists(request.getDishId());
        entity.setDishId(request.getDishId());
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        entity.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        mealSchedulePlanMapper.updateById(entity);
        return mealSchedulePlanMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        mealSchedulePlanMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MealSchedulePlanBatchResult batchCreate(List<MealSchedulePlanSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("批量落位数据不能为空");
        }

        MealSchedulePlanBatchResult result = new MealSchedulePlanBatchResult();
        for (MealSchedulePlanSaveRequest request : requests) {
            try {
                create(request);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception ex) {
                result.setFailCount(result.getFailCount() + 1);
                result.getFailedItems().add(buildSlotLabel(request) + ": " + ex.getMessage());
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MealSchedulePlanBatchResult copyWeek(MealSchedulePlanCopyRequest request) {
        validateWeek(request.getFromWeek());
        validateWeek(request.getToWeek());
        if (request.getFromWeek().equals(request.getToWeek())) {
            throw new BadRequestException("来源周和目标周不能相同");
        }

        List<MealSchedulePlan> sourcePlans = mealSchedulePlanMapper.findByWeekForCopy(request.getFromWeek());
        MealSchedulePlanBatchResult result = new MealSchedulePlanBatchResult();
        for (MealSchedulePlan sourcePlan : sourcePlans) {
            try {
                ensureSlotAvailable(request.getToWeek(), sourcePlan.getDayOfWeek(), sourcePlan.getMealTime(), sourcePlan.getDishCategory(), null);
                MealSchedulePlan entity = new MealSchedulePlan();
                entity.setWeekNum(request.getToWeek());
                entity.setDayOfWeek(sourcePlan.getDayOfWeek());
                entity.setMealTime(sourcePlan.getMealTime());
                entity.setDishCategory(sourcePlan.getDishCategory());
                entity.setDishId(sourcePlan.getDishId());
                entity.setEnabled(sourcePlan.getEnabled());
                Timestamp now = new Timestamp(System.currentTimeMillis());
                entity.setCreateTime(now);
                entity.setUpdateTime(now);
                mealSchedulePlanMapper.insert(entity);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception ex) {
                result.setFailCount(result.getFailCount() + 1);
                result.getFailedItems().add(buildSlotLabel(request.getToWeek(), sourcePlan.getDayOfWeek(), sourcePlan.getMealTime(), sourcePlan.getDishCategory()) + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private MealSchedulePlan getById(Long id) {
        MealSchedulePlan entity = mealSchedulePlanMapper.selectById(id);
        if (entity == null) {
            throw new BadRequestException("排餐坑位记录不存在");
        }
        return entity;
    }

    private void validateSaveRequest(MealSchedulePlanSaveRequest request) {
        validateWeek(request.getWeekNum());
        if (request.getDayOfWeek() == null || request.getDayOfWeek() < 1 || request.getDayOfWeek() > 7) {
            throw new BadRequestException("dayOfWeek必须在1到7之间");
        }
        if (!ALLOWED_MEAL_TIMES.contains(request.getMealTime())) {
            throw new BadRequestException("mealTime仅支持BREAKFAST、LUNCH、DINNER");
        }
        if (!ALLOWED_DISH_CATEGORIES.contains(request.getDishCategory())) {
            throw new BadRequestException("dishCategory不合法");
        }
    }

    private void validateWeek(Integer weekNum) {
        if (weekNum == null || weekNum < 1 || weekNum > 4) {
            throw new BadRequestException("weekNum必须在1到4之间");
        }
    }

    private void assertDishExists(Integer dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BadRequestException("菜品不存在");
        }
    }

    private void ensureSlotAvailable(Integer weekNum, Integer dayOfWeek, String mealTime, String dishCategory, Long excludeId) {
        Integer count = mealSchedulePlanMapper.countBySlot(weekNum, dayOfWeek, mealTime, dishCategory, excludeId);
        if (count != null && count > 0) {
            throw new BadRequestException("该坑位已存在排餐记录，请先移除再添加");
        }
    }

    private String buildSlotLabel(MealSchedulePlanSaveRequest request) {
        return buildSlotLabel(request.getWeekNum(), request.getDayOfWeek(), request.getMealTime(), request.getDishCategory());
    }

    private String buildSlotLabel(Integer weekNum, Integer dayOfWeek, String mealTime, String dishCategory) {
        return "week=" + weekNum + ",day=" + dayOfWeek + ",mealTime=" + mealTime + ",dishCategory=" + dishCategory;
    }
}
