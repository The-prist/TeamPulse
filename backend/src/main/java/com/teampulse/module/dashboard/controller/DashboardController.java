package com.teampulse.module.dashboard.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.module.dashboard.dto.DashboardDtos;
import com.teampulse.module.dashboard.service.DashboardService;
import com.teampulse.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页概览接口。
 *
 * <p>三条路由的定位：
 * <ul>
 *   <li>{@code /admin} —— 管理端首页，全公司口径，只有 admin 能看；</li>
 *   <li>{@code /me}    —— 个人工作台，只看自己的日程/日志/打卡，全员可访问；</li>
 *   <li>{@code /trend} —— 当前登录人的近 N 天趋势，供个人工作台的折线图使用
 *       （全公司趋势已包含在 {@code /admin} 的 recentDays 里，不必再开一个跨用户查询入口）。</li>
 * </ul>
 *
 * <p>用户身份一律取自 JWT：{@code /me} 与 {@code /trend} 都不接受 userId 参数，
 * 否则任何人都能通过改参数查别人的工作量。
 */
@Tag(name = "概览统计", description = "管理端首页指标卡与近 N 天趋势")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "全公司概览（管理员）",
            description = "用户/团队/日程/日志/看板/设备与今日打卡的聚合指标，days 默认 7、区间 [1,90]")
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DashboardDtos.OverviewVO> admin(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(dashboardService.adminOverview(days));
    }

    @Operation(summary = "我的概览",
            description = "只统计当前登录人的日程、日志与打卡；公司级指标恒为 0，前端按非零渲染")
    @GetMapping("/me")
    public ApiResponse<DashboardDtos.OverviewVO> me(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(dashboardService.myOverview(SecurityUtils.currentUserId(), days));
    }

    @Operation(summary = "我的近 N 天趋势",
            description = "按天返回新建日程数、新建日志数、打卡笔数，缺失的日期补 0 值点")
    @GetMapping("/trend")
    public ApiResponse<List<DashboardDtos.DailyPoint>> trend(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(dashboardService.dailyTrend(days, SecurityUtils.currentUserId()));
    }
}
