package me.zhengjie.modules.agent.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 当前内部 Agent 请求可访问的客户范围。未绑定访问上下文时必须失败关闭。 */
public final class AgentCustomerDataScopeContext {
    private static final ThreadLocal<Set<Long>> CUSTOMER_IDS = new ThreadLocal<>();
    private static final ThreadLocal<ScopeStatus> STATUS = new ThreadLocal<>();

    /** 客户数据范围的明确状态，禁止以 null 隐式表示全量权限。 */
    public enum ScopeStatus { UNBOUND, RESTRICTED, ALL_ALLOWED }

    private AgentCustomerDataScopeContext() { }

    /**
     * 绑定当前请求的客户范围。
     *
     * @param customerIds 解析后的客户集合；null 仅表示已验签上下文明确授予全量范围
     */
    public static void bind(Set<Long> customerIds) {
        if (customerIds == null) {
            CUSTOMER_IDS.remove();
            STATUS.set(ScopeStatus.ALL_ALLOWED);
            return;
        }
        CUSTOMER_IDS.set(Collections.unmodifiableSet(new LinkedHashSet<>(customerIds)));
        STATUS.set(ScopeStatus.RESTRICTED);
    }

    /** 清理请求范围，避免容器线程复用时串用上一位客服的数据。 */
    public static void clear() { CUSTOMER_IDS.remove(); STATUS.remove(); }

    /** 返回受限客户集合；仅 ALL_ALLOWED 状态下返回 null。 */
    public static Set<Long> customerIds() { return CUSTOMER_IDS.get(); }

    /** 返回当前范围状态，未绑定请求上下文时为 UNBOUND。 */
    public static ScopeStatus status() { return STATUS.get() == null ? ScopeStatus.UNBOUND : STATUS.get(); }

    /** 判断当前范围是否允许访问指定客户。 */
    public static boolean allows(Long customerId) {
        if (status() == ScopeStatus.ALL_ALLOWED) return customerId != null;
        Set<Long> ids = CUSTOMER_IDS.get();
        return status() == ScopeStatus.RESTRICTED && customerId != null && ids != null && ids.contains(customerId);
    }
}
