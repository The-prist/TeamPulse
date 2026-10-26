package com.teampulse.module.schedule.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.schedule.dto.ScheduleDtos;
import com.teampulse.module.schedule.entity.Schedule;
import com.teampulse.module.schedule.service.ScheduleService;
import com.teampulse.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 日程接口。
 *
 * <p>这里没有 {@code @PreAuthorize}：日程是「登录后人人可用」的功能，
 * 权限差异只体现在「改/删必须是自己创建的」这一条业务规则上，由 Service 判定。
 * 状态流转单独做成子路径动词 {@code POST /{id}/status}，与 REST 约定保持一致。
 */
@Tag(name = "日程管理")
@RestController
@RequestMapping("/api/v1/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "分页查询日程",
            description = "支持按团队/创建人/状态/关键字/开始日期区间/时间段过滤；order 默认 desc（新日程在上）")
    // 同时映射 "" 与 "/"：Spring 6 默认关闭尾斜杠匹配，前端带不带斜杠都能打到同一个方法
    @GetMapping({"", "/"})
    public ApiResponse<PageResult<ScheduleDtos.ScheduleVO>> list(@RequestParam(required = false) Long teamId,
                                                                @RequestParam(required = false) Long creatorId,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                @RequestParam(required = false) String timeFrom,
                                                                @RequestParam(required = false) String timeTo,
                                                                @RequestParam(defaultValue = "desc") String order,
                                                                @RequestParam(defaultValue = "1") long page,
                                                                @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(scheduleService.page(teamId, creatorId, status, keyword,
                from, to, timeFrom, timeTo, order, page, size));
    }

    @Operation(summary = "日程详情")
    @GetMapping("/{id}")
    public ApiResponse<ScheduleDtos.ScheduleVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(scheduleService.detail(id));
    }

    @Operation(summary = "创建日程", description = "创建人取自 JWT；teamId 为空表示个人日程")
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody ScheduleDtos.CreateRequest request) {
        Schedule schedule = scheduleService.create(request, SecurityUtils.currentUserId());
        return ApiResponse.ok(Map.of("scheduleId", schedule.getScheduleId()));
    }

    @Operation(summary = "更新日程（创建人或管理员）", description = "只覆盖请求中的非空字段")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody ScheduleDtos.UpdateRequest request) {
        scheduleService.update(id, request, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "更新日程状态", description = "看板拖拽卡片时也会走这个接口，状态是日程与卡片的唯一状态源")
    @PostMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                         @Valid @RequestBody ScheduleDtos.StatusUpdateRequest request) {
        scheduleService.updateStatus(id, request.getStatus());
        return ApiResponse.ok(Map.of("scheduleId", id, "status", request.getStatus()));
    }

    @Operation(summary = "删除日程（创建人或管理员，逻辑删除）")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "日程状态统计", description = "返回 todo/doing/done/canceled 四类数量，缺失状态补 0")
    @GetMapping("/summary")
    public ApiResponse<Map<String, Long>> summary(@RequestParam(required = false) Long teamId) {
        return ApiResponse.ok(scheduleService.statusSummary(teamId));
    }
}
