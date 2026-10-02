package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import me.zhengjie.modules.customer.profile.service.CustomerDietMatchService;
import me.zhengjie.modules.meal.domain.DishIngredient;
import me.zhengjie.modules.meal.domain.dto.DishIngredientDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientQueryCriteria;
import me.zhengjie.modules.meal.service.DishIngredientService;
import me.zhengjie.modules.meal.util.DietDictionarySegmenter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** mock 仅替换字典读取；识别与客户回归均使用真实的生产 HanLP 分词器。 */
class DishIngredientRecognitionServiceTest {
    private DishIngredientService ingredients;
    private DishIngredientRecognitionService service;

    @BeforeEach
    void setUp() {
        ingredients = mock(DishIngredientService.class);
        service = new DishIngredientRecognitionService(ingredients);
    }

    @Test
    void keepsWholeNamesAndFirstOccurrenceOrderWithoutQuantities() {
        dictionary(ingredient(1, "胡萝卜"), ingredient(2, "萝卜"), ingredient(3, "生姜"));
        List<DishIngredientDto> result = service.recognize("胡萝卜切丁，加入生姜，胡萝卜炒熟");
        assertEquals(Arrays.asList(1, 3), ids(result));
        assertEquals(Arrays.asList(1, 3, 2), ids(service.recognize("胡萝卜切丁，加入生姜，萝卜炒熟")));
        for (DishIngredientDto dto : result) {
            assertNull(dto.getQuantity());
            assertEquals("g", dto.getUnit());
            assertEquals("", dto.getRemark());
        }
        verify(ingredients, times(2)).queryAll(argThat(c -> Boolean.TRUE.equals(c.getEnabled())));
        verifyNoMoreInteractions(ingredients);
    }

    @Test
    void recognizesJoinedSeasoningsButDoesNotGuessAliasesOrRecipeSemantics() {
        dictionary(ingredient(1, "葱"), ingredient(2, "姜"), ingredient(3, "蒜"), ingredient(4, "盐"));
        assertEquals(Arrays.asList(1, 2, 3, 4), ids(service.recognize("葱姜蒜切碎，加盐")));
        assertEquals(Arrays.asList(2, 3), ids(service.recognize("不放姜，姜/蒜任选")));
        dictionary(ingredient(5, "生姜"));
        assertTrue(service.recognize("加姜、海鲜、荤菜和未知食物").isEmpty());
    }

    @Test
    void normalizesDictionaryAndTextAndReturnsOriginalNames() {
        dictionary(ingredient(1, " Ａ盐\u200B "), ingredient(2, "胡萝卜"));
        List<DishIngredientDto> result = service.recognize("　A盐，胡萝\u200B卜，Ａ盐　");
        assertEquals(Arrays.asList(1, 2), ids(result));
        assertEquals(" Ａ盐\u200B ", result.get(0).getIngredientName());
    }

    @Test
    void ignoresDisabledInvalidAndEmptyEntriesAndDeduplicatesById() {
        DishIngredient disabled = ingredient(8, "蒜");
        disabled.setEnabled(false);
        dictionary(disabled, ingredient(1, "葱"), ingredient(1, "葱"),
                ingredient(2, "　\u200B"), ingredient(null, "姜"));
        assertEquals(Collections.singletonList(1), ids(service.recognize("葱姜蒜")));
    }

    @Test
    void emptyTextDoesNotReadDictionaryAndEmptyDictionaryReturnsEmpty() {
        for (String text : Arrays.asList(null, "", " \t\n　\u200B")) {
            assertTrue(service.recognize(text).isEmpty());
        }
        verifyNoInteractions(ingredients);
        dictionary();
        assertTrue(service.recognize("胡萝卜").isEmpty());
        assertFalse(DietDictionarySegmenter.create(Collections.emptyList()).seg("胡萝卜").isEmpty());
    }

    @Test
    void concurrentRequestsAndCustomerMatchingKeepIndependentDictionaries() throws Exception {
        dictionary(ingredient(1, "花椒油"), ingredient(2, "花椒"));
        DishIngredientService otherIngredients = mock(DishIngredientService.class);
        when(otherIngredients.queryAll(any(DishIngredientQueryCriteria.class)))
                .thenReturn(Collections.singletonList(ingredient(2, "花椒")));
        DishIngredientRecognitionService other = new DishIngredientRecognitionService(otherIngredients);
        CustomerDietOptionDto seasoning = new CustomerDietOptionDto();
        seasoning.setId(1L);
        seasoning.setName("花椒油");
        seasoning.setType("INGREDIENT");
        seasoning.setIgnoreDietMatch(true);
        CustomerDietMatchService customer = new CustomerDietMatchService();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> first = executor.submit(() -> {
                for (int i = 0; i < 10; i++) {
                    assertEquals(Collections.singletonList(1), ids(service.recognize("花椒油炒熟")));
                }
            });
            Future<?> second = executor.submit(() -> {
                for (int i = 0; i < 10; i++) {
                    assertEquals(Collections.singletonList(2), ids(other.recognize("花椒切碎")));
                }
            });
            Future<?> third = executor.submit(() -> assertTrue(customer.matchRestrictions(
                    Collections.singletonList("不吃花椒油"), customer.snapshot(Collections.singletonList(seasoning))).isEmpty()));
            first.get();
            second.get();
            third.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private void dictionary(DishIngredient... entries) {
        when(ingredients.queryAll(any(DishIngredientQueryCriteria.class))).thenReturn(Arrays.asList(entries));
    }

    private DishIngredient ingredient(Integer id, String name) {
        DishIngredient ingredient = new DishIngredient();
        ingredient.setId(id);
        ingredient.setName(name);
        ingredient.setEnabled(true);
        ingredient.setUnit("g");
        ingredient.setRemark("不可复制字典备注");
        return ingredient;
    }

    private List<Integer> ids(List<DishIngredientDto> result) {
        return result.stream().map(DishIngredientDto::getIngredientId).collect(Collectors.toList());
    }
}
