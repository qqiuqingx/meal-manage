package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.analysis.domain.ContextHandleKind;
import me.zhengjie.agent.analysis.domain.ConversationContextHandle;
import me.zhengjie.agent.analysis.domain.SemanticEntityType;
import me.zhengjie.agent.analysis.domain.SemanticOperation;
import me.zhengjie.agent.chat.MealPlanChatSession;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.BusinessResponseTypeCatalog;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.query.presentation.BusinessPresentationResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 业务查询结果管线。
 *
 * <p>统一保存下一轮需要的稳定对象焦点、受控结果形状和不可逆查询计划指纹。
 * 本管线只保存脱敏摘要，不保存工具原始结果、客户姓名或任意模型文本。</p>
 */
@Component
public class BusinessConversationResultPipeline {

    /**
     * 将受控查询成功返回的稳定对象标识回写到当前会话槽位。
     *
     * @param session 当前会话
     * @param responseType 已登记的响应类型
     * @param result Presenter 强类型结果
     */
    public void captureBusinessFocus(MealPlanChatSession session, String responseType,
                                     BusinessPresentationResult result) {
        if (session == null || result == null || result.isExplicitlyAbsent()) return;
        DiagnosisSlots slots = session.getSlots();
        if (result.getCustomerId() != null) slots.setCustomerId(result.getCustomerId());
        if (result.getCustomerCode() != null) slots.setCustomerCode(result.getCustomerCode());
        if (result.getItems().size() != 1) return;
        BusinessPresentationResult item = result.getItems().get(0);
        if (BusinessResponseTypeCatalog.ORDER.equals(responseType)) {
            if (item.getOrderId() != null) slots.setOrderId(item.getOrderId());
            if (item.getOrderCode() != null) slots.setOrderCode(item.getOrderCode());
        }
        if (BusinessResponseTypeCatalog.MEAL_PLAN.equals(responseType)
            && item.getCustomerMealPlanId() != null) {
            slots.setMealPlanRecordId(item.getCustomerMealPlanId());
        }
    }

    /**
     * 从最终聊天响应保存下一轮重新规划所需的脱敏摘要。
     *
     * @param session 当前会话
     * @param response 已完成安全校验的最终响应
     */
    public void captureLastBusinessQueryContext(MealPlanChatSession session,
                                                AgentChatResponse response) {
        if (session == null || response == null || response.getQueryPlan() == null
            || !BusinessResponseTypeCatalog.isBusinessResponse(response.getResponseType())) {
            return;
        }
        BusinessPresentationResult result =
            BusinessPresentationResult.fromLegacyMap(response.getInsightResult());
        LastBusinessQueryContext context = new LastBusinessQueryContext();
        context.setResponseType(response.getResponseType());
        context.setQueryTarget(BusinessResponseTypeCatalog.find(response.getResponseType())
            .map(BusinessResponseTypeCatalog.Definition::queryTarget).orElse(null));
        context.setDomain(response.getQueryPlan().getDomain() == null
            ? null : response.getQueryPlan().getDomain().name());
        context.setQueryPlanFingerprint(queryPlanFingerprint(response.getQueryPlan()));
        context.setRecordDate(response.getQueryPlan().getFilters() == null
            ? null : response.getQueryPlan().getFilters().getRecordDate());
        context.setStartDate(response.getQueryPlan().getFilters() == null
            ? null : response.getQueryPlan().getFilters().getStartDate());
        context.setEndDate(response.getQueryPlan().getFilters() == null
            ? null : response.getQueryPlan().getFilters().getEndDate());
        context.setMetric(response.getQueryPlan().getMetrics() == null
            || response.getQueryPlan().getMetrics().isEmpty()
            ? null : response.getQueryPlan().getMetrics().get(0).name());
        context.setMealScope(response.getQueryPlan().getMealScope());
        context.setAssistantSummary(limitText(response.getAssistantMessage(), 160));
        context.setQueriedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
        context.setResultShape(resultShape(response, result, context));
        session.getConversationState().setLastBusinessQueryContext(context);
        response.setLastBusinessQueryContext(context);
    }

