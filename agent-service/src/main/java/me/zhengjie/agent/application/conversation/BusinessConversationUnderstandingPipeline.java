package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.analysis.BusinessQuestionAnalyzer;
import me.zhengjie.agent.analysis.BusinessTemporalResolver;
import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.chat.MealPlanChatSession;
import me.zhengjie.agent.config.AgentProperties;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentMetricDefinition;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.PendingBusinessQueryContext;
import me.zhengjie.agent.query.domain.SemanticTraceSummary;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务会话理解管线。
 *
 * <p>统一负责 Pending 恢复、确定性槽位合并、业务时间解析和受控语义摘要，
 * 默认聊天处理器只消费最终分析结果，不再维护这些通用会话规则。</p>
 */
@Component
public class BusinessConversationUnderstandingPipeline {

    private static final String REQUEST_ID_KEY = "requestId";

    private final BusinessQuestionAnalyzer analyzer;
    private final BusinessTemporalResolver temporalResolver;
    private final boolean pendingContextEnabled;
    private final int pendingContextTtlMinutes;

    /**
     * 创建业务会话理解管线。
     *
     * @param analyzer 受控业务问题分析器
     * @param temporalResolver 业务时间解析器
     * @param properties Agent 强类型配置
     */
    public BusinessConversationUnderstandingPipeline(BusinessQuestionAnalyzer analyzer,
                                                     BusinessTemporalResolver temporalResolver,
                                                     AgentProperties properties) {
        this.analyzer = analyzer;
        this.temporalResolver = temporalResolver;
        this.pendingContextEnabled = properties.getChat().getBusinessSemantic().isPendingContextEnabled();
        this.pendingContextTtlMinutes = Math.max(1,
            properties.getChat().getBusinessSemantic().getPendingContextTtlMinutes());
    }

    /**
     * 执行通用业务理解管线；纯槽位回复优先恢复 Pending，否则分析新的完整问题。
     *
     * @param session 当前受控会话
     * @param message 当前用户消息
     * @return 分析结果及是否复用了 Pending 上下文
     */
    public UnderstandingOutcome understand(MealPlanChatSession session, String message) {
        BusinessQuestionAnalysis analysis = resolvePendingAnalysis(session, message);
        boolean pendingReused = analysis != null;
        if (analysis == null) {
            if (session.getConversationState().getPendingBusinessQueryContext() != null
                && !isPureSlotReply(message)) {
                clearPendingContext(session, false);
            }
            analysis = analyzer.analyze(message, session.getSlots(),
                session.getConversationState().getLastBusinessQueryContext());
            analysis = resolveTemporalAnalysis(analysis, session, false);
        }
        return new UnderstandingOutcome(analysis, pendingReused);
    }

    /**
     * 执行运营统计理解；保持该入口不继承上一轮业务查询焦点的既有行为。
     *
     * @param session 当前受控会话
     * @param message 当前用户消息
     * @return 完成确定性槽位和业务时间落地的分析结果
     */
    public BusinessQuestionAnalysis understandOperation(MealPlanChatSession session, String message) {
        return resolveTemporalAnalysis(analyzer.analyze(message, session.getSlots()), session, false);
    }

