package com.teampulse.module.worklog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作日志的请求体与展示对象。
 *
 * <p>CreateRequest 里 title/content/logDate 必填：日志的价值就在于「哪天、写了什么」，
 * 这三个字段缺一个，列表与统计都没有意义；关联日程/团队是可选的补充信息。
 */
public final class WorklogDtos {

    private WorklogDtos() {
    }

    /** 新增日志 */
    @Data
    public static class CreateRequest {

        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题不能超过 200 字")
        private String title;

        @NotBlank(message = "内容不能为空")
        private String content;

        @NotNull(message = "所属日期不能为空")
        private LocalDate logDate;

        /** 关联日程，可为空 */
        private Long relatedScheduleId;

        /** 关联团队，可为空；按团队筛选日志时以它为准 */
        private Long relatedTeamId;
    }

    /** 更新日志：全部字段可空，null 表示不修改（与用户模块的更新语义保持一致） */
    @Data
    public static class UpdateRequest {

        @Size(max = 200, message = "标题不能超过 200 字")
        private String title;

        private String content;

        private LocalDate logDate;

        private Long relatedScheduleId;

        private Long relatedTeamId;
    }

    /**
     * 日志展示对象：作者名、团队名、日程标题由 Service 批量补齐。
     *
     * <p>补齐走「先收集 ID 集合，再批量查一次」，避免列表页 N+1；
     * 关联对象已被删除时对应字段为 null，前端按空值展示即可。
     */
    @Data
    public static class WorklogVO {

        private Long worklogId;

        private String title;

        private String content;

        private LocalDate logDate;

        private Long creatorId;

        /** 记录人昵称，昵称为空时回退到用户名 */
        private String creatorName;

        private Long relatedScheduleId;

        /** 关联日程标题 */
        private String scheduleTitle;

        private Long relatedTeamId;

        /** 关联团队名称 */
        private String teamName;

        private LocalDateTime createdAt;

        private LocalDateTime updatedAt;
    }
}
