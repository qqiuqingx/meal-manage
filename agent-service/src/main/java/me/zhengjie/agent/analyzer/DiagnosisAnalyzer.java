package me.zhengjie.agent.analyzer;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;

import java.util.List;

public interface DiagnosisAnalyzer {

    List<DiagnosisReasonDto> analyze(DiagnosisContextDto context);
}
