package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 业务只读查询审计统计结果。
 */
@Data
public class AgentBusinessQueryAuditStatsDto {

    /** 业务查询总次数。 */
    private Long queryCount = 0L;

    /** 部分成功或截断次数。 */
    private Long partialCount = 0L;

    /** 部分成功或截断比例。 */
    private Double partialRate = 0D;

    /** 命中同轮缓存次数。 */
    private Long cachedCount = 0L;

    /** 同轮缓存命中率。 */
    private Double cachedRate = 0D;

    /** 失败次数。 */
    private Long failureCount = 0L;

    /** 失败率。 */
    private Double failureRate = 0D;

    /** 权限或身份拒绝次数。 */
    private Long permissionDeniedCount = 0L;

    /** 要求客服补充关键口径或对象的次数。 */
    private Long clarificationRequiredCount = 0L;

    /** 回答安全校验拒绝次数。 */
    private Long answerValidationRejectedCount = 0L;

    /** 回答安全校验拒绝比例。 */
    private Double answerValidationRejectedRate = 0D;

    /** 未追问、未失败且非部分结果的直接回答次数。 */
    private Long directAnswerCount = 0L;

    /** 未追问、未失败且非部分结果的直接回答比例。 */
    private Double directAnswerRate = 0D;

    /** 发起关键澄清的比例。 */
    private Double clarificationRequiredRate = 0D;

    /** 同一会话发起澄清后最终出现完整有效回答的比例。 */
    private Double clarificationSuccessRate = 0D;

    /** 平均查询耗时毫秒。 */
    private Double averageCostMs = 0D;

    /** 处理耗时 P95，单位毫秒。 */
    private Long p95CostMs = 0L;

    /** 按查询领域统计次数。 */
    private Map<String, Long> domainDistribution = new LinkedHashMap<>();

    /** 按工具名称统计次数。 */
    private Map<String, Long> toolDistribution = new LinkedHashMap<>();

    /** 按受控指标代码统计的问题分布。 */
    private Map<String, Long> metricDistribution = new LinkedHashMap<>();

    /** 按失败类型统计次数。 */
    private Map<String, Long> failureTypeDistribution = new LinkedHashMap<>();

    /** 未支持问题的稳定原因分布，可按次数取 Top N。 */
    private Map<String, Long> unsupportedReasonDistribution = new LinkedHashMap<>();
}
