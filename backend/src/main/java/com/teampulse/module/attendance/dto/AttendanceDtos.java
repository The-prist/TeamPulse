package com.teampulse.module.attendance.dto;

import com.teampulse.module.attendance.vo.AttendanceRecordVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 考勤与设备管理相关的 Web 请求体。
 */
public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    /** 手工补卡（管理员补录，source=MANUAL，不校验签名） */
    @Data
    public static class ManualPunchRequest {
        @NotBlank(message = "工号不能为空")
        private String employeeNo;

        /** 补卡时间，格式 yyyy-MM-dd HH:mm:ss */
        @NotBlank(message = "打卡时间不能为空")
        private String punchTime;

        @Pattern(regexp = "^(CHECK_IN|CHECK_OUT|AUTO)$", message = "打卡类型只能是 CHECK_IN/CHECK_OUT/AUTO")
        private String punchType = "AUTO";

        @Size(max = 255, message = "备注过长")
        private String remark;
    }

    /** 新增设备 */
    @Data
    public static class DeviceCreateRequest {
        @NotBlank(message = "设备序列号不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{4,64}$", message = "序列号只能包含字母数字下划线短横线，长度 4~64")
        private String deviceSn;

        @Size(max = 100)
        private String deviceName;

        @Size(max = 120)
        private String location;

        @Size(max = 255)
        private String remark;
    }

    /** 更新设备（不含密钥） */
    @Data
    public static class DeviceUpdateRequest {
        @Size(max = 100)
        private String deviceName;

        @Size(max = 120)
        private String location;

        @Size(max = 255)
        private String remark;

        @Pattern(regexp = "^(ONLINE|OFFLINE|DISABLED)$", message = "状态只能是 ONLINE/OFFLINE/DISABLED")
        private String status;
    }

    /** 考勤规则 */
    @Data
    public static class RuleUpdateRequest {
        @NotNull(message = "上班时间不能为空")
        private LocalTime workStart;

        @NotNull(message = "下班时间不能为空")
        private LocalTime workEnd;

        @Min(value = 0, message = "迟到宽限不能为负")
        @Max(value = 240, message = "迟到宽限不能超过 240 分钟")
        private Integer lateGraceMinutes = 0;

        @Min(value = 0, message = "早退宽限不能为负")
        @Max(value = 240, message = "早退宽限不能超过 240 分钟")
        private Integer earlyGraceMinutes = 0;

        @Pattern(regexp = "^[1-7](,[1-7])*$", message = "工作日格式应为 1,2,3 这样的 ISO 星期序列")
        private String workDays = "1,2,3,4,5";
    }

    /** 记录查询条件 */
    @Data
    public static class RecordQuery {
        private String employeeNo;
        private Long userId;
        /** 姓名/账号/工号的模糊关键字，由服务层解析成 userId 集合 */
        private String userName;
        private String status;
        private LocalDate from;
        private LocalDate to;
        private Long page = 1L;
        private Long size = 10L;
    }

    /** 考勤总览（看板顶部指标卡） */
    @Data
    public static class OverviewResponse {
        private LocalDate workDate;
        private long totalEmployees;
        private long checkedIn;
        private long lateCount;
        private long absentCount;
        private long onlineDevices;
        private long totalDevices;
        private LocalDateTime lastPunchTime;
    }

    /** 趋势折线：某一天的汇总 */
    @Data
    public static class DailyTrendItem {
        private LocalDate workDate;
        private long normalCount;
        private long lateCount;
        private long absentCount;
    }

    /**
     * 考勤日历的一格明细：某人某天的考勤结果。
     *
     * <p>与 {@link DailyTrendItem} 的区别：那个是「某天的合计」，这个是「某天某人」，
     * 供日历展开成「谁迟到了、谁缺勤」的具体名单。
     */
    @Data
    public static class CalendarDay {
        private Long userId;
        private String employeeNo;
        /** 姓名（昵称优先，没有则用户名）；用户被删时为「未知员工」 */
        private String userName;
        private LocalDate workDate;
        private LocalDateTime firstCheckIn;
        private LocalDateTime lastCheckOut;
        private Integer workMinutes;
        private Integer lateMinutes;
        private Integer earlyLeaveMinutes;
        /** NORMAL / LATE / EARLY_LEAVE / LATE_AND_EARLY / ABSENT */
        private String attendanceStatus;
    }

    /**
     * 单个员工的考勤详情（管理端点人名后弹出的抽屉用）。
     *
     * <p>一次返回三块内容，避免前端为一个人连打三个接口：
     * 汇总统计、按天汇总、原始打卡流水。
     */
    @Data
    public static class EmployeeAttendance {
        private Long userId;
        private String employeeNo;
        private String userName;
        private LocalDate from;
        private LocalDate to;
        private Summary summary;
        /** 按天汇总，日期正序 */
        private List<CalendarDay> days;
        /** 原始打卡流水，时间正序 */
        private List<AttendanceRecordVO> records;
    }

    /** 员工详情里的汇总数字 */
    @Data
    public static class Summary {
        /** 有考勤记录的天数（含缺勤） */
        private long workDays;
        private long normalCount;
        private long lateCount;
        private long earlyLeaveCount;
        private long absentCount;
        /** 在岗总分钟数 */
        private long workMinutes;
        /** 平均在岗分钟数（按有出勤的天数算，缺勤不计入分母） */
        private long avgWorkMinutes;
    }
}
