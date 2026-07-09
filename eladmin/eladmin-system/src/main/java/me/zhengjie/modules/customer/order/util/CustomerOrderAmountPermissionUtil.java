package me.zhengjie.modules.customer.order.util;

import me.zhengjie.utils.SecurityUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 订单金额权限判断工具，统一封装金额查看/编辑权限口径。
 */
public final class CustomerOrderAmountPermissionUtil {

    public static final String ADMIN = "admin";
    public static final String AMOUNT_VIEW = "customerOrder:amount:view";
    public static final String AMOUNT_EDIT = "customerOrder:amount:edit";

    private CustomerOrderAmountPermissionUtil() {
    }

    /**
     * 判断当前用户是否具备订单金额查看权限。
     *
     * @return true-可查看金额，false-不可查看金额
     */
    public static boolean canViewAmount() {
        return hasAnyAuthority(ADMIN, AMOUNT_VIEW);
    }

    /**
     * 判断当前用户是否具备订单金额编辑权限。
     *
     * @return true-可编辑金额，false-不可编辑金额
     */
    public static boolean canEditAmount() {
        return hasAnyAuthority(ADMIN, AMOUNT_EDIT);
    }

    /**
     * 判断当前请求对应的登录用户是否具备任一指定权限。
     * 匿名请求、内部调用或缺少 JWT 的场景统一按无权限处理，避免金额脱敏逻辑反向依赖登录态抛异常。
     *
     * @param authorities 目标权限编码
     * @return true-具备任一权限，false-未登录或无权限
     */
    private static boolean hasAnyAuthority(String... authorities) {
        UserDetails currentUser = resolveCurrentUser();
        if (currentUser == null || currentUser.getAuthorities() == null) {
            return false;
        }
        Collection<? extends GrantedAuthority> currentAuthorities = currentUser.getAuthorities();
        for (GrantedAuthority authority : currentAuthorities) {
            if (authority == null || authority.getAuthority() == null) {
                continue;
            }
            String current = authority.getAuthority();
            for (String expected : authorities) {
                if (expected.equals(current)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 解析当前登录用户。
     * 无请求上下文、无 JWT 或 token 非法时返回 null，由调用方按无权限处理。
     *
     * @return 当前登录用户；不存在时返回 null
     */
    private static UserDetails resolveCurrentUser() {
        try {
            String token = SecurityUtils.getToken();
            if (StringUtils.isBlank(token)) {
                return null;
            }
            return SecurityUtils.getCurrentUser();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
