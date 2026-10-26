package com.teampulse.module.team.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.team.dto.TeamDtos;
import com.teampulse.module.team.entity.Team;
import com.teampulse.module.team.service.TeamService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 团队接口。
 *
 * <p>鉴权分三层，逐层收紧：
 * <ul>
 *   <li>列表 {@code /list}：管理端专用，方法注解限 admin</li>
 *   <li>读写团队 / 成员：接口层只要求登录，真正的「是不是 leader」判定在 Service 里，
 *       因为那需要查 team_member，属于业务规则而非角色规则</li>
 *   <li>删除团队：创建人或 admin，同样由 Service 判定</li>
 * </ul>
 * 所有写接口的操作者一律取自 JWT，不接受请求参数里的 userId（否则等于让人随便冒充）。
 */
@Tag(name = "团队管理")
@RestController
@RequestMapping("/api/v1/team")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "分页查询团队（管理员）")
    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<TeamDtos.TeamVO>> list(@RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(teamService.page(keyword, Math.max(1, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "我参与的团队", description = "只返回当前登录人在 team_member 中状态为 active 的团队")
    @GetMapping("/my")
    public ApiResponse<List<TeamDtos.TeamVO>> my() {
        return ApiResponse.ok(teamService.listByUser(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "团队详情")
    @GetMapping("/{teamId}")
    public ApiResponse<TeamDtos.TeamVO> detail(@PathVariable Long teamId) {
        return ApiResponse.ok(teamService.detail(teamId));
    }

    @Operation(summary = "团队成员列表", description = "负责人置顶，其余按加入时间排序")
    @GetMapping("/{teamId}/members")
    public ApiResponse<List<TeamDtos.MemberVO>> members(@PathVariable Long teamId) {
        return ApiResponse.ok(teamService.members(teamId));
    }

    @Operation(summary = "创建团队", description = "创建人自动成为该团队的 leader")
    // 同时映射 "" 与 "/"：Spring 6 默认关闭尾斜杠匹配，前端带不带斜杠都能打到同一个方法
    @PostMapping({"", "/"})
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody TeamDtos.CreateRequest request) {
        Team team = teamService.create(request, SecurityUtils.currentUserId());
        return ApiResponse.ok(Map.of("teamId", team.getTeamId()));
    }

    @Operation(summary = "更新团队资料（负责人或管理员）")
    @PutMapping("/{teamId}")
    public ApiResponse<Void> update(@PathVariable Long teamId,
                                    @Valid @RequestBody TeamDtos.UpdateRequest request) {
        teamService.update(teamId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除团队（创建人或管理员）", description = "团队逻辑删除，成员关系同步物理清理")
    @DeleteMapping("/{teamId}")
    public ApiResponse<Void> delete(@PathVariable Long teamId) {
        teamService.delete(teamId);
        return ApiResponse.ok();
    }

    @Operation(summary = "添加团队成员（负责人或管理员）")
    @PostMapping("/{teamId}/members")
    public ApiResponse<Void> addMember(@PathVariable Long teamId,
                                       @Valid @RequestBody TeamDtos.MemberAddRequest request) {
        teamService.addMember(teamId, SecurityUtils.currentUserId(), request);
        return ApiResponse.ok();
    }

    @Operation(summary = "调整成员角色（负责人或管理员）")
    @PutMapping("/{teamId}/members/{userId}/role")
    public ApiResponse<Void> updateMemberRole(@PathVariable Long teamId,
                                              @PathVariable Long userId,
                                              @Valid @RequestBody TeamDtos.MemberRoleUpdateRequest request) {
        teamService.updateMemberRole(teamId, SecurityUtils.currentUserId(), userId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "移除团队成员（负责人或管理员）")
    @DeleteMapping("/{teamId}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long teamId, @PathVariable Long userId) {
        teamService.removeMember(teamId, SecurityUtils.currentUserId(), userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询某人在团队中的角色", description = "返回 leader/member，不是成员时 teamRole 为 null")
    @GetMapping("/{teamId}/members/{userId}/role")
    public ApiResponse<Map<String, Object>> memberRole(@PathVariable Long teamId, @PathVariable Long userId) {
        // LinkedHashMap 而不是 Map.of：Map.of 不允许 null 值，而「不是成员」必须显式返回 null，
        // 前端据此区分「查不到角色」与「接口失败」两种状态
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("teamRole", teamService.memberRole(teamId, userId));
        return ApiResponse.ok(body);
    }
}