    /**
     * 计算不可逆的受控查询计划指纹，用于阻止纠错轮重复执行同一计划。
     *
     * @param plan 受控查询计划
     * @return SHA-256 指纹；运行环境缺少算法时返回稳定占位值
     */
    public String queryPlanFingerprint(AgentQueryPlan plan) {
        if (plan == null) return "";
        String material = String.valueOf(plan.getDomain()) + "|" + plan.getAction() + "|"
            + plan.getMealScope() + "|"
            + (plan.getFilters() == null ? "" : plan.getFilters().getRecordDate()) + "|"
            + (plan.getFilters() == null ? "" : plan.getFilters().getMealType()) + "|"
            + plan.getToolNames();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder("sha256:");
            for (byte value : digest) encoded.append(String.format("%02x", value));
            return encoded.toString();
        } catch (Exception ignored) {
            return "sha256:unavailable";
        }
    }

    /** 生成只包含计数和枚举分布的结果形状，禁止持久化明细对象。 */
    private Map<String, Object> resultShape(AgentChatResponse response,
                                            BusinessPresentationResult result,
                                            LastBusinessQueryContext context) {
        Map<String, Object> shape = new LinkedHashMap<>();
        if (result.getTotal() instanceof Number) shape.put("total", result.getTotal());
        String activeResponseType = AgentMetricCatalog
            .definition(AgentQueryMetric.ACTIVE_CUSTOMER_COUNT).getResponseType();
        if (activeResponseType.equals(response.getResponseType())
            && result.getTotal() instanceof Number total) {
            context.setContextHandles(List.of(activeCustomerHandle(total, context.getQueriedAt())));
        }
        if (BusinessResponseTypeCatalog.SCHEDULED_MENU.equals(response.getResponseType())
            && !result.getGroups().isEmpty()) {
            Map<String, Integer> mealTypes = new LinkedHashMap<>();
            Map<String, Integer> dishTypes = new LinkedHashMap<>();
            for (BusinessPresentationResult group : result.getGroups()) {
                String mealType = String.valueOf(group.getMealTypeCode());
                Object total = group.getTotal();
                mealTypes.put(mealType, total instanceof Number ? ((Number) total).intValue() : 0);
                for (BusinessPresentationResult item : group.getItems()) {
                    String dishType = String.valueOf(item.getDishTypeCode());
                    dishTypes.put(dishType, dishTypes.getOrDefault(dishType, 0) + 1);
                }
            }
            shape.put("mealTypes", mealTypes);
            shape.put("dishTypeDistribution", dishTypes);
            shape.put("warnings", response.getWarnings() == null
                ? List.of() : new ArrayList<>(response.getWarnings()));
        }
        return shape;
    }

    /** 构造短期可引用的活跃客户集合句柄，仅保存口径和基数。 */
    private ConversationContextHandle activeCustomerHandle(Number total,
                                                            OffsetDateTime queriedAt) {
        ConversationContextHandle handle = new ConversationContextHandle();
        handle.setHandleId("ctx-" + UUID.randomUUID());
        handle.setKind(ContextHandleKind.ENTITY_SET);
        handle.setEntityType(SemanticEntityType.CUSTOMER);
        handle.setDefinitionId("AGENT_ACTIVE_CUSTOMER_V1");
        handle.setCardinality(total.intValue());
        handle.setSafeDescriptor(Map.of("metric", "ACTIVE_CUSTOMER_COUNT"));
        handle.setAllowedOperations(List.of(SemanticOperation.COUNT,
            SemanticOperation.PROJECT, SemanticOperation.GROUP, SemanticOperation.FILTER));
        handle.setSalience(1D);
        handle.setCreatedAt(queriedAt);
        handle.setExpiresAt(queriedAt.plusMinutes(30));
        return handle;
    }

    /** 截断仅用于受控上下文的助手摘要，避免历史话术无限增长。 */
    private String limitText(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
