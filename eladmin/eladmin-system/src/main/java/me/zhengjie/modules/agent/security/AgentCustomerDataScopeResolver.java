package me.zhengjie.modules.agent.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.customer.profile.domain.CustomerProfile;
import me.zhengjie.modules.customer.profile.mapper.CustomerProfileMapper;
import me.zhengjie.modules.system.domain.User;
import me.zhengjie.modules.system.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 将签名访问上下文的部门数据范围转换为客户档案创建人范围。 */
@Component
@RequiredArgsConstructor
public class AgentCustomerDataScopeResolver {
    private final UserMapper userMapper;
    private final CustomerProfileMapper customerProfileMapper;

    /**
     * 解析本次请求允许访问的客户集合。
     *
     * @param context 已验签且未过期的客服访问上下文
     * @return null 表示全量数据范围；空集合表示当前角色在该范围内没有客户
     */
    public Set<Long> resolve(AgentAccessContext context) {
        if (context == null || context.isAllDataScope()) return null;
        List<Long> deptIds = context.getDataScopeDeptIds();
        if (deptIds == null || deptIds.isEmpty()) return Collections.emptySet();
        List<User> users = userMapper.selectList(new QueryWrapper<User>().in("dept_id", deptIds));
        Set<String> creators = users.stream().map(User::getUsername).filter(this::hasText).collect(Collectors.toSet());
        if (creators.isEmpty()) return Collections.emptySet();
        List<CustomerProfile> profiles = customerProfileMapper.selectList(new QueryWrapper<CustomerProfile>()
            .select("id").in("create_by", creators));
        return profiles.stream().map(CustomerProfile::getId).filter(java.util.Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
