package me.zhengjie.modules.agent.query;

import me.zhengjie.modules.agent.query.domain.dto.AgentBusinessRuleDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerAddressDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerCandidateDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerOverviewDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentCustomerPackageDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentDishSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanDishItemDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentMealPlanSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderMealBalanceDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOrderSummaryDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentPackageSpecDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentRefundLogDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentSubPackageSpecDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentVerificationLogDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Agent 专用只读 DTO 的金额字段隔离门禁。 */
class AgentQueryDtoAmountIsolationTest {

    private static final List<String> FORBIDDEN = List.of("amount", "price", "fee", "money", "discount", "refundamount", "received");

    /** 任何 Agent 查询 DTO 都不能声明订单金额、退款金额或价格相关字段。 */
    @Test
    void shouldNotExposeAmountSemanticFieldsInAgentQueryDtos() {
        List<Class<?>> types = List.of(
            AgentBusinessRuleDto.class, AgentCustomerAddressDto.class, AgentCustomerCandidateDto.class,
            AgentCustomerOverviewDto.class, AgentCustomerPackageDto.class, AgentDishSummaryDto.class,
            AgentMealPlanDishItemDto.class, AgentMealPlanSummaryDto.class, AgentOrderMealBalanceDto.class,
            AgentOrderSummaryDto.class, AgentPackageSpecDto.class, AgentRefundLogDto.class,
            AgentSubPackageSpecDto.class, AgentVerificationLogDto.class
        );
        for (Class<?> type : types) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                assertFalse(FORBIDDEN.stream().anyMatch(name::contains), type.getSimpleName() + "." + field.getName());
            }
        }
    }
}
