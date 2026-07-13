package me.zhengjie.agent.query;

import me.zhengjie.agent.query.domain.AgentQueryFact;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校验受控业务查询回答不携带金额或写操作声称；复杂模型润色必须经过本校验器。
 */
@Component
public class BusinessAnswerValidator {

    private static final List<String> FORBIDDEN_TERMS = List.of("订单金额", "单价", "退款金额", "优惠金额", "已收金额", "餐费余额", "￥", "¥", "已修改", "已新增", "已删除", "已重排");
    private static final Pattern FACT_REFERENCE = Pattern.compile("\\[F(\\d+)]");
    private static final Pattern QUANTIFIED_NUMBER = Pattern.compile("(?<![\\d-])(\\d+)(?=\\s*(?:笔|餐|条|个))");

    /**
     * 判断回答和事实是否可安全展示。
     *
     * @param message 业务回答文本
     * @param facts 结构化事实
     * @return 安全时 true
     */
    public boolean isSafe(String message, List<AgentQueryFact> facts) {
        if (message == null || containsForbidden(message)) return false;
        if (facts == null) return !containsUnknownFactReference(message, Set.of());
        Set<String> factIds = new HashSet<>();
        for (AgentQueryFact fact : facts) {
            if (fact == null || containsForbidden(String.valueOf(fact.getLabel())) || containsForbidden(String.valueOf(fact.getValue()))
                || fact.getFactId() == null || !fact.getFactId().matches("F[1-9]\\d*")) return false;
            factIds.add(fact.getFactId());
        }
        return !containsUnknownFactReference(message, factIds) && !containsUnfoundedQuantifiedNumber(message, facts);
    }

    /**
     * 识别金额和写操作声称；“未删除核销日志”是只读统计口径，不能被误判为执行删除。
     */
    private boolean containsForbidden(String value) {
        if (value == null) return false;
        for (String term : FORBIDDEN_TERMS) {
            if (!value.contains(term)) continue;
            if ("已删除".equals(term) && value.contains("未删除")) continue;
            return true;
        }
        return false;
    }
    /** 校验回答中出现的事实引用均由本轮结构化事实提供，防止模型伪造引用。 */
    private boolean containsUnknownFactReference(String message, Set<String> factIds) {
        Matcher matcher = FACT_REFERENCE.matcher(message);
        while (matcher.find()) if (!factIds.contains("F" + matcher.group(1))) return true;
        return false;
    }

    /** 校验话术中的确定性数量均能在同轮 facts 中找到，日期和规则版本不按数量处理。 */
    private boolean containsUnfoundedQuantifiedNumber(String message, List<AgentQueryFact> facts) {
        if (message == null) return false;
        Set<String> values = new HashSet<>();
        for (AgentQueryFact fact : facts) if (fact != null && fact.getValue() != null) values.add(String.valueOf(fact.getValue()));
        Matcher matcher = QUANTIFIED_NUMBER.matcher(message);
        while (matcher.find()) if (!values.contains(matcher.group(1))) return true;
        return false;
    }
}
