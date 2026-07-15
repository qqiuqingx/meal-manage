package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.query.domain.PendingBusinessQueryContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 排餐诊断会话状态，负责维护阶段、最近轮次和最近诊断摘要。
 */
public class DiagnosisConversationState {

    public static final String COLLECTING_SLOTS = "COLLECTING_SLOTS";
    public static final String READY_TO_DIAGNOSE = "READY_TO_DIAGNOSE";
    public static final String DIAGNOSING = "DIAGNOSING";
    public static final String DIAGNOSED = "DIAGNOSED";
    public static final String FOLLOWING_UP = "FOLLOWING_UP";
    public static final String RESET = "RESET";
    public static final String ERROR = "ERROR";

    private static final int MAX_TURNS = 10;
    private static final int MAX_DIAGNOSIS_HISTORY = 3;

    private String stage = COLLECTING_SLOTS;
    private DiagnosisResponse lastDiagnosisResult;
    private List<DiagnosisConversationTurn> recentTurns = new ArrayList<>();
    private List<DiagnosisResponse> recentDiagnosisResults = new ArrayList<>();
    private LastBusinessQueryContext lastBusinessQueryContext;
    private PendingBusinessQueryContext pendingBusinessQueryContext;

    /**
     * 创建默认会话状态，新会话默认进入槽位收集阶段。
     *
     * @return 初始化后的会话状态
     */
    public static DiagnosisConversationState initialize() {
        return new DiagnosisConversationState();
    }

    /**
     * 记录一轮会话消息，并限制保留窗口。
     *
     * @param turn 当前轮次快照
     */
    public void addTurn(DiagnosisConversationTurn turn) {
        if (turn == null) {
            return;
        }
        recentTurns.add(turn);
        if (recentTurns.size() > MAX_TURNS) {
            recentTurns = new ArrayList<>(recentTurns.subList(recentTurns.size() - MAX_TURNS, recentTurns.size()));
        }
    }

    /**
     * 记录最近一次诊断结果，并维护最近三次诊断摘要窗口。
     *
     * @param diagnosisResult 新的诊断结果
     */
    public void addDiagnosisResult(DiagnosisResponse diagnosisResult) {
        lastDiagnosisResult = diagnosisResult;
        if (diagnosisResult == null) {
            return;
        }
        recentDiagnosisResults.add(diagnosisResult);
        if (recentDiagnosisResults.size() > MAX_DIAGNOSIS_HISTORY) {
            recentDiagnosisResults = new ArrayList<>(
                recentDiagnosisResults.subList(recentDiagnosisResults.size() - MAX_DIAGNOSIS_HISTORY, recentDiagnosisResults.size())
            );
        }
    }

    /**
     * 清空本轮待复用的诊断结果，但保留历史摘要，便于重新发起排查。
     */
    public void clearLastDiagnosisResult() {
        this.lastDiagnosisResult = null;
    }

    /**
     * 重置整个会话状态，用于清空会话。
     */
    public void reset() {
        this.stage = RESET;
        this.lastDiagnosisResult = null;
        this.recentTurns = new ArrayList<>();
        this.recentDiagnosisResults = new ArrayList<>();
        this.lastBusinessQueryContext = null;
        this.pendingBusinessQueryContext = null;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public DiagnosisResponse getLastDiagnosisResult() {
        return lastDiagnosisResult;
    }

    public void setLastDiagnosisResult(DiagnosisResponse lastDiagnosisResult) {
        this.lastDiagnosisResult = lastDiagnosisResult;
    }

    public List<DiagnosisConversationTurn> getRecentTurns() {
        return recentTurns;
    }

    public void setRecentTurns(List<DiagnosisConversationTurn> recentTurns) {
        this.recentTurns = recentTurns;
    }

    public List<DiagnosisResponse> getRecentDiagnosisResults() {
        return recentDiagnosisResults;
    }

    public void setRecentDiagnosisResults(List<DiagnosisResponse> recentDiagnosisResults) {
        this.recentDiagnosisResults = recentDiagnosisResults;
    }

    /** 返回可供下一轮重新规划使用的脱敏业务查询摘要。 */
    public LastBusinessQueryContext getLastBusinessQueryContext() { return lastBusinessQueryContext; }

    /** 保存最近一次受控业务查询摘要，不保存工具原始响应。 */
    public void setLastBusinessQueryContext(LastBusinessQueryContext lastBusinessQueryContext) {
        this.lastBusinessQueryContext = lastBusinessQueryContext;
    }

    /** 返回等待补充日期、餐次或实体的受控查询上下文。 */
    public PendingBusinessQueryContext getPendingBusinessQueryContext() { return pendingBusinessQueryContext; }

    /** 保存待执行语义；调用方应在成功、重置、目标切换或过期时清空。 */
    public void setPendingBusinessQueryContext(PendingBusinessQueryContext pendingBusinessQueryContext) {
        this.pendingBusinessQueryContext = pendingBusinessQueryContext;
    }
}
