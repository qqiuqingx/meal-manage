package me.zhengjie.modules.agent.rest;

import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextDto;
import me.zhengjie.modules.agent.domain.dto.MealPlanDiagnosisContextRequest;
import me.zhengjie.modules.agent.service.AgentDiagnosisContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

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

        ResponseEntity<MealPlanDiagnosisContextDto> response = controller.buildContext(request);

        assertNotNull(response.getBody());
        assertEquals(1001L, response.getBody().getCustomerId());
        verify(contextService).buildContext(request);
    }
}
