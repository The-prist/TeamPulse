package com.teampulse.module.attendance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 考勤判定内核的单元测试。
 *
 * <p>为什么优先测这一块：判定口径是考勤系统里唯一「算错了也不会报错、但业务后果严重」的地方，
 * 而且它是纯函数，测试成本极低。这里把边界值全部固定下来，
 * 以后改规则（例如改宽限）如果破坏了口径，CI 会立刻红。
 */
class AttendanceCalculatorTest {

    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);
    private static final int GRACE = 5;

    private LocalDateTime at(int hour, int minute, int second) {
        return LocalDateTime.of(2025, 6, 3, hour, minute, second);
    }

    @Test
    @DisplayName("宽限内不算迟到")
    void withinGraceIsNotLate() {
        assertEquals(0, AttendanceCalculator.lateMinutes(at(9, 5, 0), WORK_START, GRACE));
    }

    @Test
    @DisplayName("迟到分钟数从上班时间起算，而不是从宽限点起算")
    void lateMinutesCountFromWorkStart() {
        // 9:08 打卡 → 迟到 8 分钟（不是 3 分钟）
        assertEquals(8, AttendanceCalculator.lateMinutes(at(9, 8, 0), WORK_START, GRACE));
    }

    @Test
    @DisplayName("早于上班时间不算迟到")
    void earlyArrivalIsNotLate() {
        assertEquals(0, AttendanceCalculator.lateMinutes(at(8, 40, 0), WORK_START, GRACE));
    }

    @Test
    @DisplayName("宽限为 0 时，晚一分钟即迟到")
    void zeroGraceMeansStrict() {
        assertEquals(1, AttendanceCalculator.lateMinutes(at(9, 1, 0), WORK_START, 0));
    }

    @Test
    @DisplayName("早退分钟数计算到下班时间为止")
    void earlyLeaveMinutes() {
        // 17:50 走 → 早退 10 分钟
        assertEquals(10, AttendanceCalculator.earlyLeaveMinutes(at(17, 50, 0), WORK_END, GRACE));
    }

    @Test
    @DisplayName("宽限内离开不算早退")
    void withinGraceIsNotEarlyLeave() {
        assertEquals(0, AttendanceCalculator.earlyLeaveMinutes(at(17, 56, 0), WORK_END, GRACE));
    }

    @Test
    @DisplayName("单次打卡状态：签到只看迟到，签退只看早退")
    void punchStatus() {
        assertEquals(AttendanceCalculator.STATUS_LATE,
                AttendanceCalculator.punchStatus(AttendanceCalculator.TYPE_CHECK_IN, 8, 0));
        assertEquals(AttendanceCalculator.STATUS_NORMAL,
                AttendanceCalculator.punchStatus(AttendanceCalculator.TYPE_CHECK_OUT, 0, 0));
        assertEquals(AttendanceCalculator.STATUS_EARLY_LEAVE,
                AttendanceCalculator.punchStatus(AttendanceCalculator.TYPE_CHECK_OUT, 0, 10));
    }

    @Test
    @DisplayName("日汇总状态：迟到且早退要合并成组合状态")
    void dailyStatus() {
        assertEquals(AttendanceCalculator.STATUS_NORMAL, AttendanceCalculator.dailyStatus(0, 0));
        assertEquals(AttendanceCalculator.STATUS_LATE, AttendanceCalculator.dailyStatus(8, 0));
        assertEquals(AttendanceCalculator.STATUS_EARLY_LEAVE, AttendanceCalculator.dailyStatus(0, 10));
        assertEquals(AttendanceCalculator.STATUS_LATE_AND_EARLY, AttendanceCalculator.dailyStatus(8, 10));
    }

    @Test
    @DisplayName("工作日判定按 ISO 星期：2025-06-03 是周二")
    void workDay() {
        LocalDate tuesday = LocalDate.of(2025, 6, 3);
        assertEquals(2, tuesday.getDayOfWeek().getValue());
        assertTrue(AttendanceCalculator.isWorkDay(tuesday, "1,2,3,4,5"));
        assertFalse(AttendanceCalculator.isWorkDay(tuesday, "6,7"));

        LocalDate saturday = LocalDate.of(2025, 6, 7);
        assertFalse(AttendanceCalculator.isWorkDay(saturday, "1,2,3,4,5"));
        assertTrue(AttendanceCalculator.isWorkDay(saturday, "6,7"));
    }

    @Test
    @DisplayName("签到签退：设备 AUTO 时当天第一条判签到，之后判签退")
    void resolvePunchType() {
        assertEquals(AttendanceCalculator.TYPE_CHECK_IN,
                AttendanceCalculator.resolvePunchType("AUTO", 0));
        assertEquals(AttendanceCalculator.TYPE_CHECK_OUT,
                AttendanceCalculator.resolvePunchType("AUTO", 1));
        // 设备显式指定时以设备为准
        assertEquals(AttendanceCalculator.TYPE_CHECK_IN,
                AttendanceCalculator.resolvePunchType("IN", 5));
        assertEquals(AttendanceCalculator.TYPE_CHECK_OUT,
                AttendanceCalculator.resolvePunchType("CHECK_OUT", 0));
    }

    @Test
    @DisplayName("空工作日配置不应把任何一天判为工作日")
    void blankWorkDays() {
        assertFalse(AttendanceCalculator.isWorkDay(LocalDate.of(2025, 6, 3), ""));
        assertFalse(AttendanceCalculator.isWorkDay(LocalDate.of(2025, 6, 3), null));
    }
}
