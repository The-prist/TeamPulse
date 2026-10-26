package com.teampulse.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 补卡申请。
 *
 * <p>为什么要有这张表：打卡是考勤的**证据**，管理员直接改数据会让证据失去效力。
 * 企业里的做法是「员工申请 → 主管/HR 审批 → 通过后才生成记录」，
 * 审批链本身也是审计线索：谁批的、什么时候批的、备注是什么。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("makeup_request")
public class MakeupRequest extends BaseEntity {

    @TableId(value = "request_id", type = IdType.AUTO)
    private Long requestId;

    private Long userId;

    private String employeeNo;

    /** 申请补录的打卡时间 */
    private LocalDateTime punchTime;

    /** AUTO / CHECK_IN / CHECK_OUT */
    private String punchType;

    private String reason;

    /** pending / approved / rejected / canceled */
    private String status;

    private Long approverId;

    private LocalDateTime approveTime;

    private String approveRemark;

    /** 审批通过后生成的 attendance_record.id */
    private Long recordId;
}
