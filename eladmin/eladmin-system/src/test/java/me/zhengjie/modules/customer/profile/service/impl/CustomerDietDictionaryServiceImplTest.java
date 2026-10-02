package me.zhengjie.modules.customer.profile.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietItemDto;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import me.zhengjie.modules.customer.profile.mapper.CustomerDietDictionaryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CustomerDietDictionaryServiceImplTest {

    @Mock
    private CustomerDietDictionaryMapper dictionaryMapper;

    @InjectMocks
    private CustomerDietDictionaryServiceImpl service;

    @Test
    void shouldAllowManualSelectionOfIgnoredIngredientsAndCategories() {
        CustomerDietOptionDto ingredient = option("INGREDIENT", 80L, "花椒油");
        CustomerDietOptionDto category = option("INGREDIENT_CATEGORY", 81L, "调料");
        ingredient.setIgnoreDietMatch(true);
        category.setIgnoreDietMatch(true);
        when(dictionaryMapper.selectActiveOptions()).thenReturn(Arrays.asList(ingredient, category));

        List<CustomerDietOptionDto> active = service.listActiveOptions();
        assertEquals(2, active.size());
        List<CustomerDietItemDto> result = service.normalizeSelections(Arrays.asList(
                item("INGREDIENT", 80L, "客户端名称"), item("INGREDIENT_CATEGORY", 81L, "客户端分类")), null, active);
        assertEquals(Arrays.asList(item("INGREDIENT", 80L, "花椒油"), item("INGREDIENT_CATEGORY", 81L, "调料")), result);
        assertEquals(Collections.singletonList(item("INGREDIENT", 80L, "历史名称")), service.normalizeSelections(
                Collections.singletonList(item("INGREDIENT", 80L, "花椒油")),
                Collections.singletonList(item("INGREDIENT", 80L, "历史名称")), active));
    }

    @Test
    void shouldIgnoreClientNameDeduplicateAndPreserveExistingSnapshot() {
        CustomerDietItemDto existing = item("DISH", 8L, "历史菜名");
        List<CustomerDietOptionDto> active = Arrays.asList(option("DISH", 8L, "现名"), option("DISH_TAG", 8L, "清淡"));
        List<CustomerDietItemDto> request = Arrays.asList(
                item("DISH", 8L, "伪造名称"),
                item("DISH", 8L, "重复项"),
                item("DISH_TAG", 8L, "忽略此名称"));

        List<CustomerDietItemDto> result = service.normalizeSelections(request, Collections.singletonList(existing), active);

        assertEquals(2, result.size());
        assertEquals("历史菜名", result.get(0).getName());
        assertEquals("清淡", result.get(1).getName());
    }

    @Test
    void shouldRejectUnsupportedOrUnavailableNewReferences() {
        assertThrows(BadRequestException.class,
                () -> service.normalizeSelections(Collections.singletonList(item("UNKNOWN", 1L, "x")), null,
                        Collections.emptyList()));
        assertThrows(BadRequestException.class,
                () -> service.normalizeSelections(Collections.singletonList(item("DISH", 1L, "x")), null,
                        Collections.emptyList()));
        assertThrows(BadRequestException.class,
                () -> service.normalizeSelections(Collections.singletonList(item("DISH", 0L, "x")), null,
                        Collections.emptyList()));
    }

    @Test
    void shouldTreatNullAsUnsubmittedAndEmptyArrayAsClear() {
        assertTrue(service.normalizeSelections(Collections.emptyList(), null, Collections.emptyList()).isEmpty());
        assertEquals(null, service.normalizeSelections(null, null, Collections.emptyList()));
    }

    private CustomerDietItemDto item(String type, Long id, String name) {
        CustomerDietItemDto item = new CustomerDietItemDto();
        item.setType(type);
        item.setId(id);
        item.setName(name);
        return item;
    }

    private CustomerDietOptionDto option(String type, Long id, String name) {
        CustomerDietOptionDto option = new CustomerDietOptionDto();
        option.setType(type);
        option.setId(id);
        option.setName(name);
        return option;
    }
}
