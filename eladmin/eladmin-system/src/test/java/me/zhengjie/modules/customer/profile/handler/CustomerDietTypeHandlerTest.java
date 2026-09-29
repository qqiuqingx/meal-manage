package me.zhengjie.modules.customer.profile.handler;

import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerDietTypeHandlerTest {

    private final CustomerDietItemsTypeHandler itemsHandler = new CustomerDietItemsTypeHandler();
    private final CustomerDietRawBlocksTypeHandler rawHandler = new CustomerDietRawBlocksTypeHandler();

    @Test
    void shouldRoundTripTypedItemsAndKeepNameSnapshots() {
        CustomerDietItemDto item = new CustomerDietItemDto();
        item.setType("INGREDIENT_CATEGORY");
        item.setId(12L);
        item.setName("牛肉");

        List<CustomerDietItemDto> result = itemsHandler.parse(itemsHandler.toJson(Arrays.asList(item)));

        assertEquals(1, result.size());
        assertEquals("INGREDIENT_CATEGORY", result.get(0).getType());
        assertEquals(12L, result.get(0).getId());
        assertEquals("牛肉", result.get(0).getName());
    }

    @Test
    void shouldRoundTripRawBlocksWithoutChangingPunctuationOrNewlines() {
        List<String> source = Arrays.asList("不吃香菜，芹菜\n葱少量调味", "鱼类（除鲈鱼外）");

        assertEquals(source, rawHandler.parse(rawHandler.toJson(source)));
    }

    @Test
    void shouldRejectNonArrayJson() {
        assertThrows(IllegalArgumentException.class, () -> itemsHandler.parse("{\"type\":\"DISH\"}"));
        assertThrows(IllegalArgumentException.class, () -> rawHandler.parse("\"原文\""));
    }
}
