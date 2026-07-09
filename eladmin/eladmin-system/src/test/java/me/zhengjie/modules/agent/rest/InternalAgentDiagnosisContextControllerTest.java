package me.zhengjie.modules.agent.rest;

import me.zhengjie.modules.agent.domain.dto.AgentCandidateDishStatsRequest;
import me.zhengjie.modules.agent.domain.dto.AgentCustomerLookupRequest;
import me.zhengjie.modules.agent.domain.dto.AgentCustomerOrdersRequest;
import me.zhengjie.modules.agent.domain.dto.AgentMealPlanLookupRequest;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerInsightRequest;
import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerMealSummaryResponse;
import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerOrderSummaryResponse;
import me.zhengjie.modules.agent.domain.dto.insight.AgentCustomerVerificationSummaryResponse;
import me.zhengjie.modules.agent.service.AgentCustomerInsightService;
import me.zhengjie.modules.agent.service.AgentDiagnosisContextService;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderDetailDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerProfileDetailDto;
import me.zhengjie.modules.meal.domain.dto.MealPackageStatDto;
import me.zhengjie.modules.meal.domain.dto.MealPlanDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAgentDiagnosisContextControllerTest {

    private static final String INTERNAL_TOKEN = "agent-diagnosis-internal-token";

    @Mock
    private AgentDiagnosisContextService contextService;

    @Mock
    private AgentCustomerInsightService customerInsightService;

    @InjectMocks
    private InternalAgentDiagnosisContextController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "internalToken", INTERNAL_TOKEN);
    }

    @Test
    void shouldDelegateContextBuild() {
        MealPlanDiagnosisContextRequest request = new MealPlanDiagnosisContextRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        MealPlanDiagnosisContextDto context = new MealPlanDiagnosisContextDto();
        context.setCustomerId(1001L);
        when(contextService.buildContext(any(MealPlanDiagnosisContextRequest.class))).thenReturn(context);

        ResponseEntity<MealPlanDiagnosisContextDto> response = controller.buildContext("rid-1", INTERNAL_TOKEN, request);

        assertNotNull(response.getBody());
        assertEquals(1001L, response.getBody().getCustomerId());
        verify(contextService).buildContext(request);
    }

    @Test
    void shouldRejectInvalidInternalToken() {
        MealPlanDiagnosisContextRequest request = new MealPlanDiagnosisContextRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.buildContext("rid-1", "wrong-token", request));

        assertEquals(403, exception.getStatus().value());
        assertEquals("Invalid agent internal token", exception.getReason());
        verify(contextService, never()).buildContext(any(MealPlanDiagnosisContextRequest.class));
    }

    @Test
    void shouldRejectBlankInternalToken() {
        MealPlanDiagnosisContextRequest request = new MealPlanDiagnosisContextRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.buildContext("rid-1", "   ", request));

        assertEquals(403, exception.getStatus().value());
        assertEquals("Invalid agent internal token", exception.getReason());
        verify(contextService, never()).buildContext(any(MealPlanDiagnosisContextRequest.class));
    }

    @Test
    void shouldRejectBlankConfiguredInternalTokenOnStartup() {
        ReflectionTestUtils.setField(controller, "internalToken", "   ");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(controller, "validateInternalToken"));

        assertEquals("agent.internal-token must be configured", exception.getMessage());
    }

    @Test
    void shouldRejectRequestWhenConfiguredInternalTokenIsBlank() {
        ReflectionTestUtils.setField(controller, "internalToken", "   ");
        MealPlanDiagnosisContextRequest request = new MealPlanDiagnosisContextRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.buildContext("rid-1", "   ", request));

        assertEquals(403, exception.getStatus().value());
        assertEquals("Invalid agent internal token", exception.getReason());
        verify(contextService, never()).buildContext(any(MealPlanDiagnosisContextRequest.class));
    }

    @Test
    void shouldDelegateCustomerProfileLookup() {
        AgentCustomerLookupRequest request = new AgentCustomerLookupRequest();
        request.setCustomerId(1001L);
        CustomerProfileDetailDto profile = new CustomerProfileDetailDto();
        profile.setId(1001L);
        when(contextService.resolveCustomerProfile(1001L, null)).thenReturn(profile);

        ResponseEntity<CustomerProfileDetailDto> response = controller.getCustomerProfile("rid-1", INTERNAL_TOKEN, request);

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

        ResponseEntity<java.util.List<CustomerOrderDetailDto>> response = controller.listCustomerOrders("rid-1", INTERNAL_TOKEN, request);

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

        ResponseEntity<MealPlanDetailVO> response = controller.getMealPlan("rid-1", INTERNAL_TOKEN, request);

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

        ResponseEntity<java.util.List<MealPackageStatDto>> response = controller.getCandidateDishStats("rid-1", INTERNAL_TOKEN, request);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(contextService).resolveCandidateDishStats("2026-05-17");
    }

    @Test
    void shouldDelegateCustomerMealSummary() {
        AgentCustomerInsightRequest request = new AgentCustomerInsightRequest();
        request.setCustomerCode("B3303");
        AgentCustomerMealSummaryResponse summary = new AgentCustomerMealSummaryResponse();
        summary.setCustomerCode("B3303");
        summary.setPresent(true);
        when(customerInsightService.getMealSummary(request)).thenReturn(summary);

        ResponseEntity<AgentCustomerMealSummaryResponse> response =
                controller.getCustomerMealSummary("rid-1", INTERNAL_TOKEN, request);

        assertNotNull(response.getBody());
        assertEquals("B3303", response.getBody().getCustomerCode());
        verify(customerInsightService).getMealSummary(request);
    }

    @Test
    void shouldDelegateCustomerVerificationSummary() {
        AgentCustomerInsightRequest request = new AgentCustomerInsightRequest();
        request.setCustomerCode("B3303");
        AgentCustomerVerificationSummaryResponse summary = new AgentCustomerVerificationSummaryResponse();
        summary.setCustomerCode("B3303");
        when(customerInsightService.getVerificationSummary(request)).thenReturn(summary);

        ResponseEntity<AgentCustomerVerificationSummaryResponse> response =
                controller.getCustomerVerificationSummary("rid-1", INTERNAL_TOKEN, request);

        assertNotNull(response.getBody());
        assertEquals("B3303", response.getBody().getCustomerCode());
        verify(customerInsightService).getVerificationSummary(request);
    }

    @Test
    void shouldDelegateCustomerOrderSummary() {
        AgentCustomerInsightRequest request = new AgentCustomerInsightRequest();
        request.setCustomerCode("B3303");
        AgentCustomerOrderSummaryResponse summary = new AgentCustomerOrderSummaryResponse();
        summary.setCustomerCode("B3303");
        when(customerInsightService.getOrderSummary(request)).thenReturn(summary);

        ResponseEntity<AgentCustomerOrderSummaryResponse> response =
                controller.getCustomerOrderSummary("rid-1", INTERNAL_TOKEN, request);

        assertNotNull(response.getBody());
        assertEquals("B3303", response.getBody().getCustomerCode());
        verify(customerInsightService).getOrderSummary(request);
    }

    @Test
    void shouldRejectCustomerInsightWhenTokenInvalid() {
        AgentCustomerInsightRequest request = new AgentCustomerInsightRequest();
        request.setCustomerCode("B3303");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getCustomerMealSummary("rid-1", "wrong-token", request));

        assertEquals(403, exception.getStatus().value());
        verify(customerInsightService, never()).getMealSummary(any(AgentCustomerInsightRequest.class));
    }
}
