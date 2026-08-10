package me.zhengjie.modules.meal.service.impl;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.meal.domain.Dish;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.domain.MealPlanManualReplace;
import me.zhengjie.modules.meal.domain.dto.MealPlanManualReplaceSaveRequest;
import me.zhengjie.modules.meal.domain.dto.MealPlanManualReplaceVO;
import me.zhengjie.modules.meal.mapper.DishMapper;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanManualReplaceMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealPlanManualReplaceServiceImplTest {

    @Mock
    private MealPlanMapper mealPlanMapper;
    @Mock
    private MealPlanCustomerMapper mealPlanCustomerMapper;
    @Mock
    private MealPlanManualReplaceMapper mealPlanManualReplaceMapper;
    @Mock
    private DishMapper dishMapper;

    @InjectMocks
    private MealPlanManualReplaceServiceImpl service;

    @Test
    void saveManualReplaces_rejectsMissingMealPlan() {
        when(mealPlanMapper.selectById(100L)).thenReturn(null);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.saveManualReplaces(100L, request(item(401, "MAIN", 201L))));

        assertEquals("排餐计划不存在或已删除", ex.getMessage());
        verify(mealPlanManualReplaceMapper, never()).insert(any(MealPlanManualReplace.class));
    }

    @Test
    void saveManualReplaces_rejectsDeletedMealPlan() {
        MealPlan mealPlan = new MealPlan();
        mealPlan.setId(100L);
        mealPlan.setDeleted(true);
        when(mealPlanMapper.selectById(100L)).thenReturn(mealPlan);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.saveManualReplaces(100L, request(item(401, "MAIN", 201L))));

        assertEquals("排餐计划不存在或已删除", ex.getMessage());
    }

    @Test
    void saveManualReplaces_rejectsCustomerPlanOutsideMealPlan() {
        when(mealPlanMapper.selectById(100L)).thenReturn(activeMealPlan());
        when(mealPlanCustomerMapper.selectByMealPlanId(100L))
                .thenReturn(Collections.singletonList(customerPlan(201L, 301L, "C001", "张三")));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.saveManualReplaces(100L, request(item(401, "MAIN", 999L))));

        assertEquals("客户排餐记录ID 999 不属于当前排餐单", ex.getMessage());
        verify(dishMapper, never()).selectById(401);
    }

    @Test
    void saveManualReplaces_rejectsMissingDish() {
        when(mealPlanMapper.selectById(100L)).thenReturn(activeMealPlan());
        when(mealPlanCustomerMapper.selectByMealPlanId(100L))
                .thenReturn(Collections.singletonList(customerPlan(201L, 301L, "C001", "张三")));
        when(dishMapper.selectById(401)).thenReturn(null);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.saveManualReplaces(100L, request(item(401, "MAIN", 201L))));

        assertEquals("菜品ID 401 不存在", ex.getMessage());
    }

    @Test
    void saveManualReplaces_rejectsDisabledDish() {
        when(mealPlanMapper.selectById(100L)).thenReturn(activeMealPlan());
        when(mealPlanCustomerMapper.selectByMealPlanId(100L))
                .thenReturn(Collections.singletonList(customerPlan(201L, 301L, "C001", "张三")));
        when(dishMapper.selectById(401)).thenReturn(dish(401, "红烧肉", false));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.saveManualReplaces(100L, request(item(401, "MAIN", 201L))));

        assertEquals("菜品「红烧肉」已停用，无法作为换菜菜品", ex.getMessage());
    }

    @Test
    void saveManualReplaces_deduplicatesSameCustomerPlanAndDish() {
        when(mealPlanMapper.selectById(100L)).thenReturn(activeMealPlan());
        when(mealPlanCustomerMapper.selectByMealPlanId(100L))
                .thenReturn(Collections.singletonList(customerPlan(201L, 301L, "C001", "张三")));
        when(dishMapper.selectById(401)).thenReturn(dish(401, "清蒸鱼", true));

        service.saveManualReplaces(100L, request(item(401, "MAIN", 201L, 201L)));

        InOrder inOrder = inOrder(mealPlanManualReplaceMapper);
        inOrder.verify(mealPlanManualReplaceMapper).deleteHistoryByMealPlanId(100L);
        inOrder.verify(mealPlanManualReplaceMapper).softDeleteByMealPlanId(100L);

        ArgumentCaptor<MealPlanManualReplace> captor = ArgumentCaptor.forClass(MealPlanManualReplace.class);
        verify(mealPlanManualReplaceMapper).insert(captor.capture());
        MealPlanManualReplace saved = captor.getValue();
        assertEquals(100L, saved.getMealPlanId());
        assertEquals(201L, saved.getCustomerPlanId());
        assertEquals(301L, saved.getCustomerId());
        assertEquals("C001", saved.getCustomerCode());
        assertEquals("张三", saved.getCustomerName());
        assertEquals(Integer.valueOf(401), saved.getDishId());
        assertEquals("清蒸鱼", saved.getDishName());
        assertEquals("MAIN", saved.getDishType());
        assertEquals(false, saved.getDeleted());
    }

    @Test
    void saveManualReplaces_withEmptyItemsOnlyClearsExistingRelations() {
        when(mealPlanMapper.selectById(100L)).thenReturn(activeMealPlan());
        when(mealPlanCustomerMapper.selectByMealPlanId(100L)).thenReturn(Collections.emptyList());

        service.saveManualReplaces(100L, request());

        verify(mealPlanManualReplaceMapper).deleteHistoryByMealPlanId(100L);
        verify(mealPlanManualReplaceMapper).softDeleteByMealPlanId(100L);
        verify(mealPlanManualReplaceMapper, never()).insert(any(MealPlanManualReplace.class));
    }

    @Test
    void queryByMealPlanId_mapsEntityToVo() {
        MealPlanManualReplace entity = new MealPlanManualReplace();
        entity.setId(1L);
        entity.setMealPlanId(100L);
        entity.setCustomerPlanId(201L);
        entity.setCustomerId(301L);
        entity.setCustomerCode("C001");
        entity.setCustomerName("张三");
        entity.setDishId(401);
        entity.setDishName("清蒸鱼");
        entity.setDishType("MAIN");
        when(mealPlanManualReplaceMapper.selectByMealPlanId(100L)).thenReturn(Collections.singletonList(entity));

        List<MealPlanManualReplaceVO> result = service.queryByMealPlanId(100L);

        assertEquals(1, result.size());
        MealPlanManualReplaceVO vo = result.get(0);
        assertEquals(1L, vo.getId());
        assertEquals(100L, vo.getMealPlanId());
        assertEquals(201L, vo.getCustomerPlanId());
        assertEquals(301L, vo.getCustomerId());
        assertEquals("C001", vo.getCustomerCode());
        assertEquals("张三", vo.getCustomerName());
        assertEquals(Integer.valueOf(401), vo.getDishId());
        assertEquals("清蒸鱼", vo.getDishName());
        assertEquals("MAIN", vo.getDishType());
    }

    private MealPlan activeMealPlan() {
        MealPlan mealPlan = new MealPlan();
        mealPlan.setId(100L);
        mealPlan.setDeleted(false);
        return mealPlan;
    }

    private MealPlanCustomer customerPlan(Long id, Long customerId, String customerCode, String customerName) {
        MealPlanCustomer customer = new MealPlanCustomer();
        customer.setId(id);
        customer.setMealPlanId(100L);
        customer.setCustomerId(customerId);
        customer.setCustomerCode(customerCode);
        customer.setCustomerName(customerName);
        return customer;
    }

    private Dish dish(Integer id, String name, boolean enabled) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setName(name);
        dish.setEnabled(enabled);
        return dish;
    }

    private MealPlanManualReplaceSaveRequest request(MealPlanManualReplaceSaveRequest.ReplaceItem... items) {
        MealPlanManualReplaceSaveRequest request = new MealPlanManualReplaceSaveRequest();
        request.setItems(Arrays.asList(items));
        return request;
    }

    private MealPlanManualReplaceSaveRequest.ReplaceItem item(Integer dishId, String dishType, Long... customerPlanIds) {
        MealPlanManualReplaceSaveRequest.ReplaceItem item = new MealPlanManualReplaceSaveRequest.ReplaceItem();
        item.setDishId(dishId);
        item.setDishType(dishType);
        item.setCustomerPlanIds(Arrays.asList(customerPlanIds));
        return item;
    }
}
