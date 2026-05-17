package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;

/**
 * 诊断上下文客户端，负责从主系统拉取诊断所需的业务数据。
 */
public interface DiagnosisContextClient {

    DiagnosisContextDto fetch(DiagnosisRequest request);
}
