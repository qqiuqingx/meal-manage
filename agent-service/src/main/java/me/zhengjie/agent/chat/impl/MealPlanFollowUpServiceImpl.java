package me.zhengjie.agent.chat.impl;

import me.zhengjie.agent.chat.MealPlanFollowUpService;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 默认追问实现，优先基于原因标题、描述和代码做关键词命中。
 */
@Service
public class MealPlanFollowUpServiceImpl implements MealPlanFollowUpService {

    @Override
    public String buildFollowUpReply(String question, DiagnosisResponse diagnosisResult) {
        DiagnosisReasonDto matchedReason = matchReason(question, diagnosisResult);
        if (matchedReason != null) {
            return "结合上一轮诊断，"
                + safe(matchedReason.getTitle())
                + "："
                + safe(matchedReason.getDescription())
                + evidenceSummary(matchedReason)
                + suggestionSummary(matchedReason);
        }
        return "上次诊断摘要：" + safe(diagnosisResult == null ? null : diagnosisResult.getSummary()) + "请结合结果卡片中的原因、建议和证据继续人工确认。";
    }

    /**
     * 根据用户追问内容匹配上一轮诊断原因，未命中明确关键词时返回空，由调用方降级为摘要回复。
     *
     * @param question 用户追问内容
     * @param diagnosisResult 上一轮诊断结果
     * @return 命中的诊断原因，未命中时返回空
     */
    private DiagnosisReasonDto matchReason(String question, DiagnosisResponse diagnosisResult) {
        if (diagnosisResult == null || diagnosisResult.getReasons() == null || diagnosisResult.getReasons().isEmpty()) {
            return null;
        }
        String normalizedQuestion = normalize(question);
        return diagnosisResult.getReasons().stream()
            .max(Comparator.comparingInt(reason -> matchScore(normalizedQuestion, reason)))
            .filter(reason -> matchScore(normalizedQuestion, reason) > 0)
            .orElse(null);
    }

    private int matchScore(String normalizedQuestion, DiagnosisReasonDto reason) {
        int score = 0;
        score += containsKeyword(normalizedQuestion, reason.getTitle()) ? 3 : 0;
        score += containsKeyword(normalizedQuestion, reason.getDescription()) ? 2 : 0;
        score += containsKeyword(normalizedQuestion, reason.getCode()) ? 1 : 0;
        return score;
    }

    private String evidenceSummary(DiagnosisReasonDto reason) {
        List<DiagnosisEvidenceDto> evidence = reason == null ? null : reason.getEvidence();
        if (evidence == null || evidence.isEmpty()) {
            return " 当前没有补充证据，请结合结果卡片继续核对。";
        }
        DiagnosisEvidenceDto firstEvidence = evidence.get(0);
        return " 证据线索："
            + safe(firstEvidence.getLabel())
            + "="
            + safe(firstEvidence.getValue())
            + "。";
    }

    private String suggestionSummary(DiagnosisReasonDto reason) {
        if (reason != null && isNotBlank(reason.getSuggestion())) {
            return " 建议：" + reason.getSuggestion();
        }
        return "";
    }

    private boolean containsKeyword(String question, String candidate) {
        String normalizedCandidate = normalize(candidate);
        if (!isNotBlank(question) || !isNotBlank(normalizedCandidate)) {
            return false;
        }
        if (question.contains(normalizedCandidate)) {
            return true;
        }
        String[] keywords = normalizedCandidate.split("[：:，,。\\s]+");
        for (String keyword : keywords) {
            if (keyword.length() >= 2 && question.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").trim();
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return isNotBlank(value) ? value : "暂无诊断摘要。";
    }
}
