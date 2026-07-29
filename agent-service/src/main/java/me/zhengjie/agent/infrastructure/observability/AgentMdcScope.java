package me.zhengjie.agent.infrastructure.observability;

import org.slf4j.MDC;

/**
 * 单字段 MDC 作用域。
 *
 * <p>进入作用域时写入动态领域字段，关闭时恢复调用前的值，避免线程复用或嵌套调用造成上下文串扰。</p>
 */
public final class AgentMdcScope implements AutoCloseable {

    private final String key;
    private final String previousValue;

    private AgentMdcScope(String key, String value) {
        this.key = key;
        this.previousValue = MDC.get(key);
        if (value == null || value.isBlank()) MDC.remove(key);
        else MDC.put(key, value);
    }

    /**
     * 创建并立即应用一个 MDC 字段作用域。
     *
     * @param key 日志字段名
     * @param value 当前调用的受控字段值
     * @return 可用于 try-with-resources 的作用域
     */
    public static AgentMdcScope put(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("MDC key must not be blank");
        }
        return new AgentMdcScope(key, value);
    }

    /** 恢复进入作用域前的字段值。 */
    @Override
    public void close() {
        if (previousValue == null) MDC.remove(key);
        else MDC.put(key, previousValue);
    }
}
