package com.teampulse.module.attendance.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teampulse.module.attendance.entity.AttendanceDaily;
import com.teampulse.module.attendance.entity.AttendanceRecord;
import com.teampulse.module.attendance.entity.AttendanceRule;
import com.teampulse.module.attendance.mapper.AttendanceDailyMapper;
import com.teampulse.module.attendance.mapper.AttendanceRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考勤日汇总服务。
 *
 * <p>触发时机：
 * <ol>
 *   <li>每次打卡落库后立即重算当天汇总（保证看板实时）</li>
 *   <li>每日 00:05 定时补齐前一天（覆盖「只打了一次卡」「忘打卡」等场景）</li>
 * </ol>
 *
 * <p>为什么要有汇总表：明细表会随月份线性膨胀，报表页每次做 group by 都扫全表；
 * 汇总后「按月看板」只查 work_date 索引，单次查询稳定在毫秒级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceSummaryService {

    private final AttendanceRecordMapper recordMapper;
    private final AttendanceDailyMapper dailyMapper;
    private final AttendanceRuleService ruleService;

    /**
     * 重算某人某天的汇总。无有效打卡且是工作日则记为缺勤。
     */
    @Transactional
    public AttendanceDaily rebuild(Long userId, String employeeNo, LocalDate workDate) {
        List<AttendanceRecord> records = recordMapper.selectList(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getEmployeeNo, employeeNo)
                .eq(AttendanceRecord::getWorkDate, workDate)
                .ne(AttendanceRecord::getStatus, AttendanceCalculator.STATUS_INVALID)
                .orderByAsc(AttendanceRecord::getPunchTime));

        AttendanceRule rule = ruleService.getOrNull();
        boolean workDay = rule != null && AttendanceCalculator.isWorkDay(workDate, rule.getWorkDays());

        AttendanceDaily existing = dailyMapper.selectOne(Wrappers.<AttendanceDaily>lambdaQuery()
                .eq(AttendanceDaily::getUserId, userId)
                .eq(AttendanceDaily::getWorkDate, workDate)
                .last("LIMIT 1"));

        if (records.isEmpty()) {
            if (!workDay) {
                // 非工作日不产生汇总记录
                if (existing != null) {
                    dailyMapper.deleteById(existing.getId());
                }
                return null;
            }
            AttendanceDaily absent = existing != null ? existing : new AttendanceDaily();
            absent.setUserId(userId);
            absent.setEmployeeNo(employeeNo);
            absent.setWorkDate(workDate);
            absent.setFirstCheckIn(null);
            absent.setLastCheckOut(null);
            absent.setWorkMinutes(0);
            absent.setLateMinutes(0);
            absent.setEarlyLeaveMinutes(0);
            absent.setAttendanceStatus(AttendanceCalculator.STATUS_ABSENT);
            save(absent);
            return absent;
        }

        LocalDateTime first = records.get(0).getPunchTime();
        LocalDateTime last = records.get(records.size() - 1).getPunchTime();
        int late = records.stream()
                .filter(r -> AttendanceCalculator.TYPE_CHECK_IN.equals(r.getPunchType()))
                .mapToInt(r -> r.getLateMinutes() == null ? 0 : r.getLateMinutes())
                .max().orElse(0);
        int early = records.stream()
                .filter(r -> AttendanceCalculator.TYPE_CHECK_OUT.equals(r.getPunchType()))
                .mapToInt(r -> r.getEarlyLeaveMinutes() == null ? 0 : r.getEarlyLeaveMinutes())
                .max().orElse(0);
        int workMinutes = (int) Math.max(0, Duration.between(first, last).toMinutes());

        AttendanceDaily daily = existing != null ? existing : new AttendanceDaily();
        daily.setUserId(userId);
        daily.setEmployeeNo(employeeNo);
        daily.setWorkDate(workDate);
        daily.setFirstCheckIn(first);
        daily.setLastCheckOut(records.size() > 1 ? last : null);
        daily.setWorkMinutes(workMinutes);
        daily.setLateMinutes(late);
        daily.setEarlyLeaveMinutes(early);
        daily.setAttendanceStatus(AttendanceCalculator.dailyStatus(late, early));
        save(daily);
        return daily;
    }

    private void save(AttendanceDaily daily) {
        if (daily.getId() == null) {
            dailyMapper.insert(daily);
        } else {
            dailyMapper.updateById(daily);
        }
    }

    /** 定时任务入口：补齐指定日期的所有人汇总 */
    @Transactional
    public int rebuildAllForDate(LocalDate date, List<Worker> workers) {
        int count = 0;
        for (Worker worker : workers) {
            rebuild(worker.userId(), worker.employeeNo(), date);
            count++;
        }
        log.info("考勤日汇总完成: date={}, 覆盖员工数={}", date, count);
        return count;
    }

    /** 汇总所需的最小员工信息 */
    public record Worker(Long userId, String employeeNo) {
    }
}
