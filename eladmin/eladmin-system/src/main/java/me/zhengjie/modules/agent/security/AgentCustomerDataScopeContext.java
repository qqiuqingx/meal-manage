package me.zhengjie.modules.agent.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 当前内部 Agent 请求可访问的客户范围；未绑定时仅供本地业务调用使用。 */
public final class AgentCustomerDataScopeContext {
    private static final ThreadLocal<Set<Long>> CUSTOMER_IDS = new ThreadLocal<>();

    private AgentCustomerDataScopeContext() { }

    /** 绑定当前请求的客户范围；null 表示经签名上下文确认可访问全部客户。 */
    public static void bind(Set<Long> customerIds) {
        CUSTOMER_IDS.set(customerIds == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(customerIds)));
    }

    /** 清理请求范围，避免容器线程复用时串用上一位客服的数据。 */
    public static void clear() { CUSTOMER_IDS.remove(); }

    /** 返回受限客户集合；null 表示全量范围或非 Agent 的本地调用。 */
    public static Set<Long> customerIds() { return CUSTOMER_IDS.get(); }

    /** 判断当前范围是否允许访问指定客户。 */
    public static boolean allows(Long customerId) {
        Set<Long> ids = CUSTOMER_IDS.get();
        return ids == null || (customerId != null && ids.contains(customerId));
    }
}
