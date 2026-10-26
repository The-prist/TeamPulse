package com.teampulse.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作审计日志。
 *
 * <p>刻意不继承 {@code BaseEntity}：审计记录一旦写入就不允许修改或逻辑删除，
 * 因此没有 {@code updated_at} 与 {@code deleted} 字段。
 * 操作人昵称、角色都是**写入时的快照**，用户后来改名或调岗，历史记录依然反映当时的情况。
 */
@Data
@TableName("audit_log")
public class AuditLog implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long actorId;

    private String actorName;

    private String actorRole;

    private String module;

    private String action;

    private String resource;

    private String resourceId;

    private String method;

    private String path;

    private String detail;

    /** SUCCESS / FAILURE */
    private String result;

    private String errorMsg;

    private Integer costMs;

    private String ip;

    private String userAgent;

    private LocalDateTime createdAt;
}
