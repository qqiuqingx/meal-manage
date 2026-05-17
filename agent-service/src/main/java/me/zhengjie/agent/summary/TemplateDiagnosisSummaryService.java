package me.zhengjie.agent.summary;

import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import org.springframework.stereotype.Service;

@Service
public class TemplateDiagnosisSummaryService implements DiagnosisSummaryService {

    /**
     * 根据诊断结果生成一段便于客服阅读的摘要。
     */
    @Override
    public String buildSummary(DiagnosisResponse response) {
        if (response.getReasons().isEmpty()) {
            return "当前未命中明确异常原因，建议人工继续核对订单与菜单配置。";
        }
        return response.getReasons().get(0).getTitle();
    }
}
