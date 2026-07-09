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
 * 稳定的规则抽取器，优先覆盖常见聊天输入。
 */
@Component
public class RuleBasedMealPlanChatExtractor implements MealPlanChatExtractor {

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
    private static final Pattern AMBIGUOUS_CUSTOMER_PATTERN = Pattern.compile("(?i)(?:客户|编号)\\s*(\\d{3,})");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern NEXT_WEEKDAY_PATTERN = Pattern.compile("下周([一二三四五六日天])");

    // 客户餐数余额查询关键词
    private static final Pattern MEAL_BALANCE_PATTERN = Pattern.compile(
            "还剩多少|剩多少|剩余多少|剩余餐数|餐数余额|还能吃几餐|还剩几餐|还有多少餐|还有几餐|餐数还剩|剩余.*早餐|剩余.*午餐|剩余.*晚餐|早餐.*剩|午晚餐.*剩|午餐.*剩|晚餐.*剩",
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
    private static final Pattern ACTIVE_ORDER_PATTERN = Pattern.compile("进行中订单|有效订单", Pattern.CASE_INSENSITIVE);

    private final Clock clock;

    public RuleBasedMealPlanChatExtractor() {
        this(Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    RuleBasedMealPlanChatExtractor(Clock clock) {
        this.clock = clock;
    }

    @Override
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
        if (isOutOfScope(text)) {
            result.setIntent(ChatIntent.OUT_OF_SCOPE);
            result.setSlots(merged);
            result.setMissingSlots(List.of());
            result.setAmbiguousSlots(List.of());
            return result;
        }

        mergeCustomer(text, merged, result);
        mergeDate(text, merged, result);
        mergeMealType(text, merged, result);
        mergeOrderStatus(text, merged);

        result.setSlots(merged);
        result.setMissingSlots(missingSlots(merged));
        result.setAmbiguousSlots(ambiguousSlots(merged));

        // 优先级：客户信息查询 > 追问 > 诊断
        ChatIntent insightIntent = detectCustomerInsightIntent(text);
        if (insightIntent != null) {
            result.setIntent(insightIntent);
        } else {
            result.setIntent(isFollowUp(text, existingSlots, result.getMissingSlots(), result.getAmbiguousSlots())
                ? ChatIntent.FOLLOW_UP
                : ChatIntent.DIAGNOSE);
        }
        return result;
    }

    /**
     * 检测客户信息查询意图，优先级高于追问和诊断。
     * 先检查 reset/retry/out_of_scope，再检查客户信息查询，最后回退诊断。
     */
    private ChatIntent detectCustomerInsightIntent(String text) {
        if (MEAL_BALANCE_PATTERN.matcher(text).find()) {
            return ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY;
        }
        if (VERIFICATION_PATTERN.matcher(text).find()) {
            return ChatIntent.CUSTOMER_VERIFICATION_QUERY;
        }
        if (ORDER_PATTERN.matcher(text).find()) {
            return ChatIntent.CUSTOMER_ORDER_QUERY;
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
            slots.setCustomerCode(codeMatcher.group().toUpperCase());
            slots.setCustomerId(null);
            slots.setCustomerConfidence(CONFIDENCE_HIGH);
            slots.setCustomerSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
            return;
        }
        Matcher labeledCodeMatcher = CUSTOMER_CODE_WITH_LABEL_PATTERN.matcher(text);
        if (labeledCodeMatcher.find()) {
            slots.setCustomerCode(labeledCodeMatcher.group(1).toUpperCase(Locale.ROOT));
            slots.setCustomerId(null);
            slots.setCustomerConfidence(CONFIDENCE_HIGH);
            slots.setCustomerSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
            return;
        }
        Matcher idMatcher = CUSTOMER_ID_PATTERN.matcher(text);
        if (idMatcher.find()) {
            slots.setCustomerId(Long.valueOf(idMatcher.group(1)));
            slots.setCustomerCode(null);
            slots.setCustomerConfidence(CONFIDENCE_HIGH);
            slots.setCustomerSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
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
            slots.setRecordDate(dateMatcher.group());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
            return;
        }
        Matcher nextWeekdayMatcher = NEXT_WEEKDAY_PATTERN.matcher(text);
        LocalDate today = LocalDate.now(clock);
        if (text.contains("后天")) {
            slots.setRecordDate(today.plusDays(2).toString());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
        } else if (text.contains("明天")) {
            slots.setRecordDate(today.plusDays(1).toString());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
        } else if (text.contains("今天")) {
            slots.setRecordDate(today.toString());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
        } else if (text.contains("昨天")) {
            slots.setRecordDate(today.minusDays(1).toString());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
        } else if (nextWeekdayMatcher.find()) {
            slots.setRecordDate(resolveNextWeekday(today, nextWeekdayMatcher.group(1)).toString());
            slots.setRecordDateConfidence(CONFIDENCE_HIGH);
            slots.setRecordDateSource(hasOverrideIntent(text) ? SOURCE_OVERRIDE : SOURCE_EXPLICIT);
        }
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
     * 根据当前槽位计算仍然缺失的关键槽位。
     *
     * @param slots 当前槽位
     * @return 缺失槽位列表
     */
    private List<MissingSlot> missingSlots(DiagnosisSlots slots) {
        List<MissingSlot> missing = new ArrayList<>();
        if (slots.getCustomerId() == null && isBlank(slots.getCustomerCode())) {
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
        return text.contains("修改") || text.contains("改一下") || text.contains("下单") || text.contains("退款") || text.contains("地址");
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
