package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.DishIngredientRelation;
import me.zhengjie.modules.meal.domain.dto.DishIngredientDto;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.service.DishTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证生产菜品保存路径的配料列表空值语义和空用量参数，不模拟后端识别。 */
@ExtendWith(MockitoExtension.class)
class DishServiceImplIngredientUpdateTest {
    @Mock private DishMapper dishMapper;
    @Mock private DishIngredientMapper dishIngredientMapper;
    @Mock private DishTagService dishTagService;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private DishServiceImpl service;
    private Dish stored;

    @BeforeEach
    void setUp() {
        stored = new Dish();
        stored.setId(9);
        stored.setName("菜品");
        stored.setIngredients("胡萝卜");
    }

    @Test
    void nullListKeepsRelationsAndRebuildsDisplayFromExistingDictionaryNames() {
        when(dishMapper.selectByIdForUpdate(9)).thenReturn(stored);
        DishIngredientRelation existing = new DishIngredientRelation();
        existing.setIngredientId(1);
        existing.setIngredientName("胡萝卜");
        existing.setQuantity(35.0);
        existing.setRemark("已有备注");
        when(dishIngredientMapper.findRelationsByDishId(9)).thenReturn(Collections.singletonList(existing));
        Dish update = update(null);

        service.update(update);

        assertEquals("胡萝卜", stored.getIngredients());
        verify(dishIngredientMapper).findRelationsByDishId(9);
        verifyNoMoreInteractions(dishIngredientMapper);
        verify(dishMapper).updateById(stored);
        verifyNoInteractions(events, dishTagService);
    }

    @Test
    void explicitEmptyListClearsRelationsAndWritesNonNullEmptyDisplay() {
        when(dishMapper.selectByIdForUpdate(9)).thenReturn(stored);

        service.update(update(Collections.emptyList()));

        assertEquals("", stored.getIngredients());
        verify(dishIngredientMapper).deleteRelationsByDishId(9);
        verifyNoMoreInteractions(dishIngredientMapper);
        verify(dishMapper).updateById(stored);
        verifyNoInteractions(events, dishTagService);
    }

    @Test
    void nonEmptyListReplacesRelationsAndPreservesQuantityAndRemark() {
        when(dishMapper.selectByIdForUpdate(9)).thenReturn(stored);
        DishIngredientDto row = row();
        row.setQuantity(35.0);
        row.setRemark("少盐");

        service.update(update(Collections.singletonList(row)));

        assertEquals("生姜", stored.getIngredients());
        verify(dishIngredientMapper).deleteRelationsByDishId(9);
        verify(dishIngredientMapper).insertRelation(9, row);
        assertEquals(35.0, row.getQuantity());
        assertEquals("少盐", row.getRemark());
        verifyNoMoreInteractions(dishIngredientMapper);
    }

    @Test
    void createAndUpdatePassNullQuantityToPersistentMapperWithoutInventingAmount() {
        DishIngredientDto row = row();
        Dish create = new Dish();
        create.setName("生姜汤");
        create.setIngredientList(Collections.singletonList(row));
        doAnswer(invocation -> {
            ((Dish) invocation.getArgument(0)).setId(10);
            return 1;
        }).when(dishMapper).insert(any(Dish.class));

        service.create(create);

        assertEquals("生姜", create.getIngredients());
        verify(dishIngredientMapper).insertRelation(10, row);
        assertNull(row.getQuantity());
        when(dishMapper.selectByIdForUpdate(9)).thenReturn(stored);
        service.update(update(Collections.singletonList(row)));
        verify(dishIngredientMapper).insertRelation(9, row);
        assertNull(row.getQuantity());
    }

    private Dish update(java.util.List<DishIngredientDto> rows) {
        Dish update = new Dish();
        update.setId(9);
        update.setIngredientList(rows);
        update.setCookingMethod("制作流程仍可保留配料名称");
        return update;
    }

    private DishIngredientDto row() {
        DishIngredientDto row = new DishIngredientDto();
        row.setIngredientId(2);
        row.setIngredientName("生姜");
        row.setUnit("g");
        row.setRemark("");
        return row;
    }
}
