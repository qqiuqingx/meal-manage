package me.zhengjie.agent.guardrail;

import me.zhengjie.agent.domain.chat.MissingSlot;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 最终回答事实引用、敏感数据和写操作声称护栏。 */
public class FinalAnswerGuardrail {
    private static final Set<String> SMALL_TALK = Set.of("你好", "您好", "谢谢", "你是谁", "能做什么", "帮助", "功能");
    private static final Pattern MARKDOWN_TABLE_SEPARATOR = Pattern.compile(
        "(?m)^\\s*\\|(?:\\s*:?-{3,}:?\\s*\\|)+\\s*$");
    private final SensitiveDataPolicy sensitiveDataPolicy;

    /** 使用敏感数据策略构建最终回答护栏。 */
    public FinalAnswerGuardrail(SensitiveDataPolicy sensitiveDataPolicy) { this.sensitiveDataPolicy = sensitiveDataPolicy; }

    /** 校验回答；涉及实时业务事实时必须有成功工具事实，客户姓名必须和同一事实中的客户编号配对。 */
    public void validate(String userMessage, String answer, int successfulToolCalls) {
        validate(userMessage, answer, successfulToolCalls, Collections.emptySet());
    }

    /**
     * 校验回答及其客户身份引用，禁止模型只凭姓名或历史上下文生成客户身份结论。
     *
     * @param userMessage 客服原始问题，仅用于判断是否需要实时事实
     * @param answer 模型待返回的最终回答
     * @param successfulToolCalls 本轮成功工具调用数量
     * @param customerIdentities 本轮成功工具事实中提取的客户编号—姓名配对
     */
    public void validate(String userMessage, String answer, int successfulToolCalls,
                         Set<CustomerIdentity> customerIdentities) {
        if (answer == null || answer.isBlank()) throw new ToolGuardrailException("ANSWER_EMPTY", "answer is empty");
        sensitiveDataPolicy.assertSafeAnswer(answer);
        if (requiresBusinessFact(userMessage) && successfulToolCalls == 0) {
            throw new ToolGuardrailException("ANSWER_FACT_WITHOUT_TOOL", "business answer requires a successful tool fact");
        }
        validateStructuredAnswer(answer, successfulToolCalls);
        validateCustomerIdentities(answer, customerIdentities);
    }

    /**
     * 校验明确的澄清回复；澄清允许没有成功工具事实，但不能伪装成实时查询结论。
     *
     * @param answer 面向客服的澄清文本
     * @param missingSlots 受控缺失项列表
     */
    public void validateClarification(String answer, List<MissingSlot> missingSlots) {
        if (answer == null || answer.isBlank()) throw new ToolGuardrailException("ANSWER_EMPTY", "answer is empty");
        if (missingSlots == null || missingSlots.isEmpty()) {
            throw new ToolGuardrailException("ANSWER_MISSING_SLOTS_REQUIRED", "clarification requires missing slots");
        }
        sensitiveDataPolicy.assertSafeAnswer(answer);
        if (containsRealtimeConclusion(answer)) {
            throw new ToolGuardrailException("ANSWER_CLARIFICATION_HAS_RESULT", "clarification must not claim a realtime result");
        }
    }

    /** 成功工具结果会由前端结构化展示，模型回答不得再携带重复的 Markdown 表格。 */
    private void validateStructuredAnswer(String answer, int successfulToolCalls) {
        if (successfulToolCalls > 0 && MARKDOWN_TABLE_SEPARATOR.matcher(answer).find()) {
            throw new ToolGuardrailException("ANSWER_STRUCTURED_DETAIL_REPEATED",
                "structured tool result must be summarized without a markdown table");
        }
    }

    /** 校验回答中的完整客户姓名必须同时出现同一事实对应的客户编号。 */
    private void validateCustomerIdentities(String answer, Set<CustomerIdentity> customerIdentities) {
        if (customerIdentities == null || customerIdentities.isEmpty()) return;
        for (CustomerIdentity identity : customerIdentities) {
            if (identity == null || !hasText(identity.customerName())) continue;
            if (answer.contains(identity.customerName())
                && (!hasText(identity.customerCode()) || !answer.contains(identity.customerCode()))) {
                throw new ToolGuardrailException("ANSWER_CUSTOMER_IDENTITY_UNPAIRED",
                    "customer name must be paired with its customer code");
            }
        }
    }

    /** 判断澄清文本是否声称已经取得实时业务结果。 */
    private boolean containsRealtimeConclusion(String answer) {
        return answer.matches("(?s).*(已查询|查询结果|已核实|共有\\s*\\d|共\\s*\\d|目前有\\s*\\d|已排餐|已核销).*" );
    }

    /** 判断客户身份字段是否为有效文本。 */
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }

    /** 本轮工具事实中可验证的客户编号—完整姓名配对。 */
    public record CustomerIdentity(String customerCode, String customerName) { }

    /** 判断是否涉及不能依靠历史上下文回答的实时业务事实。 */
    public boolean requiresBusinessFact(String message) {
        if (message == null || message.isBlank()) return false;
        String value = message.trim();
        for (String smallTalk : SMALL_TALK) if (value.equals(smallTalk)) return false;
        return value.matches("(?s).*(当前|现在|今天|最近|系统中|客户|订单|排餐|核销|退餐|套餐|菜品|剩余|餐数|多少|哪些|什么时候|统计|为什么|规则).*" );
    }
}
