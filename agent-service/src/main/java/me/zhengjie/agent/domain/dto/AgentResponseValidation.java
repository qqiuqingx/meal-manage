package me.zhengjie.agent.domain.dto;

/** 面向前端的最小验证摘要；不携带模型原文、敏感字段或内部校验细节。 */
public class AgentResponseValidation {
    /** 契约版本，字段只增不改。 */ private String version = "v1";
    /** VERIFIED、DEGRADED 或 AI_SUGGESTION。 */ private String status;
    /** 本轮已验证事实数量。 */ private int verifiedFactCount;
    /** 是否仅包含需人工核对的 AI 建议。 */ private boolean suggestion;
    public String getVersion() { return version; }
    public void setVersion(String value) { version = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public int getVerifiedFactCount() { return verifiedFactCount; }
    public void setVerifiedFactCount(int value) { verifiedFactCount = value; }
    public boolean isSuggestion() { return suggestion; }
    public void setSuggestion(boolean value) { suggestion = value; }
}
