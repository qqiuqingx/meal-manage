package me.zhengjie.agent.capability;

import org.springframework.stereotype.Component;
import java.util.Set;

/** 迁移适配器：现有受控 QueryPlan 编译器暂时承载目录中的已发布 profile。 */
@Component
public class LegacyBusinessQueryCapabilityHandler implements CapabilityHandler {
    public String handlerId() { return "legacy-business-query"; }
    public Set<String> plannerProfiles() {
        return Set.of("ACTIVE_CUSTOMER_BALANCE_DETAIL_V1", "CUSTOMER_ORDER_LIST_V1", "CUSTOMER_VERIFICATION_LIST_V1",
            "CUSTOMER_REFUND_LIST_V1", "CUSTOMER_MEAL_PLAN_LIST_V1");
    }
}
