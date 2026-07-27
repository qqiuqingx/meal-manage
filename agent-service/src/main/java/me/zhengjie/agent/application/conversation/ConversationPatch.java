package me.zhengjie.agent.application.conversation;

import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.ConversationTaskStack;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.query.domain.PendingBusinessQueryContext;

/**
 * Agent 基于可信快照计算出的会话增量。
 *
 * <p>该对象不执行持久化；只有主系统在 sessionVersion 条件更新成功后才能提交它。</p>
 */
public record ConversationPatch(DiagnosisSlots slots, String conversationStage,
                                PendingBusinessQueryContext pendingBusinessQueryContext,
                                LastBusinessQueryContext lastBusinessQueryContext,
                                ConversationTaskStack activeTaskStack) {
}
