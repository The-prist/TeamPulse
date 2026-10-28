package com.teampulse.module.auth.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.module.auth.dto.AuthDtos;
import com.teampulse.module.auth.service.AuthService;
import com.teampulse.module.rbac.service.RbacService;
import com.teampulse.security.LoginUser;
import com.teampulse.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认证接口（公开访问，见 SecurityConfig 的 /api/v1/auth/** 放行规则）。
 */
@Tag(name = "认证", description = "登录 / 注册 / 当前登录人")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RbacService rbacService;

    @Operation(summary = "登录", description = "返回 JWT，前端后续请求以 Authorization: Bearer <token> 携带")
    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                     jakarta.servlet.http.HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest));
    }

    @Operation(summary = "注册", description = "自助注册，角色固定为 member")
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        Long userId = authService.register(request);
        return ApiResponse.ok(Map.of("userId", userId));
    }

    @Operation(summary = "当前登录人", description = "含角色、数据范围与权限点，前端刷新页面据此恢复菜单与按钮")
    @GetMapping("/me")
    public ApiResponse<AuthDtos.MeResponse> me() {
        LoginUser me = SecurityUtils.currentUser();
        Map<Long, String> deptNames = rbacService.departmentNameMap();
        return ApiResponse.ok(AuthDtos.MeResponse.builder()
                .userId(me.getUserId())
                .username(me.getUsername())
                .nickname(me.getNickname())
                .role(me.getRole())
                .roleName(rbacService.roleNameOf(me.getRole()))
                .departmentId(me.getDepartmentId())
                .departmentName(me.getDepartmentId() == null ? null : deptNames.get(me.getDepartmentId()))
                .dataScope(me.getDataScope())
                .permissions(me.getPermissions() == null ? List.of() : List.copyOf(me.getPermissions()))
                .build());
    }
}
