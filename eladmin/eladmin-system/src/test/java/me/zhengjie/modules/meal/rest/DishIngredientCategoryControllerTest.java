package me.zhengjie.modules.meal.rest;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.exception.handler.GlobalExceptionHandler;
import me.zhengjie.modules.meal.domain.DishIngredientCategory;
import me.zhengjie.modules.meal.domain.dto.DishIngredientCategoryCreateDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientCategoryUpdateDto;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DishIngredientCategoryControllerTest {

    private DishIngredientCategoryService categoryService;
    private DishIngredientCategoryController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        categoryService = mock(DishIngredientCategoryService.class);
        controller = new DishIngredientCategoryController(categoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void createCategory_returnsCreatedCategory() {
        DishIngredientCategoryCreateDto request = new DishIngredientCategoryCreateDto();
        request.setName("蔬菜");
        request.setLevel(1);
        DishIngredientCategory created = new DishIngredientCategory();
        created.setId(11);
        created.setName("蔬菜");
        when(categoryService.createCategory(request)).thenReturn(created);

        org.springframework.http.ResponseEntity<DishIngredientCategory> response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(11, response.getBody().getId());
        verify(categoryService).createCategory(request);
    }

    @Test
    void updateCategory_returnsNoContentAndDelegatesIdAndRequest() {
        DishIngredientCategoryUpdateDto request = new DishIngredientCategoryUpdateDto();
        request.setName("绿叶菜");

        org.springframework.http.ResponseEntity<Object> response = controller.update(11, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(categoryService).updateCategory(11, request);
    }

    @Test
    void deleteCategory_callsServiceAndReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/dish-ingredient-categories/{id}", 1))
            .andExpect(status().isOk());

        verify(categoryService).delete(1);
    }

    @Test
    void deleteCategory_returnsBadRequestWhenServiceRejectsDeletion() throws Exception {
        doThrow(new BadRequestException("一级分类下存在二级分类，无法删除"))
            .when(categoryService).delete(1);

        mockMvc.perform(delete("/api/dish-ingredient-categories/{id}", 1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("一级分类下存在二级分类，无法删除"));
    }
}
