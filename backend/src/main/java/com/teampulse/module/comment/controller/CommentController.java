package com.teampulse.module.comment.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.comment.dto.CommentDtos;
import com.teampulse.module.comment.service.CommentService;
import com.teampulse.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/**
 * 评论接口。
 *
 * <p>日程与工作日志共用这一套接口，用 {@code relatedType + relatedId} 区分归属，
 * 前端组件也就能复用同一份评论面板。当前登录人一律取自 JWT（{@link SecurityUtils}），
 * 不接受前端传 creatorId —— 否则任何人都能冒名发帖。
 */
@Tag(name = "评论", description = "日程 / 工作日志的讨论区，写入时自动过滤敏感词")
@RestController
@RequestMapping("/api/v1/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "发表评论或回复", description = "content 入库前替换敏感词，原文另存 original_content 供审计")
    @PostMapping
    public ApiResponse<Void> add(@Valid @RequestBody CommentDtos.AddRequest request) {
        commentService.add(request, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "评论分页查询", description = "平铺列表，按时间倒序；keyword 匹配过滤后的正文")
    @GetMapping
    public ApiResponse<PageResult<CommentDtos.CommentVO>> page(@RequestParam String relatedType,
                                                               @RequestParam Long relatedId,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(commentService.page(relatedType, relatedId, keyword, page, size));
    }

    @Operation(summary = "管理端全量评论检索",
            description = "可不限关联对象，按日期区间 / 时间段 / 评论人 / 关键词筛选；默认时间正序")
    @GetMapping("/admin/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<CommentDtos.CommentVO>> adminPage(
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) Long relatedId,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timeFrom,
            @RequestParam(required = false) String timeTo,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        return ApiResponse.ok(commentService.adminPage(relatedType, relatedId, creatorId, keyword,
                from, to, timeFrom, timeTo, order, page, size));
    }

    @Operation(summary = "评论树", description = "顶层评论 + children 两级回复，一次查询组装完成")
    @GetMapping("/tree")
    public ApiResponse<List<CommentDtos.CommentVO>> tree(@RequestParam String relatedType,
                                                         @RequestParam Long relatedId) {
        return ApiResponse.ok(commentService.tree(relatedType, relatedId));
    }

    @Operation(summary = "编辑评论", description = "仅作者本人；改动同样会重新过滤敏感词")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody CommentDtos.UpdateRequest request) {
        commentService.update(id, request.getContent(), SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除评论", description = "仅作者本人或管理员，逻辑删除并级联删除其子回复")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        commentService.delete(id, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }
}
