package com.teampulse.module.audit.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.audit.dto.AuditDtos;
import com.teampulse.module.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审计日志查询。
 *
 * <p>只读接口：审计表本身不提供任何修改或删除入口（连逻辑删除都没有）。
 * 权限点 {@code audit:view} 当前授予系统管理员、人事专员、审计专员。
 */
@Tag(name = "审计日志", description = "管理操作留痕的查询，只读")
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogService auditLogService;

    @Operation(summary = "审计日志分页查询", description = "支持操作人、动作、模块、结果、时间区间、traceId 过滤")
    @GetMapping
    @PreAuthorize("hasAuthority('audit:view')")
    public ApiResponse<PageResult<AuditDtos.AuditVO>> page(@Valid AuditDtos.Query query) {
        return ApiResponse.ok(auditLogService.page(query));
    }

    @Operation(summary = "审计日志详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('audit:view')")
    public ApiResponse<AuditDtos.AuditVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(auditLogService.detail(id));
    }

    @Operation(summary = "筛选项字典", description = "前端下拉用：模块与结果枚举；动作按 module.动词 命名，直接文本检索即可")
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('audit:view')")
    public ApiResponse<Map<String, Object>> options() {
        return ApiResponse.ok(Map.of(
                "modules", List.of(
                        Map.of("value", "user", "label", "用户"),
                        Map.of("value", "team", "label", "团队"),
                        Map.of("value", "attendance", "label", "考勤"),
                        Map.of("value", "device", "label", "设备"),
                        Map.of("value", "comment", "label", "评论"),
                        Map.of("value", "sensitive", "label", "敏感词")
                ),
                "results", List.of(
                        Map.of("value", "SUCCESS", "label", "成功"),
                        Map.of("value", "FAILURE", "label", "失败")
                )
        ));
    }
}
