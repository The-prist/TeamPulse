package com.teampulse.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
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
        private String role;
        private String employeeNo;
    }
}
