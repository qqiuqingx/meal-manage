package me.zhengjie.agent.guardrail;

import me.zhengjie.agent.tool.ToolRegistry;

import java.util.Set;

/** 最终回答事实引用、敏感数据和写操作声称护栏。 */
public class FinalAnswerGuardrail {
    private static final Set<String> SMALL_TALK = Set.of("你好", "您好", "谢谢", "你是谁", "能做什么", "帮助", "功能");
    private final SensitiveDataPolicy sensitiveDataPolicy;

    /** 使用敏感数据策略构建最终回答护栏。 */
    public FinalAnswerGuardrail(SensitiveDataPolicy sensitiveDataPolicy) { this.sensitiveDataPolicy = sensitiveDataPolicy; }

    /** 校验回答；涉及实时业务事实时必须有成功工具事实。 */
    public void validate(String userMessage, String answer, int successfulToolCalls) {
        if (answer == null || answer.isBlank()) throw new ToolGuardrailException("ANSWER_EMPTY", "answer is empty");
        sensitiveDataPolicy.assertSafeAnswer(answer);
        if (requiresBusinessFact(userMessage) && successfulToolCalls == 0) {
            throw new ToolGuardrailException("ANSWER_FACT_WITHOUT_TOOL", "business answer requires a successful tool fact");
        }
    }

    /** 判断是否涉及不能依靠历史上下文回答的实时业务事实。 */
    public boolean requiresBusinessFact(String message) {
        if (message == null || message.isBlank()) return false;
        String value = message.trim();
        for (String smallTalk : SMALL_TALK) if (value.equals(smallTalk)) return false;
        return value.matches("(?s).*(当前|现在|今天|最近|系统中|客户|订单|排餐|核销|退餐|套餐|菜品|剩余|餐数|多少|哪些|什么时候|统计|为什么|规则).*" );
    }
}
