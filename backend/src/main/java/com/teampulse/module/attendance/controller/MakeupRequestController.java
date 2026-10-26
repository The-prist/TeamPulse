package com.teampulse.module.attendance.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.attendance.dto.MakeupDtos;
import com.teampulse.module.attendance.service.MakeupRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.teampulse.module.audit.annotation.Audited;

/**
 * 补卡申请与审批。
 *
 * <p>权限分成两端：
 * <ul>
 *   <li>员工端：提交申请、查看自己的申请（{@code makeup:submit}）</li>
 *   <li>审批端：待办/已办列表、通过/驳回（{@code attendance:approve}）</li>
 * </ul>
 */
@Tag(name = "补卡审批", description = "员工提交补卡申请，部门经理或 HR 审批后才写入考勤")
@RestController
@RequestMapping("/api/v1/makeup")
@RequiredArgsConstructor
public class MakeupRequestController {

    private final MakeupRequestService makeupRequestService;

    @Operation(summary = "提交补卡申请")
    @PostMapping
    @PreAuthorize("hasAuthority('makeup:submit')")
    public ApiResponse<Map<String, Object>> submit(@Valid @RequestBody MakeupDtos.SubmitRequest request) {
        return ApiResponse.ok(Map.of("requestId", makeupRequestService.submit(request)));
    }

    @Operation(summary = "我的补卡申请", description = "按下单时间倒序")
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('makeup:submit')")
    public ApiResponse<PageResult<MakeupDtos.MakeupVO>> mine(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(makeupRequestService.mine(page, size));
    }

    @Operation(summary = "待我审批的补卡申请", description = "按数据范围过滤：经理只看本部门、HR/管理员看全部")
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('attendance:approve')")
    public ApiResponse<PageResult<MakeupDtos.MakeupVO>> pending(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(makeupRequestService.pending(page, size));
    }

    @Operation(summary = "已处理的补卡申请")
    @GetMapping("/reviewed")
    @PreAuthorize("hasAuthority('attendance:approve')")
    public ApiResponse<PageResult<MakeupDtos.MakeupVO>> reviewed(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(makeupRequestService.reviewed(page, size));
    }

    @Operation(summary = "审批补卡申请", description = "approved=true 时生成正式打卡记录并重建当日汇总")
    @PostMapping("/{requestId}/review")
    @PreAuthorize("hasAuthority('attendance:approve')")
    @Audited(module = "attendance", action = "attendance.makeup.review", detail = "审批补卡申请")
    public ApiResponse<Void> review(@PathVariable Long requestId,
                                    @Valid @RequestBody MakeupDtos.ReviewRequest request) {
        makeupRequestService.review(requestId, request);
        return ApiResponse.ok();
    }
}
