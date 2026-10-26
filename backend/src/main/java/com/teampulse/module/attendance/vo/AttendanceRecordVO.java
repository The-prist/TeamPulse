package com.teampulse.module.attendance.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡记录展示对象：在明细字段基础上补齐姓名与设备位置，
 * 避免前端为每行再发一次请求（N+1）。
 */
@Data
public class AttendanceRecordVO {

    private Long id;
    private Long userId;
    private String employeeNo;
    private String userName;
    private String deviceSn;
    private String deviceLocation;
    private String punchType;
    private LocalDateTime punchTime;
    private LocalDate workDate;
    private String status;
    private Integer lateMinutes;
    private Integer earlyLeaveMinutes;
    private String source;
    private Integer signVerified;
    private String remark;
    private LocalDateTime createdAt;
}
