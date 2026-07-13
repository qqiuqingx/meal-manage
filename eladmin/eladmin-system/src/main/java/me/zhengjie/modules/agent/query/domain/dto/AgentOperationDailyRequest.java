package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

/** Agent 每日客户工作量的受控查询条件。 */
@Data
public class AgentOperationDailyRequest {
    /** 统计日期，格式 yyyy-MM-dd。 */
    @NotBlank(message = "统计日期不能为空")
    private String recordDate;
    /** 可选餐次过滤，仅支持 BREAKFAST、LUNCH、DINNER。 */
    private String mealType;
    /** 可选分组维度，仅支持 MEAL_TYPE、PACKAGE、CUSTOMER_SOURCE，最多两个。 */
    private List<String> dimensions = new ArrayList<>();
}
