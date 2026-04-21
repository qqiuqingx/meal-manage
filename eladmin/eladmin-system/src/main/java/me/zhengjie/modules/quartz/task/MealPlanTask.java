package me.zhengjie.modules.quartz.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.domain.dto.MealVerificationDto;
import me.zhengjie.modules.meal.domain.dto.MealVerificationResultDto;
import me.zhengjie.modules.meal.domain.enums.MealTypeEnum;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.service.MealPlanService;
import me.zhengjie.modules.meal.service.MealVerificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealPlanTask {
    private final MealPlanService mealPlanService;
    private final MealVerificationService mealVerificationService;
    private final MealPlanMapper mealPlanMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;

    // BREAKFAST 生成明日的早餐排餐计划
    public void runBreakfast() {
        log.info("开始执行生成明日早餐排餐计划任务");

        try {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            String recordDate = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            log.info("生成早餐排餐计划 - 日期: {}, 餐次: BREAKFAST", recordDate);

            MealPlanGenerateResult result = mealPlanService.generateMealPlan(
                recordDate,
                MealTypeEnum.BREAKFAST.getCode(),
                null
            );

            log.info("早餐排餐计划生成完成 - 计划ID: {}, 总数: {}, 成功: {}, 失败: {}",
                result.getMealPlanId(),
                result.getTotalCount(),
                result.getSuccessCount(),
                result.getFailCount());

        } catch (Exception e) {
            log.error("生成明日早餐排餐计划失败", e);
        }
    }

    // LUNCH 生成明日的午餐排餐计划
    public void runLunch() {
        log.info("开始执行生成明日午餐排餐计划任务");

        try {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            String recordDate = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            log.info("生成午餐排餐计划 - 日期: {}, 餐次: LUNCH", recordDate);

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

    // DINNER 生成明日的晚餐排餐计划
    public void runDinner() {
        log.info("开始执行生成明日晚餐排餐计划任务");

        try {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            String recordDate = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            log.info("生成晚餐排餐计划 - 日期: {}, 餐次: DINNER", recordDate);

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

    // 核销当天早餐排餐
    public void runVerifyBreakfast() {
        log.info("开始执行定时核销任务 - 早餐");

        try {
            String recordDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            verifyByDateAndMealType(recordDate, MealTypeEnum.BREAKFAST.getCode());
        } catch (Exception e) {
            log.error("定时核销早餐任务执行失败", e);
        }
    }

    // 核销当天午餐排餐
    public void runVerifyLunch() {
        log.info("开始执行定时核销任务 - 午餐");

        try {
            String recordDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            verifyByDateAndMealType(recordDate, MealTypeEnum.LUNCH.getCode());
        } catch (Exception e) {
            log.error("定时核销午餐任务执行失败", e);
        }
    }

    // 核销当天晚餐排餐
    public void runVerifyDinner() {
        log.info("开始执行定时核销任务 - 晚餐");

        try {
            String recordDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            verifyByDateAndMealType(recordDate, MealTypeEnum.DINNER.getCode());
        } catch (Exception e) {
            log.error("定时核销晚餐任务执行失败", e);
        }
    }

    private void verifyByDateAndMealType(String recordDate, String mealType) {
        LocalDate date = LocalDate.parse(recordDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        MealPlan mealPlan = mealPlanMapper.selectByDateAndMealType(date, mealType);
        if (mealPlan == null) {
            log.warn("未找到排餐计划 - 日期: {}, 餐次: {}", recordDate, mealType);
            return;
        }

        List<MealPlanCustomer> customerPlans = mealPlanCustomerMapper.selectUnverifiedByMealPlanId(mealPlan.getId());
        if (customerPlans.isEmpty()) {
            log.info("没有需要核销的客户排餐记录 - 日期: {}, 餐次: {}", recordDate, mealType);
            return;
        }

        List<Long> customerPlanIds = customerPlans.stream()
            .map(MealPlanCustomer::getId)
            .collect(Collectors.toList());

        MealVerificationDto dto = new MealVerificationDto();
        dto.setCustomerPlanIds(customerPlanIds);
        dto.setRemark("定时任务自动核销");

        // 传入系统用户名，避免定时任务无 SecurityContext 导致的 NPE
        MealVerificationResultDto result = mealVerificationService.verify(dto, "system");

        log.info("定时核销任务完成 - 日期: {}, 餐次: {}, 成功: {}, 失败: {}",
            recordDate, mealType, result.getSuccessCount(), result.getFailCount());
    }
}
