package me.zhengjie.agent.query;

import me.zhengjie.agent.query.domain.AgentQueryFact;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentMetricDefinition;
import me.zhengjie.agent.query.presentation.BusinessPresentationResult;

import java.util.List;

/**
 * 统一组装只读业务回答的事实引用，避免各查询分支自行拼接不可追溯文案。
 */
public class BusinessAnswerComposer {

    /** 构造客户概览固定话术，只使用主系统返回的受控字段。 */
    public String customerOverview(BusinessPresentationResult result) {
        if (result == null || !result.isPresent()) return "未找到该客户，或当前无权查看客户档案。";
        BusinessPresentationResult balance = result.getMealBalance();
        String createTime = displayTime(result.getCreateTime(), "创建时间暂无记录");
        String purchaseSummary = !notBlank(result.getFirstPurchaseTime())
            ? "暂无购买订单" : "首次购买于 " + displayTime(result.getFirstPurchaseTime(), "");
        return String.format("%s（%s）客户档案创建于 %s，%s。当前有 %s 笔进行中订单，剩余早餐 %s 餐、午晚餐 %s 餐。",
            textOrEmpty(result.getCustomerCode()), textOrEmpty(result.getCustomerName()),
            createTime, purchaseSummary,
            valueOrZero(result.getActiveOrderCount()),
            balance == null ? 0 : valueOrZero(balance.getRemainingBreakfast()),
            balance == null ? 0 : valueOrZero(balance.getRemainingLunchDinner()));
    }

    /** 将主系统 ISO 时间转为客服可读文本；空值使用明确业务说明。 */
    private String displayTime(Object value, String emptyText) {
        if (value == null || String.valueOf(value).isBlank()) return emptyText;
        return String.valueOf(value).replace('T', ' ');
    }

    /** 构造无金额订单列表或详情摘要话术。 */
    public String orderList(BusinessPresentationResult result) {
        if (result == null) return "订单查询异常，请稍后重试。";
        int count = result.getItems().size();
        return String.format("共查询到 %s 笔订单，当前返回 %d 笔订单摘要（不含金额信息）。",
            result.totalOr(count), count);
    }

    /** 构造核销列表固定话术，并明确午餐和晚餐使用同一餐数池。 */
    public String verificationList(BusinessPresentationResult result) {
        if (result == null) return "核销查询异常，请稍后重试。";
        int count = result.getItems().size();
        return String.format("查询到 %s 条未删除核销记录，当前展示最近 %d 条。午餐和晚餐均扣减午晚餐餐数池。",
            result.totalOr(0), count);
    }

    /** 构造退餐列表固定话术，明确排除退款金额。 */
    public String refundList(BusinessPresentationResult result) {
        if (result == null) return "退餐查询异常，请稍后重试。";
        int count = result.getItems().size();
        return String.format("查询到 %s 条退餐记录，当前展示最近 %d 条。退餐记录不包含退款金额。",
            result.totalOr(0), count);
    }

    /** 构造餐数变化组合查询话术，只引用本轮客户、核销和退餐工具结果。 */
    public String mealBalanceChange(BusinessPresentationResult overview,
                                    BusinessPresentationResult verification,
                                    BusinessPresentationResult refunds) {
        Object verified = verification == null ? 0 : verification.totalOr(0);
        Object refunded = refunds == null ? 0 : refunds.totalOr(0);
        return "当前餐数余额以有效订单和未删除核销记录实时计算；最近查询到核销记录 " + verified + " 条、退餐记录 " + refunded + " 条。午餐和晚餐共同扣减午晚餐餐数池。";
    }

    /** 构造客户套餐概览话术。 */
    public String customerPackages(BusinessPresentationResult result) {
        if (result == null || !result.isPresent()) return "未找到该客户，或当前无权查看客户档案。";
        if (result.getPackages().isEmpty()) return "该客户当前没有可展示的签约套餐记录。";
        String names = result.getPackages().stream()
            .map(item -> firstText(item.getParentPackageName(), item.getChildPackageName()))
            .filter(this::notBlank).distinct().limit(5).collect(java.util.stream.Collectors.joining("、"));
        return names.isEmpty() ? "该客户的签约套餐记录缺少套餐名称，请到订单页面核对。" : "该客户签约套餐：" + names + "。";
    }

    /** 构造套餐规格明细话术。 */
    public String packageDetails(BusinessPresentationResult result) {
        if (result == null || result.getItems().isEmpty()) {
            return "该客户当前没有可展示的套餐规格，或当前账号缺少套餐查询权限。";
        }
        String names = result.getItems().stream()
            .map(item -> textOr(item.getParentPackageName(), "未命名套餐")).limit(5)
            .collect(java.util.stream.Collectors.joining("、"));
        return "已查询客户关联套餐规格：" + names + "。子套餐餐品规格见下方。";
    }

    /** 构造菜品配料摘要话术。 */
    public String dishIngredients(BusinessPresentationResult result) {
        if (result == null || result.getItems().isEmpty()) return "未找到对应菜品或配料摘要。";
        String text = result.getItems().stream().limit(5)
            .map(item -> textOr(item.getDishName(), "菜品") + "："
                + joinValues(item.getIngredientNames()))
            .collect(java.util.stream.Collectors.joining("；"));
        return "菜品配料摘要：" + text + "。";
    }

