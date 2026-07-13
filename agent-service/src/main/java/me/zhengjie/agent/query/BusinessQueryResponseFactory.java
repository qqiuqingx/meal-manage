package me.zhengjie.agent.query;

import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentQueryFact;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.AgentQueryMetric;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 组装受控业务查询响应，统一处理事实、话术安全校验、时间和 QueryPlan。 */
public class BusinessQueryResponseFactory {
    private final BusinessQueryPlanner planner = new BusinessQueryPlanner();
    private final BusinessAnswerValidator answerValidator;
    private final BusinessAnswerComposer answerComposer = new BusinessAnswerComposer();

    /** 创建查询响应工厂。 */
    public BusinessQueryResponseFactory(BusinessAnswerValidator answerValidator) {
        this.answerValidator = answerValidator;
    }

    /** 根据响应类型和槽位构造受控 QueryPlan。 */
    public AgentQueryPlan plan(String responseType, DiagnosisSlots slots) {
        return planner.plan(responseType, slots);
    }

    /** 返回统一的固定业务话术组装器。 */
    public BusinessAnswerComposer answerComposer() { return answerComposer; }

    /**
     * 创建业务查询聊天响应；输入结果必须已由工具 DTO 转换为受控展示字段。
     *
     * @param sessionId 当前会话 ID
     * @param slots 已复制的会话槽位
     * @param slotConfidence 已复制的槽位置信度
     * @param conversationStage 当前会话阶段
     * @param responseType 受控业务响应类型
     * @param insightResult 受控展示结果
     * @param message 固定模板话术
     * @param quickReplies 快捷追问
     * @return 经过事实和敏感字段校验的响应
     */
    public AgentChatResponse create(String sessionId, DiagnosisSlots slots, Map<String, String> slotConfidence,
                                    String conversationStage, String responseType, Map<String, Object> insightResult,
                                    String message, List<String> quickReplies) {
        AgentQueryPlan queryPlan = plan(responseType, slots);
        List<AgentQueryFact> facts = buildFacts(responseType, insightResult);
        boolean planMatches = matchesQueryPlan(queryPlan, insightResult);
        boolean safe = answerValidator.isSafe(message, facts) && planMatches;
        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(sessionId); response.setStatus(ChatStatus.ANSWERED);
        response.setAssistantMessage(safe ? answerComposer.appendFactReferences(message, facts) : "查询结果包含当前回答契约不允许展示的内容，请到业务页面人工核对。");
        response.setSlots(slots); response.setSlotConfidence(slotConfidence); response.setMissingSlots(List.of());
        response.setDiagnosisResult(null); response.setQuickReplies(quickReplies); response.setConversationStage(conversationStage);
        response.setResponseType(responseType); response.setInsightResult(safe && insightResult != null ? insightResult : Map.of());
        response.setFacts(safe ? facts : List.of());
        if (!safe) response.setWarnings(planMatches ? List.of("回答安全校验未通过，已隐藏结构化结果。") : List.of("PLAN_RESULT_MISMATCH", "回答安全校验未通过，已隐藏结构化结果。"));
        response.setPartial(!planMatches || Boolean.TRUE.equals(insightResult == null ? false : insightResult.get("truncated")));
        response.setQueriedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        response.setQueryPlan(queryPlan);
        return response;
    }

    /** 校验主系统返回对象与当前受控 QueryPlan 的客户、订单、日期和餐次约束一致。 */
    @SuppressWarnings("unchecked")
    private boolean matchesQueryPlan(AgentQueryPlan plan, Map<String, Object> result) {
        if (plan == null || result == null || plan.getEntities() == null || plan.getFilters() == null) return true;
        Map<String, Object> item = firstItem(result);
        if (!matchesId(plan.getEntities().getCustomerId(), firstValue(result, item, "customerId"))) return false;
        if (!matchesId(plan.getEntities().getOrderId(), firstValue(result, item, "orderId"))) return false;
        if (!matchesText(plan.getFilters().getRecordDate(), firstValue(result, item, "recordDate"))) return false;
        return matchesText(plan.getFilters().getMealType(), firstValue(result, item, "mealTypeCode"));
    }

