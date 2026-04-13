package me.zhengjie.modules.meal.service;

import me.zhengjie.modules.meal.domain.MealSchedulePlan;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanBatchResult;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanCopyRequest;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanSaveRequest;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanUpdateRequest;
import me.zhengjie.modules.meal.domain.dto.MealSchedulePlanWeekVO;

import java.util.List;

/**
 * 排餐坑位服务
 * @author qqx
 * @date 2026-04-13
 **/
public interface MealSchedulePlanService {

    MealSchedulePlanWeekVO queryWeek(MealSchedulePlanQueryCriteria criteria);

    MealSchedulePlan create(MealSchedulePlanSaveRequest request);

    MealSchedulePlan update(Long id, MealSchedulePlanUpdateRequest request);

    void delete(Long id);

    MealSchedulePlanBatchResult batchCreate(List<MealSchedulePlanSaveRequest> requests);

    MealSchedulePlanBatchResult copyWeek(MealSchedulePlanCopyRequest request);
}
