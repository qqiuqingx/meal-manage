package me.zhengjie.modules.customer.profile.handler;

import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExcludedDateListTypeHandlerTest {

    private final ExcludedDateListTypeHandler handler = new ExcludedDateListTypeHandler();

    @Test
    void shouldParseJsonAsExcludedDateDtoList() {
        String json = "[{\"date\":\"2026-04-15\",\"mealTypes\":[\"LUNCH\",\"DINNER\"]}]";

        List<ExcludedDateDto> result = handler.parse(json);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("2026-04-15", result.get(0).getDate());
        assertEquals(Arrays.asList("LUNCH", "DINNER"), result.get(0).getMealTypes());
    }

    @Test
    void shouldSerializeExcludedDateDtoListToJson() {
        ExcludedDateDto dto = new ExcludedDateDto();
        dto.setDate("2026-04-16");
        dto.setMealTypes(Arrays.asList("BREAKFAST"));

        String json = handler.toJson(Arrays.asList(dto));

        assertEquals("[{\"date\":\"2026-04-16\",\"mealTypes\":[\"BREAKFAST\"]}]", json);
    }
}
