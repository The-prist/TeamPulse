package com.teampulse.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡记录（事件流，只增不改）。
 *
 * <p>幂等设计：
 * <ul>
 *   <li>{@code messageId} 唯一 —— 同一报文集重投不会重复落库</li>
 *   <li>{@code (employeeNo, punchTime)} 唯一 —— 同一人同一秒只能有一条记录</li>
 * </ul>
 */
@Data
@TableName("attendance_record")
public class AttendanceRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 匹配到的系统用户；工号不存在时为 null，记录仍保留以便排障 */
    private Long userId;

    private String employeeNo;

    private String deviceSn;

    /** CHECK_IN / CHECK_OUT */
    private String punchType;

    private LocalDateTime punchTime;

    /** 归属工作日（跨零点打卡时按班次归属，当前按自然日） */
    private LocalDate workDate;

    /** NORMAL / LATE / EARLY_LEAVE / LATE_AND_EARLY / INVALID */
    private String status;

    private Integer lateMinutes;

    private Integer earlyLeaveMinutes;

    /** MQTT / HTTP / MANUAL */
    private String source;

    private String messageId;

    /** 签名是否校验通过 */
    private Integer signVerified;

    /** 原始报文，便于事后审计与复现问题 */
    private String rawPayload;

    private String remark;

    private LocalDateTime createdAt;
}
