package me.zhengjie.agent.query.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 保证任务栈有界，长期会话不会无限保存任务状态。 */
class ConversationTaskStackTest {
    @Test
    void retainsOnlyMostRecentFiveTasks() {
        ConversationTaskStack stack = new ConversationTaskStack();
        for (int index = 0; index < 6; index++) {
            ConversationTask task = new ConversationTask(); task.setTaskId("task-" + index); stack.add(task);
        }
        assertEquals(5, stack.getTasks().size());
        assertEquals("task-1", stack.getTasks().get(0).getTaskId());
    }

    @Test
    void suspendsAndRestoresLatestActiveTask() {
        ConversationTaskStack stack = new ConversationTaskStack();
        ConversationTask first = new ConversationTask(); first.setTaskId("first"); first.setStatus(ConversationTaskStatus.ACTIVE); stack.add(first);
        ConversationTask second = new ConversationTask(); second.setTaskId("second"); second.setStatus(ConversationTaskStatus.ACTIVE); stack.add(second);
        stack.suspendActive();
        assertEquals(ConversationTaskStatus.SUSPENDED, second.getStatus());
        assertEquals("second", stack.restoreLatestSuspended().orElseThrow().getTaskId());
        assertEquals(ConversationTaskStatus.ACTIVE, second.getStatus());
    }

    @Test
    void doesNotRestoreCompletedTask() {
        ConversationTaskStack stack = new ConversationTaskStack();
        ConversationTask task = new ConversationTask(); task.setTaskId("done"); task.setStatus(ConversationTaskStatus.COMPLETED); stack.add(task);
        assertTrue(stack.restoreLatestSuspended().isEmpty());
    }

    @Test
    void restoresSuspendedPendingContextAfterRelatedInterruption() {
        ConversationTaskStack stack = new ConversationTaskStack();
        PendingBusinessQueryContext pending = new PendingBusinessQueryContext();
        pending.setOriginalQuestionSummary("待补充统计日期");
        stack.registerPending(pending);
        stack.suspendActive();
        ConversationTask interruption = new ConversationTask(); interruption.setTaskId("interruption");
        interruption.setStatus(ConversationTaskStatus.COMPLETED); stack.add(interruption);

        assertEquals(pending, stack.restoreLatestSuspendedPending().orElseThrow());
        assertEquals(ConversationTaskStatus.ACTIVE, stack.getTasks().get(0).getStatus());
        stack.completeActivePending();
        assertEquals(ConversationTaskStatus.COMPLETED, stack.getTasks().get(0).getStatus());
        assertTrue(stack.restoreLatestSuspendedPending().isEmpty());
    }
}
