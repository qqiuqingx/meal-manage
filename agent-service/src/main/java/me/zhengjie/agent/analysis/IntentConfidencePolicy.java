package me.zhengjie.agent.analysis;

/** 将模型自报置信度与目录、引用和风险信号合并为可执行策略。 */
public class IntentConfidencePolicy {
    /** 评估输入特征，所有字段均为受控布尔值或分数。 */
    public record Input(double modelConfidence, boolean capabilityMatched, boolean deterministicConflict,
                        boolean uniqueReference, boolean sensitiveDetail) { }
    /** 输出执行、确认、澄清或拒绝的稳定决策。 */
    public enum Decision { HIGH, MEDIUM, LOW, REJECTED }
    /** 计算保守置信度：敏感明细必须有唯一引用与高分才可执行。 */
    public Decision evaluate(Input input) {
        if (input == null || !input.capabilityMatched() || input.deterministicConflict()) return Decision.REJECTED;
        if (input.sensitiveDetail() && !input.uniqueReference()) return Decision.LOW;
        if (input.modelConfidence() >= .90D && (!input.sensitiveDetail() || input.uniqueReference())) return Decision.HIGH;
        if (input.modelConfidence() >= .80D) return input.sensitiveDetail() ? Decision.LOW : Decision.MEDIUM;
        return Decision.LOW;
    }
}
