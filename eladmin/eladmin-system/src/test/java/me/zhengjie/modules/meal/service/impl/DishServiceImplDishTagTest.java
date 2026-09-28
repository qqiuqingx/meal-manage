package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.mapper.CustomerDietaryRestrictionsMapper;
import me.zhengjie.modules.meal.mapper.CustomerMenuRecordMapper;
import me.zhengjie.modules.meal.mapper.DishIngredientMapper;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.DishScheduleRecordMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.mapper.MealSchedulePlanMapper;
import me.zhengjie.modules.meal.service.CustomerMenuRecordService;
import me.zhengjie.modules.meal.service.DishTagService;
import me.zhengjie.modules.system.service.DictDetailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DishServiceImplDishTagTest {

    @Mock private DishMapper dishMapper;
    @Mock private CustomerDietaryRestrictionsMapper customerDietaryRestrictionsMapper;
    @Mock private DishScheduleRecordMapper dishScheduleRecordMapper;
    @Mock private CustomerMenuRecordMapper customerMenuRecordMapper;
    @Mock private CustomerMenuRecordService customerMenuRecordService;
    @Mock private DishIngredientMapper dishIngredientMapper;
    @Mock private DictDetailService dictDetailService;
    @Mock private CustomerOrderMapper customerOrderMapper;
    @Mock private ParentPackageMapper parentPackageMapper;
    @Mock private CustomerProfileMapper customerProfileMapper;
    @Mock private MealPlanMapper mealPlanMapper;
    @Mock private MealPlanCustomerMapper mealPlanCustomerMapper;
    @Mock private MealSchedulePlanMapper mealSchedulePlanMapper;
    @Mock private DishTagService dishTagService;

    @InjectMocks
    private DishServiceImpl service;

    @Test
    void updateWithoutTagIdsKeepsExistingRelations() {
        Dish stored = new Dish();
        stored.setId(9);
        stored.setName("原菜品");
        stored.setDishType("MAIN");
        when(dishMapper.selectByIdForUpdate(9)).thenReturn(stored);
        when(dishIngredientMapper.findRelationsByDishId(9)).thenReturn(Collections.emptyList());
        Dish update = new Dish();
        update.setId(9);
        update.setName("改名");

        service.update(update);

        verify(dishMapper).selectByIdForUpdate(9);
        verify(dishMapper).updateById(stored);
        verifyNoInteractions(dishTagService);
    }

    @Test
    void updateWithEmptyTagIdsClearsRelations() {
        Dish stored = new Dish();
        stored.setId(9);
        stored.setName("菜品");
        stored.setDishType("MAIN");
        when(dishMapper.selectByIdForUpdate(9)).thenReturn(stored);
        when(dishIngredientMapper.findRelationsByDishId(9)).thenReturn(Collections.emptyList());
        Dish update = new Dish();
        update.setId(9);
        update.setTagIds(Collections.emptyList());

        service.update(update);

        verify(dishTagService).replaceDishTags(9, Collections.emptyList());
    }

    @Test
    void deleteLocksDishesInAscendingOrderAndClearsOnlyTheirRelations() {
        service.deleteAll(Arrays.asList(9, 3, 9));

        InOrder order = inOrder(dishMapper, dishIngredientMapper, dishTagService);
        order.verify(dishMapper).selectByIdForUpdate(3);
        order.verify(dishMapper).selectByIdForUpdate(9);
        order.verify(dishIngredientMapper).deleteRelationsByDishId(3);
        order.verify(dishIngredientMapper).deleteRelationsByDishId(9);
        order.verify(dishTagService).deleteDishRelations(Arrays.asList(3, 9));
        order.verify(dishMapper).deleteBatchIds(Arrays.asList(3, 9));
    }
}
