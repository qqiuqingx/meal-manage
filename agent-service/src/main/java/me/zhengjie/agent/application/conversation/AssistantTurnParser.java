package me.zhengjie.agent.application.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.guardrail.ToolGuardrailException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 解析 Agent 最终回复的最小 JSON 协议，并为旧纯文本提供兼容路径。
 */
public final class AssistantTurnParser {
    private static final Set<String> PROTOCOL_FIELDS = Set.of("outcome", "assistantMessage", "missingSlots", "diagnosisResult");
    private static final int MAX_MISSING_SLOTS = 4;
    private static final int MAX_QUICK_REPLIES = 6;
    private static final int MAX_QUICK_REPLY_LENGTH = 20;
    private final ObjectMapper objectMapper;

    /**
     * 创建助手回复解析器。
     *
     * @param objectMapper JSON 映射器
     */
    public AssistantTurnParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析模型最终输出；非 JSON 的旧纯文本按 ANSWERED 兼容处理。
     *
     * @param rawAnswer 模型最终输出
     * @return 受控的单轮助手结果
     * @throws ToolGuardrailException JSON 协议存在结构、枚举或内容错误时抛出稳定错误
     */
    public AssistantTurnResult parse(String rawAnswer) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            throw protocolError("ANSWER_EMPTY");
        }
        String text = rawAnswer.trim();
        if (!looksLikeJson(text)) {
            return AssistantTurnResult.answered(text);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception ex) {
            throw protocolError("ANSWER_PROTOCOL_INVALID");
        }
        if (root == null || !root.isObject()) {
            throw protocolError("ANSWER_PROTOCOL_INVALID");
        }
        if (!root.has("outcome")) {
            DiagnosisResponse legacyDiagnosis = parseLegacyDiagnosis(root);
            if (legacyDiagnosis != null) {
                return AssistantTurnResult.answered(text, legacyDiagnosis);
            }
            throw protocolError("ANSWER_PROTOCOL_INVALID");
        }
        rejectUnknownFields(root);

        String outcome = requiredText(root, "outcome");
        ChatStatus status;
        try {
            status = ChatStatus.valueOf(outcome);
        } catch (Exception ex) {
            throw protocolError("ANSWER_OUTCOME_INVALID");
        }
        if (status != ChatStatus.ANSWERED && status != ChatStatus.NEED_MORE_INFO) {
            throw protocolError("ANSWER_OUTCOME_INVALID");
        }

        String assistantMessage = requiredText(root, "assistantMessage");
        List<MissingSlot> missingSlots = parseMissingSlots(root.get("missingSlots"));
        if (status == ChatStatus.ANSWERED && !missingSlots.isEmpty()) {
            throw protocolError("ANSWER_MISSING_SLOTS_ON_ANSWERED");
        }
        if (status == ChatStatus.NEED_MORE_INFO && missingSlots.isEmpty()) {
            throw protocolError("ANSWER_MISSING_SLOTS_REQUIRED");
        }

        DiagnosisResponse diagnosisResult = parseDiagnosis(root.get("diagnosisResult"));
        return new AssistantTurnResult(status, assistantMessage, missingSlots, diagnosisResult, true);
    }

    /**
     * 根据受控缺失项生成固定中文快捷回复，模型不能自定义按钮文本。
     *
     * @param turn 已解析的助手结果
     * @param slots 当前安全会话槽位
     * @return 最多六个、每项不超过二十个字符的快捷回复
     */
    public List<String> quickReplies(AssistantTurnResult turn, DiagnosisSlots slots) {
        if (turn == null || !turn.needsMoreInfo()) {
            return Collections.emptyList();
        }
        List<String> replies = new ArrayList<>();
        for (MissingSlot missingSlot : turn.missingSlots()) {
            if (missingSlot == null) continue;
            switch (missingSlot) {
                case RECORD_DATE:
                    addQuickReply(replies, "今天");
                    addQuickReply(replies, "昨天");
                    addQuickReply(replies, "明天");
                    break;
                case MEAL_TYPE:
                    addQuickReply(replies, "早餐");
                    addQuickReply(replies, "午餐");
                    addQuickReply(replies, "晚餐");
                    break;
                case DATE_RANGE:
                    addQuickReply(replies, "输入起止日期");
                    break;
                case CUSTOMER_OR_ORDER:
                case PACKAGE:
                case RULE_TOPIC:
                    // 没有已登记的固定选项时不伪造编号、套餐或规则主题按钮。
                    break;
                default:
                    break;
            }
            if (replies.size() >= MAX_QUICK_REPLIES) break;
        }
        return replies;
    }

    /** 判断字符串是否应进入 JSON 协议解析路径。 */
    private boolean looksLikeJson(String text) {
        return text.startsWith("{") || text.startsWith("[");
    }

    /** 拒绝协议之外的字段，避免模型借 JSON 字段注入动作或内部状态。 */
    private void rejectUnknownFields(JsonNode root) {
        Iterator<String> fields = root.fieldNames();
        while (fields.hasNext()) {
            if (!PROTOCOL_FIELDS.contains(fields.next())) {
                throw protocolError("ANSWER_PROTOCOL_INVALID");
            }
        }
    }

    /** 读取协议必填的非空文本字段。 */
    private String requiredText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw protocolError("ANSWER_PROTOCOL_INVALID");
        }
        return value.asText().trim();
    }

    /** 解析受控缺失项枚举并限制数量、重复项和类型。 */
    private List<MissingSlot> parseMissingSlots(JsonNode node) {
        if (node == null || node.isNull()) return Collections.emptyList();
        if (!node.isArray() || node.size() > MAX_MISSING_SLOTS) {
            throw protocolError("ANSWER_MISSING_SLOTS_INVALID");
        }
        LinkedHashSet<MissingSlot> unique = new LinkedHashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) throw protocolError("ANSWER_MISSING_SLOTS_INVALID");
            try {
                if (!unique.add(MissingSlot.valueOf(value.asText()))) {
                    throw protocolError("ANSWER_MISSING_SLOTS_INVALID");
                }
            } catch (IllegalArgumentException ex) {
                throw protocolError("ANSWER_MISSING_SLOTS_INVALID");
            }
        }
        return new ArrayList<>(unique);
    }

    /** 解析可选的结构化诊断对象，诊断校验仍由现有结果校验器负责。 */
    private DiagnosisResponse parseDiagnosis(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isObject()) throw protocolError("ANSWER_DIAGNOSIS_INVALID");
        try {
            return objectMapper.treeToValue(node, DiagnosisResponse.class);
        } catch (Exception ex) {
            throw protocolError("ANSWER_DIAGNOSIS_INVALID");
        }
    }

    /** 兼容旧版直接返回 summary/reasons 或 diagnosisResult 的诊断 JSON。 */
    private DiagnosisResponse parseLegacyDiagnosis(JsonNode root) {
        JsonNode candidate = root.has("diagnosisResult") ? root.get("diagnosisResult") : root;
        if (candidate == null || !candidate.isObject() || !candidate.has("summary") || !candidate.has("reasons")) {
            return null;
        }
        try {
            return objectMapper.treeToValue(candidate, DiagnosisResponse.class);
        } catch (Exception ex) {
            throw protocolError("ANSWER_DIAGNOSIS_INVALID");
        }
    }

    /** 添加符合长度与数量约束的固定快捷回复。 */
    private void addQuickReply(List<String> replies, String value) {
        if (replies.size() < MAX_QUICK_REPLIES && value != null && value.length() <= MAX_QUICK_REPLY_LENGTH
            && !replies.contains(value)) {
            replies.add(value);
        }
    }

    /** 构造不包含模型原文的稳定协议异常。 */
    private ToolGuardrailException protocolError(String code) {
        return new ToolGuardrailException(code, "assistant turn protocol invalid");
    }
}
