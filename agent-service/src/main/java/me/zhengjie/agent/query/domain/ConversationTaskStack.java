package me.zhengjie.agent.query.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
/** 有界会话任务栈，最多保留五个受控任务。 */
public class ConversationTaskStack {
    private static final int MAX_TASKS = 5;
    private List<ConversationTask> tasks = new ArrayList<>();
    /** 入栈并自动丢弃最旧任务，避免会话状态无界增长。 */
    public void add(ConversationTask task) { if (task != null) { tasks.add(task); if (tasks.size() > MAX_TASKS) tasks = new ArrayList<>(tasks.subList(tasks.size() - MAX_TASKS, tasks.size())); } }
    public List<ConversationTask> getTasks() { return tasks; }
    public void setTasks(List<ConversationTask> tasks) { this.tasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks.subList(Math.max(0, tasks.size() - MAX_TASKS), tasks.size())); }
    /** 挂起当前活动任务，以便相关插问结束后继续原任务。 */
    public void suspendActive() {
        for (ConversationTask task : tasks) if (task != null && task.getStatus() == ConversationTaskStatus.ACTIVE) {
            task.setStatus(ConversationTaskStatus.SUSPENDED); task.setUpdatedAt(OffsetDateTime.now());
        }
    }

    /**
     * 将待补查询登记为当前活动任务；同一任务重复澄清时只更新受控摘要。
     *
     * @param pendingBusinessQueryContext 已脱敏、可持久化的待补查询
     */
    public void registerPending(PendingBusinessQueryContext pendingBusinessQueryContext) {
        if (pendingBusinessQueryContext == null) return;
        for (int index = tasks.size() - 1; index >= 0; index--) {
            ConversationTask task = tasks.get(index);
            if (task != null && task.getStatus() == ConversationTaskStatus.ACTIVE && task.getPendingBusinessQueryContext() != null) {
                task.setPendingBusinessQueryContext(pendingBusinessQueryContext); task.setUpdatedAt(OffsetDateTime.now()); return;
            }
        }
        suspendActive();
        ConversationTask task = new ConversationTask(); task.setTaskId("task-" + java.util.UUID.randomUUID());
        task.setStatus(ConversationTaskStatus.ACTIVE); task.setPendingBusinessQueryContext(pendingBusinessQueryContext);
        task.setUpdatedAt(OffsetDateTime.now()); add(task);
    }

    /** 标记当前待补任务已由确定性槽位补齐并执行完成。 */
    public void completeActivePending() { updateActivePending(ConversationTaskStatus.COMPLETED); }

    /** 标记当前待补任务因用户切换目标、过期或取消而结束。 */
    public void cancelActivePending() { updateActivePending(ConversationTaskStatus.CANCELLED); }

    /** 标记当前待补任务已过期或无法稳定恢复。 */
    public void failActivePending() { updateActivePending(ConversationTaskStatus.FAILED); }
    /** 恢复最近一个可续接任务；没有挂起任务时不改变状态。 */
    public Optional<ConversationTask> restoreLatestSuspended() {
        for (int index = tasks.size() - 1; index >= 0; index--) {
            ConversationTask task = tasks.get(index);
            if (task != null && task.getStatus() == ConversationTaskStatus.SUSPENDED) {
                task.setStatus(ConversationTaskStatus.ACTIVE); task.setUpdatedAt(OffsetDateTime.now()); return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    /** 恢复最近被相关插问挂起的待补查询；不恢复已完成、取消或失败的任务。 */
    public Optional<PendingBusinessQueryContext> restoreLatestSuspendedPending() {
        for (int index = tasks.size() - 1; index >= 0; index--) {
            ConversationTask task = tasks.get(index);
            if (task != null && task.getStatus() == ConversationTaskStatus.SUSPENDED && task.getPendingBusinessQueryContext() != null) {
                task.setStatus(ConversationTaskStatus.ACTIVE); task.setUpdatedAt(OffsetDateTime.now());
                return Optional.of(task.getPendingBusinessQueryContext());
            }
        }
        return Optional.empty();
    }

    /** 将活动待补任务转换到指定终态，并保留最小受控摘要供审计。 */
    private void updateActivePending(ConversationTaskStatus status) {
        for (int index = tasks.size() - 1; index >= 0; index--) {
            ConversationTask task = tasks.get(index);
            if (task != null && task.getStatus() == ConversationTaskStatus.ACTIVE && task.getPendingBusinessQueryContext() != null) {
                task.setStatus(status); task.setUpdatedAt(OffsetDateTime.now()); return;
            }
        }
    }
}