    private Map<String, Object> firstItem(Map<String, Object> result) {
        Object items = result.get("items");
        if (!(items instanceof List) || ((List<?>) items).isEmpty() || !(((List<?>) items).get(0) instanceof Map)) return Map.of();
        return (Map<String, Object>) ((List<?>) items).get(0);
    }

    private Object firstValue(Map<String, Object> result, Map<String, Object> item, String key) {
        return result.containsKey(key) ? result.get(key) : item.get(key);
    }

    private boolean matchesId(Long expected, Object actual) {
        if (expected == null || actual == null) return true;
        return actual instanceof Number && expected.equals(((Number) actual).longValue());
    }

    private boolean matchesText(String expected, Object actual) {
        if (expected == null || actual == null) return true;
        return expected.equalsIgnoreCase(String.valueOf(actual));
    }

    /** 构建每个可展示确定性数字的事实引用。 */
    @SuppressWarnings("unchecked")
    private List<AgentQueryFact> buildFacts(String responseType, Map<String, Object> result) {
        if (responseType == null || result == null) return List.of();
        List<AgentQueryFact> facts = new ArrayList<>();
        if ("CUSTOMER_MEAL_SUMMARY".equals(responseType)) {
            addLegacyMealSummaryFacts(facts, result);
        } else if ("CUSTOMER_VERIFICATION_SUMMARY".equals(responseType)) {
            addLegacyVerificationFacts(facts, result);
        } else if (!responseType.startsWith("BUSINESS_QUERY")) {
            return facts;
        } else if ("BUSINESS_QUERY_MEAL_PLAN_ALLERGY".equals(responseType)) {
            addMealPlanAllergyFacts(facts, result);
        } else if ("BUSINESS_QUERY_CUSTOMER_CANDIDATES".equals(responseType) && result.containsKey("total")) {
            facts.add(new AgentQueryFact("F1", "候选客户数", result.get("total"), "个", "CUSTOMER_CANDIDATE_LIST", null));
        } else if ("BUSINESS_QUERY_DISH_CANDIDATES".equals(responseType) && result.containsKey("totalCandidateCount")) {
            String sourceId = String.valueOf(result.get("customerId"));
            facts.add(new AgentQueryFact("F1", "排期候选菜数", result.get("totalCandidateCount"), "个", "DISH_CANDIDATE_PREVIEW", sourceId));
            facts.add(new AgentQueryFact("F2", "当前可用候选菜数", result.get("availableCandidateCount"), "个", "DISH_CANDIDATE_PREVIEW", sourceId));
            facts.add(new AgentQueryFact("F3", "已过滤候选菜数", result.get("filteredCandidateCount"), "个", "DISH_CANDIDATE_PREVIEW", sourceId));
        } else if ("BUSINESS_QUERY_CUSTOMER".equals(responseType) && result.containsKey("activeOrderCount")) {
            facts.add(new AgentQueryFact("F1", "进行中订单数", result.get("activeOrderCount"), "笔", "CUSTOMER_OVERVIEW", String.valueOf(result.get("customerId"))));
            Object balanceValue = result.get("mealBalance");
            if (balanceValue instanceof Map) {
                Map<String, Object> balance = (Map<String, Object>) balanceValue;
                facts.add(new AgentQueryFact("F2", "剩余早餐", balance.get("remainingBreakfast"), "餐", "ORDER_MEAL_BALANCE", String.valueOf(result.get("customerId"))));
                facts.add(new AgentQueryFact("F3", "剩余午晚餐", balance.get("remainingLunchDinner"), "餐", "ORDER_MEAL_BALANCE", String.valueOf(result.get("customerId"))));
            }
            if (result.containsKey("verificationRecordCount")) facts.add(new AgentQueryFact("F4", "核销记录数", result.get("verificationRecordCount"), "条", "VERIFICATION_LIST", String.valueOf(result.get("customerId"))));
            if (result.containsKey("refundRecordCount")) facts.add(new AgentQueryFact("F5", "退餐记录数", result.get("refundRecordCount"), "条", "REFUND_LIST", String.valueOf(result.get("customerId"))));
        } else if ("BUSINESS_QUERY_OPERATION_REPORT".equals(responseType)) {
            addOperationReportFacts(facts, result);
        } else if (responseType.startsWith("BUSINESS_QUERY_OPERATION_")) {
            facts.add(new AgentQueryFact("F1", operationFactLabel(responseType), operationFactValue(responseType, result), "个",
                String.valueOf(result.getOrDefault("metricDefinitionId", "AGENT_OPERATION_STATISTICS")),
                result.get("recordDate") == null ? null : String.valueOf(result.get("recordDate"))));
        } else if (result.containsKey("total")) {
            String label = totalFactLabel(responseType);
            if (label == null) return facts;
            facts.add(new AgentQueryFact("F1", label, result.get("total"), totalFactUnit(responseType), totalFactSourceType(responseType), null));
            if (("BUSINESS_QUERY_ORDER".equals(responseType) || "BUSINESS_QUERY_VERIFICATION".equals(responseType) || "BUSINESS_QUERY_REFUND".equals(responseType)) && result.get("items") instanceof List) {
                facts.add(new AgentQueryFact("F2", "当前展示记录数", ((List<?>) result.get("items")).size(), "笔", totalFactSourceType(responseType), null));
            }
        } else if ("BUSINESS_QUERY_RULE".equals(responseType) && result.containsKey("version")) {
            facts.add(new AgentQueryFact("F1", "规则版本", result.get("version"), null, "BUSINESS_RULE", String.valueOf(result.get("ruleId"))));
        }
        return facts;
    }

