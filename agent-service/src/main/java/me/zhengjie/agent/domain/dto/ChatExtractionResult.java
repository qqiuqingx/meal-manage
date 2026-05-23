package me.zhengjie.agent.domain.dto;

import java.util.ArrayList;
import java.util.List;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.MissingSlot;

/**
 * 聊天抽取结果。
 */
public class ChatExtractionResult {

    private ChatIntent intent;
    private DiagnosisSlots slots;
    private List<MissingSlot> missingSlots = new ArrayList<>();
    private String reply;

    public ChatIntent getIntent() {
        return intent;
    }

    public void setIntent(ChatIntent intent) {
        this.intent = intent;
    }

    public DiagnosisSlots getSlots() {
        return slots;
    }

    public void setSlots(DiagnosisSlots slots) {
        this.slots = slots;
    }

    public List<MissingSlot> getMissingSlots() {
        return missingSlots;
    }

    public void setMissingSlots(List<MissingSlot> missingSlots) {
        this.missingSlots = missingSlots;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }
}
