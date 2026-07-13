package me.zhengjie.modules.agent.security;

import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.system.domain.User;
import me.zhengjie.modules.system.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 部门数据范围必须先映射到创建人，再映射到客户档案，不能将部门 ID 当作客户字段。 */
@ExtendWith(MockitoExtension.class)
class AgentCustomerDataScopeResolverTest {
    @Mock private UserMapper userMapper;
    @Mock private CustomerProfileMapper customerProfileMapper;

    @Test
    void shouldReturnNullForAllDataScopeWithoutQueryingMappings() {
        AgentAccessContext context = new AgentAccessContext();
        context.setAllDataScope(true);

        assertNull(resolver().resolve(context));

        verify(userMapper, never()).selectList(any());
        verify(customerProfileMapper, never()).selectList(any());
    }

    @Test
    void shouldResolveDepartmentUsersToCustomerCreators() {
        AgentAccessContext context = new AgentAccessContext();
        context.setAllDataScope(false); context.setDataScopeDeptIds(List.of(10L));
        User user = new User(); user.setUsername("service01");
        CustomerProfile first = new CustomerProfile(); first.setId(1001L);
        CustomerProfile second = new CustomerProfile(); second.setId(1002L);
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(customerProfileMapper.selectList(any())).thenReturn(List.of(first, second));

        assertEquals(Set.of(1001L, 1002L), resolver().resolve(context));

        verify(userMapper).selectList(any());
        verify(customerProfileMapper).selectList(any());
    }

    private AgentCustomerDataScopeResolver resolver() {
        return new AgentCustomerDataScopeResolver(userMapper, customerProfileMapper);
    }
}
