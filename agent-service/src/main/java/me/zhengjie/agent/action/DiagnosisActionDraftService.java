package me.zhengjie.agent.action;

import me.zhengjie.agent.domain.dto.DiagnosisResponse;

/**
 * 根据诊断原因生成动作草稿。
 */
public interface DiagnosisActionDraftService {

    /**
     * 将诊断原因转换为前端可展示的动作草稿。
     *
     * @param response 已完成校验和上下文补全的诊断结果
     * @return 带动作草稿的诊断结果；入参为空时返回空
     */
    DiagnosisResponse applyActionDrafts(DiagnosisResponse response);
}
