package me.zhengjie.agent.query;

import me.zhengjie.agent.query.domain.AgentQueryFact;
import me.zhengjie.agent.query.domain.AgentQueryMetric;

import java.util.List;
import java.util.Map;

/**
 * 统一组装只读业务回答的事实引用，避免各查询分支自行拼接不可追溯文案。
 */
public class BusinessAnswerComposer {

    /** 构造客户概览固定话术，只使用主系统返回的受控字段。 */
    @SuppressWarnings("unchecked")
    public String customerOverview(Map<String, Object> result) {
        if (result == null || !Boolean.TRUE.equals(result.get("present"))) return "未找到该客户，或当前无权查看客户档案。";
        Map<String, Object> balance = result.get("mealBalance") instanceof Map ? (Map<String, Object>) result.get("mealBalance") : Map.of();
        return String.format("%s（%s）当前有 %s 笔进行中订单，剩余早餐 %s 餐、午晚餐 %s 餐。",
            result.getOrDefault("customerCode", ""), result.getOrDefault("customerName", ""),
            result.getOrDefault("activeOrderCount", 0), balance.getOrDefault("remainingBreakfast", 0),
            balance.getOrDefault("remainingLunchDinner", 0));
    }

    /** 构造无金额订单列表或详情摘要话术。 */
    public String orderList(Map<String, Object> result) {
        if (result == null) return "订单查询异常，请稍后重试。";
        Object items = result.get("items");
        int count = items instanceof List ? ((List<?>) items).size() : 0;
        return String.format("共查询到 %s 笔订单，当前返回 %d 笔订单摘要（不含金额信息）。", result.getOrDefault("total", count), count);
    }

    /** 构造核销列表固定话术，并明确午餐和晚餐使用同一餐数池。 */
    public String verificationList(Map<String, Object> result) {
        if (result == null) return "核销查询异常，请稍后重试。";
        Object items = result.get("items");
        int count = items instanceof List ? ((List<?>) items).size() : 0;
        return String.format("查询到 %s 条未删除核销记录，当前展示最近 %d 条。午餐和晚餐均扣减午晚餐餐数池。", result.getOrDefault("total", 0), count);
    }

    /** 构造退餐列表固定话术，明确排除退款金额。 */
    public String refundList(Map<String, Object> result) {
        if (result == null) return "退餐查询异常，请稍后重试。";
        Object items = result.get("items");
        int count = items instanceof List ? ((List<?>) items).size() : 0;
        return String.format("查询到 %s 条退餐记录，当前展示最近 %d 条。退餐记录不包含退款金额。", result.getOrDefault("total", 0), count);
    }

    /** 构造餐数变化组合查询话术，只引用本轮客户、核销和退餐工具结果。 */
    public String mealBalanceChange(Map<String, Object> overview, Map<String, Object> verification, Map<String, Object> refunds) {
        Object verified = verification == null ? 0 : verification.getOrDefault("total", 0);
        Object refunded = refunds == null ? 0 : refunds.getOrDefault("total", 0);
        return "当前餐数余额以有效订单和未删除核销记录实时计算；最近查询到核销记录 " + verified + " 条、退餐记录 " + refunded + " 条。午餐和晚餐共同扣减午晚餐餐数池。";
    }

    /** 构造客户套餐概览话术。 */
    @SuppressWarnings("unchecked")
    public String customerPackages(Map<String, Object> result) {
        if (result == null || !Boolean.TRUE.equals(result.get("present"))) return "未找到该客户，或当前无权查看客户档案。";
        Object packages = result.get("packages");
        if (!(packages instanceof List) || ((List<?>) packages).isEmpty()) return "该客户当前没有可展示的签约套餐记录。";
        String names = ((List<Map<String, Object>>) packages).stream()
            .map(item -> firstText(item.get("parentPackageName"), item.get("childPackageName")))
            .filter(this::notBlank).distinct().limit(5).collect(java.util.stream.Collectors.joining("、"));
        return names.isEmpty() ? "该客户的签约套餐记录缺少套餐名称，请到订单页面核对。" : "该客户签约套餐：" + names + "。";
    }

    /** 构造套餐规格明细话术。 */
    @SuppressWarnings("unchecked")
    public String packageDetails(Map<String, Object> result) {
        if (result == null || !(result.get("items") instanceof List) || ((List<?>) result.get("items")).isEmpty()) {
            return "该客户当前没有可展示的套餐规格，或当前账号缺少套餐查询权限。";
        }
        String names = ((List<Map<String, Object>>) result.get("items")).stream()
            .map(item -> String.valueOf(item.getOrDefault("parentPackageName", "未命名套餐"))).limit(5)
            .collect(java.util.stream.Collectors.joining("、"));
        return "已查询客户关联套餐规格：" + names + "。子套餐餐品规格见下方。";
    }

