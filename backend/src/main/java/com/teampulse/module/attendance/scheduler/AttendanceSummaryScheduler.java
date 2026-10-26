package com.teampulse.module.attendance.scheduler;

import com.teampulse.module.attendance.service.AttendanceService;
import com.teampulse.module.attendance.service.AttendanceSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 考勤日汇总定时任务。
 *
 * <p>为什么打卡时已经实时重算，还要再跑一次批量任务：
 * <ul>
 *   <li>补「当天一次卡都没打」的缺勤记录——这类人没有事件触发，只能靠定时补</li>
 *   <li>修复接口异常/回滚导致的汇总缺失</li>
 *   <li>服务停机跨过零点时，第二天启动会由下一次任务补齐</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceSummaryScheduler {

    private final AttendanceService attendanceService;
    private final AttendanceSummaryService summaryService;

    /** 默认每天 00:05 汇总前一天（cron 可通过配置覆盖） */
    @Scheduled(cron = "${teampulse.attendance.daily-summary-cron:0 5 0 * * ?}")
    public void summarizeYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<AttendanceSummaryService.Worker> workers = attendanceService.attendanceWorkers();
        if (workers.isEmpty()) {
            log.debug("考勤汇总跳过：没有配置工号的在职员工");
            return;
        }
        summaryService.rebuildAllForDate(yesterday, workers);
    }
}
