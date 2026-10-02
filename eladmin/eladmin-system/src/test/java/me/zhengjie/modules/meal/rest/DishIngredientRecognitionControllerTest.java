package me.zhengjie.modules.meal.rest;

import cn.hutool.jwt.JWT;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.support.spring.http.converter.FastJsonHttpMessageConverter;
import me.zhengjie.config.AuthorityConfig;
import me.zhengjie.exception.handler.GlobalExceptionHandler;
import me.zhengjie.modules.customer.pkg.service.ParentPackageService;
import me.zhengjie.modules.meal.domain.dto.DishIngredientDto;
import me.zhengjie.modules.meal.domain.dto.DishIngredientRecognizeRequest;
import me.zhengjie.modules.meal.service.DishService;
import me.zhengjie.modules.meal.service.impl.DishIngredientRecognitionService;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.SpringBeanHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用生产权限评估器、方法安全代理、请求校验及 fastjson2 验证识别接口。 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DishIngredientRecognitionControllerTest.Config.class)
class DishIngredientRecognitionControllerTest {
    @Autowired private DishController controller;
    @Autowired private DishIngredientRecognitionService recognition;
    @Autowired private DishService dishes;
    @Autowired private ParentPackageService packages;
    @Autowired private UserDetailsService users;
    @Autowired private ApplicationContext context;
    private MockMvc mvc;
    private String previousHeader;
    private String previousPrefix;

    @BeforeEach
    void setUp() {
        reset(recognition, dishes, packages, users);
        new SpringBeanHolder().setApplicationContext(context);
        previousHeader = SecurityUtils.header;
        previousPrefix = SecurityUtils.tokenStartWith;
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        authenticate("dish:add");
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.APPLICATION_JSON));
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultRequest(post("/").header("Authorization", token()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter).build();
        authenticate("dish:add");
    }

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        SecurityUtils.header = previousHeader;
        SecurityUtils.tokenStartWith = previousPrefix;
        new SpringBeanHolder().destroy();
    }

    @Test
    void acceptsRequestAndReturnsReadOnlyCandidates() throws Exception {
        DishIngredientDto dto = new DishIngredientDto();
        dto.setIngredientId(1);
        dto.setIngredientName("胡萝卜");
        dto.setUnit("g");
        dto.setRemark("");
        when(recognition.recognize("胡萝卜切丁")).thenReturn(Collections.singletonList(dto));
        String response = mvc.perform(post("/api/dishes/recognize-ingredients")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cookingMethod\":\"胡萝卜切丁\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ingredientId").value(1))
                .andExpect(jsonPath("$[0].ingredientName").value("胡萝卜"))
                .andExpect(jsonPath("$[0].unit").value("g"))
                .andExpect(jsonPath("$[0].remark").value(""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNull(JSON.parseArray(response, DishIngredientDto.class).get(0).getQuantity());
        verify(recognition).recognize("胡萝卜切丁");
        verifyNoInteractions(dishes, packages);
    }

    @Test
    void acceptsNullEmptyAndLimitLengthButRejectsOversizeBeforeRecognition() throws Exception {
        when(recognition.recognize(any())).thenReturn(Collections.emptyList());
        for (String text : new String[]{null, "", " \t", String.join("", Collections.nCopies(10000, "姜"))}) {
            DishIngredientRecognizeRequest request = new DishIngredientRecognizeRequest();
            request.setCookingMethod(text);
            mvc.perform(post("/api/dishes/recognize-ingredients").contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.toJSONString(request))).andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        reset(recognition);
        DishIngredientRecognizeRequest request = new DishIngredientRecognizeRequest();
        request.setCookingMethod(String.join("", Collections.nCopies(10001, "姜")));
        mvc.perform(post("/api/dishes/recognize-ingredients").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(request))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("cookingMethod: 制作流程不能超过10000字符"));
        verifyNoInteractions(recognition, dishes, packages);
    }

    @Test
    void realPermissionEvaluatorAllowsEitherMaintenancePermissionAndAdminAndRejectsReadOnly() {
        when(recognition.recognize(any())).thenReturn(Collections.emptyList());
        for (String authority : new String[]{"dish:add", "dish:edit", "admin"}) {
            authenticate(authority);
            assertEquals(200, controller.recognizeIngredients(new DishIngredientRecognizeRequest()).getStatusCodeValue());
        }
        reset(recognition);
        for (String authority : new String[]{"dish:list", "dishIngredient:edit", "ROLE_USER"}) {
            authenticate(authority);
            assertThrows(AccessDeniedException.class,
                    () -> controller.recognizeIngredients(new DishIngredientRecognizeRequest()));
        }
        verifyNoInteractions(recognition, dishes, packages);
    }

    private void authenticate(String authority) {
        when(users.loadUserByUsername(anyString())).thenReturn(new User("tester", "",
                AuthorityUtils.createAuthorityList(authority)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "", AuthorityUtils.createAuthorityList(authority)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", token());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private String token() {
        return "Bearer " + JWT.create().setPayload("sub", "tester")
                .setKey("recognition-test-key".getBytes(StandardCharsets.UTF_8)).sign();
    }

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class Config {
        @Bean(name = "el") AuthorityConfig permissionEvaluator() { return new AuthorityConfig(); }
        @Bean DishService dishService() { return mock(DishService.class); }
        @Bean ParentPackageService packages() { return mock(ParentPackageService.class); }
        @Bean UserDetailsService users() { return mock(UserDetailsService.class); }
        @Bean DishIngredientRecognitionService recognition() { return mock(DishIngredientRecognitionService.class); }
        @Bean DishController controller(DishService dishes, ParentPackageService packages,
                                      DishIngredientRecognitionService recognition) {
            return new DishController(dishes, packages, recognition);
        }
    }
}
