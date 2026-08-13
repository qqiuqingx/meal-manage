package me.zhengjie.modules.agent.formdraft.domain.enums;

/** Agent 可创建的固定表单草稿类型。 */
public enum AgentFormDraftType {
    /** 新增客户档案并创建首单。 */
    CREATE_CUSTOMER_WITH_ORDER("customerProfile:add"),
    /** 为已有客户新增订单。 */
    CREATE_ORDER("customerOrder:add");

    private final String targetPermission;

    /** 绑定草稿类型对应的目标新增权限。 */
    AgentFormDraftType(String targetPermission) {
        this.targetPermission = targetPermission;
    }

    /** 返回领取和提交目标表单所需的新增权限。 */
    public String getTargetPermission() {
        return targetPermission;
    }
}