    /** 为每条实际过敏过滤菜品建立客户编号绑定证据，客户主动排除菜品不会成为事实。 */
    @SuppressWarnings("unchecked")
    private void addMealPlanAllergyFacts(List<AgentQueryFact> facts, Map<String, Object> result) {
        Object itemValue = result.get("items");
        if (!(itemValue instanceof List)) return;
        for (Object planValue : (List<?>) itemValue) {
            if (!(planValue instanceof Map)) continue;
            Map<String, Object> plan = (Map<String, Object>) planValue;
            String customerCode = plan.get("customerCode") == null ? null : String.valueOf(plan.get("customerCode"));
            String recordDate = plan.get("recordDate") == null ? null : String.valueOf(plan.get("recordDate"));
            String mealType = plan.get("mealTypeCode") == null ? null : String.valueOf(plan.get("mealTypeCode"));
            String sourceRecordId = plan.get("customerMealPlanId") == null ? null : String.valueOf(plan.get("customerMealPlanId"));
            Object dishesValue = plan.get("dishes");
            if (!(dishesValue instanceof List)) continue;
            for (Object dishValue : (List<?>) dishesValue) {
                if (!(dishValue instanceof Map)) continue;
                Map<String, Object> dish = (Map<String, Object>) dishValue;
                if (!Boolean.TRUE.equals(dish.get("allergyFiltered")) || !"ALLERGY".equals(dish.get("replaceReason"))) continue;
                AgentQueryFact fact = new AgentQueryFact("F" + (facts.size() + 1), "因过敏过滤菜品", dish.get("dishName"), null,
                    "MEAL_PLAN_DISH_ITEM", sourceRecordId);
                fact.setCustomerCode(customerCode); fact.setRecordDate(recordDate); fact.setMealType(mealType); fact.setSourceRecordId(sourceRecordId);
                facts.add(fact);
            }
        }
        if (result.get("scannedCount") != null) facts.add(new AgentQueryFact("F" + (facts.size() + 1), "已扫描排餐记录数",
            result.get("scannedCount"), "条", "MEAL_PLAN_LIST", null));
    }

    private String operationFactLabel(String responseType) {
        if (responseType.endsWith("ACTIVE")) return "活跃客户数";
        if (responseType.endsWith("EXPIRING")) return "即将到期订单数";
        if (responseType.endsWith("FAILURE")) return "排餐失败数";
        if (responseType.endsWith("UNSCHEDULED")) return "待排餐客户数";
        if (responseType.endsWith("VERIFIED")) return "已核销客户数";
        if (responseType.endsWith("SCHEDULED")) return "已排餐客户数";
        return "待核销客户数";
    }

