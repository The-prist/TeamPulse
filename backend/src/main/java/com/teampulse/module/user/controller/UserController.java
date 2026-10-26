package com.teampulse.module.user.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.user.dto.UserDtos;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.service.UserService;
import com.teampulse.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import com.teampulse.module.audit.annotation.Audited;

/**
 * 用户管理接口。
 *
 * <p>鉴权分三档：
 * <ul>
 *   <li>管理端 CRUD —— {@code @PreAuthorize("hasAuthority('user:manage')")}</li>
 *   <li>下拉选项 —— 任意登录用户（选人、指派卡片都要用）</li>
 *   <li>自助操作 —— 只能操作自己，用户 ID 取自 JWT 而非请求参数</li>
 * </ul>
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页查询用户（管理员）")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('user:view')")
    public ApiResponse<PageResult<User>> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(userService.page(keyword, Math.max(1, page), Math.min(Math.max(1, size), 500)));
    }

    @Operation(summary = "用户下拉选项", description = "只返回 id/用户名/昵称/工号，替代旧版 size=1000 的全量拉取")
    @GetMapping("/options")
    public ApiResponse<List<User>> options() {
        return ApiResponse.ok(userService.listOptions());
    }

    @Operation(summary = "当前登录人资料")
    @GetMapping("/self")
    public ApiResponse<User> self() {
        return ApiResponse.ok(userService.getById(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "更新当前登录人资料")
    @PutMapping("/self")
    public ApiResponse<Void> updateSelf(@Valid @RequestBody UserDtos.SelfUpdateRequest request) {
        userService.updateSelf(SecurityUtils.currentUserId(), request);
        return ApiResponse.ok();
    }

    @Operation(summary = "修改当前登录人密码")
    @PutMapping("/self/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody UserDtos.PasswordChangeRequest request) {
        userService.changePassword(SecurityUtils.currentUserId(), request.getOldPassword(), request.getNewPassword());
        return ApiResponse.ok();
    }

    @Operation(summary = "用户详情（管理员）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:view')")
    public ApiResponse<User> detail(@PathVariable Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    @Operation(summary = "创建用户（管理员）")
    @PostMapping
    @PreAuthorize("hasAuthority('user:manage')")
    @Audited(module = "user", action = "user.create", detail = "新增用户")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody UserDtos.CreateRequest request) {
        User user = userService.create(request);
        return ApiResponse.ok(Map.of("userId", user.getUserId()));
    }

    @Operation(summary = "更新用户（管理员）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @Audited(module = "user", action = "user.update", detail = "修改用户资料/角色/部门")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UserDtos.UpdateRequest request) {
        userService.update(id, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "重置密码（管理员）", description = "无需旧密码，用于员工忘记密码场景")
    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('user:manage')")
    @Audited(module = "user", action = "user.password.reset", detail = "管理员重置用户密码")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody UserDtos.PasswordResetRequest request) {
        userService.resetPassword(id, request.getNewPassword());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除用户（管理员，逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @Audited(module = "user", action = "user.delete", detail = "删除用户（逻辑删除）")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok();
    }
}
