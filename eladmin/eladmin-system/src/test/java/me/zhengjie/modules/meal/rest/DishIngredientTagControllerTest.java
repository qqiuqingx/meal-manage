package me.zhengjie.modules.meal.rest;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.exception.handler.GlobalExceptionHandler;
import me.zhengjie.modules.meal.domain.DishIngredientTag;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishIngredientTagSaveDto;
import me.zhengjie.modules.meal.service.DishIngredientTagService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DishIngredientTagControllerTest {

    private DishIngredientTagService tagService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tagService = mock(DishIngredientTagService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DishIngredientTagController(tagService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void queryReturnsContentAndTotal() throws Exception {
        DishIngredientTag tag = new DishIngredientTag();
        tag.setId(3);
        tag.setName("清真");
        when(tagService.query(any(DishIngredientTagQueryCriteria.class)))
            .thenReturn(new PageResult<>(Collections.singletonList(tag), 1));

        mockMvc.perform(get("/api/dish-ingredient-tags").param("name", "清").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(3))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createReturnsCreatedTag() throws Exception {
        DishIngredientTag tag = new DishIngredientTag();
        tag.setId(8);
        tag.setName("无麸质");
        when(tagService.create(any(DishIngredientTagSaveDto.class))).thenReturn(tag);

        mockMvc.perform(post("/api/dish-ingredient-tags")
                .contentType("application/json")
                .content("{\"name\":\"无麸质\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(8))
            .andExpect(jsonPath("$.name").value("无麸质"));
    }

    @Test
    void deleteReturnsBadRequestForTagInUse() throws Exception {
        doThrow(new BadRequestException("标签正在被配料使用，无法删除"))
            .when(tagService).delete(3);

        mockMvc.perform(delete("/api/dish-ingredient-tags/3"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("标签正在被配料使用，无法删除"));

        verify(tagService).delete(3);
    }
}
