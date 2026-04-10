package me.zhengjie.modules.quartz.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.domain.enums.MealTypeEnum;
import me.zhengjie.modules.meal.service.MealPlanService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealPlanTask {
    private final MealPlanService mealPlanService;
    //LUNCH 生成明日的午餐排餐计划
    public void runLunch() {
        log.info("开始执行生成明日午餐排餐计划任务");

        try {
            // 计算明日的日期
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            String recordDate = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            log.info("生成午餐排餐计划 - 日期: {}, 餐次: LUNCH", recordDate);

            // 调用服务生成排餐计划
            MealPlanGenerateResult result = mealPlanService.generateMealPlan(
                recordDate,
                MealTypeEnum.LUNCH.getCode(),
                null
            );

            log.info("午餐排餐计划生成完成 - 计划ID: {}, 总数: {}, 成功: {}, 失败: {}",
                result.getMealPlanId(),
                result.getTotalCount(),
                result.getSuccessCount(),
                result.getFailCount());

        } catch (Exception e) {
            log.error("生成明日午餐排餐计划失败", e);
        }
    }
    //DINNER 生成明日的晚餐排餐计划
    public void runDinner() {
        log.info("开始执行生成明日晚餐排餐计划任务");

        try {
            // 计算明日的日期
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            String recordDate = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            log.info("生成晚餐排餐计划 - 日期: {}, 餐次: DINNER", recordDate);

            // 调用服务生成排餐计划
            MealPlanGenerateResult result = mealPlanService.generateMealPlan(
                recordDate,
                MealTypeEnum.DINNER.getCode(),
                null
            );

            log.info("晚餐排餐计划生成完成 - 计划ID: {}, 总数: {}, 成功: {}, 失败: {}",
                result.getMealPlanId(),
                result.getTotalCount(),
                result.getSuccessCount(),
                result.getFailCount());

        } catch (Exception e) {
            log.error("生成明日晚餐排餐计划失败", e);
        }
    }
}
