package me.zhengjie.modules.meal.rest;

import me.zhengjie.exception.handler.GlobalExceptionHandler;
import me.zhengjie.modules.meal.domain.DishTag;
import me.zhengjie.modules.meal.domain.dto.DishTagQueryCriteria;
import me.zhengjie.modules.meal.domain.dto.DishTagSaveDto;
import me.zhengjie.modules.meal.service.DishTagService;
import me.zhengjie.utils.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DishTagControllerTest {

    private DishTagService tagService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tagService = mock(DishTagService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DishTagController(tagService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void queryReturnsContentAndTotal() throws Exception {
        DishTag tag = new DishTag();
        tag.setId(3);
        tag.setName("清真");
        when(tagService.query(any(DishTagQueryCriteria.class)))
            .thenReturn(new PageResult<>(Collections.singletonList(tag), 1));

        mockMvc.perform(get("/api/dish-tags").param("name", "清").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(3))
            .andExpect(jsonPath("$.content[0].name").value("清真"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createReturnsCreatedTag() throws Exception {
        DishTag tag = new DishTag();
        tag.setId(8);
        tag.setName("低盐");
        when(tagService.create(any(DishTagSaveDto.class))).thenReturn(tag);

        mockMvc.perform(post("/api/dish-tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"低盐\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(8))
            .andExpect(jsonPath("$.name").value("低盐"));
    }

    @Test
    void updateReturnsNoContent() throws Exception {
        mockMvc.perform(put("/api/dish-tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":8,\"name\":\"低盐\"}"))
            .andExpect(status().isNoContent());

        verify(tagService).update(any(DishTagSaveDto.class));
    }

    @Test
    void deleteReturnsBusinessErrorForReferencedTag() throws Exception {
        doThrow(new me.zhengjie.exception.BadRequestException("标签正在被菜品使用，请先从菜品中移除并保存关联后再删除"))
            .when(tagService).delete(8);

        mockMvc.perform(delete("/api/dish-tags/8"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("标签正在被菜品使用，请先从菜品中移除并保存关联后再删除"));

        verify(tagService).delete(8);
    }

    @Test
    void writeEndpointsRequireDedicatedPermissions() throws Exception {
        assertPermission("create", DishTagSaveDto.class, "@el.check('dishTag:add')");
        assertPermission("update", DishTagSaveDto.class, "@el.check('dishTag:edit')");
        assertPermission("delete", Integer.class, "@el.check('dishTag:del')");
    }

    private void assertPermission(String methodName, Class<?> parameterType, String expected) throws Exception {
        PreAuthorize permission = DishTagController.class.getMethod(methodName, parameterType)
            .getAnnotation(PreAuthorize.class);
        org.junit.jupiter.api.Assertions.assertEquals(expected, permission.value());
    }
}
