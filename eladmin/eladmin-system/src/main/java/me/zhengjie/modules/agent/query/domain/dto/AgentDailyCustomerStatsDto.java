package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 每日客户工作量聚合响应，不包含客户、手机号、地址或金额明细。
 */
@Data
public class AgentDailyCustomerStatsDto {
    /** 查询日期。 */
    private String recordDate;
    /** 可选餐次过滤。 */
    private String mealType;
    /** 有效排餐客户去重数。 */
    private long scheduledCustomerCount;
    /** 已核销客户去重数。 */
    private long verifiedCustomerCount;
    /** 已排餐但未核销客户去重数。 */
    private long unverifiedCustomerCount;
    /** 按有效订单、排餐模式和排除规则计算的应服务客户加餐次去重数。 */
    private long expectedCustomerCount;
    /** 应服务客户减去成功生成有效排餐的客户加餐次去重数。 */
    private long unscheduledCustomerCount;
    /** 生成失败排餐记录数。 */
    private long mealPlanFailureCount;
    /** 口径标识。 */
    private String metricDefinitionId = "AGENT_DAILY_CUSTOMER_WORKLOAD_V1";
    /** 口径版本。 */
    private String metricVersion = "2026.07";
    /** 系统时区。 */
    private String timezone = "Asia/Shanghai";
    /** 查询完成时间。 */
    private String queriedAt;
    /** 已按聚合返回，永不包含明细。 */
    private boolean truncated;
    /** 按餐次的客户去重分组数。 */
    private Map<String, Long> mealTypeBreakdown = new LinkedHashMap<>();
    /** 每个每日工作量指标按餐次聚合的受控分组数，键为指标枚举名。 */
    private Map<String, Map<String, Long>> metricMealTypeBreakdown = new LinkedHashMap<>();
    /** 本次实际生效的分组维度，最多两个。 */
    private List<String> breakdownDimensions = new ArrayList<>();
    /** 每个每日工作量指标按本次维度组合聚合的受控分组数，键为指标枚举名。 */
    private Map<String, Map<String, Long>> metricDimensionBreakdown = new LinkedHashMap<>();
}
