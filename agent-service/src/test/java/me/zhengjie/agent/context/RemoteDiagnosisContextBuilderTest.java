package me.zhengjie.agent.context;

import me.zhengjie.agent.client.DiagnosisContextClient;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RemoteDiagnosisContextBuilderTest {

    @Test
    void shouldUseRemoteContextWhenAvailable() {
        DiagnosisContextClient client = request -> {
            DiagnosisContextDto context = new DiagnosisContextDto();
            context.setCustomerId(request.getCustomerId());
            context.setCustomerName("张三");
            context.setRecordDate(request.getRecordDate());
            context.setMealType(request.getMealType());
            context.setCustomerProfile(Map.of("excludeDates", List.of(Map.of("date", request.getRecordDate(), "mealTypes", List.of(request.getMealType())))));
            return context;
        };
        DefaultDiagnosisContextBuilder fallback = new DefaultDiagnosisContextBuilder();
        RemoteDiagnosisContextBuilder builder = new RemoteDiagnosisContextBuilder(client, fallback);

        DiagnosisRequest request = new DiagnosisRequest();
        request.setCustomerId(1001L);
        request.setRecordDate("2026-05-17");
        request.setMealType("LUNCH");

        DiagnosisContextDto context = builder.build(request);

        assertNotNull(context);
        assertEquals(1001L, context.getCustomerId());
        assertEquals("张三", context.getCustomerName());
        assertEquals(1, ((List<?>) context.getCustomerProfile().get("excludeDates")).size());
    }
}
