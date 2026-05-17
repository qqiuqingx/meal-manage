package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能排查诊断上下文
 */
@Data
public class MealPlanDiagnosisContextDto {

    private Long customerId;

    private String customerCode;

    private String customerName;

    private String recordDate;

    private String mealType;

    private CustomerProfileDetailDto customerProfile;

    private List<CustomerOrderDetailDto> orders = new ArrayList<>();

    private MealPlanDetailVO mealPlan;

    private List<MealPackageStatDto> candidateDishStats = new ArrayList<>();
}
