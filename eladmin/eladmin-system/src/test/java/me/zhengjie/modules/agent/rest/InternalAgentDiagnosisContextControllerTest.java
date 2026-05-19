package me.zhengjie.modules.agent.rest;

import me.zhengjie.modules.agent.domain.dto.AgentCandidateDishStatsRequest;
import me.zhengjie.modules.agent.domain.dto.AgentCustomerLookupRequest;
import me.zhengjie.modules.agent.domain.dto.AgentCustomerOrdersRequest;
import me.zhengjie.modules.agent.domain.dto.AgentMealPlanLookupRequest;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.agent.service.AgentDiagnosisContextService;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAgentDiagnosisContextControllerTest {

    @Mock
    private AgentDiagnosisContextService contextService;

    @InjectMocks
    private InternalAgentDiagnosisContextController controller;

    @Test
    void shouldDelegateContextBuild() {
        MealPlanDiagnosisContextRequest request = new MealPlanDiagnosisContextRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        MealPlanDiagnosisContextDto context = new MealPlanDiagnosisContextDto();
        context.setCustomerId(1001L);
        when(contextService.buildContext(any(MealPlanDiagnosisContextRequest.class))).thenReturn(context);

        ResponseEntity<MealPlanDiagnosisContextDto> response = controller.buildContext("rid-1", request);

        assertNotNull(response.getBody());
        assertEquals(1001L, response.getBody().getCustomerId());
        verify(contextService).buildContext(request);
    }

    @Test
    void shouldDelegateCustomerProfileLookup() {
        AgentCustomerLookupRequest request = new AgentCustomerLookupRequest();
        request.setCustomerId(1001L);
        CustomerProfileDetailDto profile = new CustomerProfileDetailDto();
        profile.setId(1001L);
        when(contextService.resolveCustomerProfile(1001L, null)).thenReturn(profile);

        ResponseEntity<CustomerProfileDetailDto> response = controller.getCustomerProfile("rid-1", request);

        assertNotNull(response.getBody());
        assertEquals(1001L, response.getBody().getId());
        verify(contextService).resolveCustomerProfile(1001L, null);
    }

    @Test
    void shouldDelegateCustomerOrdersLookup() {
        AgentCustomerOrdersRequest request = new AgentCustomerOrdersRequest();
        request.setCustomerId(1001L);
        request.setPage(1);
        request.setSize(20);
        CustomerOrderDetailDto order = new CustomerOrderDetailDto();
        order.setId(2001L);
        when(contextService.resolveOrders(1001L, null, 1, 20)).thenReturn(Collections.singletonList(order));

        ResponseEntity<java.util.List<CustomerOrderDetailDto>> response = controller.listCustomerOrders("rid-1", request);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(contextService).resolveOrders(1001L, null, 1, 20);
    }

    @Test
    void shouldDelegateMealPlanLookup() {
        AgentMealPlanLookupRequest request = new AgentMealPlanLookupRequest();
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");
        MealPlanDetailVO detail = new MealPlanDetailVO();
        detail.setTotalCustomers(2);
        when(contextService.resolveMealPlan("2026-05-17", "LUNCH")).thenReturn(detail);

        ResponseEntity<MealPlanDetailVO> response = controller.getMealPlan("rid-1", request);

        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getTotalCustomers());
        verify(contextService).resolveMealPlan("2026-05-17", "LUNCH");
    }

    @Test
    void shouldDelegateCandidateDishStatsLookup() {
        AgentCandidateDishStatsRequest request = new AgentCandidateDishStatsRequest();
        request.setRecordDate("2026-05-17");
        MealPackageStatDto stat = new MealPackageStatDto();
        stat.setPackageCode("PKG001");
        when(contextService.resolveCandidateDishStats("2026-05-17")).thenReturn(Collections.singletonList(stat));

        ResponseEntity<java.util.List<MealPackageStatDto>> response = controller.getCandidateDishStats("rid-1", request);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(contextService).resolveCandidateDishStats("2026-05-17");
    }
}
