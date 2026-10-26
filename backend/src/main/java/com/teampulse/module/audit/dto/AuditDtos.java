package com.teampulse.module.audit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

/** 审计日志的查询参数与视图对象。 */
public final class AuditDtos {

    private AuditDtos() {
    }

    /** 列表查询条件；所有字段可选，条件之间是「且」 */
    @Data
    public static class Query {
        /** 操作人 id */
        private Long actorId;
        /** 动作关键字，如 user.delete */
        private String action;
        /** 模块，如 user */
        private String module;
        /** SUCCESS / FAILURE */
        private String result;
        /** 时间区间（含当天） */
        private String from;
        private String to;
        /** traceId 精确查（排查某次请求时用） */
        private String traceId;

        @Min(1)
        private long page = 1;

        @Min(1)
        @Max(200)
        private long size = 20;
    }

    @Data
    public static class AuditVO {
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
        private String result;
        private String errorMsg;
        private Integer costMs;
        private String ip;
        private String userAgent;
        private LocalDateTime createdAt;
    }
}
