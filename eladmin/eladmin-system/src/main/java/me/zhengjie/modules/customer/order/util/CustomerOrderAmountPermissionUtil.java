package me.zhengjie.modules.customer.order.util;

import me.zhengjie.utils.SecurityUtils;
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

    private static boolean hasAnyAuthority(String... authorities) {
        UserDetails currentUser = SecurityUtils.getCurrentUser();
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
}
