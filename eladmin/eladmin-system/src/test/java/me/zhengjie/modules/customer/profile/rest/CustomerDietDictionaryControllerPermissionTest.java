package me.zhengjie.modules.customer.profile.rest;

import me.zhengjie.modules.customer.profile.domain.dto.CustomerDietOptionDto;
import me.zhengjie.modules.customer.profile.service.CustomerDietDictionaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CustomerDietDictionaryControllerPermissionTest.MethodSecurityTestConfig.class)
class CustomerDietDictionaryControllerPermissionTest {

    @Autowired
    private CustomerDietDictionaryController controller;

    @Autowired
    private CustomerDietDictionaryService dictionaryService;

    @BeforeEach
    void resetSecurityAndService() {
        SecurityContextHolder.clearContext();
        reset(dictionaryService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void orderEditorCanReadActiveDietOptions() {
        authenticate("customerOrder:edit");
        List<CustomerDietOptionDto> options = Collections.singletonList(new CustomerDietOptionDto());
        when(dictionaryService.listActiveOptions()).thenReturn(options);

        assertEquals(options, controller.listOptions());
        verify(dictionaryService).listActiveOptions();
    }

    @Test
    void userWithoutDietOrOrderReadPermissionIsRejected() {
        authenticate("customerOrder:list");

        assertThrows(AccessDeniedException.class, () -> controller.listOptions());
        verifyNoInteractions(dictionaryService);
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", "", AuthorityUtils.createAuthorityList(authorities)));
    }

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {

        @Bean(name = "el")
        PermissionVerifier permissionVerifier() {
            return new PermissionVerifier();
        }

        @Bean
        CustomerDietDictionaryService dictionaryService() {
            return mock(CustomerDietDictionaryService.class);
        }

        @Bean
        CustomerDietDictionaryController controller(CustomerDietDictionaryService service) {
            return new CustomerDietDictionaryController(service);
        }
    }

    static class PermissionVerifier {

        public boolean check(String permission) {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                return false;
            }
            return AuthorityUtils.authorityListToSet(
                    SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .contains("ROLE_ADMIN") || AuthorityUtils.authorityListToSet(
                    SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .contains(permission);
        }
    }
}
