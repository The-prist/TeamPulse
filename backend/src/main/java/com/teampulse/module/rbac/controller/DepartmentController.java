package com.teampulse.module.rbac.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.module.rbac.entity.Department;
import com.teampulse.module.rbac.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门接口（当前只提供下拉选项）。
 *
 * <p>部门维护暂时走数据库迁移，不在界面里做增删改 —— 真实的组织架构调整
 * 通常由 HR 系统单向同步过来，不适合在业务后台随便改。
 */
@Tag(name = "部门", description = "组织架构下拉选项")
@RestController
@RequestMapping("/api/v1/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final RbacService rbacService;

    @Operation(summary = "部门下拉选项", description = "返回 id/名称/上级，供用户表单与筛选使用")
    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Department>> options() {
        return ApiResponse.ok(rbacService.listDepartments());
    }
}
