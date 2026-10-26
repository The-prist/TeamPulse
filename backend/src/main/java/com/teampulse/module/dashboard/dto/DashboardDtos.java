package com.teampulse.module.dashboard.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 概览统计的返回结构。
 *
 * <p>管理端（全公司口径）与个人端（我的口径）复用同一个 {@link OverviewVO}：
 * 前端只维护一张指标卡布局，靠「字段为 0 就不渲染」来区分两种视角，
 * 避免出现 AdminOverviewVO / MyOverviewVO 两套几乎一样、却要同步改两次的类。
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /**
     * 概览指标。
     *
     * <p>全部用基本类型 long 而不是 Long：指标为 0 也要有值可渲染，
     * 前端不必对 null 做兜底，接口也不会出现「字段缺失」这种情况。
     */
    @Data
    public static class OverviewVO {

        /** 用户总数（未删除） */
        private long totalUsers;

        /** 启用状态的用户数（status=active） */
        private long activeUsers;

        /** 团队总数 */
        private long totalTeams;

        /** 日程总数 */
        private long totalSchedules;

        /** 待办日程数（status=todo） */
        private long todoCount;

        /** 进行中日程数（status=doing） */
        private long doingCount;

        /** 已完成日程数（status=done） */
        private long doneCount;

        /** 已取消日程数（status=canceled）—— 与 todo/doing/done 共同构成完整分布 */
        private long canceledCount;

        /** 工作日志总数 */
        private long totalWorklogs;

        /** 看板总数 */
        private long totalKanbans;

        /** 在线设备数（iot_device.status=ONLINE） */
        private long deviceOnline;

        /** 今日打卡笔数（按 work_date 归日，一个人一天两次签到/签退算两笔） */
        private long todayPunchCount;

        /** 今日迟到笔数（late_minutes > 0 的打卡记录数） */
        private long todayLateCount;

        // ---------------- 个人视角专用（管理端为 0，前端据此决定是否渲染） ----------------

        /** 我参与的团队数（team_member.status=active） */
        private long myTeamCount;

        /** 我作为负责人的团队数（team_member.team_role=leader） */
        private long ledTeamCount;

        /** 我的最近工作日志（默认 5 条，仅个人视角返回） */
        private List<RecentWorklog> recentWorklogs = new ArrayList<>();

        /** 我即将开始的日程（默认 5 条，仅个人视角返回） */
        private List<UpcomingSchedule> upcomingSchedules = new ArrayList<>();

        /** 近 N 天趋势，前端直接当折线图数据源 */
        private List<DailyPoint> recentDays = new ArrayList<>();
    }

    /** 个人概览里的「最近工作日志」行 */
    @Data
    public static class RecentWorklog {
        private Long worklogId;
        private String title;
        private LocalDate logDate;
        private LocalDateTime createdAt;
    }

    /** 个人概览里的「即将开始的日程」行 */
    @Data
    public static class UpcomingSchedule {
        private Long scheduleId;
        private String title;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String status;
        private String priority;
        private Long teamId;
        private String teamName;
    }

    /** 折线图上的一个点：某一天的各条曲线取值 */
    @Data
    public static class DailyPoint {

        /** 日期，格式 yyyy-MM-dd，直接用作 X 轴标签 */
        private String date;

        /** 当天新建的日程数（按 created_at 归日） */
        private long scheduleCreated;

        /**
         * 当天完成的日程数。
         *
         * <p>口径说明：schedule 表没有「完成时间」字段，这里用 {@code status=done 且 updated_at 落在当天}
         * 来近似「这天被推进到完成」。副作用是「已完成」的日程之后被再次编辑（改标题等）会刷新
         * updated_at，导致完成日被顺延。这是当前表结构的已知限制，比前端凭空造数要诚实得多。
         */
        private long scheduleDone;

        /** 当天新建的工作日志数（按 created_at 归日） */
        private long worklogCreated;

        /** 当天打卡笔数（按 attendance_record.work_date 归日） */
        private long punchCount;
    }
}
