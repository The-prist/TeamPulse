package com.teampulse.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤规则（当前实现为「全局单条生效」的简化模型，多规则可按部门扩展）。
 */
@Data
@TableName("attendance_rule")
public class AttendanceRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String ruleName;

    /** 上班时间，如 09:00:00 */
    private LocalTime workStart;

    /** 下班时间，如 18:00:00 */
    private LocalTime workEnd;

    /** 迟到宽限分钟：<= 该分钟数不算迟到 */
    private Integer lateGraceMinutes;

    /** 早退宽限分钟 */
    private Integer earlyGraceMinutes;

    /** 工作日，ISO 星期，1=周一 … 7=周日，逗号分隔 */
    private String workDays;

    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
