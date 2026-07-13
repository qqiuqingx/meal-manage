package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessAmbiguity;
import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryMetric;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则优先的业务问题分析器，识别高确定性的运营统计词、日期、餐次与会话对象。
 */
public class RuleBasedBusinessQuestionAnalyzer implements BusinessQuestionAnalyzer {
    @Override
    public BusinessQuestionAnalysis analyze(String question, DiagnosisSlots context) {
        String text = question == null ? "" : question.trim();
        BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
        analysis.setEntities(copyEntities(context));
        analysis.setFilters(copyFilters(context));
        analysis.setSource("RULE");
        List<AgentQueryMetric> dailyReportMetrics = dailyReportMetrics(text);
        if (dailyReportMetrics.size() > 1) {
            operation(analysis, dailyReportMetrics, text);
        } else if (contains(text, "排餐失败", "生成失败", "失败记录")) {
            operation(analysis, AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT, text);
        } else if (contains(text, "待核销", "没核销", "未核销", "尚未核销")) {
            operation(analysis, AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT, text);
        } else if (contains(text, "待排餐", "没排餐", "未排餐")) {
            operation(analysis, AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT, text);
        } else if (contains(text, "已排餐", "排餐客户", "排了多少", "生成排餐", "排餐的客户")) {
            operation(analysis, AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT, text);
        } else if (contains(text, "活跃客户", "进行中客户")) {
            operation(analysis, AgentQueryMetric.ACTIVE_CUSTOMER_COUNT, text);
        } else if (contains(text, "即将到期", "快到期", "会到期", "到期订单")) {
            operation(analysis, AgentQueryMetric.EXPIRING_ORDER_COUNT, text);
        } else if (contains(text, "核销", "使用了多少餐")) {
            analysis.setDomains(List.of(AgentQueryDomain.VERIFICATION));
            analysis.setMetrics(List.of(AgentQueryMetric.VERIFICATION_COUNT));
            analysis.setConfidence(0.92D);
        } else if (contains(text, "退餐")) {
            analysis.setDomains(List.of(AgentQueryDomain.REFUND));
            analysis.setMetrics(List.of(AgentQueryMetric.REFUND_COUNT));
            analysis.setConfidence(0.92D);
        } else if (contains(text, "订单", "还有几餐", "剩余餐数")) {
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
        return analysis;
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
        if (filters.getRecordDate() == null && contains(text, "今天", "今日")) filters.setRecordDate(LocalDate.now().toString());
        if (filters.getMealType() == null) {
            if (contains(text, "早餐")) filters.setMealType("BREAKFAST");
            else if (contains(text, "午餐")) filters.setMealType("LUNCH");
            else if (contains(text, "晚餐")) filters.setMealType("DINNER");
        }
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
}
