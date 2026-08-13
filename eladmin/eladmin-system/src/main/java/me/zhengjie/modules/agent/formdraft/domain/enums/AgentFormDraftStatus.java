package me.zhengjie.modules.agent.formdraft.domain.enums;

/** Agent 表单草稿生命周期状态。 */
public enum AgentFormDraftStatus {
    /** 关键关联仍有歧义，可继续在对话中修订。 */
    EDITABLE,
    /** 关键关联已确定，可以跳转领取。 */
    READY,
    /** 业务页面已领取，仍需客服手动提交。 */
    CLAIMED,
    /** 正式业务记录已成功创建。 */
    SUBMITTED,
    /** 草稿已过期且敏感 payload 已清空。 */
    EXPIRED,
    /** 草稿已由客服放弃或被替代。 */
    CANCELLED
}
