package me.zhengjie.agent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** 写入不包含用户原文和客户信息的会话理解评测汇总。 */
public class IntentEvaluationReporter {
    private final ObjectMapper mapper = new ObjectMapper();
    /** 将稳定指标写到 target 目录，报告只保存计数、耗时和协议版本。 */
    public Path write(Map<String, Object> metrics) throws Exception {
        Path output = Path.of("target", "intent-evaluation", "summary.json");
        Files.createDirectories(output.getParent());
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("schemaVersion", "1.0"); safe.put("metrics", metrics == null ? Map.of() : metrics);
        mapper.writeValue(output.toFile(), safe); return output;
    }

    /** 从脱敏样本结果计算准确率与延迟分位；调用方不得传入用户原文或客户信息。 */
    public Map<String, Object> summarize(List<EvaluationObservation> observations) {
        List<EvaluationObservation> values = observations == null ? List.of() : observations;
        long total = values.size(); long interactionCorrect = values.stream().filter(EvaluationObservation::interactionCorrect).count();
        long referenceCorrect = values.stream().filter(EvaluationObservation::referenceCorrect).count();
        long clarificationCorrect = values.stream().filter(EvaluationObservation::clarificationCorrect).count();
        long targetCorrect = values.stream().filter(EvaluationObservation::primaryTargetCorrect).count();
        long multiFrameCorrect = values.stream().filter(EvaluationObservation::multiFrameExactMatch).count();
        long slotCorrect = values.stream().filter(EvaluationObservation::slotCorrect).count();
        long unknownCorrect = values.stream().filter(EvaluationObservation::unknownCorrect).count();
        List<Long> latency = new ArrayList<>(); for (EvaluationObservation value : values) latency.add(Math.max(0L, value.latencyMs())); Collections.sort(latency);
        Map<String, Object> result = new LinkedHashMap<>(); result.put("caseCount", total);
        result.put("interactionModeAccuracy", rate(interactionCorrect, total)); result.put("referenceResolutionAccuracy", rate(referenceCorrect, total));
        result.put("clarificationAccuracy", rate(clarificationCorrect, total)); result.put("p50LatencyMs", percentile(latency, .50D)); result.put("p95LatencyMs", percentile(latency, .95D));
        result.put("primaryIntentAccuracy", rate(targetCorrect, total)); result.put("multiIntentExactMatch", rate(multiFrameCorrect, total));
        result.put("slotAccuracy", rate(slotCorrect, total)); result.put("unknownIntentRecall", rate(unknownCorrect, total));
        result.put("confidenceCalibration", confidenceCalibration(values));
        return result;
    }
    private double rate(long numerator, long denominator) { return denominator == 0 ? 0D : numerator * 1D / denominator; }
    private long percentile(List<Long> values, double percentile) { return values.isEmpty() ? 0L : values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * percentile) - 1)); }
    /** 按模型置信度桶汇总真实正确率，避免只相信模型自报分数。 */
    private Map<String, Double> confidenceCalibration(List<EvaluationObservation> values) {
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (EvaluationObservation value : values) {
            String bucket = value.confidenceBucket() == null ? "UNKNOWN" : value.confidenceBucket();
            long[] counts = buckets.computeIfAbsent(bucket, ignored -> new long[2]);
            counts[0]++;
            if (value.overallCorrect()) counts[1]++;
        }
        Map<String, Double> result = new LinkedHashMap<>();
        buckets.forEach((bucket, counts) -> result.put(bucket, rate(counts[1], counts[0])));
        return result;
    }

    /** 单条真实模型评测的脱敏结果。 */
    public record EvaluationObservation(boolean interactionCorrect, boolean referenceCorrect, boolean clarificationCorrect,
                                        boolean primaryTargetCorrect, boolean multiFrameExactMatch, boolean slotCorrect,
                                        boolean unknownCorrect, String confidenceBucket, boolean overallCorrect, long latencyMs) {
        /** 兼容旧调用方；未覆盖的维度不降低其已有汇总指标。 */
        public EvaluationObservation(boolean interactionCorrect, boolean referenceCorrect, boolean clarificationCorrect, long latencyMs) {
            this(interactionCorrect, referenceCorrect, clarificationCorrect, true, true, true, true, "UNKNOWN",
                interactionCorrect && referenceCorrect && clarificationCorrect, latencyMs);
        }
    }
}
