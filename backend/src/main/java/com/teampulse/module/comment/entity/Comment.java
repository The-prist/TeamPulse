package com.teampulse.module.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论实体（日程 / 工作日志的讨论区）。
 *
 * <p>双字段设计是本表的核心：{@code content} 存**过滤后**的内容，直接给所有人看；
 * {@code originalContent} 存**用户原文**，只用于审计与申诉复核。
 * 拆成两列而不是「过滤时直接覆盖」，是因为敏感词库本身会演进——
 * 今天被替换掉的词，明天可能被管理员从词库里移除，此时需要能一键还原用户真实表述。
 * 反过来，如果只存原文、每次读取时再过滤，则历史评论的展示会随词库变化而漂移，同样不可接受。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comment")
public class Comment extends BaseEntity {

    @TableId(value = "comment_id", type = IdType.AUTO)
    private Long commentId;

    /** 关联业务类型，取值只有 schedule / worklog（与 DDL 注释一致） */
    private String relatedType;

    /** 关联业务 ID，指向 schedule.schedule_id 或 worklog.worklog_id */
    private Long relatedId;

    /** 评论人 user_id */
    private Long creatorId;

    /** 过滤敏感词后的正文，对外展示用 */
    private String content;

    /** 用户提交的原始正文，仅作者本人与管理员可见 */
    private String originalContent;

    /** 回复的父评论 ID；为 null 表示顶层评论。业务上只允许两级（见 CommentService#add） */
    private Long replyTo;
}
