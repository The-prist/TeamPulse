package com.teampulse.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤日汇总。
 *
 * <p>由打卡事件触发增量重算，或由每日定时任务兜底补齐：
 * 直接查明细表做报表在大数据量下会拖慢接口，汇总表把「按天看板」的查询成本降到一次主键查找。
 */
@Data
@TableName("attendance_daily")
public class AttendanceDaily {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String employeeNo;

    private LocalDate workDate;

    private LocalDateTime firstCheckIn;

    private LocalDateTime lastCheckOut;

    /** 在岗分钟数（最后一次打卡 - 第一次打卡） */
    private Integer workMinutes;

    private Integer lateMinutes;

    private Integer earlyLeaveMinutes;

    /** NORMAL / LATE / EARLY_LEAVE / LATE_AND_EARLY / ABSENT */
    private String attendanceStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
