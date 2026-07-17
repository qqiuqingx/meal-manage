package me.zhengjie.agent.query.domain;

import me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult;
import java.time.OffsetDateTime;
/** 可持久化的受控任务摘要，不保存原始模型输出或工具响应。 */
public class ConversationTask {
    private String taskId;
    private ConversationTaskStatus status;
    private ConversationUnderstandingResult understanding;
    private PendingBusinessQueryContext pendingBusinessQueryContext;
    private String failureCode;
    private OffsetDateTime updatedAt;
    public String getTaskId() { return taskId; } public void setTaskId(String taskId) { this.taskId = taskId; }
    public ConversationTaskStatus getStatus() { return status; } public void setStatus(ConversationTaskStatus status) { this.status = status; }
    public ConversationUnderstandingResult getUnderstanding() { return understanding; } public void setUnderstanding(ConversationUnderstandingResult understanding) { this.understanding = understanding; }
    /** 返回等待补槽的受控查询摘要；仅任务处于 ACTIVE/SUSPENDED 时允许存在。 */
    public PendingBusinessQueryContext getPendingBusinessQueryContext() { return pendingBusinessQueryContext; }
    public void setPendingBusinessQueryContext(PendingBusinessQueryContext pendingBusinessQueryContext) { this.pendingBusinessQueryContext = pendingBusinessQueryContext; }
    public String getFailureCode() { return failureCode; } public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
