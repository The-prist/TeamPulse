package com.teampulse.module.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 补卡申请相关的请求体与视图对象。 */
public final class MakeupDtos {

    private MakeupDtos() {
    }

    /** 员工提交补卡申请 */
    @Data
    public static class SubmitRequest {

        /** 补录的打卡时间，yyyy-MM-dd HH:mm:ss */
        @NotBlank(message = "打卡时间不能为空")
        private String punchTime;

        @Pattern(regexp = "^(CHECK_IN|CHECK_OUT|AUTO)$", message = "打卡类型只能是 CHECK_IN/CHECK_OUT/AUTO")
        private String punchType = "AUTO";

        @NotBlank(message = "补卡原因不能为空")
        @Size(max = 255, message = "原因不能超过 255 字")
        private String reason;
    }

    /** 审批（通过 / 驳回） */
    @Data
    public static class ReviewRequest {

        private boolean approved;

        @Size(max = 255, message = "备注过长")
        private String remark;
    }

    /** 列表展示：申请表 + 申请人/审批人昵称 */
    @Data
    public static class MakeupVO {
        private Long requestId;
        private Long userId;
        private String userName;
        private String employeeNo;
        private LocalDateTime punchTime;
        private String punchType;
        private String reason;
        private String status;
        private Long approverId;
        private String approverName;
        private LocalDateTime approveTime;
        private String approveRemark;
        private Long recordId;
        private LocalDateTime createdAt;
    }
}
