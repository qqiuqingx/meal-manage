package me.zhengjie.agent.analyzer.impl;

import me.zhengjie.agent.analyzer.DiagnosisAnalyzer;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExcludeDateAnalyzer implements DiagnosisAnalyzer {

    /**
     * 命中客户排除日期时，返回对应的诊断原因。
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<DiagnosisReasonDto> analyze(DiagnosisContextDto context) {
        Object excludeDatesObject = context.getCustomerProfile().get("excludeDates");
        if (!(excludeDatesObject instanceof List<?> excludeDates)) {
            return List.of();
        }

        List<DiagnosisReasonDto> reasons = new ArrayList<>();
        for (Object excludeDateObject : excludeDates) {
            if (!(excludeDateObject instanceof Map<?, ?> excludeDateMap)) {
                continue;
            }

            Object dateObject = excludeDateMap.get("date");
            Object mealTypesObject = excludeDateMap.get("mealTypes");
            if (!context.getRecordDate().equals(String.valueOf(dateObject))) {
                continue;
            }
            if (!(mealTypesObject instanceof List<?> mealTypes)) {
                continue;
            }
            if (!mealTypes.contains(context.getMealType())) {
                continue;
            }

            DiagnosisReasonDto reason = new DiagnosisReasonDto();
            reason.setCode("EXCLUDE_DATE_HIT");
            reason.setTitle("命中客户排除日期");
            reason.setLevel("HIGH");
            reason.setDescription("客户档案配置了目标日期和餐次不配送。");
            reason.setSuggestion("先核对客户是否已登记停送。");
            reason.setEvidence(List.of(
                new DiagnosisEvidenceDto("排除日期", context.getRecordDate()),
                new DiagnosisEvidenceDto("排除餐次", context.getMealType())
            ));
            reasons.add(reason);
        }
        return reasons;
    }
}
