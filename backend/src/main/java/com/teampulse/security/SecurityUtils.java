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

    public static boolean isAdmin() {
        LoginUser user = currentUserOrNull();
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    /** 管理员或本人才能操作，否则 403 */
    public static void requireSelfOrAdmin(Long targetUserId) {
        LoginUser user = currentUser();
        if (!"admin".equalsIgnoreCase(user.getRole()) && !user.getUserId().equals(targetUserId)) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
    }
}
