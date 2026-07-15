package me.zhengjie.agent.query.domain;

import java.util.ArrayList;
import java.util.List;
/** 有界会话任务栈，最多保留五个受控任务。 */
public class ConversationTaskStack {
    private static final int MAX_TASKS = 5;
    private List<ConversationTask> tasks = new ArrayList<>();
    /** 入栈并自动丢弃最旧任务，避免会话状态无界增长。 */
    public void add(ConversationTask task) { if (task != null) { tasks.add(task); if (tasks.size() > MAX_TASKS) tasks = new ArrayList<>(tasks.subList(tasks.size() - MAX_TASKS, tasks.size())); } }
    public List<ConversationTask> getTasks() { return tasks; }
    public void setTasks(List<ConversationTask> tasks) { this.tasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks.subList(Math.max(0, tasks.size() - MAX_TASKS), tasks.size())); }
}
