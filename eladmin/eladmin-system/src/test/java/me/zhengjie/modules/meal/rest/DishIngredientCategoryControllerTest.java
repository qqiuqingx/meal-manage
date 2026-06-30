package me.zhengjie.modules.meal.rest;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.exception.handler.GlobalExceptionHandler;
import me.zhengjie.modules.meal.service.DishIngredientCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DishIngredientCategoryControllerTest {

    private DishIngredientCategoryService categoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        categoryService = mock(DishIngredientCategoryService.class);
        DishIngredientCategoryController controller = new DishIngredientCategoryController(categoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
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
