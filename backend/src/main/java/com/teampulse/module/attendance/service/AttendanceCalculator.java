package com.teampulse.module.attendance.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤判定内核：纯函数、无副作用、不依赖 Spring 容器，便于单元测试。
 *
 * <p>判定口径（与《考勤规则》页面的文案保持一致）：
 * <ul>
 *   <li>迟到：打卡时间晚于「上班时间 + 迟到宽限」，迟到分钟数从「上班时间」算起</li>
 *   <li>早退：打卡时间早于「下班时间 - 早退宽限」，早退分钟数算到「下班时间」为止</li>
 *   <li>宽限内不算异常，但分钟数不显示为负数</li>
 * </ul>
 */
public final class AttendanceCalculator {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_LATE = "LATE";
    public static final String STATUS_EARLY_LEAVE = "EARLY_LEAVE";
    public static final String STATUS_LATE_AND_EARLY = "LATE_AND_EARLY";
    public static final String STATUS_ABSENT = "ABSENT";
    public static final String STATUS_INVALID = "INVALID";

    public static final String TYPE_CHECK_IN = "CHECK_IN";
    public static final String TYPE_CHECK_OUT = "CHECK_OUT";

    private AttendanceCalculator() {
    }

    /**
     * 计算迟到分钟数。
     *
     * @param punchTime 实际打卡时间
     * @param workStart 规定上班时间
     * @param graceMinutes 宽限分钟
     * @return 迟到分钟数；未超过宽限返回 0
     */
    public static int lateMinutes(LocalDateTime punchTime, LocalTime workStart, int graceMinutes) {
        if (punchTime == null || workStart == null) {
            return 0;
        }
        LocalDateTime workStartAt = LocalDateTime.of(punchTime.toLocalDate(), workStart);
        LocalDateTime deadline = workStartAt.plusMinutes(Math.max(0, graceMinutes));
        if (punchTime.isAfter(deadline)) {
            return (int) Duration.between(workStartAt, punchTime).toMinutes();
        }
        return 0;
    }

    /**
     * 计算早退分钟数。
     */
    public static int earlyLeaveMinutes(LocalDateTime punchTime, LocalTime workEnd, int graceMinutes) {
        if (punchTime == null || workEnd == null) {
            return 0;
        }
        LocalDateTime workEndAt = LocalDateTime.of(punchTime.toLocalDate(), workEnd);
        LocalDateTime boundary = workEndAt.minusMinutes(Math.max(0, graceMinutes));
        if (punchTime.isBefore(boundary)) {
            return (int) Duration.between(punchTime, workEndAt).toMinutes();
        }
        return 0;
    }

    /** 单次打卡的状态 */
    public static String punchStatus(String punchType, int lateMinutes, int earlyLeaveMinutes) {
        if (TYPE_CHECK_IN.equals(punchType)) {
            return lateMinutes > 0 ? STATUS_LATE : STATUS_NORMAL;
        }
        return earlyLeaveMinutes > 0 ? STATUS_EARLY_LEAVE : STATUS_NORMAL;
    }

    /** 日汇总状态：同时迟到 + 早退时给出组合状态 */
    public static String dailyStatus(int lateMinutes, int earlyLeaveMinutes) {
        boolean late = lateMinutes > 0;
        boolean early = earlyLeaveMinutes > 0;
        if (late && early) {
            return STATUS_LATE_AND_EARLY;
        }
        if (late) {
            return STATUS_LATE;
        }
        if (early) {
            return STATUS_EARLY_LEAVE;
        }
        return STATUS_NORMAL;
    }

    /**
     * 判断某天是否为工作日。
     *
     * @param date      日期
     * @param workDays  ISO 星期序列，如 "1,2,3,4,5"（1=周一）
     */
    public static boolean isWorkDay(LocalDate date, String workDays) {
        if (date == null || workDays == null || workDays.isBlank()) {
            return false;
        }
        int isoWeekday = date.getDayOfWeek().getValue();
        for (String token : workDays.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty() && trimmed.equals(String.valueOf(isoWeekday))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据设备上报的类型与当天已有记录数，决定本次是签到还是签退。
     *
     * <p>规则：设备显式指定 IN/OUT 时以设备为准；AUTO 时「当天第一条为签到，之后均为签退」。
     */
    public static String resolvePunchType(String reportedType, long existingRecordsToday) {
        if (reportedType != null) {
            String t = reportedType.trim().toUpperCase();
            if ("CHECK_IN".equals(t) || "IN".equals(t)) {
                return TYPE_CHECK_IN;
            }
            if ("CHECK_OUT".equals(t) || "OUT".equals(t)) {
                return TYPE_CHECK_OUT;
            }
        }
        return existingRecordsToday == 0 ? TYPE_CHECK_IN : TYPE_CHECK_OUT;
    }
}
