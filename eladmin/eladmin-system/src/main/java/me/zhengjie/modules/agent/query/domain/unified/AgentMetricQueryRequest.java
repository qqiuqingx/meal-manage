package me.zhengjie.modules.agent.query.domain.unified;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/** 运营指标枚举及受控维度查询请求。 */
@Data
public class AgentMetricQueryRequest {
    /** 指标枚举。 */ @NotBlank @Pattern(regexp = "CUSTOMER_PROFILE_COUNT|ACTIVE_SERVICE_CUSTOMER_COUNT|ACTIVE_ORDER_COUNT|VERIFICATION_RECORD_COUNT|DAILY_SCHEDULED_CUSTOMER_COUNT|DAILY_VERIFIED_CUSTOMER_COUNT|DAILY_UNVERIFIED_CUSTOMER_COUNT|DAILY_UNSCHEDULED_CUSTOMER_COUNT|MEAL_PLAN_FAILURE_COUNT|EXPIRING_ORDER_COUNT") private String metric;
    /** 开始日期。 */ @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") private String startDate;
    /** 结束日期。 */ @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") private String endDate;
    /** 单日指标日期。 */ @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") private String recordDate;
    /** 餐次枚举。 */ @Pattern(regexp = "BREAKFAST|LUNCH|DINNER") private String mealType;
    /** 最多两个受控分组维度。 */ @Size(max = 2) private List<@Pattern(regexp = "MEAL_TYPE|PACKAGE|CUSTOMER_SOURCE") String> dimensions = new ArrayList<>();
}