    private Object operationFactValue(String responseType, Map<String, Object> result) {
        if (result.containsKey("total")) return result.get("total");
        if (responseType.endsWith("FAILURE")) return result.getOrDefault("mealPlanFailureCount", 0);
        if (responseType.endsWith("UNSCHEDULED")) return result.getOrDefault("unscheduledCustomerCount", 0);
        if (responseType.endsWith("VERIFIED")) return result.getOrDefault("verifiedCustomerCount", 0);
        if (responseType.endsWith("SCHEDULED")) return result.getOrDefault("scheduledCustomerCount", 0);
        return result.getOrDefault("unverifiedCustomerCount", 0);
    }

    /** 为多指标运营报表逐项生成事实，禁止将主系统字段名直接暴露为展示标签。 */
    private void addOperationReportFacts(List<AgentQueryFact> facts, Map<String, Object> result) {
        Object source = result.get("reportMetrics");
        if (!(source instanceof List)) return;
        int index = 1;
        for (Object item : (List<?>) source) {
            AgentQueryMetric metric = parseMetric(item);
            if (metric == null) continue;
            facts.add(new AgentQueryFact("F" + index++, reportMetricLabel(metric), reportMetricValue(result, metric), "个",
                String.valueOf(result.getOrDefault("metricDefinitionId", "AGENT_OPERATION_STATISTICS")),
                result.get("recordDate") == null ? null : String.valueOf(result.get("recordDate"))));
        }
    }

