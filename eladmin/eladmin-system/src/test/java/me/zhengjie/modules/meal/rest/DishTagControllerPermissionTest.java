package me.zhengjie.modules.meal.rest;

import me.zhengjie.modules.meal.domain.DishTag;
import me.zhengjie.modules.meal.domain.dto.DishTagSaveDto;
import me.zhengjie.modules.meal.service.DishTagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DishTagControllerPermissionTest.MethodSecurityTestConfig.class)
class DishTagControllerPermissionTest {

    @Autowired
    private DishTagController controller;

    @Autowired
    private DishTagService tagService;

    @BeforeEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        reset(tagService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAllowsDedicatedTagPermission() {
        authenticate("dishTag:add");
        DishTag tag = new DishTag();
        tag.setId(8);
        when(tagService.create(any(DishTagSaveDto.class))).thenReturn(tag);

        assertEquals(HttpStatus.CREATED, controller.create(new DishTagSaveDto()).getStatusCode());
        verify(tagService).create(any(DishTagSaveDto.class));
    }

    @Test
    void createRejectsDishEditPermissionWithoutTagAddPermission() {
        authenticate("dish:edit");

        assertThrows(AccessDeniedException.class, () -> controller.create(new DishTagSaveDto()));

        verifyNoInteractions(tagService);
    }

    @Test
    void adminCanUseDedicatedTagEndpoint() {
        authenticate("ROLE_ADMIN");
        DishTag tag = new DishTag();
        tag.setId(9);
        when(tagService.create(any(DishTagSaveDto.class))).thenReturn(tag);

        assertEquals(HttpStatus.CREATED, controller.create(new DishTagSaveDto()).getStatusCode());
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "tester", "", AuthorityUtils.createAuthorityList(authorities)
        ));
    }

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {

        @Bean(name = "el")
        PermissionVerifier permissionVerifier() {
            return new PermissionVerifier();
        }

        @Bean
        DishTagService dishTagService() {
            return mock(DishTagService.class);
        }

        @Bean
        DishTagController dishTagController(DishTagService service) {
            return new DishTagController(service);
        }
    }

    static class PermissionVerifier {

        public boolean check(String permission) {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                return false;
            }
            return AuthorityUtils.authorityListToSet(
                SecurityContextHolder.getContext().getAuthentication().getAuthorities()
            ).contains("ROLE_ADMIN") || AuthorityUtils.authorityListToSet(
                SecurityContextHolder.getContext().getAuthentication().getAuthorities()
            ).contains(permission);
        }
    }
}