    /**
     * 保存待补语义，摘要只包含登记指标或领域，不保存用户原文。
     *
     * @param session 当前受控会话
     * @param analysis 已校验分析
     * @param missingFields 仍缺失的受控字段
     */
    public void savePendingContext(MealPlanChatSession session, BusinessQuestionAnalysis analysis,
                                   List<String> missingFields) {
        if (session == null || analysis == null) return;
        if (!pendingContextEnabled) {
            session.getConversationState().setPendingBusinessQueryContext(null);
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(temporalResolver.getZoneId());
        PendingBusinessQueryContext pending = new PendingBusinessQueryContext();
        pending.setAnalysis(analysis);
        pending.setMissingFields(missingFields == null ? List.of() : new ArrayList<>(missingFields));
        AgentMetricDefinition definition = analysis.getMetrics() == null || analysis.getMetrics().isEmpty()
            ? null : AgentMetricCatalog.definition(analysis.getMetrics().get(0));
        pending.setOriginalQuestionSummary(limitText(definition == null
            ? (analysis.getDomains() == null || analysis.getDomains().isEmpty()
                ? "受控业务查询" : analysis.getDomains().get(0).name())
            : definition.getSemanticDescription(), 120));
        pending.setSourceRequestId(MDC.get(REQUEST_ID_KEY));
        pending.setCreatedAt(now);
        pending.setExpiresAt(now.plusMinutes(pendingContextTtlMinutes));
        session.getConversationState().setPendingBusinessQueryContext(pending);
        session.getConversationState().getTaskStack().registerPending(pending);
    }

    /**
     * 清理 Pending 上下文并同步任务终态。
     *
     * @param session 当前受控会话
     * @param completed true 表示查询已完成，false 表示取消旧问题
     */
    public void clearPendingContext(MealPlanChatSession session, boolean completed) {
        if (session == null) return;
        session.getConversationState().setPendingBusinessQueryContext(null);
        if (completed) session.getConversationState().getTaskStack().completeActivePending();
        else session.getConversationState().getTaskStack().cancelActivePending();
    }

    /**
     * 根据受控分析结构计算仍缺失的字段。
     *
     * @param analysis 业务问题分析
     * @return 待补字段名
     */
    public List<String> missingFields(BusinessQuestionAnalysis analysis) {
        List<String> missing = new ArrayList<>();
        if (analysis == null) return missing;
        if (analysis.getFilters() == null || !isNotBlank(analysis.getFilters().getRecordDate())
            && !(isNotBlank(analysis.getFilters().getStartDate())
            && isNotBlank(analysis.getFilters().getEndDate()))) {
            missing.add("recordDate");
        }
        if (analysis.getAmbiguities() != null) {
            analysis.getAmbiguities().forEach(item -> {
                if (item != null && isNotBlank(item.getField()) && !missing.contains(item.getField())) {
                    missing.add(item.getField());
                }
            });
        }
        return missing;
    }

    /**
     * 将解析后的日期和餐次同步到会话槽位，供主系统持久化和下一轮复用。
     *
     * @param session 当前受控会话
     * @param filters 已解析过滤条件
     */
    public void applyResolvedFiltersToSlots(MealPlanChatSession session, AgentQueryFilters filters) {
        if (session == null || filters == null) return;
        session.getSlots().setRecordDate(filters.getRecordDate());
        session.getSlots().setStartDate(filters.getStartDate());
        session.getSlots().setEndDate(filters.getEndDate());
        if (isNotBlank(filters.getMealType())) session.getSlots().setMealType(filters.getMealType());
    }

    /**
     * 创建不含原始问题、Prompt 和结果数据的语义追踪摘要。
     *
     * @param analysis 受控分析
     * @param pendingReused 是否复用 Pending
     * @return 可持久化的审计摘要
     */
    public SemanticTraceSummary semanticTrace(BusinessQuestionAnalysis analysis, boolean pendingReused) {
        SemanticTraceSummary trace = new SemanticTraceSummary();
        trace.setSemanticSource(analysis == null ? null : analysis.getSource());
        trace.setFallbackReason(analysis == null ? null : analysis.getFallbackReason());
        trace.setSemanticConfidence(analysis == null ? null : analysis.getConfidence());
        trace.setSemanticCatalogVersion(analysis == null || analysis.getSemanticCatalogVersion() == null
            ? AgentMetricCatalog.VERSION : analysis.getSemanticCatalogVersion());
        if (analysis != null && analysis.getTemporal() != null
            && analysis.getTemporal().getExpression() != null) {
            trace.setTemporalExpression(analysis.getTemporal().getExpression().name());
        }
        if (analysis != null && analysis.getFilters() != null) {
            trace.setResolvedRecordDate(analysis.getFilters().getRecordDate());
            trace.setResolvedStartDate(analysis.getFilters().getStartDate());
            trace.setResolvedEndDate(analysis.getFilters().getEndDate());
        }
        trace.setPendingContextReused(pendingReused);
        trace.setInteractionMode(analysis == null || analysis.getInteractionMode() == null
            ? null : analysis.getInteractionMode().name());
        double confidence = analysis == null ? 0D : analysis.getConfidence();
        trace.setConfidenceBucket(confidence >= .90D ? "HIGH" : confidence >= .80D ? "MEDIUM" : "LOW");
        return trace;
    }

    /** 校验 Pending 声明的缺失字段是否已由本轮确定性槽位补齐。 */
    private List<String> unresolvedPendingFields(List<String> fields, BusinessQuestionAnalysis analysis) {
        if (fields == null || fields.isEmpty()) return List.of();
        List<String> unresolved = new ArrayList<>();
        for (String field : fields) {
            boolean resolved;
            if ("recordDate".equals(field)) {
                AgentQueryFilters filters = analysis.getFilters();
                resolved = filters != null && (isNotBlank(filters.getRecordDate())
                    || isNotBlank(filters.getStartDate()) && isNotBlank(filters.getEndDate()));
            } else if ("mealType".equals(field)) {
                resolved = analysis.getFilters() != null
                    && isNotBlank(analysis.getFilters().getMealType());
            } else if ("customer".equals(field) || "customerOrOrder".equals(field)) {
                resolved = analysis.getEntities() != null
                    && (analysis.getEntities().getCustomerId() != null
                    || isNotBlank(analysis.getEntities().getCustomerCode())
                    || isNotBlank(analysis.getEntities().getCustomerName())
                    || "customerOrOrder".equals(field)
                    && (analysis.getEntities().getOrderId() != null
                    || isNotBlank(analysis.getEntities().getOrderCode())));
            } else {
                resolved = false;
            }
            if (!resolved) unresolved.add(field);
        }
        return unresolved;
    }

    /** 纯槽位回复优先复用待执行语义；过期或缺条件时更新 Pending。 */
    private BusinessQuestionAnalysis resolvePendingAnalysis(MealPlanChatSession session, String message) {
        if (!pendingContextEnabled) {
            clearPendingContext(session, false);
            return null;
        }
        PendingBusinessQueryContext pending =
            session.getConversationState().getPendingBusinessQueryContext();
        if (pending == null || !isPureSlotReply(message)) return null;
        OffsetDateTime now = OffsetDateTime.now(temporalResolver.getZoneId());
        if (pending.isExpired(now) || pending.getAnalysis() == null) {
            session.getConversationState().setPendingBusinessQueryContext(null);
            session.getConversationState().getTaskStack().failActivePending();
            return null;
        }
        BusinessQuestionAnalysis analysis = pending.getAnalysis();
        analysis.setRequiresClarification(false);
        analysis.setClarificationQuestion(null);
        analysis.setAmbiguities(new ArrayList<>());
        analysis = resolveTemporalAnalysis(analysis, session, true);
        List<String> unresolved = unresolvedPendingFields(pending.getMissingFields(), analysis);
        if (!unresolved.isEmpty()) {
            analysis.setRequiresClarification(true);
            analysis.setClarificationQuestion("还需要补充：" + String.join("、", unresolved) + "。");
            pending.setMissingFields(unresolved);
            session.getConversationState().setPendingBusinessQueryContext(pending);
        }
        return analysis;
    }

    /** 合并确定性槽位后解析相对时间，确保 QueryPlan 只接收标准日期。 */
    private BusinessQuestionAnalysis resolveTemporalAnalysis(BusinessQuestionAnalysis analysis,
                                                             MealPlanChatSession session,
                                                             boolean pendingReused) {
        if (analysis == null) return null;
        mergeDeterministicSlots(analysis, session.getSlots());
        if (pendingReused) analysis.setSource("PENDING_CONTEXT");
        return temporalResolver.resolve(analysis,
            session.getConversationState().getPendingBusinessQueryContext(),
            session.getConversationState().getLastBusinessQueryContext());
    }

    /** 本轮确定性提取的实体、日期和餐次优先于模型推断。 */
    private void mergeDeterministicSlots(BusinessQuestionAnalysis analysis, DiagnosisSlots slots) {
        if (slots == null) return;
        if (analysis.getEntities() == null) analysis.setEntities(new AgentEntityReference());
        if (slots.getCustomerId() != null && slots.getCustomerId() > 0) {
            analysis.getEntities().setCustomerId(slots.getCustomerId());
        }
        if (isNotBlank(slots.getCustomerCode())) {
            analysis.getEntities().setCustomerCode(slots.getCustomerCode());
        }
        if (isNotBlank(slots.getCustomerName())) {
            analysis.getEntities().setCustomerName(slots.getCustomerName());
        }
        if (slots.getOrderId() != null && slots.getOrderId() > 0) {
            analysis.getEntities().setOrderId(slots.getOrderId());
        }
        if (isNotBlank(slots.getOrderCode())) {
            analysis.getEntities().setOrderCode(slots.getOrderCode());
        }
        AgentQueryFilters filters =
            analysis.getFilters() == null ? new AgentQueryFilters() : analysis.getFilters();
        analysis.setFilters(filters);
        if (isNotBlank(slots.getRecordDate())) filters.setRecordDate(slots.getRecordDate());
        if (isNotBlank(slots.getStartDate()) && isNotBlank(slots.getEndDate())) {
            filters.setRecordDate(null);
            filters.setStartDate(slots.getStartDate());
            filters.setEndDate(slots.getEndDate());
        }
        if (isNotBlank(slots.getMealType())) filters.setMealType(slots.getMealType());
    }

    /** 判断输入是否只携带日期、餐次和客户/订单标识等确定性槽位。 */
    private boolean isPureSlotReply(String message) {
        if (message == null) return false;
        String text = message.trim();
        if (text.isEmpty()) return false;
        String remainder = text
            .replaceAll("今天|今日|昨天|昨日|明天|明日|本周|这周|下周|早餐|午餐|晚餐", "")
            .replaceAll("\\d{4}-\\d{2}-\\d{2}", "")
            .replaceAll("(?i)[A-Z]\\d{3,}", "")
            .replaceAll("\\d{1,18}", "")
            .replaceAll("[，,、/\\s]+", "");
        return remainder.isEmpty();
    }

    /** 截断受控摘要，避免持久化超长上下文。 */
    private String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 一次理解管线的不可变输出。 */
    public record UnderstandingOutcome(BusinessQuestionAnalysis analysis, boolean pendingReused) { }
}
