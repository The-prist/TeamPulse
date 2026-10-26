package com.teampulse.module.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日程模块的请求体与视图对象。
 *
 * <p>时间字段一律用 {@link LocalDateTime}：Jackson 已按 ISO-8601（{@code 2024-05-01T09:00:00}）
 * 序列化，前端不需要再自己拼 {@code yyyy-MM-dd HH:mm} 字符串，也就不会出现格式漂移。
 */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    /** 创建日程 */
    @Data
    public static class CreateRequest {

        @NotBlank(message = "日程标题不能为空")
        @Size(max = 200, message = "日程标题不能超过 200 字")
        private String title;

        private String content;

        @NotNull(message = "开始时间不能为空")
        private LocalDateTime startTime;

        @NotNull(message = "结束时间不能为空")
        private LocalDateTime endTime;

        @Size(max = 200, message = "地点不能超过 200 字")
        private String location;

        /** 优先级，缺省为 medium，与数据库默认值保持一致 */
        @Pattern(regexp = "^(low|medium|high)$", message = "优先级只能是 low/medium/high")
        private String priority = "medium";

        /** 所属团队，可为空：空表示个人日程 */
        private Long teamId;

        /** 提醒时间，可为空 */
        private LocalDateTime remindTime;
    }

    /**
     * 更新日程：所有字段可空，null 表示「不修改」。
     *
     * <p>代价是无法通过本接口把 teamId / remindTime 改回 null（清空）；
     * 相对于「字段缺省即清空」会误删数据，这里选择更保守的语义。
     */
    @Data
    public static class UpdateRequest {

        @Size(max = 200, message = "日程标题不能超过 200 字")
        private String title;

        private String content;

        private LocalDateTime startTime;

        private LocalDateTime endTime;

        @Size(max = 200, message = "地点不能超过 200 字")
        private String location;

        @Pattern(regexp = "^(low|medium|high)$", message = "优先级只能是 low/medium/high")
        private String priority;

        private Long teamId;

        private LocalDateTime remindTime;
    }

    /** 状态流转（看板拖拽与日程页按钮共用） */
    @Data
    public static class StatusUpdateRequest {

        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "^(todo|doing|done|canceled)$", message = "状态只能是 todo/doing/done/canceled")
        private String status;
    }

    /**
     * 日程视图对象。
     *
     * <p>{@code creatorName} / {@code teamName} 是展示用的派生字段，由服务层批量补齐；
     * {@code kanbanItemId} 可空——只有被投影到看板上的日程才有卡片，
     * 前端据此决定是否展示「查看看板卡片」入口。
     */
    @Data
    public static class ScheduleVO {

        private Long scheduleId;
        private String title;
        private String content;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String location;
        /** 状态：todo / doing / done / canceled */
        private String status;
        /** 优先级：low / medium / high */
        private String priority;
        private Long creatorId;
        private String creatorName;
        /** 所属团队，为 null 表示个人日程 */
        private Long teamId;
        private String teamName;
        private LocalDateTime remindTime;
        /** 对应的看板卡片 ID，未投影到看板时为 null */
        private Long kanbanItemId;
        private LocalDateTime createdAt;
    }
}
