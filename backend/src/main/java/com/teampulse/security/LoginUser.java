package com.teampulse.security;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 认证主体。
 *
 * <p>除了基础账号信息，还携带**角色、数据范围与权限点**：
 * <ul>
 *   <li>{@code role}：角色标识（sysadmin/hr/manager/leader/employee/auditor）</li>
 *   <li>{@code dataScope}：ALL / DEPT / SELF，决定能看多大范围的数据</li>
 *   <li>{@code permissions}：权限点集合，直接映射成 Spring Security 的 authority</li>
 * </ul>
 * 这样 {@code @PreAuthorize("hasAuthority('attendance:rule')")} 就能生效，
 * 不再依赖硬编码的角色名。
 */
@Getter
@Builder
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String nickname;
    private final String role;
    /** 角色展示名，如「部门经理」 */
    private final String roleName;
    private final String status;
    private final Long departmentId;
    /** ALL / DEPT / SELF */
    private final String dataScope;
    /** 权限点，如 attendance:rule */
    @Builder.Default
    private final Set<String> permissions = Set.of();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // Spring Security 约定角色权限带 ROLE_ 前缀
        if (role != null && !role.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
        }
        if (permissions != null) {
            for (String perm : permissions) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }
        }
        return authorities;
    }

    public boolean hasPermission(String perm) {
        // 超管恒为 true：权限点是否齐全不该由数据正确性决定。
        // 否则一旦 role_permission 少了行（迁移漏数据、误删），超管就会
        // 「少一个按钮」，现象像前端 bug，排查成本极高。
        return isSysAdmin() || (permissions != null && permissions.contains(perm));
    }

    /** 数据范围是否为全公司 */
    public boolean isUnrestricted() {
        return "ALL".equalsIgnoreCase(dataScope);
    }

    public boolean isSysAdmin() {
        return "sysadmin".equalsIgnoreCase(role);
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