    /** 构造公共排期菜单话术，结果不含任何客户或订单信息。 */
    public String scheduledMenu(BusinessPresentationResult result) {
        if (result != null && !result.getGroups().isEmpty()) {
            String text = result.getGroups().stream().map(group -> {
                String names = group.getItems().stream()
                    .map(BusinessPresentationResult::getDishName)
                    .filter(this::notBlank).limit(10).collect(java.util.stream.Collectors.joining("、"));
                String mealType = textOr(group.getMealTypeName(),
                    textOr(group.getMealTypeCode(), "菜单"));
                return names.isEmpty() ? mealType + "暂无已配置菜品" : mealType + "：" + names;
            }).collect(java.util.stream.Collectors.joining("；"));
            return text.isEmpty() ? "指定日期暂无已配置的公共排期菜单。" : "指定日期公共排期菜单（按餐次）：" + text + "。";
        }
        List<BusinessPresentationResult> items =
            result == null ? List.of() : result.getItems();
        if (items.isEmpty()) return "指定日期暂无已配置的公共排期菜单。";
        String names = items.stream().map(BusinessPresentationResult::getDishName)
            .filter(this::notBlank).limit(10)
            .collect(java.util.stream.Collectors.joining("、"));
        return names.isEmpty() ? "指定日期已配置公共排期，但暂无可展示的菜品名称。" : "指定日期的公共排期菜单：" + names + "。";
    }

    /** 构造指定日期餐次的候选菜预览话术，不声称已创建排餐。 */
    public String dishCandidates(BusinessPresentationResult result) {
        if (result == null || !result.isPresent()) return "未找到该客户，或当前无权查询其候选菜。";
        return "指定日期餐次共有 " + number(result.getTotalCandidateCount()) + " 个排期候选菜，其中 "
            + number(result.getAvailableCandidateCount()) + " 个当前可用、"
            + number(result.getFilteredCandidateCount())
            + " 个因套餐、客户排除菜或过敏标签被过滤。该结果仅为候选预览，不表示已生成排餐。";
    }

    /** 构造排餐记录摘要话术。 */
    public String mealPlan(BusinessPresentationResult result) {
        return mealPlan(result, false);
    }

    /**
     * 构造排餐记录摘要；历史查询直接回答是否曾经排餐，并展示最近一条记录。
     *
     * @param result 排餐分页结果
     * @param historical 是否未限定日期的历史查询
     * @return 可直接展示的确定性回答
     */
    public String mealPlan(BusinessPresentationResult result, boolean historical) {
        if (result == null) return "排餐查询异常，请稍后重试。";
        if (result.getItems().isEmpty()) {
            return historical ? "该客户从未生成过排餐记录。" : "该客户在指定日期和餐次没有已生成的排餐记录。";
        }
        BusinessPresentationResult plan = result.getItems().get(0);
        String dishNames = plan.getDishes().stream()
            .map(BusinessPresentationResult::getDishName).filter(this::notBlank).limit(5)
            .collect(java.util.stream.Collectors.joining("、"));
        String detail = String.format("%s %s 已生成排餐，状态：%s；菜品：%s。",
            textOrEmpty(plan.getRecordDate()), mealTypeText(plan.getMealTypeCode()),
            textOr(plan.getGenerationStatus(), "-"),
            dishNames.isEmpty() ? "暂无菜品明细" : dishNames);
        if (!historical) return detail;
        return String.format("该客户曾经排过餐，共 %s 条记录；最近一条：%s",
            number(result.getTotal()), detail);
    }

    /** 根据已过滤的排餐过敏事实生成确定性回答，不把客户主动排除菜品表述为过敏。 */
    public String mealPlanAllergy(BusinessPresentationResult result) {
        List<BusinessPresentationResult> items =
            result == null ? List.of() : result.getItems();
        if (items.isEmpty()) return "指定日期和餐次的已查询排餐中，没有发现实际命中过敏过滤的菜品。";
        String details = items.stream().map(item -> {
            String customerCode = textOrEmpty(item.getCustomerCode());
            String dishesText = item.getDishes().stream().map(dish -> {
                String name = textOr(dish.getDishName(), "未命名菜品");
                String tags = String.join("、", dish.getAllergyReasons());
                return tags.isEmpty() ? name : name + "（过敏标签：" + tags + "）";
            }).collect(java.util.stream.Collectors.joining("、"));
            return customerCode + "：" + dishesText;
        }).collect(java.util.stream.Collectors.joining("；"));
        long scanned = result.getScannedCount() instanceof Number
            ? ((Number) result.getScannedCount()).longValue() : 0L;
        return "指定日期和餐次的排餐中，以下客户存在实际过敏过滤：" + details + "。本次扫描 " + scanned + " 条排餐记录。";
    }

