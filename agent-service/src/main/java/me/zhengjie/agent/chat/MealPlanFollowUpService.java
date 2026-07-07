package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.DiagnosisResponse;

/**
 * 诊断后追问服务，负责根据最近一次诊断结果生成追问回复。
 */
public interface MealPlanFollowUpService {

    /**
     * 基于用户追问和最近一次诊断结果生成回复文案。
     *
     * @param question 用户追问
     * @param diagnosisResult 最近一次诊断结果
     * @return 追问回复
     */
    String buildFollowUpReply(String question, DiagnosisResponse diagnosisResult);
}
