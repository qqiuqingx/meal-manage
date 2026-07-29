package me.zhengjie.agent.query;

import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentQueryFact;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentMetricDefinition;
import me.zhengjie.agent.query.domain.BusinessResponseTypeCatalog;
import me.zhengjie.agent.query.presentation.BusinessPresentationResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 组装受控业务查询响应，统一处理事实、话术安全校验、时间和 QueryPlan。 */
public class BusinessQueryResponseFactory {
    private static final DateTimeFormatter RECORD_DATE_FORMATTER = new DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .optionalStart().appendLiteral(' ').append(DateTimeFormatter.ISO_LOCAL_TIME).optionalEnd()
        .optionalStart().appendLiteral('T').append(DateTimeFormatter.ISO_LOCAL_TIME).optionalEnd()
        .toFormatter();
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
                                    String conversationStage, String responseType,
                                    BusinessPresentationResult insightResult,
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
        response.setResponseType(responseType);
        response.setInsightResult(safe && insightResult != null
            ? insightResult.toPresentationMap() : Map.of());
        response.setFacts(safe ? facts : List.of());
        if (!safe) response.setWarnings(planMatches ? List.of("回答安全校验未通过，已隐藏结构化结果。") : List.of("PLAN_RESULT_MISMATCH", "回答安全校验未通过，已隐藏结构化结果。"));
        response.setPartial(!planMatches
            || insightResult != null && insightResult.isTruncated());
        response.setQueriedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        response.setQueryPlan(queryPlan);
        return response;
    }

    /** 校验主系统返回对象与当前受控 QueryPlan 的客户、订单、日期和餐次约束一致。 */
    private boolean matchesQueryPlan(AgentQueryPlan plan,
                                     BusinessPresentationResult result) {
        if (plan == null || result == null || plan.getEntities() == null || plan.getFilters() == null) return true;
        BusinessPresentationResult item = result.getItems().isEmpty()
            ? null : result.getItems().get(0);
        if (!matchesId(plan.getEntities().getCustomerId(),
            firstValue(result.getCustomerId(), item == null ? null : item.getCustomerId()))) {
            return false;
        }
        if (!matchesId(plan.getEntities().getOrderId(),
            firstValue(result.getOrderId(), item == null ? null : item.getOrderId()))) {
            return false;
        }
        if (!matchesRecordDate(plan.getFilters().getRecordDate(),
            firstValue(result.getRecordDate(), item == null ? null : item.getRecordDate()))) {
            return false;
        }
        return matchesText(plan.getFilters().getMealType(),
            firstValue(result.getMealTypeCode(), item == null ? null : item.getMealTypeCode()));
    }

    private Object firstValue(Object root, Object item) {
        return root == null ? item : root;
    }

    private boolean matchesId(Long expected, Object actual) {
        if (expected == null || actual == null) return true;
        return actual instanceof Number && expected.equals(((Number) actual).longValue());
    }

    /**
     * 比较受控文本筛选条件；空白值表示用户未限定该条件，不应与返回的实际业务字段产生冲突。
     *
     * @param expected QueryPlan 中的筛选条件
     * @param actual 工具结果中的实际字段
     * @return 未限定、结果缺失或两者一致时返回 true
     */
    private boolean matchesText(String expected, Object actual) {
        if (isBlank(expected) || actual == null) return true;
        return expected.equalsIgnoreCase(String.valueOf(actual));
    }

    /**
     * 按业务日期比较 QueryPlan 与工具结果，兼容主系统将 LocalDate 序列化为零点日期时间的格式。
     * 无法解析的值继续按原始文本严格比较，避免异常日期绕过结果一致性校验。
     */
    private boolean matchesRecordDate(String expected, Object actual) {
        if (isBlank(expected) || actual == null) return true;
        LocalDate expectedDate = parseRecordDate(expected);
        LocalDate actualDate = parseRecordDate(String.valueOf(actual));
        if (expectedDate != null && actualDate != null) return expectedDate.equals(actualDate);
        return matchesText(expected, actual);
    }

    /** 判断 QueryPlan 字符串筛选条件是否为空白，空白代表不施加该维度约束。 */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 将受控日期或日期时间文本解析为业务日期，解析失败时返回 null。 */
    private LocalDate parseRecordDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDate.from(RECORD_DATE_FORMATTER.parse(value.trim()));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** 构建每个可展示确定性数字的事实引用。 */
    private List<AgentQueryFact> buildFacts(String responseType,
                                            BusinessPresentationResult result) {
        if (responseType == null || result == null) return List.of();
        List<AgentQueryFact> facts = new ArrayList<>();
        if (!BusinessResponseTypeCatalog.isBusinessResponse(responseType)) {
            return facts;
        } else if (BusinessResponseTypeCatalog.MEAL_PLAN_ALLERGY.equals(responseType)) {
            addMealPlanAllergyFacts(facts, result);
        } else if (BusinessResponseTypeCatalog.CUSTOMER_CANDIDATES.equals(responseType)
            && result.getTotal() != null) {
            facts.add(new AgentQueryFact("F1", "候选客户数", result.getTotal(),
                "个", "CUSTOMER_CANDIDATE_LIST", null));
        } else if (BusinessResponseTypeCatalog.DISH_CANDIDATES.equals(responseType)
            && result.getTotalCandidateCount() != null) {
            String sourceId = String.valueOf(result.getCustomerId());
            facts.add(new AgentQueryFact("F1", "排期候选菜数",
                result.getTotalCandidateCount(), "个", "DISH_CANDIDATE_PREVIEW", sourceId));
            facts.add(new AgentQueryFact("F2", "当前可用候选菜数",
                result.getAvailableCandidateCount(), "个", "DISH_CANDIDATE_PREVIEW", sourceId));
            facts.add(new AgentQueryFact("F3", "已过滤候选菜数",
                result.getFilteredCandidateCount(), "个", "DISH_CANDIDATE_PREVIEW", sourceId));
        } else if (BusinessResponseTypeCatalog.CUSTOMER.equals(responseType)
            && result.getActiveOrderCount() != null) {
            String customerId = String.valueOf(result.getCustomerId());
            facts.add(new AgentQueryFact("F1", "进行中订单数",
                result.getActiveOrderCount(), "笔", "CUSTOMER_OVERVIEW", customerId));
            BusinessPresentationResult balance = result.getMealBalance();
            if (balance != null) {
                facts.add(new AgentQueryFact("F2", "剩余早餐",
                    balance.getRemainingBreakfast(), "餐", "ORDER_MEAL_BALANCE", customerId));
                facts.add(new AgentQueryFact("F3", "剩余午晚餐",
                    balance.getRemainingLunchDinner(), "餐", "ORDER_MEAL_BALANCE", customerId));
            }
            if (result.getVerificationRecordCount() != null) {
                facts.add(new AgentQueryFact("F4", "核销记录数",
                    result.getVerificationRecordCount(), "条", "VERIFICATION_LIST", customerId));
            }
            if (result.getRefundRecordCount() != null) {
                facts.add(new AgentQueryFact("F5", "退餐记录数",
                    result.getRefundRecordCount(), "条", "REFUND_LIST", customerId));
            }
        } else if (BusinessResponseTypeCatalog.OPERATION_REPORT.equals(responseType)) {
            addOperationReportFacts(facts, result);
        } else if (responseType.startsWith("BUSINESS_QUERY_OPERATION_")) {
            AgentMetricDefinition definition = AgentMetricCatalog.definitionByResponseType(responseType);
            Object value = definition == null ? null : result.metricValue(definition.getMetric());
            if (definition != null && value != null) {
                facts.add(new AgentQueryFact("F1", definition.getDisplayName(), value,
                    definition.getResultUnit(),
                    result.getMetricDefinitionId() == null ? definition.getMetricVersion()
                        : result.getMetricDefinitionId(),
                    result.getRecordDate()));
            }
        } else if (result.getTotal() != null) {
            String label = totalFactLabel(responseType);
            if (label == null) return facts;
            facts.add(new AgentQueryFact("F1", label, result.getTotal(),
                totalFactUnit(responseType), totalFactSourceType(responseType), null));
            if ((BusinessResponseTypeCatalog.ORDER.equals(responseType)
                || BusinessResponseTypeCatalog.VERIFICATION.equals(responseType)
                || BusinessResponseTypeCatalog.REFUND.equals(responseType))
                && result.isItemsDeclared()) {
                facts.add(new AgentQueryFact("F2", "当前展示记录数",
                    result.getItems().size(), "笔", totalFactSourceType(responseType), null));
            }
        } else if (BusinessResponseTypeCatalog.RULE.equals(responseType)
            && result.getVersion() != null) {
            facts.add(new AgentQueryFact("F1", "规则版本", result.getVersion(),
                null, "BUSINESS_RULE", result.getRuleId()));
        }
        return facts;
    }

    /** 为每条实际过敏过滤菜品建立客户编号绑定证据，客户主动排除菜品不会成为事实。 */
    private void addMealPlanAllergyFacts(List<AgentQueryFact> facts,
                                         BusinessPresentationResult result) {
        for (BusinessPresentationResult plan : result.getItems()) {
            String customerCode = plan.getCustomerCode();
            String recordDate = plan.getRecordDate();
            String mealType = plan.getMealTypeCode();
            String sourceRecordId = plan.getCustomerMealPlanId() == null
                ? null : String.valueOf(plan.getCustomerMealPlanId());
            for (BusinessPresentationResult dish : plan.getDishes()) {
                if (!dish.isAllergyFiltered()
                    || !"ALLERGY".equals(dish.getReplaceReason())) continue;
                AgentQueryFact fact = new AgentQueryFact("F" + (facts.size() + 1),
                    "因过敏过滤菜品", dish.getDishName(), null,
                    "MEAL_PLAN_DISH_ITEM", sourceRecordId);
                fact.setCustomerCode(customerCode); fact.setRecordDate(recordDate); fact.setMealType(mealType); fact.setSourceRecordId(sourceRecordId);
                facts.add(fact);
            }
        }
        if (result.getScannedCount() != null) {
            facts.add(new AgentQueryFact("F" + (facts.size() + 1),
                "已扫描排餐记录数", result.getScannedCount(),
                "条", "MEAL_PLAN_LIST", null));
        }
    }

    /** 为多指标运营报表逐项生成事实，禁止将主系统字段名直接暴露为展示标签。 */
    private void addOperationReportFacts(List<AgentQueryFact> facts,
                                         BusinessPresentationResult result) {
        int index = 1;
        for (AgentQueryMetric metric : result.getReportMetrics()) {
            facts.add(new AgentQueryFact("F" + index++, reportMetricLabel(metric),
                result.metricValue(metric), "个",
                result.getMetricDefinitionId() == null
                    ? "AGENT_OPERATION_STATISTICS" : result.getMetricDefinitionId(),
                result.getRecordDate()));
        }
    }

    private String reportMetricLabel(AgentQueryMetric metric) {
        AgentMetricDefinition definition = AgentMetricCatalog.definition(metric);
        return definition == null ? metric.name() : definition.getDisplayName();
    }

    private String totalFactLabel(String responseType) {
        BusinessResponseTypeCatalog.FactDefinition fact = BusinessResponseTypeCatalog
            .find(responseType).map(BusinessResponseTypeCatalog.Definition::totalFact).orElse(null);
        return fact == null ? null : fact.label();
    }

    private String totalFactUnit(String responseType) {
        BusinessResponseTypeCatalog.FactDefinition fact = BusinessResponseTypeCatalog
            .find(responseType).map(BusinessResponseTypeCatalog.Definition::totalFact).orElse(null);
        return fact == null ? null : fact.unit();
    }

    private String totalFactSourceType(String responseType) {
        BusinessResponseTypeCatalog.FactDefinition fact = BusinessResponseTypeCatalog
            .find(responseType).map(BusinessResponseTypeCatalog.Definition::totalFact).orElse(null);
        return fact == null ? null : fact.sourceType();
    }
}
