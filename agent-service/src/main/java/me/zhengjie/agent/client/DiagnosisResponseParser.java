package me.zhengjie.agent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 解析并归一化模型返回的诊断 JSON。
 */
public class DiagnosisResponseParser {

    private final ObjectMapper objectMapper;

    public DiagnosisResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DiagnosisResponse parse(String content) {
        String normalizedContent = normalizeJsonContent(content);
        if (normalizedContent == null || normalizedContent.trim().isEmpty()) {
            return null;
        }
        try {
            DiagnosisResponse parsed = objectMapper.readValue(normalizedContent, DiagnosisResponse.class);
            normalizeResponse(parsed);
            if (isUsable(parsed)) {
                return parsed;
            }
            return parseLegacyDiagnosisResponse(normalizedContent);
        } catch (JsonProcessingException ex) {
            return parseLegacyDiagnosisResponse(normalizedContent);
        }
    }

    private DiagnosisResponse parseLegacyDiagnosisResponse(String content) {
        try {
            Map<?, ?> legacy = objectMapper.readValue(content, Map.class);
            if (legacy == null || legacy.isEmpty()) {
                return null;
            }

            DiagnosisResponse response = new DiagnosisResponse();
            response.setSummary(asString(legacy.get("diagnosisResult")));
            response.setReasons(parseLegacyReasons(legacy));
            normalizeResponse(response);
            if (isUsable(response)) {
                return response;
            }
            return null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private List<DiagnosisReasonDto> parseLegacyReasons(Map<?, ?> legacy) {
        Object appliedRules = legacy.get("appliedRules");
        Object suggestion = legacy.get("suggestion");
        Object evidenceSummary = legacy.get("evidenceSummary");
        if (!(appliedRules instanceof List<?> legacyReasons) || legacyReasons.isEmpty()) {
            return List.of(legacyReason(legacy, suggestion, evidenceSummary));
        }
        List<DiagnosisReasonDto> reasons = new ArrayList<>();
        for (Object item : legacyReasons) {
            if (item instanceof Map<?, ?> rule) {
                reasons.add(buildReason(rule, suggestion, evidenceSummary));
            }
        }
        return reasons.isEmpty() ? List.of(legacyReason(legacy, suggestion, evidenceSummary)) : reasons;
    }

    private DiagnosisReasonDto buildReason(Map<?, ?> rule, Object suggestion, Object evidenceSummary) {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        String code = firstNonBlank(asString(rule.get("ruleId")), asString(rule.get("code")), "AI_RESULT");
        reason.setCode(code);
        reason.setTitle(firstNonBlank(asString(rule.get("title")), code));
        reason.setLevel(firstNonBlank(asString(rule.get("level")), "LOW"));
        reason.setConfidence(firstNonBlank(asString(rule.get("confidence")), normalizeLevel(reason.getLevel())));
        reason.setRuleIds(List.of(code));
        reason.setDescription(firstNonBlank(asString(rule.get("description")), asString(evidenceSummary), "AI 返回了旧结构，请人工核对。"));
        reason.setSuggestion(firstNonBlank(asString(rule.get("suggestion")), asString(suggestion), "请人工核对。"));
        reason.setNextActions(List.of(firstNonBlank(asString(suggestion), "请人工核对。")));
        reason.setEvidence(parseLegacyEvidence(rule.get("evidence"), code));
        if (reason.getEvidence().isEmpty()) {
            reason.setEvidence(List.of(new DiagnosisEvidenceDto("legacy", firstNonBlank(asString(evidenceSummary), "true"))));
        }
        return reason;
    }

    private DiagnosisReasonDto legacyReason(Map<?, ?> legacy, Object suggestion, Object evidenceSummary) {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("LEGACY_DIAGNOSIS_RESULT");
        reason.setTitle(firstNonBlank(asString(legacy.get("diagnosisResult")), "AI 诊断结果"));
        reason.setLevel("LOW");
        reason.setConfidence("LOW");
        reason.setRuleIds(List.of("LEGACY_DIAGNOSIS_RESULT"));
        reason.setDescription(firstNonBlank(asString(evidenceSummary), asString(legacy.get("diagnosisResult")), "AI 返回了旧结构，请人工核对。"));
        reason.setSuggestion(firstNonBlank(asString(suggestion), "请人工核对。"));
        reason.setNextActions(List.of(firstNonBlank(asString(suggestion), "请人工核对。")));
        reason.setEvidence(List.of(new DiagnosisEvidenceDto("legacy", firstNonBlank(asString(evidenceSummary), "true"))));
        return reason;
    }

    private List<DiagnosisEvidenceDto> parseLegacyEvidence(Object evidence, String code) {
        if (!(evidence instanceof List<?> items) || items.isEmpty()) {
            return List.of();
        }
        List<DiagnosisEvidenceDto> evidenceDtos = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                String label = firstNonBlank(asString(map.get("label")), asString(map.get("key")), code);
                String value = firstNonBlank(asString(map.get("value")), asString(map.get("text")), "");
                evidenceDtos.add(new DiagnosisEvidenceDto(label, value));
            }
        }
        return evidenceDtos;
    }

