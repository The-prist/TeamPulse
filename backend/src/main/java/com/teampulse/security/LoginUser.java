package com.teampulse.security;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 认证主体。
 *
 * <p>既用于登录时的 UserDetails 返回值，也用于 JWT 解析后直接重建（避免每个请求查库）。
 * 由于权限只来自角色字段，重建后与数据库一致；如需即时踢人，可加令牌黑名单。
 */
@Getter
@Builder
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String nickname;
    private final String role;
    private final String status;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security 约定角色权限带 ROLE_ 前缀
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"locked".equalsIgnoreCase(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 停用账号不允许登录 */
    @Override
    public boolean isEnabled() {
        return "active".equalsIgnoreCase(status);
    }
}
