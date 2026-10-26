package com.teampulse.module.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词。
 *
 * <p>刻意**不继承** {@link com.teampulse.common.entity.BaseEntity}：DDL 里这张表没有 deleted 列。
 * 敏感词用 {@code status=active/inactive} 做软下线——词条要保留下来，
 * 因为它曾经影响过的历史评论原文还需要靠它复核；真正删除只用于「录错字」这种误操作。
 *
 * <p>时间字段由数据库默认值（DEFAULT CURRENT_TIMESTAMP / ON UPDATE）兜底，
 * 实体只做映射，不参与自动填充。
 */
@Data
@TableName("sensitive_word")
public class SensitiveWord {

    @TableId(value = "word_id", type = IdType.AUTO)
    private Long wordId;

    /** 敏感词本身，uk_word 唯一索引（默认 collation 大小写不敏感） */
    private String word;

    /** 命中后的替换文本，默认 *** */
    private String replacement;

    /** 状态：active 生效 / inactive 停用（停用后不再参与过滤，但历史记录保留） */
    private String status;

    /** 录入人 user_id */
    private Long creatorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
