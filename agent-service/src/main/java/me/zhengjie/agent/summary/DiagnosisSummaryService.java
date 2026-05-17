package me.zhengjie.agent.summary;

import me.zhengjie.agent.domain.dto.DiagnosisResponse;

public interface DiagnosisSummaryService {

    /**
     * 根据诊断结果生成最终摘要文本。
     */
    String buildSummary(DiagnosisResponse response);
}
