package me.zhengjie.agent.query;

import me.zhengjie.agent.query.client.dto.CustomerOverviewResponse;
import me.zhengjie.agent.query.client.dto.DishCandidatePreviewResponse;
import me.zhengjie.agent.query.client.dto.OrderListResponse;
import me.zhengjie.agent.query.client.dto.OrderSummaryResponse;
import me.zhengjie.agent.query.client.dto.MealPlanListResponse;
import me.zhengjie.agent.query.client.dto.VerificationListResponse;
import me.zhengjie.agent.query.client.dto.RefundListResponse;
import me.zhengjie.agent.query.client.dto.CustomerCandidateListResponse;
import me.zhengjie.agent.query.client.dto.PackageSpecResponse;
import me.zhengjie.agent.query.client.dto.BusinessRuleResponse;
import me.zhengjie.agent.query.client.dto.DishListResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Agent 跨服务传输 DTO 不得出现任何订单或退款金额语义字段。 */
class BusinessQueryTransportDtoAmountIsolationTest {

    private static final List<String> FORBIDDEN = List.of("amount", "price", "fee", "money", "discount", "refundamount", "received");

    @Test
    void shouldKeepBusinessQueryTransportDtosFreeOfAmountFields() {
        List<Class<?>> types = List.of(CustomerOverviewResponse.class, CustomerOverviewResponse.MealBalance.class,
            CustomerOverviewResponse.CustomerPackage.class, CustomerOverviewResponse.ExcludedDateRule.class, DishCandidatePreviewResponse.class, DishCandidatePreviewResponse.DishCandidateItem.class,
            OrderListResponse.class, OrderSummaryResponse.class, OrderSummaryResponse.MealBalance.class,
            MealPlanListResponse.class, MealPlanListResponse.MealPlanSummary.class, MealPlanListResponse.MealPlanDish.class,
            VerificationListResponse.class, VerificationListResponse.VerificationLog.class, RefundListResponse.class, RefundListResponse.RefundLog.class,
            CustomerCandidateListResponse.class, CustomerCandidateListResponse.CustomerCandidate.class,
            PackageSpecResponse.class, PackageSpecResponse.SubPackageSpec.class, BusinessRuleResponse.class,
            DishListResponse.class, DishListResponse.DishSummary.class);
        for (Class<?> type : types) for (Field field : type.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(FORBIDDEN.stream().anyMatch(name::contains), () -> type.getSimpleName() + " contains forbidden field " + field.getName());
        }
    }
}
