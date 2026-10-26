package com.teampulse.module.rbac.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.module.rbac.entity.Role;
import com.teampulse.module.rbac.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 角色接口：给用户表单的角色下拉用，同时暴露数据范围便于前端提示。 */
@Tag(name = "角色", description = "角色与数据范围")
@RestController
@RequestMapping("/api/v1/role")
@RequiredArgsConstructor
public class RoleController {

    private final RbacService rbacService;

    @Operation(summary = "角色下拉选项", description = "返回角色标识、名称与数据范围（ALL/DEPT/SELF）")
    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Role>> options() {
        return ApiResponse.ok(rbacService.listRoles());
    }
}
