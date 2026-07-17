package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 稳定的规则槽位提取器，优先覆盖常见聊天输入。
 */
@Component
public class RuleBasedSlotExtractor {

    private static final String CONFIDENCE_HIGH = "HIGH";
    private static final String CONFIDENCE_MEDIUM = "MEDIUM";
    private static final String CONFIDENCE_LOW = "LOW";
    private static final String SOURCE_EXPLICIT = "EXPLICIT_INPUT";
    private static final String SOURCE_CONTEXT = "CONTEXT_INHERIT";
    private static final String SOURCE_OVERRIDE = "CORRECTION_OVERRIDE";
    private static final String SOURCE_AMBIGUOUS = "AMBIGUOUS_INPUT";

    private static final Pattern CUSTOMER_CODE_PATTERN = Pattern.compile("(?i)\\b[A-Z]\\d{3,}\\b");
    private static final Pattern CUSTOMER_ID_PATTERN = Pattern.compile("(?:客户ID|客户id|客户Id)\\s*(\\d{1,})");
    private static final Pattern CUSTOMER_CODE_WITH_LABEL_PATTERN = Pattern.compile("(?i)(?:客户编号|编号)\\s*([A-Z]\\d{3,})");
    private static final Pattern CUSTOMER_NAME_WITH_LABEL_PATTERN = Pattern.compile("(?:客户姓名|姓名)\\s*[:：]?\\s*([\\u4e00-\\u9fa5]{2,8})");
    private static final Pattern CUSTOMER_NAME_SHORT_PATTERN = Pattern.compile("客户\\s+([\\u4e00-\\u9fa5]{2,4})(?=\\s|$)");
    private static final Pattern AMBIGUOUS_CUSTOMER_PATTERN = Pattern.compile("(?i)(?:客户|编号)\\s*(\\d{3,})");
    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("(?i)\\bO\\d{5,}\\b");
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?:订单ID|订单id|订单Id)\\s*(\\d+)");
    private static final Pattern MEAL_PLAN_RECORD_ID_PATTERN = Pattern.compile("(?:排餐记录ID|排餐记录id|客户排餐ID|客户排餐id)\\s*(\\d+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern RECENT_DAYS_PATTERN = Pattern.compile("最近\\s*(\\d{1,2})\\s*天");
    private static final Pattern NEXT_WEEKDAY_PATTERN = Pattern.compile("下周([一二三四五六日天])");

    // 客户餐数余额查询关键词
    private static final Pattern MEAL_BALANCE_PATTERN = Pattern.compile(
            "还剩多少|剩多少|剩余多少|剩余餐数|餐数余额|还能吃几餐|还剩几餐|还有多少餐|还有几餐|餐数还剩|一共多少餐|总共多少餐|总餐数|一共几餐|总共几餐|总共有多少餐|剩余.*早餐|剩余.*午餐|剩余.*晚餐|早餐.*剩|午晚餐.*剩|午餐.*剩|晚餐.*剩",
            Pattern.CASE_INSENSITIVE
    );

    // 客户概览查询关键词，不要求日期和餐次。
    private static final Pattern CUSTOMER_OVERVIEW_PATTERN = Pattern.compile(
            "目前什么情况|当前什么情况|客户情况|客户档案|基本信息|是谁[？?]?|客户信息|"
                + "什么时候添加|何时添加|添加时间|什么时候创建|何时创建|创建时间|什么时候录入|录入时间|"
                + "什么时候购买|何时购买|购买时间|什么时候买|首次购买|首单时间|成交时间",
            Pattern.CASE_INSENSITIVE
    );

    // 核销查询关键词
    private static final Pattern VERIFICATION_PATTERN = Pattern.compile(
            "核销了多少|已核销|核销记录|最近核销|用了多少餐|消耗多少餐|核销情况|核销统计",
            Pattern.CASE_INSENSITIVE
    );

    // 订单查询关键词
    private static final Pattern ORDER_PATTERN = Pattern.compile(
            "有哪些订单|订单情况|进行中订单|订单状态|买了什么套餐|.*订单.*有哪|.*订单.*什么|.*订单.*情况",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ORDER_DETAIL_PATTERN = Pattern.compile(
            "订单.*(?:详情|到期|结束|有效期|餐次|排餐模式|核销)|(?:这笔订单|订单) .*?(?:什么时候|何时|到期|结束|详情|核销)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ACTIVE_ORDER_PATTERN = Pattern.compile("进行中订单|有效订单", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEAL_PLAN_QUERY_PATTERN = Pattern.compile("排餐了吗|排了吗|有没有排餐|有没有排|吃什么|吃了什么|排餐情况|菜单|菜品", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFUND_QUERY_PATTERN = Pattern.compile("退过餐|退餐记录|退餐情况|最近退餐|退了多少餐", Pattern.CASE_INSENSITIVE);
    private static final Pattern CUSTOMER_PACKAGE_QUERY_PATTERN = Pattern.compile("签.*套餐|什么套餐|套餐是什么|套餐详情|用.*套餐", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISH_INGREDIENT_QUERY_PATTERN = Pattern.compile("菜.*配料|菜.*食材|配料.*什么|食材.*什么", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISH_CANDIDATE_QUERY_PATTERN = Pattern.compile("候选菜|可用菜|哪些菜能吃|哪些菜可以吃|忌口.*菜|过敏.*菜", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEAL_PLAN_UNVERIFIED_PATTERN = Pattern.compile("排.*未核销|已排.*没核销|已排.*未核销|排餐.*没核销|排餐.*未核销", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEAL_BALANCE_NO_PLAN_PATTERN = Pattern.compile("有.*餐.*没排|有餐数.*没排|有餐.*未排|还有餐.*没排", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEAL_BALANCE_CHANGE_PATTERN = Pattern.compile("餐数.*变化|为什么.*餐.*少|为什么.*剩.*餐", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEAL_BALANCE_RULE_PATTERN = Pattern.compile("剩余餐数.*怎么算|餐数.*怎么算|午餐.*扣.*哪个池|晚餐.*扣.*哪个池|核销.*扣.*哪个池", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_EFFECTIVE_RULE_PATTERN = Pattern.compile("订单.*有效.*规则|订单.*什么时候有效|订单有效性", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEAL_PLAN_MATCH_RULE_PATTERN = Pattern.compile("排餐模式.*规则|排餐模式.*匹配|餐次.*匹配.*规则|为什么.*不能排.*(?:早餐|午餐|晚餐)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIETARY_FILTER_RULE_PATTERN = Pattern.compile("(?:过敏|忌口|排除日期).*(?:规则|过滤)|为什么.*菜.*过滤", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERIFICATION_REFUND_RULE_PATTERN = Pattern.compile("(?:核销|退餐).*(?:影响|规则|餐数)|退餐.*扣.*餐|核销.*餐数", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPERATION_STATISTICS_PATTERN = Pattern.compile("待核销|未核销.*客户|待排餐|没排餐|未排餐|已排餐.*客户|(?:已经)?生成排餐.*客户|排餐(?:的)?客户.*(?:多少|几)|排餐失败|生成失败|失败记录|活跃客户|进行中客户|即将到期.*订单|快到期.*订单|(?:会|将)?到期.*订单|还有多少客户|还有几个人|几个人没弄完|剩下的客户|剩余客户|还有谁没有处理|没完成的客户|今天还有多少人|还差多少客户|今天没做完的是谁", Pattern.CASE_INSENSITIVE);

    private final Clock clock;

    public RuleBasedSlotExtractor() {
        this(Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    public RuleBasedSlotExtractor(Clock clock) {
        this.clock = clock;
    }

    public ChatExtractionResult extract(String message, DiagnosisSlots existingSlots) {
        String text = message == null ? "" : message.trim();
        DiagnosisSlots merged = copyFromContext(existingSlots);
        ChatExtractionResult result = new ChatExtractionResult();

        if (isReset(text)) {
            result.setIntent(ChatIntent.RESET);
            result.setSlots(new DiagnosisSlots());
            result.setMissingSlots(List.of());
            result.setAmbiguousSlots(List.of());
            return result;
        }
        if (isRetry(text)) {
            result.setIntent(ChatIntent.RETRY);
            result.setSlots(merged);
            result.setMissingSlots(missingSlots(merged));
            result.setAmbiguousSlots(List.of());
            return result;
        }
        if (isOutOfScope(text) && !REFUND_QUERY_PATTERN.matcher(text).find()) {
            result.setIntent(ChatIntent.OUT_OF_SCOPE);
            result.setSlots(merged);
            result.setMissingSlots(List.of());
            result.setAmbiguousSlots(List.of());
            return result;
        }

        mergeCustomer(text, merged, result);
        mergeDate(text, merged, result);
        mergeMealType(text, merged, result);
        mergeOrder(text, merged);
        mergeMealPlanRecord(text, merged);
        mergeOrderStatus(text, merged);

        result.setSlots(merged);
        result.setAmbiguousSlots(ambiguousSlots(merged));
        List<MissingSlot> genericMissing = missingSlots(merged);
        // 已有完整排餐上下文且本轮没有替换业务对象时，“为什么”优先表示上一轮结果追问。
        if (isFollowUp(text, existingSlots, genericMissing, result.getAmbiguousSlots()) && !hasExplicitBusinessTarget(text)) {
            result.setIntent(ChatIntent.FOLLOW_UP);
            result.setMissingSlots(genericMissing);
            return result;
        }

        // 优先级：客户信息查询 > 诊断；缺槽按最终意图计算。
        ChatIntent insightIntent = detectCustomerInsightIntent(text);
        // 普通自然语言统一进入受控业务语义分析；单客户诊断只能由模型高置信度显式选择。
        ChatIntent intent = insightIntent == null ? ChatIntent.BUSINESS_QUERY : insightIntent;
        result.setIntent(intent);
        result.setMissingSlots(missingSlots(merged, intent));
        return result;
    }

    /**
     * 检测客户信息查询意图，优先级高于追问和诊断。
     * 先检查 reset/retry/out_of_scope，再检查客户信息查询，最后回退诊断。
     */
    private ChatIntent detectCustomerInsightIntent(String text) {
        if (OPERATION_STATISTICS_PATTERN.matcher(text).find()) {
            return ChatIntent.OPERATION_STATISTICS_QUERY;
        }
        if (MEAL_BALANCE_RULE_PATTERN.matcher(text).find() || ORDER_EFFECTIVE_RULE_PATTERN.matcher(text).find()
            || MEAL_PLAN_MATCH_RULE_PATTERN.matcher(text).find() || DIETARY_FILTER_RULE_PATTERN.matcher(text).find()
            || VERIFICATION_REFUND_RULE_PATTERN.matcher(text).find()) {
            return ChatIntent.BUSINESS_RULE_QUERY;
        }
        if (CUSTOMER_OVERVIEW_PATTERN.matcher(text).find()) {
            return ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY;
        }
        if (MEAL_BALANCE_PATTERN.matcher(text).find()) {
            return ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY;
        }
        if (VERIFICATION_PATTERN.matcher(text).find()) {
            return ChatIntent.CUSTOMER_VERIFICATION_QUERY;
        }
        if (ORDER_DETAIL_PATTERN.matcher(text).find()) {
            return ChatIntent.CUSTOMER_ORDER_QUERY;
        }
        if (ORDER_PATTERN.matcher(text).find()) {
            return ChatIntent.CUSTOMER_ORDER_QUERY;
        }
        if (CUSTOMER_PACKAGE_QUERY_PATTERN.matcher(text).find()) {
            return ChatIntent.CUSTOMER_PACKAGE_QUERY;
        }
        if (DISH_CANDIDATE_QUERY_PATTERN.matcher(text).find()) {
            return ChatIntent.DISH_CANDIDATE_QUERY;
        }
        if (DISH_INGREDIENT_QUERY_PATTERN.matcher(text).find()) {
            return ChatIntent.DISH_INGREDIENT_QUERY;
        }
        if (MEAL_PLAN_UNVERIFIED_PATTERN.matcher(text).find()) {
            return ChatIntent.MEAL_PLAN_UNVERIFIED_QUERY;
        }
        if (MEAL_BALANCE_NO_PLAN_PATTERN.matcher(text).find()) {
            return ChatIntent.MEAL_BALANCE_NO_PLAN_QUERY;
        }
        if (MEAL_BALANCE_CHANGE_PATTERN.matcher(text).find()) {
            return ChatIntent.MEAL_BALANCE_CHANGE_QUERY;
        }
        if (text.contains("菜单") && !text.contains("客户") && !text.contains("排餐")) {
            return ChatIntent.SCHEDULED_MENU_QUERY;
        }
        if (MEAL_PLAN_QUERY_PATTERN.matcher(text).find()) {
            return ChatIntent.MEAL_PLAN_QUERY;
        }
        if (REFUND_QUERY_PATTERN.matcher(text).find()) {
            return ChatIntent.CUSTOMER_REFUND_QUERY;
        }
        return null;
    }

    /**
     * 提取客户槽位，并标记来源和置信度。
     *
     * @param text 用户输入
     * @param slots 当前槽位
     * @param result 当前提取结果
     */
    private void mergeCustomer(String text, DiagnosisSlots slots, ChatExtractionResult result) {
        Matcher codeMatcher = CUSTOMER_CODE_PATTERN.matcher(text);
        if (codeMatcher.find()) {
            String customerCode = codeMatcher.group().toUpperCase();
            clearOrderWhenCustomerChanges(slots, customerCode, null);
            slots.setCustomerCode(customerCode);
            slots.setCustomerId(null);
            slots.setCustomerName(null);
            slots.setCustomerConfidence(CONFIDENCE_HIGH);
            slots.setCustomerSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
            return;
        }
        Matcher labeledCodeMatcher = CUSTOMER_CODE_WITH_LABEL_PATTERN.matcher(text);
        if (labeledCodeMatcher.find()) {
            String customerCode = labeledCodeMatcher.group(1).toUpperCase(Locale.ROOT);
            clearOrderWhenCustomerChanges(slots, customerCode, null);
            slots.setCustomerCode(customerCode);
            slots.setCustomerId(null);
            slots.setCustomerName(null);
            slots.setCustomerConfidence(CONFIDENCE_HIGH);
            slots.setCustomerSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
            return;
        }
        Matcher idMatcher = CUSTOMER_ID_PATTERN.matcher(text);
        if (idMatcher.find()) {
            Long customerId = Long.valueOf(idMatcher.group(1));
            clearOrderWhenCustomerChanges(slots, null, customerId);
            slots.setCustomerId(customerId);
            slots.setCustomerCode(null);
            slots.setCustomerName(null);
            slots.setCustomerConfidence(CONFIDENCE_HIGH);
            slots.setCustomerSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
            return;
        }
        // "待排餐客户数" 等运营统计问法中的“客户”不是客户姓名，不能污染会话客户焦点。
        if (OPERATION_STATISTICS_PATTERN.matcher(text).find()) {
            return;
        }
        Matcher nameMatcher = CUSTOMER_NAME_WITH_LABEL_PATTERN.matcher(text);
        Matcher shortNameMatcher = CUSTOMER_NAME_SHORT_PATTERN.matcher(text);
        String customerName = nameMatcher.find() ? nameMatcher.group(1)
            : shortNameMatcher.find() ? shortNameMatcher.group(1) : null;
        if (customerName != null) {
            slots.setCustomerName(customerName);
            slots.setCustomerId(null);
            slots.setCustomerCode(null);
            slots.setCustomerConfidence(CONFIDENCE_MEDIUM);
            slots.setCustomerSource(SOURCE_EXPLICIT);
            return;
        }
        Matcher ambiguousMatcher = AMBIGUOUS_CUSTOMER_PATTERN.matcher(text);
        if (ambiguousMatcher.find()) {
            slots.setCustomerCode(ambiguousMatcher.group(1));
            slots.setCustomerId(null);
            slots.setCustomerConfidence(CONFIDENCE_LOW);
            slots.setCustomerSource(SOURCE_AMBIGUOUS);
        }
    }

    /** 客户焦点变化时清理下游订单焦点，避免跨客户复用订单。 */
    private void clearOrderWhenCustomerChanges(DiagnosisSlots slots, String customerCode, Long customerId) {
        boolean codeChanged = customerCode != null && slots.getCustomerCode() != null && !customerCode.equalsIgnoreCase(slots.getCustomerCode());
        boolean idChanged = customerId != null && slots.getCustomerId() != null && !customerId.equals(slots.getCustomerId());
        if (codeChanged || idChanged) {
            slots.setOrderId(null);
            slots.setOrderCode(null);
            slots.setMealPlanRecordId(null);
        }
    }

    /**
     * 提取日期槽位，支持绝对日期、相对日期和下周几表达。
     *
     * @param text 用户输入
     * @param slots 当前槽位
     * @param result 当前提取结果
     */
    private void mergeDate(String text, DiagnosisSlots slots, ChatExtractionResult result) {
        Matcher dateMatcher = DATE_PATTERN.matcher(text);
        if (dateMatcher.find()) {
            clearDateRange(slots);
            slots.setRecordDate(dateMatcher.group());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
            return;
        }
        Matcher nextWeekdayMatcher = NEXT_WEEKDAY_PATTERN.matcher(text);
        LocalDate today = LocalDate.now(clock);
        Matcher recentDaysMatcher = RECENT_DAYS_PATTERN.matcher(text);
        if (text.contains("本月")) {
            setDateRange(slots, today.withDayOfMonth(1), today);
        } else if (text.contains("本周")) {
            setDateRange(slots, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today);
        } else if (recentDaysMatcher.find()) {
            int days = Math.min(Math.max(Integer.parseInt(recentDaysMatcher.group(1)), 1), 31);
            setDateRange(slots, today.minusDays(days - 1L), today);
        } else if (text.contains("后天")) {
            clearDateRange(slots);
            slots.setRecordDate(today.plusDays(2).toString());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
        } else if (text.contains("明天")) {
            clearDateRange(slots);
            slots.setRecordDate(today.plusDays(1).toString());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
        } else if (text.contains("今天")) {
            clearDateRange(slots);
            slots.setRecordDate(today.toString());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
        } else if (text.contains("昨天")) {
            clearDateRange(slots);
            slots.setRecordDate(today.minusDays(1).toString());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
        } else if (nextWeekdayMatcher.find()) {
            clearDateRange(slots);
            slots.setRecordDate(resolveNextWeekday(today, nextWeekdayMatcher.group(1)).toString());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
        }
    }

    /** 设置最大 31 天的受控查询范围，并清除互斥的单日槽位。 */
    private void setDateRange(DiagnosisSlots slots, LocalDate startDate, LocalDate endDate) {
        slots.setRecordDate(null);
        slots.setStartDate(startDate.toString());
        slots.setEndDate(endDate.toString());
        slots.setRecordDateConfidence(CONFIDENCE_HIGH);
        slots.setRecordDateSource(SOURCE_EXPLICIT);
    }

    /** 清除范围槽位，使后续单日查询通过 QueryPlan 互斥条件校验。 */
    private void clearDateRange(DiagnosisSlots slots) {
        slots.setStartDate(null);
        slots.setEndDate(null);
    }

    /**
     * 提取餐次槽位，支持中文别名和纠正表达。
     *
     * @param text 用户输入
     * @param slots 当前槽位
     * @param result 当前提取结果
     */
    private void mergeMealType(String text, DiagnosisSlots slots, ChatExtractionResult result) {
        List<MealToken> mealTokens = mealTokens(text);
        if (mealTokens.isEmpty()) {
            return;
        }
        MealToken chosen = hasOverrideIntent(text)
            ? mealTokens.stream().max(Comparator.comparingInt(MealToken::position)).orElse(mealTokens.get(0))
            : mealTokens.get(0);
        slots.setMealType(chosen.mealType());
        slots.setMealTypeConfidence(CONFIDENCE_HIGH);
        slots.setMealTypeSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);

        if (mealTokens.size() > 1 && !hasOverrideIntent(text)) {
            slots.setMealTypeConfidence(CONFIDENCE_LOW);
            slots.setMealTypeSource(SOURCE_AMBIGUOUS);
        }
    }

    /**
     * 提取订单状态槽位，当前仅识别“进行中/有效订单”。
     *
     * @param text 用户输入
     * @param slots 当前槽位
     */
    private void mergeOrderStatus(String text, DiagnosisSlots slots) {
        if (ACTIVE_ORDER_PATTERN.matcher(text).find()) {
            slots.setOrderStatus(1);
        }
    }

    /**
     * 提取明确订单编号或订单 ID，并避免订单编号被误认作客户编号。
     *
     * @param text 客服输入文本
     * @param slots 当前会话槽位
     */
    private void mergeOrder(String text, DiagnosisSlots slots) {
        Matcher codeMatcher = ORDER_CODE_PATTERN.matcher(text);
        if (codeMatcher.find()) {
            String orderCode = codeMatcher.group().toUpperCase(Locale.ROOT);
            slots.setOrderCode(orderCode);
            slots.setOrderId(null);
            if (orderCode.equalsIgnoreCase(slots.getCustomerCode())) slots.setCustomerCode(null);
            return;
        }
        Matcher idMatcher = ORDER_ID_PATTERN.matcher(text);
        if (idMatcher.find()) {
            slots.setOrderId(Long.valueOf(idMatcher.group(1)));
            slots.setOrderCode(null);
        }
    }

    /** 提取客户排餐记录 ID，用于不依赖日期餐次的受控详情查询。 */
    private void mergeMealPlanRecord(String text, DiagnosisSlots slots) {
        Matcher matcher = MEAL_PLAN_RECORD_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            slots.setMealPlanRecordId(Long.valueOf(matcher.group(1)));
        }
    }

    /**
     * 根据当前槽位计算仍然缺失的关键槽位。
     *
     * @param slots 当前槽位
     * @return 缺失槽位列表
     */
    private List<MissingSlot> missingSlots(DiagnosisSlots slots) {
        List<MissingSlot> missing = new ArrayList<>();
        if (slots.getCustomerId() == null && isBlank(slots.getCustomerCode()) && slots.getMealPlanRecordId() == null) {
            missing.add(MissingSlot.CUSTOMER);
        }
        if (isBlank(slots.getRecordDate())) {
            missing.add(MissingSlot.RECORD_DATE);
        }
        if (isBlank(slots.getMealType())) {
            missing.add(MissingSlot.MEAL_TYPE);
        }
        return missing;
    }

    /**
     * 为不同查询意图裁剪必要槽位。客户、订单、核销和退餐查询不应被强制要求日期和餐次；
     * 带客户排餐记录 ID 的详情查询也不依赖客户、日期或餐次。
     *
     * @param slots 当前已解析槽位
     * @param intent 已识别意图
     * @return 当前意图仍需追问的槽位
     */
    private List<MissingSlot> missingSlots(DiagnosisSlots slots, ChatIntent intent) {
        if (intent == ChatIntent.BUSINESS_RULE_QUERY) return List.of();
        if (intent == ChatIntent.SCHEDULED_MENU_QUERY) {
            return isBlank(slots.getRecordDate()) ? List.of(MissingSlot.RECORD_DATE) : List.of();
        }
        if (intent == ChatIntent.MEAL_PLAN_QUERY && slots.getMealPlanRecordId() != null) return List.of();
        if (isCustomerOnlyIntent(intent)) {
            if (slots.getCustomerId() != null || !isBlank(slots.getCustomerCode()) || !isBlank(slots.getCustomerName())
                    || slots.getOrderId() != null || !isBlank(slots.getOrderCode())) return List.of();
            return List.of(MissingSlot.CUSTOMER);
        }
        return missingSlots(slots);
    }

    /** 判断该查询是否只需要客户或订单上下文，不要求排餐日期、餐次。 */
    private boolean isCustomerOnlyIntent(ChatIntent intent) {
        return intent == ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY
                || intent == ChatIntent.CUSTOMER_VERIFICATION_QUERY
                || intent == ChatIntent.CUSTOMER_ORDER_QUERY
                || intent == ChatIntent.CUSTOMER_REFUND_QUERY
                || intent == ChatIntent.CUSTOMER_PACKAGE_QUERY
                || intent == ChatIntent.MEAL_BALANCE_CHANGE_QUERY;
    }

    /**
     * 根据低置信度槽位输出待确认槽位。
     *
     * @param slots 当前槽位
     * @return 待确认槽位列表
     */
    private List<MissingSlot> ambiguousSlots(DiagnosisSlots slots) {
        List<MissingSlot> ambiguous = new ArrayList<>();
        if (CONFIDENCE_LOW.equals(slots.getCustomerConfidence())) {
            ambiguous.add(MissingSlot.CUSTOMER);
        }
        if (CONFIDENCE_LOW.equals(slots.getRecordDateConfidence())) {
            ambiguous.add(MissingSlot.RECORD_DATE);
        }
        if (CONFIDENCE_LOW.equals(slots.getMealTypeConfidence())) {
            ambiguous.add(MissingSlot.MEAL_TYPE);
        }
        return ambiguous;
    }

    private boolean isReset(String text) {
        return text.contains("清空") || text.contains("重新开始");
    }

    private boolean isRetry(String text) {
        return text.contains("重新排查") || text.contains("重新诊断") || text.contains("再查一次");
    }

    private boolean isOutOfScope(String text) {
        return text.contains("修改") || text.contains("改一下") || text.contains("下单") || text.contains("申请退款") || text.contains("执行退款") || text.contains("退款一下") || text.contains("修改地址")
            || text.contains("订单金额") || text.contains("退款金额") || text.contains("优惠金额") || text.contains("已收金额") || text.contains("单价") || text.contains("多少钱") || text.contains("价格");
    }

    /**
     * 判断当前输入是否属于基于已有诊断结果的追问。
     *
     * @param text 用户输入
     * @param existingSlots 既有槽位
     * @param missingSlots 当前缺失槽位
     * @param ambiguousSlots 当前待确认槽位
     * @return 是否为追问
     */
    private boolean isFollowUp(String text,
                               DiagnosisSlots existingSlots,
                               List<MissingSlot> missingSlots,
                               List<MissingSlot> ambiguousSlots) {
        return missingSlots.isEmpty()
            && ambiguousSlots.isEmpty()
            && existingSlots != null
            && !isBlank(existingSlots.getRecordDate())
            && !isBlank(existingSlots.getMealType())
            && (text.contains("为什么") || text.contains("原因") || text.contains("解释"));
    }

    /**
     * 判断本轮是否显式切换客户、订单、排餐记录、日期或餐次；有这些对象时不能误当上一轮追问。
     */
    private boolean hasExplicitBusinessTarget(String text) {
        return CUSTOMER_CODE_PATTERN.matcher(text).find() || CUSTOMER_ID_PATTERN.matcher(text).find()
                || ORDER_CODE_PATTERN.matcher(text).find() || ORDER_ID_PATTERN.matcher(text).find()
                || MEAL_PLAN_RECORD_ID_PATTERN.matcher(text).find() || DATE_PATTERN.matcher(text).find()
                || text.contains("今天") || text.contains("昨天") || text.contains("明天") || !mealTokens(text).isEmpty();
    }

    /**
     * 将上一轮会话槽位复制到当前轮次，并统一降级为上下文继承置信度。
     *
     * @param source 上一轮槽位
     * @return 当前轮次初始化槽位
     */
    private DiagnosisSlots copyFromContext(DiagnosisSlots source) {
        DiagnosisSlots target = new DiagnosisSlots();
        if (source == null) {
            return target;
        }
        target.setCustomerId(source.getCustomerId());
        target.setCustomerCode(source.getCustomerCode());
        target.setRecordDate(source.getRecordDate());
        target.setStartDate(source.getStartDate());
        target.setEndDate(source.getEndDate());
        target.setMealType(source.getMealType());
        target.setOrderStatus(source.getOrderStatus());
        if (source.getCustomerId() != null || !isBlank(source.getCustomerCode())) {
            target.setCustomerConfidence(CONFIDENCE_MEDIUM);
            target.setCustomerSource(SOURCE_CONTEXT);
        }
        if (!isBlank(source.getRecordDate())) {
            target.setRecordDateConfidence(CONFIDENCE_MEDIUM);
            target.setRecordDateSource(SOURCE_CONTEXT);
        }
        if (!isBlank(source.getMealType())) {
            target.setMealTypeConfidence(CONFIDENCE_MEDIUM);
            target.setMealTypeSource(SOURCE_CONTEXT);
        }
        return target;
    }

    /**
     * 判断本轮是否包含明确纠正或局部覆盖表达。
     *
     * @param text 用户输入
     * @return 是否为覆盖表达
     */
    private boolean hasOverrideIntent(String text) {
        return text.contains("换成") || text.contains("改成") || text.contains("不是");
    }

    /**
     * 解析文本中出现的餐次及其位置，用于处理纠正表达。
     *
     * @param text 用户输入
     * @return 按出现顺序排列的餐次标记
     */
    private List<MealToken> mealTokens(String text) {
        List<MealToken> tokens = new ArrayList<>();
        addMealTokens(tokens, text, "LUNCH_DINNER", "午晚餐");
        String normalized = text.replace("午晚餐", "   ");
        addMealTokens(tokens, normalized, "BREAKFAST", "早餐", "早饭");
        addMealTokens(tokens, normalized, "LUNCH", "午餐", "中餐", "午饭");
        addMealTokens(tokens, normalized, "DINNER", "晚餐", "晚饭");
        tokens.sort(Comparator.comparingInt(MealToken::position));
        return tokens;
    }

    private void addMealTokens(List<MealToken> tokens, String text, String mealType, String... aliases) {
        for (String alias : aliases) {
            int index = text.indexOf(alias);
            while (index >= 0) {
                tokens.add(new MealToken(mealType, index));
                index = text.indexOf(alias, index + alias.length());
            }
        }
    }

    private LocalDate resolveNextWeekday(LocalDate baseDate, String dayText) {
        DayOfWeek dayOfWeek = switch (dayText) {
            case "一" -> DayOfWeek.MONDAY;
            case "二" -> DayOfWeek.TUESDAY;
            case "三" -> DayOfWeek.WEDNESDAY;
            case "四" -> DayOfWeek.THURSDAY;
            case "五" -> DayOfWeek.FRIDAY;
            case "六" -> DayOfWeek.SATURDAY;
            case "日", "天" -> DayOfWeek.SUNDAY;
            default -> DayOfWeek.MONDAY;
        };
        return baseDate.with(TemporalAdjusters.next(dayOfWeek));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record MealToken(String mealType, int position) {
    }
}
