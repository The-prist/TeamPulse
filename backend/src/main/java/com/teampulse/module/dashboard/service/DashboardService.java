package com.teampulse.module.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teampulse.module.attendance.entity.AttendanceRecord;
import com.teampulse.module.attendance.entity.IotDevice;
import com.teampulse.module.attendance.mapper.AttendanceRecordMapper;
import com.teampulse.module.attendance.mapper.IotDeviceMapper;
import com.teampulse.module.dashboard.dto.DashboardDtos;
import com.teampulse.module.kanban.entity.Kanban;
import com.teampulse.module.kanban.mapper.KanbanMapper;
import com.teampulse.module.schedule.entity.Schedule;
import com.teampulse.module.schedule.mapper.ScheduleMapper;
import com.teampulse.module.team.entity.Team;
import com.teampulse.module.team.entity.TeamMember;
import com.teampulse.module.team.mapper.TeamMapper;
import com.teampulse.module.team.mapper.TeamMemberMapper;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import com.teampulse.module.worklog.entity.Worklog;
import com.teampulse.module.worklog.mapper.WorklogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 概览统计服务：把分散在各模块的计数聚合成首页需要的指标卡与趋势图。
 *
 * <p>为什么放在一个独立的只读服务里，而不是让各模块自己去累加：
 * 首页要一次拿到 7 个模块的口径，如果前端分 7 个接口拉，首屏会有 7 次往返，
 * 而且任何一个模块的超时都会让首页「半张卡片空白」。这里用一个接口聚合并保持只读，
 * 不会引入写事务，也不修改任何模块的数据。
 *
 * <p>统计口径（重要，前后端口径必须一致）：
 * <ul>
 *   <li>用户数：{@code user} 表未删除行；启用数按 status='active'。</li>
 *   <li>日程计数：按 {@code schedule.status} 直接分类，todo/doing/done 三档合计不等于总数，
 *       差额是 canceled —— 取消的日程不该出现在「待办/进行中/已完成」里，但计入总数。</li>
 *   <li>打卡笔数：按 {@code attendance_record.work_date} 归日（一人一天签到+签退算 2 笔）；
 *       迟到笔数是 late_minutes &gt; 0 的记录数，与考勤看板的判定口径完全一致。</li>
 *   <li>趋势：日程/日志按 {@code created_at} 归日（「当天创建了多少」），打卡按 {@code work_date} 归日。</li>
 *   <li>逻辑删除的表（user/team/schedule/worklog/kanban/iot_device）由 MyBatis-Plus 自动追加
 *       {@code deleted = 0}，因此计数天然排除已删除行；attendance_record 是只增不改的事件流，无删除概念。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 趋势窗口下限：0 天或负数会让区间倒挂，直接夹到 1 天 */
    private static final int MIN_DAYS = 1;
    /** 趋势窗口上限：90 天足够覆盖季度视图，再长就变成报表需求，不该压在首页接口上 */
    private static final int MAX_DAYS = 90;

    private static final String USER_STATUS_ACTIVE = "active";
    private static final String SCHEDULE_TODO = "todo";
    private static final String SCHEDULE_DOING = "doing";
    private static final String SCHEDULE_DONE = "done";
    private static final String SCHEDULE_CANCELED = "canceled";
    /** 团队内角色值：负责人（大写常量避免各处写字符串字面量） */
    private static final String TEAM_ROLE_LEADER = "leader";
    /** team_member.status 的生效值 */
    private static final String MEMBER_STATUS_ACTIVE = "active";
    /** 个人概览里「最近日志」「即将开始的日程」各取几条 */
    private static final int MY_LIST_LIMIT = 5;
    /** 设备在线状态值，与 iot_device.status 的枚举保持一致（大写，区别于用户表的 active） */
    private static final String DEVICE_ONLINE = "ONLINE";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ScheduleMapper scheduleMapper;
    private final WorklogMapper worklogMapper;
    private final KanbanMapper kanbanMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;
    private final IotDeviceMapper deviceMapper;

    // ==================================================================
    // 一、管理端：全公司口径
    // ==================================================================

    /**
     * 全公司概览。
     *
     * @param days 趋势窗口天数，越界时自动夹到 [1, 90]
     */
    public DashboardDtos.OverviewVO adminOverview(int days) {
        int safeDays = clampDays(days);
        LocalDate today = LocalDate.now();

        DashboardDtos.OverviewVO vo = new DashboardDtos.OverviewVO();
        vo.setTotalUsers(count(userMapper, Wrappers.<User>lambdaQuery()));
        vo.setActiveUsers(count(userMapper, Wrappers.<User>lambdaQuery()
                .eq(User::getStatus, USER_STATUS_ACTIVE)));
        vo.setTotalTeams(count(teamMapper, Wrappers.<Team>lambdaQuery()));

        vo.setTotalSchedules(count(scheduleMapper, Wrappers.<Schedule>lambdaQuery()));
        vo.setTodoCount(count(scheduleMapper, Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getStatus, SCHEDULE_TODO)));
        vo.setDoingCount(count(scheduleMapper, Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getStatus, SCHEDULE_DOING)));
        vo.setDoneCount(count(scheduleMapper, Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getStatus, SCHEDULE_DONE)));
        vo.setCanceledCount(count(scheduleMapper, Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getStatus, SCHEDULE_CANCELED)));

        vo.setTotalWorklogs(count(worklogMapper, Wrappers.<Worklog>lambdaQuery()));
        vo.setTotalKanbans(count(kanbanMapper, Wrappers.<Kanban>lambdaQuery()));
        vo.setDeviceOnline(count(deviceMapper, Wrappers.<IotDevice>lambdaQuery()
                .eq(IotDevice::getStatus, DEVICE_ONLINE)));

        vo.setTodayPunchCount(count(attendanceRecordMapper, Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getWorkDate, today)));
        // 迟到按「笔」统计：一次签到迟到就是 1 笔，不做按人去重，
        // 这样数字与考勤看板里逐条记录的迟到标记可以直接对上，不会出现两个页面数字打架
        vo.setTodayLateCount(count(attendanceRecordMapper, Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getWorkDate, today)
                .gt(AttendanceRecord::getLateMinutes, 0)));

        vo.setRecentDays(dailyTrend(safeDays, null));
        return vo;
    }

    // ==================================================================
    // 二、个人端：只看自己的数据
    // ==================================================================

    /**
     * 当前登录人的概览。
     *
     * <p>只统计「我的日程 / 我的日志 / 我的打卡」。公司级指标（用户数、团队数、看板数、设备数）
     * 在这里恒为 0：一来对普通成员没有意义，二来把全公司规模暴露给每个人属于越权信息，
     * 前端「我的概览」只渲染非零卡片即可。
     *
     * @param userId 当前登录人，取自 JWT
     * @param days   趋势窗口天数，越界时自动夹到 [1, 90]
     */
    public DashboardDtos.OverviewVO myOverview(Long userId, int days) {
        int safeDays = clampDays(days);
        LocalDate today = LocalDate.now();

        DashboardDtos.OverviewVO vo = new DashboardDtos.OverviewVO();
        vo.setTotalSchedules(count(scheduleMapper, Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getCreatorId, userId)));
        vo.setTodoCount(count(scheduleMapper, Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getCreatorId, userId)
                .eq(Schedule::getStatus, SCHEDULE_TODO)));
        vo.setDoingCount(count(scheduleMapper, Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getCreatorId, userId)
                .eq(Schedule::getStatus, SCHEDULE_DOING)));
        vo.setDoneCount(count(scheduleMapper, Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getCreatorId, userId)
                .eq(Schedule::getStatus, SCHEDULE_DONE)));
        vo.setCanceledCount(count(scheduleMapper, Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getCreatorId, userId)
                .eq(Schedule::getStatus, SCHEDULE_CANCELED)));
        vo.setTotalWorklogs(count(worklogMapper, Wrappers.<Worklog>lambdaQuery()
                .eq(Worklog::getCreatorId, userId)));

        // 团队口径走「我参与 / 我负责」，而不是全公司团队总数：
        // 全公司团队数对普通成员既无意义，也属于不该暴露的规模信息。
        vo.setMyTeamCount(count(teamMemberMapper, Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, MEMBER_STATUS_ACTIVE)));
        vo.setLedTeamCount(count(teamMemberMapper, Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, MEMBER_STATUS_ACTIVE)
                .eq(TeamMember::getTeamRole, TEAM_ROLE_LEADER)));

        vo.setRecentWorklogs(myRecentWorklogs(userId));
        vo.setUpcomingSchedules(myUpcomingSchedules(userId));

        // 个人打卡按 user_id 过滤而不是工号：换过工号的员工历史记录仍应算在自己头上
        vo.setTodayPunchCount(count(attendanceRecordMapper, Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getUserId, userId)
                .eq(AttendanceRecord::getWorkDate, today)));
        vo.setTodayLateCount(count(attendanceRecordMapper, Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getUserId, userId)
                .eq(AttendanceRecord::getWorkDate, today)
                .gt(AttendanceRecord::getLateMinutes, 0)));

        vo.setRecentDays(dailyTrend(safeDays, userId));
        return vo;
    }

    // ==================================================================
    // 三、趋势
    // ==================================================================

    /**
     * 近 N 天趋势。
     *
     * @param days   窗口天数，越界时自动夹到 [1, 90]
     * @param userId null 表示全公司口径；非 null 表示只看该用户创建的日程/日志与他的打卡
     * @return 按日期升序、逐天填充（没有数据的那天也会有一个 0 值点，折线图不会断档）
     */
    public List<DashboardDtos.DailyPoint> dailyTrend(int days, Long userId) {
        int safeDays = clampDays(days);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(safeDays - 1L);

        // 每张表只回源一次、在内存里按天聚合：
        // 逐天 selectCount 的话 90 天 × 3 张表 = 270 次往返，而这里固定 3 次。
        // 把聚合下推到 SQL（GROUP BY DATE(...)）需要在这三张表的 Mapper 里加 @Select，
        // 那些 Mapper 属于其他模块，本模块只做只读引用，不越界改别人的文件。
        Map<LocalDate, Long> scheduleByDay = groupByDateTime(
                scheduleMapper.selectList(scheduleCreatedRange(start, end, userId)),
                Schedule::getCreatedAt);
        Map<LocalDate, Long> worklogByDay = groupByDateTime(
                worklogMapper.selectList(worklogCreatedRange(start, end, userId)),
                Worklog::getCreatedAt);
        Map<LocalDate, Long> punchByDay = countPunchByDay(start, end, userId);
        // 「当天完成」用 status=done 且 updated_at 落在当天近似（表结构没有完成时间字段）
        Map<LocalDate, Long> doneByDay = groupByDateTime(
                scheduleMapper.selectList(scheduleDoneRange(start, end, userId)),
                Schedule::getUpdatedAt);

        List<DashboardDtos.DailyPoint> points = new ArrayList<>(safeDays);
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
            DashboardDtos.DailyPoint point = new DashboardDtos.DailyPoint();
            point.setDate(cursor.format(DATE_FORMAT));
            point.setScheduleCreated(scheduleByDay.getOrDefault(cursor, 0L));
            point.setScheduleDone(doneByDay.getOrDefault(cursor, 0L));
            point.setWorklogCreated(worklogByDay.getOrDefault(cursor, 0L));
            point.setPunchCount(punchByDay.getOrDefault(cursor, 0L));
            points.add(point);
        }
        return points;
    }

    /** 只取 created_at 一列，避免把日程正文等大字段读进内存 */
    private LambdaQueryWrapper<Schedule> scheduleCreatedRange(LocalDate start, LocalDate end, Long userId) {
        LambdaQueryWrapper<Schedule> wrapper = Wrappers.<Schedule>lambdaQuery()
                .select(Schedule::getCreatedAt)
                .ge(Schedule::getCreatedAt, start.atStartOfDay())
                // 用 < 次日零点而不是 <= 当日 23:59:59：毫秒级时间戳不会被漏掉
                .lt(Schedule::getCreatedAt, end.plusDays(1).atStartOfDay());
        if (userId != null) {
            wrapper.eq(Schedule::getCreatorId, userId);
        }
        return wrapper;
    }

    private LambdaQueryWrapper<Worklog> worklogCreatedRange(LocalDate start, LocalDate end, Long userId) {
        LambdaQueryWrapper<Worklog> wrapper = Wrappers.<Worklog>lambdaQuery()
                .select(Worklog::getCreatedAt)
                .ge(Worklog::getCreatedAt, start.atStartOfDay())
                .lt(Worklog::getCreatedAt, end.plusDays(1).atStartOfDay());
        if (userId != null) {
            wrapper.eq(Worklog::getCreatorId, userId);
        }
        return wrapper;
    }

    /** 只取 updated_at 一列：已完成日程按「最后更新时间」归日 */
    private LambdaQueryWrapper<Schedule> scheduleDoneRange(LocalDate start, LocalDate end, Long userId) {
        LambdaQueryWrapper<Schedule> wrapper = Wrappers.<Schedule>lambdaQuery()
                .select(Schedule::getUpdatedAt)
                .eq(Schedule::getStatus, SCHEDULE_DONE)
                .ge(Schedule::getUpdatedAt, start.atStartOfDay())
                .lt(Schedule::getUpdatedAt, end.plusDays(1).atStartOfDay());
        if (userId != null) {
            wrapper.eq(Schedule::getCreatorId, userId);
        }
        return wrapper;
    }

    // ==================================================================
    // 三、个人视角的两个小列表
    // ==================================================================

    /** 我最近写的工作日志（按创建时间倒序，取 5 条） */
    private List<DashboardDtos.RecentWorklog> myRecentWorklogs(Long userId) {
        List<Worklog> rows = worklogMapper.selectList(Wrappers.<Worklog>lambdaQuery()
                .select(Worklog::getWorklogId, Worklog::getTitle, Worklog::getLogDate, Worklog::getCreatedAt)
                .eq(Worklog::getCreatorId, userId)
                .orderByDesc(Worklog::getCreatedAt)
                .last("LIMIT " + MY_LIST_LIMIT));
        List<DashboardDtos.RecentWorklog> items = new ArrayList<>(rows.size());
        for (Worklog row : rows) {
            DashboardDtos.RecentWorklog item = new DashboardDtos.RecentWorklog();
            item.setWorklogId(row.getWorklogId());
            item.setTitle(row.getTitle());
            item.setLogDate(row.getLogDate());
            item.setCreatedAt(row.getCreatedAt());
            items.add(item);
        }
        return items;
    }

    /**
     * 我即将开始的日程。
     *
     * <p>取 {@code start_time >= 现在} 且未取消的日程，按开始时间升序取 5 条。
     * 团队名用一次 {@code selectBatchIds} 补齐，不逐行查库。
     */
    private List<DashboardDtos.UpcomingSchedule> myUpcomingSchedules(Long userId) {
        List<Schedule> rows = scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getCreatorId, userId)
                .ge(Schedule::getStartTime, LocalDateTime.now())
                .ne(Schedule::getStatus, SCHEDULE_CANCELED)
                .orderByAsc(Schedule::getStartTime)
                .last("LIMIT " + MY_LIST_LIMIT));
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<Long> teamIds = rows.stream()
                .map(Schedule::getTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> teamNames = teamIds.isEmpty()
                ? Map.of()
                : teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(Team::getTeamId, Team::getTeamName));

        List<DashboardDtos.UpcomingSchedule> items = new ArrayList<>(rows.size());
        for (Schedule row : rows) {
            DashboardDtos.UpcomingSchedule item = new DashboardDtos.UpcomingSchedule();
            item.setScheduleId(row.getScheduleId());
            item.setTitle(row.getTitle());
            item.setStartTime(row.getStartTime());
            item.setEndTime(row.getEndTime());
            item.setStatus(row.getStatus());
            item.setPriority(row.getPriority());
            item.setTeamId(row.getTeamId());
            item.setTeamName(row.getTeamId() == null ? null : teamNames.get(row.getTeamId()));
            items.add(item);
        }
        return items;
    }

    /** 打卡按 work_date 归日（不是 created_at）：补录昨天那次打卡应该落到昨天，而不是落库当天 */
    private Map<LocalDate, Long> countPunchByDay(LocalDate start, LocalDate end, Long userId) {
        LambdaQueryWrapper<AttendanceRecord> wrapper = Wrappers.<AttendanceRecord>lambdaQuery()
                .select(AttendanceRecord::getWorkDate)
                .ge(AttendanceRecord::getWorkDate, start)
                .le(AttendanceRecord::getWorkDate, end);
        if (userId != null) {
            wrapper.eq(AttendanceRecord::getUserId, userId);
        }
        Map<LocalDate, Long> result = new HashMap<>();
        for (AttendanceRecord record : attendanceRecordMapper.selectList(wrapper)) {
            if (record.getWorkDate() != null) {
                result.merge(record.getWorkDate(), 1L, Long::sum);
            }
        }
        return result;
    }

    // ==================================================================
    // 四、内部工具
    // ==================================================================

    /**
     * 统一的计数封装。
     *
     * <p>MyBatis-Plus 的 {@code selectCount} 返回 {@code Long}，理论上不会为 null，
     * 但一旦因为驱动或自定义 SQL 返回 null，指标卡就会显示 null / NPE；
     * 这里一次性兜底成 0，调用方就不用每处都判空。
     */
    private static <T> long count(BaseMapper<T> mapper, Wrapper<T> wrapper) {
        Long value = mapper.selectCount(wrapper);
        return value == null ? 0L : value;
    }

    /** 按「时间戳所在自然日」计数，忽略时间为空的行 */
    private static <T> Map<LocalDate, Long> groupByDateTime(List<T> rows, Function<T, LocalDateTime> timeGetter) {
        Map<LocalDate, Long> result = new HashMap<>(Math.max(16, rows.size()));
        for (T row : rows) {
            LocalDateTime time = timeGetter.apply(row);
            if (time != null) {
                result.merge(time.toLocalDate(), 1L, Long::sum);
            }
        }
        return result;
    }

    /**
     * 把请求参数里的 days 夹到 [1, 90]。
     *
     * <p>不夹的话：传 0 或负数会让区间倒挂、循环一次都不执行（前端拿到空数组却没有报错，最难排查）；
     * 传 3650 会让三张表各扫十年数据，一次请求就能把首页接口拖到超时。
     */
    private static int clampDays(int days) {
        return Math.min(Math.max(days, MIN_DAYS), MAX_DAYS);
    }
}