    /** 构造菜品配料摘要话术。 */
    @SuppressWarnings("unchecked")
    public String dishIngredients(Map<String, Object> result) {
        if (result == null || !(result.get("items") instanceof List) || ((List<?>) result.get("items")).isEmpty()) return "未找到对应菜品或配料摘要。";
        String text = ((List<Map<String, Object>>) result.get("items")).stream().limit(5)
            .map(item -> String.valueOf(item.getOrDefault("dishName", "菜品")) + "：" + joinValues(item.get("ingredientNames")))
            .collect(java.util.stream.Collectors.joining("；"));
        return "菜品配料摘要：" + text + "。";
    }

    /** 构造公共排期菜单话术，结果不含任何客户或订单信息。 */
    @SuppressWarnings("unchecked")
    public String scheduledMenu(Map<String, Object> result) {
        List<Map<String, Object>> items = result != null && result.get("items") instanceof List ? (List<Map<String, Object>>) result.get("items") : List.of();
        if (items.isEmpty()) return "指定日期暂无已配置的公共排期菜单。";
        String names = items.stream().map(item -> String.valueOf(item.getOrDefault("dishName", ""))).filter(this::notBlank).limit(10).collect(java.util.stream.Collectors.joining("、"));
        return names.isEmpty() ? "指定日期已配置公共排期，但暂无可展示的菜品名称。" : "指定日期的公共排期菜单：" + names + "。";
    }

    /** 构造指定日期餐次的候选菜预览话术，不声称已创建排餐。 */
    public String dishCandidates(Map<String, Object> result) {
        if (result == null || !Boolean.TRUE.equals(result.get("present"))) return "未找到该客户，或当前无权查询其候选菜。";
        return "指定日期餐次共有 " + number(result.get("totalCandidateCount")) + " 个排期候选菜，其中 "
            + number(result.get("availableCandidateCount")) + " 个当前可用、" + number(result.get("filteredCandidateCount"))
            + " 个因套餐、客户排除菜或过敏标签被过滤。该结果仅为候选预览，不表示已生成排餐。";
    }

    /** 构造排餐记录摘要话术。 */
    @SuppressWarnings("unchecked")
    public String mealPlan(Map<String, Object> result) {
        if (result == null) return "排餐查询异常，请稍后重试。";
        Object items = result.get("items");
        if (!(items instanceof List) || ((List<?>) items).isEmpty()) return "该客户在指定日期和餐次没有已生成的排餐记录。";
        Map<String, Object> plan = ((List<Map<String, Object>>) items).get(0);
        List<Map<String, Object>> dishes = plan.get("dishes") instanceof List ? (List<Map<String, Object>>) plan.get("dishes") : List.of();
        String dishNames = dishes.stream().map(item -> String.valueOf(item.get("dishName"))).filter(this::notBlank).limit(5)
            .collect(java.util.stream.Collectors.joining("、"));
        return String.format("%s %s 已生成排餐，状态：%s；菜品：%s。", plan.getOrDefault("recordDate", ""),
            mealTypeText(String.valueOf(plan.get("mealTypeCode"))), plan.getOrDefault("generationStatus", "-"),
            dishNames.isEmpty() ? "暂无菜品明细" : dishNames);
    }

    /** 构造已排未核销的记录数量话术。 */
    public String unverifiedMealPlans(Map<String, Object> result) {
        return "查询到 " + (result == null ? 0 : result.getOrDefault("total", 0)) + " 条已排餐但尚未核销的记录，详情见下方。";
    }

    /** 构造有餐未排的组合结论，只使用本轮概览和排餐结果。 */
    @SuppressWarnings("unchecked")
    public String mealBalanceWithoutPlan(Map<String, Object> overview, Map<String, Object> plans) {
        Map<String, Object> balance = overview != null && overview.get("mealBalance") instanceof Map ? (Map<String, Object>) overview.get("mealBalance") : Map.of();
        int remaining = number(balance.get("remainingBreakfast")) + number(balance.get("remainingLunchDinner"));
        int planCount = plans != null && plans.get("items") instanceof List ? ((List<?>) plans.get("items")).size() : 0;
        if (remaining > 0 && planCount == 0) return "当前仍有 " + remaining + " 餐可用餐数，但指定日期和餐次未查询到排餐记录；请继续核对排除日期、订单有效性、排餐模式和生成失败原因。";
        return "当前可用餐数与指定排餐记录已查询完成，详情见下方。";
    }

    /** 构造已配置业务规则的只读解释话术。 */
    public String businessRule(Map<String, Object> result) {
        if (result == null || !Boolean.TRUE.equals(result.get("present"))) return "当前问题尚未配置为可解释的业务规则。";
        return String.valueOf(result.getOrDefault("content", "当前规则暂无说明"));
    }

