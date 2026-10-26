package com.teampulse.module.kanban.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.kanban.dto.KanbanDtos;
import com.teampulse.module.kanban.entity.Kanban;
import com.teampulse.module.kanban.entity.KanbanColumn;
import com.teampulse.module.kanban.entity.KanbanItem;
import com.teampulse.module.kanban.service.KanbanService;
import com.teampulse.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/**
 * 看板接口：看板列表与详情、列维护、卡片增删挪指派。
 *
 * <p>鉴权口径：
 * <ul>
 *   <li>所有接口都需要登录（{@code anyRequest().authenticated()}）</li>
 *   <li>看板改名/换团队/删除由 Service 校验「创建者或 admin」——需要读 JWT 角色，
 *       用 {@code @PreAuthorize("hasRole('ADMIN')")} 会把 admin 之外的创建者也拦掉</li>
 *   <li>拖拽卡片、指派属于团队内的协作动作，登录即可</li>
 * </ul>
 */
@Tag(name = "看板")
@RestController
@RequestMapping("/api/v1/kanban")
@RequiredArgsConstructor
public class KanbanController {

    private final KanbanService kanbanService;

    @Operation(summary = "分页查询看板", description = "teamId / keyword 可选，size 上限 200")
    @GetMapping
    public ApiResponse<PageResult<Kanban>> page(@RequestParam(required = false) Long teamId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(kanbanService.page(teamId, keyword, page, size));
    }

    @Operation(summary = "当前登录人可见的看板", description = "本人创建的 + 所在 active 团队的")
    @GetMapping("/my")
    public ApiResponse<List<Kanban>> my() {
        return ApiResponse.ok(kanbanService.listByUser(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "看板详情", description = "一次返回 kanban / columns / cards 三段数据")
    @GetMapping("/{kanbanId}/detail")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long kanbanId) {
        return ApiResponse.ok(kanbanService.detail(kanbanId));
    }

    @Operation(summary = "创建看板", description = "自动预置「待办 / 进行中 / 已完成」三列")
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody KanbanDtos.CreateRequest request) {
        Kanban kanban = kanbanService.create(request, SecurityUtils.currentUserId());
        return ApiResponse.ok(Map.of("kanbanId", kanban.getKanbanId()));
    }

    @Operation(summary = "更新看板", description = "仅创建者或管理员")
    @PutMapping("/{kanbanId}")
    public ApiResponse<Void> update(@PathVariable Long kanbanId,
                                    @Valid @RequestBody KanbanDtos.UpdateRequest request) {
        kanbanService.update(kanbanId, request, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除看板", description = "仅创建者或管理员，级联清理列与卡片")
    @DeleteMapping("/{kanbanId}")
    public ApiResponse<Void> delete(@PathVariable Long kanbanId) {
        kanbanService.delete(kanbanId);
        return ApiResponse.ok();
    }

    @Operation(summary = "新增看板列", description = "排位自动取当前最大值 +1")
    @PostMapping("/{kanbanId}/columns")
    public ApiResponse<KanbanColumn> addColumn(@PathVariable Long kanbanId,
                                               @Valid @RequestBody KanbanDtos.ColumnCreateRequest request) {
        return ApiResponse.ok(kanbanService.addColumn(kanbanId, request));
    }

    @Operation(summary = "删除看板列", description = "列内卡片一并清理，剩余列重排为 1..n")
    @DeleteMapping("/columns/{columnId}")
    public ApiResponse<Void> deleteColumn(@PathVariable Long columnId) {
        kanbanService.deleteColumn(columnId);
        return ApiResponse.ok();
    }

    @Operation(summary = "新增卡片", description = "columnId 为空时落到第一列")
    @PostMapping("/{kanbanId}/items")
    public ApiResponse<KanbanItem> addItem(@PathVariable Long kanbanId,
                                           @Valid @RequestBody KanbanDtos.ItemCreateRequest request) {
        return ApiResponse.ok(kanbanService.addItem(kanbanId, request));
    }

    @Operation(summary = "移动卡片", description = "给目标列腾位后落位，并按目标列名反推日程状态")
    @PutMapping("/items/{itemId}/move")
    public ApiResponse<Void> moveItem(@PathVariable Long itemId,
                                      @Valid @RequestBody KanbanDtos.MoveRequest request) {
        kanbanService.moveItem(itemId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "指派卡片承接人", description = "assigneeId 为空表示取消承接")
    @PutMapping("/items/{itemId}/assign")
    public ApiResponse<Void> assignItem(@PathVariable Long itemId,
                                        @RequestParam(required = false) Long assigneeId) {
        kanbanService.assignItem(itemId, assigneeId);
        return ApiResponse.ok();
    }
}
