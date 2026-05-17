package me.zhengjie.agent.orchestrator;

import me.zhengjie.agent.analyzer.DiagnosisAnalyzer;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.summary.DiagnosisSummaryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MealPlanDiagnosisOrchestrator {

    private final List<DiagnosisAnalyzer> analyzers;
    private final DiagnosisSummaryService summaryService;

    /**
     * 组装全部分析器和摘要服务，负责统一编排输出。
     */
    public MealPlanDiagnosisOrchestrator(List<DiagnosisAnalyzer> analyzers, DiagnosisSummaryService summaryService) {
        this.analyzers = analyzers;
        this.summaryService = summaryService;
    }

    /**
     * 依次执行分析器，汇总命中的原因，并生成最终摘要。
     */
    public DiagnosisResponse orchestrate(DiagnosisContextDto context) {
        List<DiagnosisReasonDto> reasons = new ArrayList<>();
        for (DiagnosisAnalyzer analyzer : analyzers) {
            reasons.addAll(analyzer.analyze(context));
        }

        DiagnosisResponse response = new DiagnosisResponse();
        response.setCustomerId(context.getCustomerId());
        response.setCustomerName(context.getCustomerName());
        response.setRecordDate(context.getRecordDate());
        response.setMealType(context.getMealType());
        response.setReasons(reasons);
        response.setSummary(summaryService.buildSummary(response));
        return response;
    }
}
