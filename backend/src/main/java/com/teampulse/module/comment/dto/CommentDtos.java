package com.teampulse.module.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论模块的请求体与视图对象。
 *
 * <p>约定：请求体只做「格式」校验（非空、长度、枚举值），
 * 业务性判断（父评论是否存在、是否有权删除）留给 Service，由它抛 BizException。
 */
public final class CommentDtos {

    private CommentDtos() {
    }

    /** 发表评论 / 回复 */
    @Data
    public static class AddRequest {

        /** 评论对象类型：schedule 日程 / worklog 工作日志 */
        @NotBlank(message = "评论对象类型不能为空")
        @Pattern(regexp = "^(schedule|worklog)$", message = "评论对象类型只能是 schedule 或 worklog")
        private String relatedType;

        @NotNull(message = "评论对象ID不能为空")
        private Long relatedId;

        /** 正文上限 2000 字：与前端输入框 maxlength 对齐，也避免超长字符串拖慢敏感词过滤 */
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 2000, message = "评论内容不能超过 2000 字")
        private String content;

        /** 回复的父评论 ID，顶层评论留空 */
        private Long replyTo;
    }

    /** 编辑评论：只允许改正文，关联对象与父评论不可变更 */
    @Data
    public static class UpdateRequest {

        @NotBlank(message = "评论内容不能为空")
        @Size(max = 2000, message = "评论内容不能超过 2000 字")
        private String content;
    }

    /**
     * 评论视图对象。
     *
     * <p>比实体多出 {@code creatorName/creatorAvatar}（前端头像昵称直出，不用再查用户接口）
     * 与 {@code children}（两级回复树），少了 {@code deleted/updatedAt} 这类内部字段。
     */
    @Data
    public static class CommentVO {

        private Long commentId;

        private String relatedType;

        private Long relatedId;

        private Long creatorId;

        /** 评论人昵称，无昵称时回落到用户名 */
        private String creatorName;

        private String creatorAvatar;

        /** 过滤后的正文 */
        private String content;

        /** 原始正文；仅作者本人与管理员返回，其他人为 null（防止敏感词又被回传到前端） */
        private String originalContent;

        private Long replyTo;

        private LocalDateTime createTime;

        /** 子回复，仅顶层评论会有内容；预置空集合，前端免判空 */
        private List<CommentVO> children = new ArrayList<>();
    }
}