    private void normalizeResponse(DiagnosisResponse response) {
        if (response == null || response.getReasons() == null) {
            return;
        }
        response.setConfidence(firstNonBlank(response.getConfidence(), "LOW"));
        if (response.getNextActions() == null || response.getNextActions().isEmpty()) {
            response.setNextActions(inferNextActions(response.getReasons()));
        }
        for (DiagnosisReasonDto reason : response.getReasons()) {
            if (reason != null) {
                reason.setLevel(normalizeLevel(reason.getLevel()));
                reason.setConfidence(firstNonBlank(reason.getConfidence(), reason.getLevel()));
                if (reason.getRuleIds() == null || reason.getRuleIds().isEmpty()) {
                    reason.setRuleIds(List.of(firstNonBlank(reason.getCode(), "AI_RESULT")));
                }
                if (reason.getNextActions() == null || reason.getNextActions().isEmpty()) {
                    reason.setNextActions(List.of(firstNonBlank(reason.getSuggestion(), "请人工核对。")));
                }
            }
        }
    }

    private List<String> inferNextActions(List<DiagnosisReasonDto> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return List.of();
        }
        List<String> nextActions = new ArrayList<>();
        for (DiagnosisReasonDto reason : reasons) {
            if (reason != null && reason.getNextActions() != null) {
                nextActions.addAll(reason.getNextActions());
            }
        }
        return nextActions.isEmpty() ? List.of("请人工核对。") : nextActions;
    }

    private String normalizeLevel(String level) {
        if (level == null || level.trim().isEmpty()) {
            return "LOW";
        }
        return switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "HIGH", "MEDIUM", "LOW" -> level.trim().toUpperCase(Locale.ROOT);
            case "ERROR", "DANGER", "CRITICAL", "FATAL" -> "HIGH";
            case "WARN", "WARNING" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private String normalizeJsonContent(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        String fencedJson = extractFencedJson(trimmed);
        if (fencedJson != null) {
            return fencedJson;
        }
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1).trim();
        }
        return trimmed;
    }

    private String extractFencedJson(String value) {
        int fenceStart = value.indexOf("```");
        while (fenceStart >= 0) {
            int firstLineEnd = value.indexOf('\n', fenceStart + 3);
            int fenceEnd = firstLineEnd < 0 ? -1 : value.indexOf("```", firstLineEnd + 1);
            if (firstLineEnd >= 0 && fenceEnd > firstLineEnd) {
                String fenceHeader = value.substring(fenceStart + 3, firstLineEnd).trim();
                String fencedContent = value.substring(firstLineEnd + 1, fenceEnd).trim();
                if (fenceHeader.isEmpty() || "json".equalsIgnoreCase(fenceHeader)) {
                    return fencedContent;
                }
            }
            fenceStart = value.indexOf("```", fenceStart + 3);
        }
        return null;
    }

    private boolean isUsable(DiagnosisResponse response) {
        return response != null
            && response.getSummary() != null
            && !response.getSummary().trim().isEmpty()
            && response.getReasons() != null
            && !response.getReasons().isEmpty()
            && response.getReasons().stream().allMatch(reason -> reason != null && reason.getCode() != null && !reason.getCode().trim().isEmpty());
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