    /** 构造跨客户运营统计的确定性回答，不让模型重新计算聚合数。 */
    public String operationStatistics(Map<String, Object> result, String metricLabel) {
        if (result == null || result.isEmpty()) return "运营统计查询未返回可用结果，请稍后重试。";
        Object value = result.containsKey("total") ? result.get("total") : result.get(metricKey(metricLabel));
        String date = String.valueOf(result.getOrDefault("recordDate", "指定日期"));
        String definition = String.valueOf(result.getOrDefault("metricDefinitionId", "受控运营统计口径"));
        return date + metricLabel + "为 " + number(value) + " 个。统计口径：" + definition + "。";
    }

    /**
     * 组装同源运营指标报表，不计算或修改主系统已返回的任何数值。
     *
     * @param result 主系统每日工作量聚合结果
     * @param metrics 已登记且同源的指标列表
     * @return 可由逐项 facts 校验的固定话术
     */
    public String operationReport(Map<String, Object> result, List<AgentQueryMetric> metrics) {
        if (result == null || result.isEmpty() || metrics == null || metrics.isEmpty()) return "运营统计查询未返回可用结果，请稍后重试。";
        String date = String.valueOf(result.getOrDefault("recordDate", "指定日期"));
        String body = metrics.stream().map(metric -> metricLabel(metric) + " " + number(metricValue(result, metric)) + " 个")
            .collect(java.util.stream.Collectors.joining("；"));
        String definition = String.valueOf(result.getOrDefault("metricDefinitionId", "受控运营统计口径"));
        return date + "运营统计：" + body + "。统计口径：" + definition + "。";
    }

    private String metricKey(String metricLabel) {
        if (metricLabel.contains("待核销")) return "unverifiedCustomerCount";
        if (metricLabel.contains("待排餐")) return "unscheduledCustomerCount";
        if (metricLabel.contains("已核销")) return "verifiedCustomerCount";
        if (metricLabel.contains("排餐失败")) return "mealPlanFailureCount";
        return "scheduledCustomerCount";
    }
    /** 返回登记指标的固定中文名称，禁止使用模型生成字段或标签。 */
    private String metricLabel(AgentQueryMetric metric) {
        if (metric == AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT) return "已排餐客户数";
        if (metric == AgentQueryMetric.DAILY_VERIFIED_CUSTOMER_COUNT) return "已核销客户数";
        if (metric == AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT) return "待核销客户数";
        if (metric == AgentQueryMetric.DAILY_EXPECTED_CUSTOMER_COUNT) return "应服务客户数";
        if (metric == AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT) return "待排餐客户数";
        if (metric == AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT) return "排餐失败数";
        return metric == null ? "运营指标" : metric.name();
    }
    /** 从主系统受控字段读取指标值，未知指标只能展示为零，不接受自由键名。 */
    private Object metricValue(Map<String, Object> result, AgentQueryMetric metric) {
        if (metric == AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT) return result.get("scheduledCustomerCount");
        if (metric == AgentQueryMetric.DAILY_VERIFIED_CUSTOMER_COUNT) return result.get("verifiedCustomerCount");
        if (metric == AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT) return result.get("unverifiedCustomerCount");
        if (metric == AgentQueryMetric.DAILY_EXPECTED_CUSTOMER_COUNT) return result.get("expectedCustomerCount");
        if (metric == AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT) return result.get("unscheduledCustomerCount");
        if (metric == AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT) return result.get("mealPlanFailureCount");
        return 0;
    }

    /**
     * 在模板回答末尾追加本轮结构化事实编号。
     *
     * @param message 已校验的模板回答
     * @param facts 本轮结构化事实
     * @return 可回溯的展示文案
     */
    public String appendFactReferences(String message, List<AgentQueryFact> facts) {
        if (facts == null || facts.isEmpty()) return message;
        String references = facts.stream().map(AgentQueryFact::getFactId).filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.joining("、", "数据依据：[", "]"));
        return references.equals("数据依据：[]") ? message : message + " " + references;
    }

    private String firstText(Object first, Object second) {
        String value = first == null ? null : String.valueOf(first);
        return notBlank(value) ? value : second == null ? null : String.valueOf(second);
    }
    private String joinValues(Object values) {
        if (!(values instanceof List)) return "暂无配料";
        String joined = ((List<?>) values).stream().map(String::valueOf).limit(20).collect(java.util.stream.Collectors.joining("、"));
        return joined.isEmpty() ? "暂无配料" : joined;
    }
    private int number(Object value) { return value instanceof Number ? ((Number) value).intValue() : 0; }
    private String mealTypeText(String mealType) {
        if ("BREAKFAST".equals(mealType)) return "早餐";
        if ("LUNCH".equals(mealType)) return "午餐";
        if ("DINNER".equals(mealType)) return "晚餐";
        return mealType == null ? "" : mealType;
    }
    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
}
