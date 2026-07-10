package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.dto.IntentClassificationRequest;
import me.zhengjie.agent.domain.dto.IntentClassificationResult;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 高确定性规则意图分类器，无法确认时返回低置信度结果交给混合编排器升级。
 */
@Component
public class RuleBasedIntentClassifier implements ChatIntentClassifier {

    private static final Pattern PRONOUN_OR_CONTEXT = Pattern.compile("他|她|这个客户|那午餐|那晚餐|这个订单|为什么|原因|解释|换成|改成");
    private static final Pattern RESET = Pattern.compile("清空|重新开始");
    private static final Pattern RETRY = Pattern.compile("重新排查|重新诊断|再查一次");
    private static final Pattern OUT_OF_SCOPE = Pattern.compile("修改|改一下|下单|退款|地址");

    /**
     * 对明确控制指令、客户查询和诊断请求进行规则分类。
     *
     * @param request 分类请求
     * @return 分类结果；低置信度结果由混合层决定是否升级模型
     */
    @Override
    public IntentClassificationResult classify(IntentClassificationRequest request) {
        String text = request == null || request.getUserMessage() == null ? "" : request.getUserMessage().trim();
        if (RESET.matcher(text).find()) return result(ChatIntent.RESET, 1.0, "显式清空会话指令");
        if (RETRY.matcher(text).find()) return result(ChatIntent.RETRY, 1.0, "显式重新排查指令");
        if (OUT_OF_SCOPE.matcher(text).find()) return result(ChatIntent.OUT_OF_SCOPE, 1.0, "命中非排餐业务操作关键词");

        ChatIntent candidate = parseCandidate(request == null ? null : request.getRuleIntentCandidate());
        if (candidate == null) return result(null, 0.0, "规则无法确定意图");
        boolean explicitCustomer = text.matches(".*(?i)[A-Z]\\d{3,}.*") || text.matches(".*客户(?:ID|id|Id)\\s*\\d+.*");
        if (PRONOUN_OR_CONTEXT.matcher(text).find() && !(explicitCustomer && isCustomerQuery(candidate))) {
            return result(candidate, 0.45, "消息依赖上下文或存在局部追问表达");
        }
        return result(candidate, 0.95, "命中高确定性规则");
    }

    private boolean isCustomerQuery(ChatIntent intent) {
        return intent == ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY
            || intent == ChatIntent.CUSTOMER_VERIFICATION_QUERY
            || intent == ChatIntent.CUSTOMER_ORDER_QUERY;
    }

    private ChatIntent parseCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) return null;
        try { return ChatIntent.valueOf(candidate); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private IntentClassificationResult result(ChatIntent intent, double confidence, String reason) {
        return new IntentClassificationResult(intent, confidence, reason, intent == null || confidence < 0.8);
    }
}
