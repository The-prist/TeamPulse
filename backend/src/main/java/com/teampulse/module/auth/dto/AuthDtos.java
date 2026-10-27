package com.teampulse.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import lombok.Data;

/**
 * 认证相关请求/响应体。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度需在 3~50 之间")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6~64 之间")
        private String password;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        private String nickname;
        private String phone;
    }

    /**
     * 登录响应：前端只需保存 token，其余字段用于首屏渲染，避免二次请求。
     */
    @Data
    @Builder
    public static class LoginResponse {
        private String token;
        private String tokenType;
        private long expiresIn;
        private Long userId;
        private String username;
        private String nickname;
        /** 角色标识：sysadmin / hr / manager / leader / employee / auditor */
        private String role;
        /** 角色展示名，如「部门经理」 */
        private String roleName;
        private String employeeNo;
        private Long departmentId;
        private String departmentName;
        /** 数据范围：ALL / DEPT / SELF */
        private String dataScope;
        /** 权限点集合，前端据此渲染菜单与按钮 */
        private List<String> permissions;
    }

    /**
     * 当前登录用户信息（GET /auth/me）。
     *
     * <p>为什么要带 permissions / dataScope / roleName：前端刷新页面时会调这个接口
     * 恢复登录态。如果这里不返回权限点，前端只能沿用 localStorage 里那份旧数据；
     * 一旦那份数据是 RBAC 改造前的（没有 permissions 字段或为空数组），
     * 菜单和按钮会全部消失，现象像「超管没有权限」，实际是前端拿不到权限。
     */
    @Data
    @Builder
    public static class MeResponse {
        private Long userId;
        private String username;
        private String nickname;
        private String role;
        private String roleName;
        private String employeeNo;
        private Long departmentId;
        private String departmentName;
        /** 数据范围：ALL / DEPT / SELF */
        private String dataScope;
        /** 权限点集合（每次请求由 JwtAuthenticationFilter 按角色重新加载，改了权限即时生效） */
        private List<String> permissions;
    }
}
