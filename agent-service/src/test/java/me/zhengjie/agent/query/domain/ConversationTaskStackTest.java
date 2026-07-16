package me.zhengjie.agent.query.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
