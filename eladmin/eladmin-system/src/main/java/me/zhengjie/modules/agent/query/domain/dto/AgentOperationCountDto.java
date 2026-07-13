package me.zhengjie.modules.agent.query.domain.dto;

import lombok.Data;

/** Agent 运营统计单指标聚合响应。 */
@Data
public class AgentOperationCountDto {
    /** 指标代码。 */
    private String metricCode;
    /** 指标聚合值。 */
    private long total;
    /** 口径标识。 */
    private String metricDefinitionId;
    /** 口径版本。 */
    private String metricVersion = "2026.07";
    /** 系统时区。 */
    private String timezone = "Asia/Shanghai";
    /** 查询完成时间。 */
    private String queriedAt;
    /** 本接口只返回聚合结果。 */
    private boolean truncated;
}
