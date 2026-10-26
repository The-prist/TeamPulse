package com.teampulse.security;

/**
 * 权限点常量。
 *
 * <p>注解里用字面量（{@code @PreAuthorize("hasAuthority('attendance:rule')")}）更直观，
 * 这个类的作用是**集中登记**所有权限点：新增接口时先在这里加一个常量，
 * 再去 V4 迁移里给角色配权限，避免"代码里用了但库里没有"的悬空权限。
 */
public final class Perms {

    private Perms() {
    }

    // 用户
    public static final String USER_VIEW = "user:view";
    public static final String USER_MANAGE = "user:manage";
    // 团队
    public static final String TEAM_VIEW = "team:view";
    public static final String TEAM_MANAGE = "team:manage";
    // 日程
    public static final String SCHEDULE_VIEW = "schedule:view";
    public static final String SCHEDULE_MANAGE = "schedule:manage";
    // 看板
    public static final String KANBAN_VIEW = "kanban:view";
    public static final String KANBAN_MANAGE = "kanban:manage";
    // 日志
    public static final String WORKLOG_VIEW = "worklog:view";
    public static final String WORKLOG_MANAGE = "worklog:manage";
    // 评论
    public static final String COMMENT_VIEW = "comment:view";
    public static final String COMMENT_MANAGE = "comment:manage";
    public static final String SENSITIVE_MANAGE = "sensitive:manage";
    // 考勤
    public static final String ATTENDANCE_VIEW = "attendance:view";
    public static final String ATTENDANCE_MANAGE = "attendance:manage";
    public static final String ATTENDANCE_RULE = "attendance:rule";
    public static final String ATTENDANCE_APPROVE = "attendance:approve";
    public static final String MAKEUP_SUBMIT = "makeup:submit";
    // 设备
    public static final String DEVICE_VIEW = "device:view";
    public static final String DEVICE_MANAGE = "device:manage";
    // 概览
    public static final String DASHBOARD_COMPANY = "dashboard:company";
    // 审计
    public static final String AUDIT_VIEW = "audit:view";
}
