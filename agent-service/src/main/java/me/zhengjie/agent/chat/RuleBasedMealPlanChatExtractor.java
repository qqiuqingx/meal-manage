package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 稳定的规则抽取器，优先覆盖常见聊天输入。
 */
@Component
public class RuleBasedMealPlanChatExtractor implements MealPlanChatExtractor {

    private static final Pattern CUSTOMER_CODE_PATTERN = Pattern.compile("(?i)\\bC\\d{3,}\\b");
    private static final Pattern CUSTOMER_ID_PATTERN = Pattern.compile("(?:客户ID|客户id|客户\\s*)\\s*(\\d{3,})");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

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
        DiagnosisSlots merged = copy(existingSlots);
        ChatExtractionResult result = new ChatExtractionResult();

        if (isReset(text)) {
            result.setIntent(ChatIntent.RESET);
            result.setSlots(new DiagnosisSlots());
            result.setMissingSlots(List.of());
            return result;
        }
        if (isOutOfScope(text)) {
            result.setIntent(ChatIntent.OUT_OF_SCOPE);
            result.setSlots(merged);
            result.setMissingSlots(List.of());
            return result;
        }

        mergeCustomer(text, merged);
        mergeDate(text, merged);
        mergeMealType(text, merged);

        result.setSlots(merged);
        result.setMissingSlots(missingSlots(merged));
        result.setIntent(isFollowUp(text, existingSlots, result.getMissingSlots()) ? ChatIntent.FOLLOW_UP : ChatIntent.DIAGNOSE);
        return result;
    }

    private void mergeCustomer(String text, DiagnosisSlots slots) {
        Matcher codeMatcher = CUSTOMER_CODE_PATTERN.matcher(text);
        if (codeMatcher.find()) {
            slots.setCustomerCode(codeMatcher.group().toUpperCase());
        }
        Matcher idMatcher = CUSTOMER_ID_PATTERN.matcher(text);
        if (idMatcher.find()) {
            slots.setCustomerId(Long.valueOf(idMatcher.group(1)));
        }
    }

    private void mergeDate(String text, DiagnosisSlots slots) {
        Matcher dateMatcher = DATE_PATTERN.matcher(text);
        if (dateMatcher.find()) {
            slots.setRecordDate(dateMatcher.group());
            return;
        }
        LocalDate today = LocalDate.now(clock);
        if (text.contains("明天")) {
            slots.setRecordDate(today.plusDays(1).toString());
        } else if (text.contains("今天")) {
            slots.setRecordDate(today.toString());
        } else if (text.contains("昨天")) {
            slots.setRecordDate(today.minusDays(1).toString());
        }
    }

    private void mergeMealType(String text, DiagnosisSlots slots) {
        if (text.contains("早餐") || text.contains("早饭")) {
            slots.setMealType("BREAKFAST");
        } else if (text.contains("午餐") || text.contains("中餐") || text.contains("午饭")) {
            slots.setMealType("LUNCH");
        } else if (text.contains("晚餐") || text.contains("晚饭")) {
            slots.setMealType("DINNER");
        }
    }

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

    private boolean isReset(String text) {
        return text.contains("清空") || text.contains("重新开始") || text.contains("重新排查") || text.contains("换一个客户");
    }

    private boolean isOutOfScope(String text) {
        return text.contains("修改") || text.contains("改一下") || text.contains("下单") || text.contains("退款") || text.contains("地址");
    }

    private boolean isFollowUp(String text, DiagnosisSlots existingSlots, List<MissingSlot> missingSlots) {
        return missingSlots.isEmpty()
            && existingSlots != null
            && !isBlank(existingSlots.getRecordDate())
            && !isBlank(existingSlots.getMealType())
            && (text.contains("为什么") || text.contains("原因") || text.contains("解释"));
    }

    private DiagnosisSlots copy(DiagnosisSlots source) {
        DiagnosisSlots target = new DiagnosisSlots();
        if (source == null) {
            return target;
        }
        target.setCustomerId(source.getCustomerId());
        target.setCustomerCode(source.getCustomerCode());
        target.setRecordDate(source.getRecordDate());
        target.setMealType(source.getMealType());
        return target;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
