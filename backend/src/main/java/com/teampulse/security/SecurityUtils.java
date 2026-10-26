package com.teampulse.security;

import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户的便捷读取工具。
 *
 * <p>Controller 里直接 {@code SecurityUtils.currentUser()} 取主体，
 * 不再从请求头里读 userId（旧实现的安全漏洞正是这里）。
 *
 * <p>RBAC 改造后新增了权限与数据范围读取方法：业务代码优先判断**权限点**
 * （{@link #hasPermission}），而不是判断角色名，这样加角色不用改代码。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 取当前用户，未登录返回 null */
    public static LoginUser currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof LoginUser user)) {
            return null;
        }
        return user;
    }

    /** 取当前用户，未登录抛 401 */
    public static LoginUser currentUser() {
        LoginUser user = currentUserOrNull();
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long currentUserId() {
        return currentUser().getUserId();
    }

    public static String currentRole() {
        LoginUser user = currentUserOrNull();
        return user == null ? null : user.getRole();
    }

    public static Long currentDepartmentId() {
        LoginUser user = currentUserOrNull();
        return user == null ? null : user.getDepartmentId();
    }

    /** 是否拥有某个权限点，如 attendance:rule */
    public static boolean hasPermission(String permission) {
        LoginUser user = currentUserOrNull();
        return user != null && user.hasPermission(permission);
    }

    /** 要求拥有某个权限点，否则 403 */
    public static void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 是否为「不受数据范围限制」的角色（系统管理员 / 人事 / 审计）。
     *
     * <p>注意语义已变化：以前判断角色名是否等于 admin，
     * 现在判断角色的 data_scope 是否为 ALL —— 这样新增一个全公司角色不用改代码。
     */
    public static boolean isAdmin() {
        LoginUser user = currentUserOrNull();
        return user != null && user.isUnrestricted();
    }

    public static boolean isSysAdmin() {
        LoginUser user = currentUserOrNull();
        return user != null && user.isSysAdmin();
    }

    /** 拥有指定权限点，或目标就是自己 */
    public static void requireSelfOrPermission(Long targetUserId, String permission) {
        LoginUser user = currentUser();
        if (user.hasPermission(permission)) {
            return;
        }
        if (user.getUserId().equals(targetUserId)) {
            return;
        }
        throw BizException.of(ErrorCode.FORBIDDEN);
    }

    /** 兼容旧调用：本人或拥有 user:manage 才能操作 */
    public static void requireSelfOrAdmin(Long targetUserId) {
        requireSelfOrPermission(targetUserId, Perms.USER_MANAGE);
    }
}