    /** 将内部枚举或 JSON 枚举文本转换为已登记指标，未知值不能成为事实。 */
    private AgentQueryMetric parseMetric(Object source) {
        if (source instanceof AgentQueryMetric) return (AgentQueryMetric) source;
        try { return source == null ? null : AgentQueryMetric.valueOf(String.valueOf(source)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    private String reportMetricLabel(AgentQueryMetric metric) {
        if (metric == AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT) return "已排餐客户数";
        if (metric == AgentQueryMetric.DAILY_VERIFIED_CUSTOMER_COUNT) return "已核销客户数";
        if (metric == AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT) return "待核销客户数";
        if (metric == AgentQueryMetric.DAILY_EXPECTED_CUSTOMER_COUNT) return "应服务客户数";
        if (metric == AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT) return "待排餐客户数";
        if (metric == AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT) return "排餐失败数";
        return metric.name();
    }
    private Object reportMetricValue(Map<String, Object> result, AgentQueryMetric metric) {
        if (metric == AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT) return result.getOrDefault("scheduledCustomerCount", 0);
        if (metric == AgentQueryMetric.DAILY_VERIFIED_CUSTOMER_COUNT) return result.getOrDefault("verifiedCustomerCount", 0);
        if (metric == AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT) return result.getOrDefault("unverifiedCustomerCount", 0);
        if (metric == AgentQueryMetric.DAILY_EXPECTED_CUSTOMER_COUNT) return result.getOrDefault("expectedCustomerCount", 0);
        if (metric == AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT) return result.getOrDefault("unscheduledCustomerCount", 0);
        if (metric == AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT) return result.getOrDefault("mealPlanFailureCount", 0);
        return 0;
    }

    /** 为过渡期客户餐数摘要补充其模板中每个确定性数字的事实来源。 */
    private void addLegacyMealSummaryFacts(List<AgentQueryFact> facts, Map<String, Object> result) {
        String sourceId = String.valueOf(result.get("customerCode"));
        addFact(facts, "有效订单数", result.get("activeOrderCount"), "笔", "CUSTOMER_MEAL_SUMMARY", sourceId);
        addFact(facts, "剩余早餐", result.get("remainingBreakfast"), "餐", "CUSTOMER_MEAL_SUMMARY", sourceId);
        addFact(facts, "剩余午晚餐", result.get("remainingLunchDinner"), "餐", "CUSTOMER_MEAL_SUMMARY", sourceId);
        addFact(facts, "合计剩余", result.get("totalRemaining"), "餐", "CUSTOMER_MEAL_SUMMARY", sourceId);
        addFact(facts, "已核销早餐", result.get("verifiedBreakfast"), "餐", "CUSTOMER_MEAL_SUMMARY", sourceId);
        addFact(facts, "已核销午餐", result.get("verifiedLunch"), "餐", "CUSTOMER_MEAL_SUMMARY", sourceId);
        addFact(facts, "已核销晚餐", result.get("verifiedDinner"), "餐", "CUSTOMER_MEAL_SUMMARY", sourceId);
        if (result.containsKey("totalRemaining") || result.containsKey("verifiedBreakfast")
                || result.containsKey("verifiedLunch") || result.containsKey("verifiedDinner")) {
            long totalMealCount = numericValue(result.get("totalRemaining")) + numericValue(result.get("verifiedBreakfast"))
                    + numericValue(result.get("verifiedLunch")) + numericValue(result.get("verifiedDinner"));
            addFact(facts, "当前有效订单总餐数", totalMealCount, "餐", "CUSTOMER_MEAL_SUMMARY", sourceId);
        }
    }

    /** 为过渡期核销摘要补充其模板中每个确定性数字的事实来源。 */
    private void addLegacyVerificationFacts(List<AgentQueryFact> facts, Map<String, Object> result) {
        String sourceId = String.valueOf(result.get("customerCode"));
        addFact(facts, "累计核销", result.get("totalVerified"), "餐", "CUSTOMER_VERIFICATION_SUMMARY", sourceId);
        addFact(facts, "累计核销早餐", result.get("totalVerifiedBreakfast"), "餐", "CUSTOMER_VERIFICATION_SUMMARY", sourceId);
        addFact(facts, "累计核销午餐", result.get("totalVerifiedLunch"), "餐", "CUSTOMER_VERIFICATION_SUMMARY", sourceId);
        addFact(facts, "累计核销晚餐", result.get("totalVerifiedDinner"), "餐", "CUSTOMER_VERIFICATION_SUMMARY", sourceId);
        Object recent = result.get("recentVerifications");
        addFact(facts, "最近核销记录数", recent instanceof List ? ((List<?>) recent).size() : 0, "条", "CUSTOMER_VERIFICATION_SUMMARY", sourceId);
    }

    /** 仅当来源结果提供数值时追加事实，避免为缺失字段伪造零值。 */
    private void addFact(List<AgentQueryFact> facts, String label, Object value, String unit, String sourceType, String sourceId) {
        if (value != null) facts.add(new AgentQueryFact("F" + (facts.size() + 1), label, value, unit, sourceType, sourceId));
    }

    /** 从主系统已返回的确定性计数字段取得数值，兼容 JSON 的 Number 表达。 */
    private long numericValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String totalFactLabel(String responseType) {
        if ("BUSINESS_QUERY_ORDER".equals(responseType)) return "订单数量";
        if ("BUSINESS_QUERY_VERIFICATION".equals(responseType)) return "核销记录数";
        if ("BUSINESS_QUERY_REFUND".equals(responseType)) return "退餐记录数";
        if ("BUSINESS_QUERY_MEAL_PLAN".equals(responseType)) return "排餐记录数";
        if ("BUSINESS_QUERY_SCHEDULED_MENU".equals(responseType)) return "排期菜品数";
        if ("BUSINESS_QUERY_DISH".equals(responseType)) return "菜品数量";
        return null;
    }

    private String totalFactUnit(String responseType) {
        if ("BUSINESS_QUERY_SCHEDULED_MENU".equals(responseType) || "BUSINESS_QUERY_DISH".equals(responseType)) return "道";
        if ("BUSINESS_QUERY_VERIFICATION".equals(responseType) || "BUSINESS_QUERY_REFUND".equals(responseType)
            || "BUSINESS_QUERY_MEAL_PLAN".equals(responseType)) return "条";
        return "笔";
    }

    private String totalFactSourceType(String responseType) {
        if ("BUSINESS_QUERY_VERIFICATION".equals(responseType)) return "VERIFICATION_LIST";
        if ("BUSINESS_QUERY_REFUND".equals(responseType)) return "REFUND_LIST";
        if ("BUSINESS_QUERY_MEAL_PLAN".equals(responseType)) return "MEAL_PLAN_LIST";
        if ("BUSINESS_QUERY_SCHEDULED_MENU".equals(responseType)) return "SCHEDULED_DISH_LIST";
        if ("BUSINESS_QUERY_DISH".equals(responseType)) return "DISH_LIST";
        return "ORDER_LIST";
    }
}