    /** 构造已排未核销的记录数量话术。 */
    public String unverifiedMealPlans(BusinessPresentationResult result) {
        return "查询到 " + (result == null ? 0 : result.totalOr(0))
            + " 条已排餐但尚未核销的记录，详情见下方。";
    }

    /** 构造有餐未排的组合结论，只使用本轮概览和排餐结果。 */
    public String mealBalanceWithoutPlan(BusinessPresentationResult overview,
                                         BusinessPresentationResult plans) {
        BusinessPresentationResult balance =
            overview == null ? null : overview.getMealBalance();
        int remaining = balance == null ? 0
            : number(balance.getRemainingBreakfast())
            + number(balance.getRemainingLunchDinner());
        int planCount = plans == null ? 0 : plans.getItems().size();
        if (remaining > 0 && planCount == 0) return "当前仍有 " + remaining + " 餐可用餐数，但指定日期和餐次未查询到排餐记录；请继续核对排除日期、订单有效性、排餐模式和生成失败原因。";
        return "当前可用餐数与指定排餐记录已查询完成，详情见下方。";
    }

    /** 构造已配置业务规则的只读解释话术。 */
    public String businessRule(BusinessPresentationResult result) {
        if (result == null || !result.isPresent()) return "当前问题尚未配置为可解释的业务规则。";
        return textOr(result.getContent(), "当前规则暂无说明");
    }

    /**
     * 按指标目录生成单指标回答，展示名和结果字段不再由用户原文或中文标签反推。
     *
     * @param result 主系统受控聚合结果
     * @param metric QueryPlan 中唯一的登记指标
     * @return 带实际业务日期和口径标识的固定回答
     */
    public String operationStatistics(BusinessPresentationResult result, AgentQueryMetric metric) {
        AgentMetricDefinition definition = AgentMetricCatalog.definition(metric);
        if (result == null || result.isEmpty() || definition == null) return "运营统计查询未返回可用结果，请稍后重试。";
        Object value = result.metricValue(metric);
        if (value == null) return "运营统计结果缺少登记指标字段，已停止展示，请稍后重试。";
        if (metric == AgentQueryMetric.ACTIVE_CUSTOMER_COUNT) {
            return "当前仍有可用餐数的活跃客户共 " + number(value) + " 位。";
        }
        String date = textOr(result.getRecordDate(), "当前口径");
        String metricDefinition =
            textOr(result.getMetricDefinitionId(), definition.getMetricVersion());
        return date + definition.getDisplayName() + "为 " + number(value) + " " + definition.getResultUnit()
            + "。统计口径：" + metricDefinition + "。";
    }

    /** 构造活跃客户餐数余额明细摘要，逐客户数值由受控明细卡展示。 */
    public String activeCustomerBalances(BusinessPresentationResult result) {
        if (result == null || result.isEmpty()) return "活跃客户餐数余额查询未返回可用结果，请稍后重试。";
        int shown = result.getItems().size();
        Object total = result.totalOr(shown);
        return "当前仍有可用餐数的活跃客户共 " + number(total) + " 位，已展示 " + shown
            + " 位客户各自的早餐、午晚餐和合计剩余餐数。";
    }

    /**
     * 组装同源运营指标报表，不计算或修改主系统已返回的任何数值。
     *
     * @param result 主系统每日工作量聚合结果
     * @param metrics 已登记且同源的指标列表
     * @return 可由逐项 facts 校验的固定话术
     */
    public String operationReport(BusinessPresentationResult result,
                                  List<AgentQueryMetric> metrics) {
        if (result == null || result.isEmpty() || metrics == null || metrics.isEmpty()) return "运营统计查询未返回可用结果，请稍后重试。";
        String date = textOr(result.getRecordDate(), "指定日期");
        String body = metrics.stream().map(metric -> metricLabel(metric) + " " + number(metricValue(result, metric)) + " 个")
            .collect(java.util.stream.Collectors.joining("；"));
        String definition =
            textOr(result.getMetricDefinitionId(), "受控运营统计口径");
        return date + "运营统计：" + body + "。统计口径：" + definition + "。";
    }

    /** 返回登记指标的固定中文名称，禁止使用模型生成字段或标签。 */
    private String metricLabel(AgentQueryMetric metric) {
        AgentMetricDefinition definition = AgentMetricCatalog.definition(metric);
        return definition == null ? "运营指标" : definition.getDisplayName();
    }
    /** 从主系统受控字段读取指标值，未知指标只能展示为零，不接受自由键名。 */
    private Object metricValue(BusinessPresentationResult result, AgentQueryMetric metric) {
        return result == null ? null : result.metricValue(metric);
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

    private String firstText(String first, String second) {
        return notBlank(first) ? first : second;
    }
    private String joinValues(List<String> values) {
        if (values == null) return "暂无配料";
        String joined = values.stream().limit(20)
            .collect(java.util.stream.Collectors.joining("、"));
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
    private Object valueOrZero(Object value) { return value == null ? 0 : value; }
    private String textOrEmpty(String value) { return value == null ? "" : value; }
    private String textOr(String value, String fallback) {
        return notBlank(value) ? value : fallback;
    }
}
