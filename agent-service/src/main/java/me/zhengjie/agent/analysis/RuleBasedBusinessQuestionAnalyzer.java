package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessAmbiguity;
import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.analysis.domain.BusinessInteractionMode;
import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.analysis.domain.MealScope;
import me.zhengjie.agent.analysis.domain.CorrectionReason;
import me.zhengjie.agent.analysis.domain.BusinessTemporalExpression;
import me.zhengjie.agent.analysis.domain.BusinessTemporalIntent;
import me.zhengjie.agent.query.domain.BusinessCorrection;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则优先的业务问题分析器，识别高确定性的运营统计词、日期、餐次与会话对象。
 */
public class RuleBasedBusinessQuestionAnalyzer implements BusinessQuestionAnalyzer {
    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context) {
        return analyze(question, context, null);
    }

    /** {@inheritDoc} */
    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context,
                                            LastBusinessQueryContext lastBusinessQueryContext) {
        String text = question == null ? "" : question.trim();
        BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
        analysis.setEntities(copyEntities(context));
        analysis.setFilters(copyFilters(context));
        analysis.setSource("RULE");
        if (isContextCorrection(text, lastBusinessQueryContext)) return correctionMenu(analysis, text, lastBusinessQueryContext);
        if (isScheduledMenuQuestion(text, context)) return scheduledMenu(analysis, text);
        List<AgentQueryMetric> dailyReportMetrics = dailyReportMetrics(text);
        if (dailyReportMetrics.size() > 1) {
            operation(analysis, dailyReportMetrics, text);
        } else if (contains(text, "排餐失败", "生成失败", "失败记录")) {
            operation(analysis, AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT, text);
        } else if (contains(text, "待核销", "没核销", "未核销", "尚未核销")) {
            operation(analysis, AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT, text);
        } else if (contains(text, "待排餐", "没排餐", "未排餐", "没有排餐", "还没安排餐", "还没给他们排")) {
            operation(analysis, AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT, text);
        } else if (contains(text, "已排餐", "排餐客户", "排了多少", "生成排餐", "排餐的客户")) {
            operation(analysis, AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT, text);
        } else if (isCustomerProfileCountQuestion(text)) {
            operation(analysis, AgentQueryMetric.CUSTOMER_PROFILE_COUNT, text);
        } else if (isCustomerOverviewQuestion(text)) {
            analysis.setDomains(List.of(AgentQueryDomain.CUSTOMER));
            analysis.setQueryTarget(BusinessQueryTarget.CUSTOMER);
            analysis.setConfidence(0.94D);
        } else if (contains(text, "活跃客户", "进行中客户")
            || text.contains("有餐数") && !contains(text, "没排餐", "未排餐", "没有排餐", "还没安排餐")) {
            operation(analysis, AgentQueryMetric.ACTIVE_CUSTOMER_COUNT, text);
        } else if (contains(text, "即将到期", "快到期", "会到期", "到期订单")) {
            operation(analysis, AgentQueryMetric.EXPIRING_ORDER_COUNT, text);
        } else if (isCustomerMealPlanQuestion(text, analysis.getEntities())) {
            customerMealPlan(analysis, text);
        } else if (contains(text, "核销", "使用了多少餐")) {
            analysis.setDomains(List.of(AgentQueryDomain.VERIFICATION));
            analysis.setMetrics(List.of(AgentQueryMetric.VERIFICATION_COUNT));
            analysis.setConfidence(0.92D);
        } else if (contains(text, "退餐")) {
            analysis.setDomains(List.of(AgentQueryDomain.REFUND));
            analysis.setMetrics(List.of(AgentQueryMetric.REFUND_COUNT));
            analysis.setConfidence(0.92D);
        } else if (isMealBalanceQuestion(text)) {
            analysis.setDomains(List.of(AgentQueryDomain.ORDER));
            analysis.setMetrics(List.of(AgentQueryMetric.MEAL_BALANCE));
            analysis.setConfidence(0.75D);
        } else if (isRemainingCustomerQuestion(text)) {
            operation(analysis, (AgentQueryMetric) null, text);
            ambiguity(analysis, "remainingMeaning", List.of("UNSCHEDULED", "UNVERIFIED", "UNDELIVERED"),
                "你想查今天待排餐、待配送还是待核销的客户数？");
            analysis.setConfidence(0.72D);
        } else {
            analysis.setConfidence(0D);
            analysis.setRequiresClarification(true);
            analysis.setClarificationQuestion("请说明想查询客户、订单、排餐、核销、退餐或运营统计中的哪类数据。");
        }
        applyTemporalIntent(analysis, text);
        return analysis;
    }

    /**
     * 构造带明确客户标识的排餐事实查询；缺少日期时保存为可续接澄清，不猜测公共菜单。
     *
     * @param analysis 当前受控分析结果
     * @param text 用户问题
     */
    private void customerMealPlan(BusinessQuestionAnalysis analysis, String text) {
        analysis.setDomains(List.of(AgentQueryDomain.MEAL_PLAN));
        analysis.setQueryTarget(BusinessQueryTarget.CUSTOMER_MEAL_PLAN);
        analysis.setConfidence(0.94D);
        boolean hasResolvedDate = analysis.getFilters() != null && (analysis.getFilters().getRecordDate() != null
            || analysis.getFilters().getStartDate() != null && analysis.getFilters().getEndDate() != null);
        boolean hasRelativeDate = contains(text, "今天", "今日", "昨天", "昨日", "明天", "明日", "本周", "这周");
        if (!hasResolvedDate && !hasRelativeDate) {
            analysis.setRequiresClarification(true);
            analysis.setClarificationQuestion("请确认要查询的排餐日期，例如今天、明天或 2026-07-14。");
        }
    }

    /** 带客户实体且明确询问排餐记录时，才启用客户排餐高精度兜底。 */
    private boolean isCustomerMealPlanQuestion(String text, AgentEntityReference entities) {
        boolean hasCustomer = entities != null && (entities.getCustomerId() != null
            || entities.getCustomerCode() != null && !entities.getCustomerCode().isBlank()
            || entities.getCustomerName() != null && !entities.getCustomerName().isBlank());
        return hasCustomer && contains(text, "排餐", "餐单", "吃什么")
            && !contains(text, "为什么", "失败", "过敏", "候选菜", "公共菜单");
    }

    /** 构造公共菜单的高精度离线回退，未指定餐次时固定为全部可用餐次。 */
    private BusinessQuestionAnalysis scheduledMenu(BusinessQuestionAnalysis analysis, String text) {
        analysis.setDomains(List.of(AgentQueryDomain.DISH));
        analysis.setQueryTarget(BusinessQueryTarget.SCHEDULED_MENU);
        MealScope scope = mealScope(text, analysis.getFilters().getMealType());
        analysis.setMealScope(scope);
        if (scope == MealScope.LUNCH || scope == MealScope.DINNER) analysis.getFilters().setMealType(scope.name());
        else analysis.getFilters().setMealType(null);
        analysis.setConfidence(0.82D);
        applyTemporalIntent(analysis, text);
        return analysis;
    }

    /** 构造上一轮公共菜单结果的受控纠错语义，未能重新规划时由服务层追问。 */
    private BusinessQuestionAnalysis correctionMenu(BusinessQuestionAnalysis analysis, String text,
                                                    LastBusinessQueryContext lastBusinessQueryContext) {
        scheduledMenu(analysis, text);
        analysis.setInteractionMode(BusinessInteractionMode.CORRECTION);
        analysis.setReferenceTurn("PREVIOUS_BUSINESS_QUERY");
        BusinessCorrection correction = new BusinessCorrection();
        correction.setRequiresReplan(true);
        if (contains(text, "米饭", "主食")) {
            correction.setReason(CorrectionReason.PREVIOUS_RESULT_IMPLAUSIBLE);
            correction.setObservations(List.of("ONLY_RICE_RETURNED"));
        } else {
            correction.setReason(CorrectionReason.UNKNOWN);
        }
        analysis.setCorrection(correction);
        analysis.setConfidence(0.84D);
        return analysis;
    }

    private boolean isScheduledMenuQuestion(String text, DiagnosisSlots context) {
        return contains(text, "菜单") && (context == null || context.getCustomerId() == null)
            && !text.matches(".*(?i)\\b[A-Z]\\d{3,}\\b.*") && !contains(text, "客户", "候选菜", "哪些菜能吃");
    }

    private boolean isContextCorrection(String text, LastBusinessQueryContext lastBusinessQueryContext) {
        if (lastBusinessQueryContext == null || lastBusinessQueryContext.getQueryTarget() != BusinessQueryTarget.SCHEDULED_MENU) return false;
        return contains(text, "不对", "不应该", "查错", "怎么全是", "明显", "不太对", "全是米饭", "主食列表");
    }

    private MealScope mealScope(String text, String inheritedMealType) {
        if (contains(text, "午餐")) return MealScope.LUNCH;
        if (contains(text, "晚餐")) return MealScope.DINNER;
        if ("LUNCH".equals(inheritedMealType)) return MealScope.LUNCH;
        if ("DINNER".equals(inheritedMealType)) return MealScope.DINNER;
        return MealScope.ALL_AVAILABLE;
    }

    private void operation(BusinessQuestionAnalysis analysis, AgentQueryMetric metric, String text) {
        operation(analysis, metric == null ? List.of() : List.of(metric), text);
    }

    /** 将单日同源指标组合为受控报表；不同底层数据源不在规则层自动混合。 */
    private void operation(BusinessQuestionAnalysis analysis, List<AgentQueryMetric> metrics, String text) {
        analysis.setDomains(List.of(AgentQueryDomain.OPERATION_STATISTICS));
        if (metrics != null && !metrics.isEmpty()) analysis.setMetrics(metrics);
        List<AgentQueryDimension> dimensions = new ArrayList<>();
        boolean explicitGrouping = contains(text, "分组", "分别", "各多少", "各有");
        if (contains(text, "按餐次", "分餐次", "各餐次") || explicitGrouping && text.contains("餐次")) dimensions.add(AgentQueryDimension.MEAL_TYPE);
        if (contains(text, "按套餐", "分套餐", "各套餐") || explicitGrouping && text.contains("套餐")) dimensions.add(AgentQueryDimension.PACKAGE);
        if (contains(text, "按来源", "按渠道", "分来源", "分渠道") || explicitGrouping && contains(text, "来源", "渠道")) dimensions.add(AgentQueryDimension.CUSTOMER_SOURCE);
        if (dimensions.size() > 2) {
            ambiguity(analysis, "dimensions", List.of("MEAL_TYPE", "PACKAGE", "CUSTOMER_SOURCE"),
                "一次报表最多选择两个分组维度，请说明优先按哪两个维度统计。");
        } else if (!dimensions.isEmpty()) analysis.setDimensions(dimensions);
        analysis.setConfidence(metrics == null || metrics.isEmpty() ? 0.7D : 0.96D);
        AgentQueryFilters filters = analysis.getFilters();
        if (filters.getMealType() == null) {
            if (contains(text, "早餐")) filters.setMealType("BREAKFAST");
            else if (contains(text, "午餐")) filters.setMealType("LUNCH");
            else if (contains(text, "晚餐")) filters.setMealType("DINNER");
        }
        applyTemporalIntent(analysis, text);
    }

    /**
     * 识别“分别多少”“各有多少”等明确的同日期多指标问法。
     *
     * @param text 已标准化的用户问题
     * @return 最多三个每日工作量指标；单指标或异构指标组合不在此处合并
     */
    private List<AgentQueryMetric> dailyReportMetrics(String text) {
        if (!contains(text, "分别", "各有", "各是多少", "各多少")) return List.of();
        List<AgentQueryMetric> metrics = new ArrayList<>();
        if (contains(text, "已排餐", "排餐客户", "生成排餐", "排餐的客户")) metrics.add(AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT);
        if (contains(text, "待核销", "没核销", "未核销", "尚未核销")) metrics.add(AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT);
        if (contains(text, "已核销")) metrics.add(AgentQueryMetric.DAILY_VERIFIED_CUSTOMER_COUNT);
        if (contains(text, "待排餐", "没排餐", "未排餐")) metrics.add(AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT);
        return metrics.size() > 3 ? metrics.subList(0, 3) : metrics;
    }

    /**
     * 判断“剩余客户”类自然语言是否缺少待办口径；该类问题必须追问而不能猜测为排餐或核销。
     *
     * @param text 已标准化的用户问题
     * @return 是否属于需要澄清的剩余客户问法
     */
    private boolean isRemainingCustomerQuestion(String text) {
        return contains(text, "还有多少客户", "几个人没弄完", "还有几个人", "剩下的客户", "剩余客户",
            "还有谁没有处理", "没完成的客户", "今天还有多少人", "还差多少客户", "今天没做完的是谁");
    }

    /**
     * 识别客户或客户集合的餐数余额问法，覆盖“还剩多少餐”等自然表达。
     * 客户实体或上一轮客户集合由服务层受控解析，本方法只选择餐数余额指标。
     */
    private boolean isMealBalanceQuestion(String text) {
        return contains(text, "订单", "还有几餐", "还剩几餐", "还剩多少餐", "剩多少餐", "剩余餐数",
            "餐数余额", "还能吃几餐", "餐数明细");
    }

    /** 判断用户是否明确查询系统已录入的客户档案总数，而不是活跃或待办客户。 */
    private boolean isCustomerProfileCountQuestion(String text) {
        return contains(text, "客户总数", "客户档案总数", "录入了多少客户", "录入多少客户")
            || contains(text, "系统中有多少客户", "系统中还有多少客户", "系统里有多少客户", "系统里还有多少客户");
    }

    /** 识别客户基本信息、档案创建时间或首次购买时间查询，统一使用受控客户概览。 */
    private boolean isCustomerOverviewQuestion(String text) {
        return contains(text, "客户信息", "客户档案", "基本信息", "客户情况", "目前什么情况", "当前什么情况",
            "什么时候添加", "何时添加", "添加时间", "什么时候创建", "何时创建", "创建时间",
            "什么时候录入", "录入时间", "什么时候购买", "何时购买", "购买时间", "什么时候买",
            "首次购买", "首单时间", "成交时间");
    }

    private void ambiguity(BusinessQuestionAnalysis analysis, String field, List<String> options, String question) {
        BusinessAmbiguity ambiguity = new BusinessAmbiguity();
        ambiguity.setField(field);
        ambiguity.setOptions(options);
        ambiguity.setMaterial(true);
        analysis.setAmbiguities(List.of(ambiguity));
        analysis.setRequiresClarification(true);
        analysis.setClarificationQuestion(question);
    }

    private AgentEntityReference copyEntities(DiagnosisSlots slots) {
        AgentEntityReference result = new AgentEntityReference();
        if (slots == null) return result;
        result.setCustomerId(slots.getCustomerId());
        result.setCustomerCode(slots.getCustomerCode());
        result.setCustomerName(slots.getCustomerName());
        result.setOrderId(slots.getOrderId());
        result.setOrderCode(slots.getOrderCode());
        result.setMealPlanRecordId(slots.getMealPlanRecordId());
        return result;
    }

    private AgentQueryFilters copyFilters(DiagnosisSlots slots) {
        AgentQueryFilters result = new AgentQueryFilters();
        if (slots == null) return result;
        result.setRecordDate(slots.getRecordDate());
        result.setStartDate(slots.getStartDate());
        result.setEndDate(slots.getEndDate());
        result.setMealType(slots.getMealType());
        result.setOrderStatus(slots.getOrderStatus() == null ? null : String.valueOf(slots.getOrderStatus()));
        return result;
    }

    private boolean contains(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    /** 高精度兜底只识别相对时间枚举，具体日期统一由 BusinessTemporalResolver 按业务时区生成。 */
    private void applyTemporalIntent(BusinessQuestionAnalysis analysis, String text) {
        if (analysis == null || analysis.getFilters() != null && (analysis.getFilters().getRecordDate() != null
            || analysis.getFilters().getStartDate() != null)) return;
        BusinessTemporalExpression expression = BusinessTemporalExpression.UNSPECIFIED;
        if (contains(text, "昨天", "昨日")) expression = BusinessTemporalExpression.PREVIOUS_DAY;
        else if (contains(text, "明天", "明日")) expression = BusinessTemporalExpression.NEXT_DAY;
        else if (contains(text, "本周", "这周")) expression = BusinessTemporalExpression.CURRENT_WEEK;
        else if (contains(text, "今天", "今日", "现在", "当前", "目前", "截至现在")) expression = BusinessTemporalExpression.CURRENT_DAY;
        BusinessTemporalIntent temporal = new BusinessTemporalIntent();
        temporal.setExpression(expression);
        analysis.setTemporal(temporal);
    }
}
