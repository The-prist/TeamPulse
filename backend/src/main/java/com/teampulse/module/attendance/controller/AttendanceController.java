package com.teampulse.module.attendance.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.attendance.dto.AttendanceDtos;
import com.teampulse.module.attendance.entity.AttendanceDaily;
import com.teampulse.module.attendance.entity.AttendanceRecord;
import com.teampulse.module.attendance.entity.AttendanceRule;
import com.teampulse.module.attendance.service.AttendanceRuleService;
import com.teampulse.module.attendance.service.AttendanceService;
import com.teampulse.module.attendance.vo.AttendanceRecordVO;
import com.teampulse.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import com.teampulse.module.audit.annotation.Audited;

/**
 * 考勤查询与补卡接口。
 *
 * <p>实时看板用的是「前端定时轮询 + 轻量接口」：
 * {@code GET /api/v1/attendance/recent?limit=20} 只返回最近 N 条，
 * 单次响应在 1KB 量级，5 秒一轮对 4C4G 服务器毫无压力。
 * 若后续要做真正的实时推送，可把该接口换成 SSE/WebSocket，前端改造成本很小。
 */
@Tag(name = "考勤", description = "打卡记录、实时看板、考勤规则")
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceRuleService ruleService;

    @Operation(summary = "打卡记录分页查询")
    @GetMapping("/records")
    public ApiResponse<PageResult<AttendanceRecordVO>> records(AttendanceDtos.RecordQuery query) {
        return ApiResponse.ok(attendanceService.pageRecords(query));
    }

    @Operation(summary = "员工考勤详情（管理员）",
            description = "一次返回该员工的汇总统计、按天明细与原始打卡流水，供点人名后的抽屉展示")
    @GetMapping("/employee/{userId}")
    @PreAuthorize("hasAuthority('attendance:view')")
    public ApiResponse<AttendanceDtos.EmployeeAttendance> employeeDetail(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(attendanceService.employeeDetail(userId, from, to));
    }

    @Operation(summary = "实时打卡流", description = "返回最近 N 条打卡，供前端 5 秒轮询")
    @GetMapping("/recent")
    public ApiResponse<List<AttendanceRecordVO>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(attendanceService.recentPunches(limit));
    }

    @Operation(summary = "考勤总览指标")
    @GetMapping("/overview")
    public ApiResponse<AttendanceDtos.OverviewResponse> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(attendanceService.overview(date));
    }

    @Operation(summary = "近 N 天考勤趋势")
    @GetMapping("/trend")
    public ApiResponse<List<AttendanceDtos.DailyTrendItem>> trend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(attendanceService.trend(from, to));
    }

    @Operation(summary = "考勤日历（管理员）",
            description = "返回日期区间内「每人每天」的考勤结果，供日历视图按天展开名单")
    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('attendance:view')")
    public ApiResponse<List<AttendanceDtos.CalendarDay>> calendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(attendanceService.calendar(from, to));
    }

    @Operation(summary = "我的考勤（个人中心）")
    @GetMapping("/my")
    public ApiResponse<List<AttendanceDaily>> my(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(attendanceService.myDaily(SecurityUtils.currentUserId(), from, to));
    }

    @Operation(summary = "手工补卡（管理员）")
    @PostMapping("/manual")
    @PreAuthorize("hasAuthority('attendance:manage')")
    @Audited(module = "attendance", action = "attendance.manual", detail = "手工补卡")
    public ApiResponse<AttendanceRecord> manual(@Valid @RequestBody AttendanceDtos.ManualPunchRequest request) {
        return ApiResponse.ok(attendanceService.manualPunch(request));
    }

    @Operation(summary = "查询当前考勤规则")
    @GetMapping("/rule")
    public ApiResponse<AttendanceRule> getRule() {
        return ApiResponse.ok(ruleService.currentRule());
    }

    @Operation(summary = "更新考勤规则（管理员）")
    @PutMapping("/rule")
    @PreAuthorize("hasAuthority('attendance:rule')")
    @Audited(module = "attendance", action = "attendance.rule.update", detail = "修改考勤规则")
    public ApiResponse<AttendanceRule> updateRule(@Valid @RequestBody AttendanceDtos.RuleUpdateRequest request) {
        return ApiResponse.ok(ruleService.update(request));
    }
}
