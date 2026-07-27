package me.zhengjie.agent.capability;

import java.util.Set;

/** 能力处理器的受控登记接口；一个处理器可明确承载多个兼容 profile。 */
public interface CapabilityHandler {
    String handlerId();
    Set<String> plannerProfiles();
}
