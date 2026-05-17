package me.zhengjie.agent.context;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;

public interface DiagnosisContextBuilder {

    /**
     * 将请求参数转换成内部诊断上下文。
     */
    DiagnosisContextDto build(DiagnosisRequest request);
}
