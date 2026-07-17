package me.zhengjie.agent.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 将人工标注的基础样例扩展为稳定的文本变体，避免把线上原文写入评测集。 */
public class IntentUnderstandingCaseGenerator {
    private static final List<String> PREFIXES = List.of("", "请问", "麻烦", "帮我", "我想知道", "现在", "能否", "请", "想问下", "劳烦", "帮忙查下", "请帮我看", "麻烦确认", "咨询一下", "想了解");
    private static final List<String> SUFFIXES = List.of("", "呢", "？", "。", "  ", "\n", "谢谢", "呀", "一下", "吧", "哈", "，麻烦了", "？谢谢", "  麻烦", "哦");
    /** 每个基础样例生成最多 15 个去重表达，用于离线理解评测。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> expand(List<Map<String, Object>> baseCases) {
        List<Map<String, Object>> expanded = new ArrayList<>();
        if (baseCases == null) return expanded;
        for (Map<String, Object> item : baseCases) {
            List<Map<String, Object>> turns = item.get("turns") instanceof List ? (List<Map<String, Object>>) item.get("turns") : List.of();
            for (int variant = 0; variant < 15; variant++) {
                java.util.Map<String, Object> copy = new java.util.LinkedHashMap<>(item); copy.put("id", item.get("id") + "-v" + (variant + 1));
                List<Map<String, Object>> copiedTurns = new ArrayList<>();
                for (Map<String, Object> turn : turns) {
                    Map<String, Object> copied = new java.util.LinkedHashMap<>(turn);
                    Object user = copied.get("user"); if (user != null) copied.put("user", PREFIXES.get(variant) + user + SUFFIXES.get(variant));
                    copiedTurns.add(copied);
                }
                copy.put("turns", copiedTurns); expanded.add(copy);
            }
        }
        return expanded;
    }
}
