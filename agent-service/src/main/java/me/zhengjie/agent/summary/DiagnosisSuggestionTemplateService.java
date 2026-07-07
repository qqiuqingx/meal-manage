package me.zhengjie.agent.summary;

import me.zhengjie.agent.domain.dto.DiagnosisResponse;

import java.util.List;
import java.util.Optional;

/**
 * 诊断建议模板服务，负责加载模板并对 AI 结果做模板化补齐。
 */
public interface DiagnosisSuggestionTemplateService {

    /**
     * 返回全部建议模板。
     *
     * @return 模板列表
     */
    List<DiagnosisSuggestionTemplate> listTemplates();

    /**
     * 按原因码查询模板。
     *
     * @param code 原因码
     * @return 模板查询结果
     */
    Optional<DiagnosisSuggestionTemplate> findByCode(String code);

    /**
     * 用模板补齐 AI 诊断结果中的 suggestion 和 nextActions。
     *
     * @param response 诊断结果
     * @return 补齐后的诊断结果
     */
    DiagnosisResponse applyTemplates(DiagnosisResponse response);
}
