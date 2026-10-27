package com.teampulse.module.worklog.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.worklog.dto.WorklogDtos;
import com.teampulse.module.worklog.entity.Worklog;
import com.teampulse.module.worklog.service.WorklogService;
import com.teampulse.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 工作日志接口。
 *
 * <p>当前用户 ID 一律取自 JWT（{@code SecurityUtils}），不接受请求参数传入 ——
 * 否则任何人都能伪造 creatorId 用别人的名义写日志。
 * 修改/删除的归属校验放在 Service（需要同时判断作者与 admin 角色）。
 */
@Tag(name = "工作日志")
@RestController
@RequestMapping("/api/v1/worklog")
@RequiredArgsConstructor
public class WorklogController {

    private final WorklogService worklogService;

    @Operation(summary = "分页查询工作日志",
            description = "creatorId / teamId / keyword / from / to / timeFrom / timeTo / order 均可选；"
                    + "from、to 为 yyyy-MM-dd（按日志日期）；timeFrom、timeTo 为 HH:mm（按提交时间）；"
                    + "order 默认 asc；size 上限 200")
    @PreAuthorize("hasAuthority('worklog:view')")
    @GetMapping
    public ApiResponse<PageResult<WorklogDtos.WorklogVO>> page(
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeFrom,
            @RequestParam(required = false) String timeTo,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(worklogService.page(creatorId, teamId, keyword, from, to,
                timeFrom, timeTo, order, page, size));
    }

    @Operation(summary = "工作日志详情", description = "含作者名、团队名、关联日程标题")
    @PreAuthorize("hasAuthority('worklog:view')")
    @GetMapping("/{id}")
    public ApiResponse<WorklogDtos.WorklogVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(worklogService.detail(id));
    }

    @Operation(summary = "新增工作日志", description = "记录人取自 JWT")
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody WorklogDtos.CreateRequest request) {
        Worklog worklog = worklogService.create(request, SecurityUtils.currentUserId());
        return ApiResponse.ok(Map.of("worklogId", worklog.getWorklogId()));
    }

    @Operation(summary = "更新工作日志", description = "仅作者本人或管理员")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody WorklogDtos.UpdateRequest request) {
        worklogService.update(id, request, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除工作日志", description = "仅作者本人或管理员，逻辑删除")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        worklogService.delete(id, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "工作日志统计",
            description = "条数、覆盖天数、平均字数；creatorId 缺省为当前登录人，日期缺省为近 30 天")
    @PreAuthorize("hasAuthority('worklog:view')")
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> statistics(
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        // 不传 creatorId 时统计本人：个人中心是主要使用场景，
        // 管理员需要看全公司时显式传 creatorId 或走概览模块的聚合接口
        Long targetCreator = creatorId != null ? creatorId : SecurityUtils.currentUserId();
        return ApiResponse.ok(worklogService.statistics(targetCreator, from, to));
    }
}
