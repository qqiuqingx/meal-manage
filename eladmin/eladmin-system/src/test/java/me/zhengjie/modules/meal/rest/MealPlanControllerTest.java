package me.zhengjie.modules.meal.rest;

import me.zhengjie.annotation.Limit;
import me.zhengjie.aspect.LimitType;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateRequest;
import me.zhengjie.modules.meal.domain.dto.MealPlanGenerateResult;
import me.zhengjie.modules.meal.service.MealPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealPlanControllerTest {

    @Mock
    private MealPlanService mealPlanService;

    @InjectMocks
    private MealPlanController mealPlanController;

    @Test
    void shouldDelegateGenerateMealPlan() {
        MealPlanGenerateRequest request = new MealPlanGenerateRequest();
        request.setRecordDate("2026-04-01");
        request.setMealType("LUNCH");

        MealPlanGenerateResult result = new MealPlanGenerateResult();
        result.setMealPlanId(100L);
        when(mealPlanService.generateMealPlan("2026-04-01", "LUNCH", null, null, null)).thenReturn(result);

        ResponseEntity<MealPlanGenerateResult> response = mealPlanController.generateMealPlan(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(100L, response.getBody().getMealPlanId());
        verify(mealPlanService).generateMealPlan("2026-04-01", "LUNCH", null, null, null);
    }

    @Test
    void shouldDelegateGenerateMealPlanWithMenuWeekAndDay() {
        MealPlanGenerateRequest request = new MealPlanGenerateRequest();
        request.setRecordDate("2026-04-01");
        request.setMealType("DINNER");
        request.setCustomerId(88L);
        request.setMenuWeekNum(2);
        request.setMenuDayOfWeek(4);

        MealPlanGenerateResult result = new MealPlanGenerateResult();
        result.setMealPlanId(101L);
        when(mealPlanService.generateMealPlan("2026-04-01", "DINNER", 88L, 2, 4)).thenReturn(result);

        ResponseEntity<MealPlanGenerateResult> response = mealPlanController.generateMealPlan(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(101L, response.getBody().getMealPlanId());
        verify(mealPlanService).generateMealPlan("2026-04-01", "DINNER", 88L, 2, 4);
    }

    @Test
    void shouldDeclareGenerateRateLimit() throws NoSuchMethodException {
        Method method = MealPlanController.class.getMethod("generateMealPlan", MealPlanGenerateRequest.class);

        Limit limit = method.getAnnotation(Limit.class);
        assertNotNull(limit);
        assertEquals("generate", limit.key());
        assertEquals("mealPlan", limit.prefix());
        assertEquals(LimitType.IP, limit.limitType());
    }
}
