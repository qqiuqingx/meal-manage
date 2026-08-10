package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.domain.chat.ChatStatus;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;

import java.util.Collections;
import java.util.List;

/**
 * 单轮助手回复的受控结果，统一承载状态、面向客服文本和缺失项。
 */
public record AssistantTurnResult(ChatStatus status, String assistantMessage,
                                  List<MissingSlot> missingSlots,
                                  DiagnosisResponse diagnosisResult,
                                  boolean structured) {

    /** 规范化解析结果，避免调用方收到可变或空的缺失项列表。 */
    public AssistantTurnResult {
        missingSlots = missingSlots == null ? Collections.emptyList() : List.copyOf(missingSlots);
    }

    /** 构造兼容旧纯文本的已回答结果。 */
    public static AssistantTurnResult answered(String assistantMessage) {
        return new AssistantTurnResult(ChatStatus.ANSWERED, assistantMessage, Collections.emptyList(), null, false);
    }

    /** 构造带诊断结果的已回答结果。 */
    public static AssistantTurnResult answered(String assistantMessage, DiagnosisResponse diagnosisResult) {
        return new AssistantTurnResult(ChatStatus.ANSWERED, assistantMessage, Collections.emptyList(), diagnosisResult, true);
    }

    /** 判断本轮是否需要客服补充查询条件。 */
    public boolean needsMoreInfo() {
        return ChatStatus.NEED_MORE_INFO == status;
    }
}
