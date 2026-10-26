package com.teampulse.module.schedule.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teampulse.common.api.PageResult;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.kanban.service.KanbanService;
import com.teampulse.module.schedule.dto.ScheduleDtos;
import com.teampulse.module.schedule.entity.Schedule;
import com.teampulse.module.schedule.mapper.ScheduleMapper;
import com.teampulse.module.team.entity.Team;
import com.teampulse.module.team.mapper.TeamMapper;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import com.teampulse.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 日程领域服务。
 *
 * <p>三条贯穿全局的规则：
 * <ol>
 *   <li>时间区间必须有效（结束晚于开始），「只改一个端点」的重叠校验按合并后的结果做</li>
 *   <li>改内容/删除只允许创建人或 admin；状态流转放开给所有登录用户，因为看板拖拽要改状态</li>
 *   <li>creatorName / teamName / kanbanItemId 全部批量补齐，任何列表接口的 SQL 条数都与页大小无关</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final String STATUS_TODO = "todo";
    private static final String PRIORITY_MEDIUM = "medium";
    /** 单页上限，避免前端传超大 size 把整表拉走 */
    private static final long MAX_PAGE_SIZE = 200;
    /** 图表需要固定顺序的四条状态：缺失状态补 0，前端不用再判 undefined */
    private static final List<String> ALL_STATUSES = List.of("todo", "doing", "done", "canceled");

    private final ScheduleMapper scheduleMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    /**
     * 看板服务：日程与看板卡片是「实体与投影」的关系，状态变更需要双向对齐。
     * 依赖方向是 日程 → 看板（单向），看板侧只写原生 SQL、不反向依赖日程服务，因此不存在循环依赖。
     */
    private final KanbanService kanbanService;

    // ==================================================================
    // 一、查询
    // ==================================================================

    /**
     * 分页查询日程，按开始时间倒序（近期/未来的排前面）。
     *
     * <p>start_time 可能有重复（同一时刻的多个日程），再按 scheduleId 倒序作为兜底排序键，
     * 否则翻页时同一条记录可能在两页里重复出现。
     */
    public PageResult<ScheduleDtos.ScheduleVO> page(Long teamId, Long creatorId, String status,
                                                    String keyword, long page, long size) {
        return page(teamId, creatorId, status, keyword, null, null, null, null, "desc", page, size);
    }

    /**
     * 日程分页查询（管理端列表）。
     *
     * <p>筛选语义：
     * <ul>
     *   <li>{@code from/to}：按**开始时间**的日期部分过滤</li>
     *   <li>{@code timeFrom/timeTo}：按开始时间的时刻部分过滤，与日期是「且」关系</li>
     *   <li>{@code order}：desc=开始时间倒序（默认，新日程在上）/ asc=正序</li>
     * </ul>
     */
    public PageResult<ScheduleDtos.ScheduleVO> page(Long teamId, Long creatorId, String status,
                                                    String keyword, LocalDate from, LocalDate to,
                                                    String timeFrom, String timeTo, String order,
                                                    long page, long size) {
        long safePage = Math.max(1, page);
        // 之前没有上限，前端传 size=10000 会全表拉取
        long safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        boolean asc = "asc".equalsIgnoreCase(order);
        var wrapper = Wrappers.<Schedule>lambdaQuery()
                .orderBy(true, asc, Schedule::getStartTime)
                .orderBy(true, asc, Schedule::getScheduleId);
        if (teamId != null) {
            wrapper.eq(Schedule::getTeamId, teamId);
        }
        if (creatorId != null) {
            wrapper.eq(Schedule::getCreatorId, creatorId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Schedule::getStatus, validStatus(status.trim()));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 标题与地点一起匹配：用户通常记得「在哪个会议室」而不是完整标题
            wrapper.and(w -> w.like(Schedule::getTitle, kw).or().like(Schedule::getLocation, kw));
        }
        if (from != null) {
            wrapper.ge(Schedule::getStartTime, from.atStartOfDay());
        }
        if (to != null) {
            wrapper.lt(Schedule::getStartTime, to.plusDays(1).atStartOfDay());
        }
        String tf = normalizeTime(timeFrom, false);
        if (tf != null) {
            wrapper.apply("TIME(start_time) >= {0}", tf);
        }
        String tt = normalizeTime(timeTo, true);
        if (tt != null) {
            wrapper.apply("TIME(start_time) <= {0}", tt);
        }
        IPage<Schedule> result = scheduleMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<ScheduleDtos.ScheduleVO> rows = toVOs(result.getRecords());
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 规范时间段字符串；endOfMinute=true 时把 "HH:mm" 补成 "HH:mm:59"，
     * 否则「到 10:30」会漏掉 10:30:15 开始的日程。
     */
    private String normalizeTime(String raw, boolean endOfMinute) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String v = raw.trim();
        if (v.matches("\\d{2}:\\d{2}")) {
            return v + (endOfMinute ? ":59" : ":00");
        }
        if (v.matches("\\d{2}:\\d{2}:\\d{2}")) {
            return v;
        }
        throw BizException.of(ErrorCode.BAD_REQUEST, "时间段格式应为 HH:mm 或 HH:mm:ss");
    }

    public ScheduleDtos.ScheduleVO detail(Long id) {
        return toVOs(List.of(getById(id))).get(0);
    }

    // ==================================================================
    // 二、写操作
    // ==================================================================

    /** 创建日程：校验时间区间与团队存在性，状态固定从 todo 起步 */
    @Transactional
    public Schedule create(ScheduleDtos.CreateRequest req, Long creatorId) {
        assertTimeRange(req.getStartTime(), req.getEndTime());
        if (req.getTeamId() != null) {
            requireTeamExists(req.getTeamId());
        }

        Schedule schedule = new Schedule();
        schedule.setTitle(req.getTitle().trim());
        schedule.setContent(req.getContent());
        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setLocation(req.getLocation());
        schedule.setStatus(STATUS_TODO);
        schedule.setPriority(StringUtils.hasText(req.getPriority()) ? req.getPriority() : PRIORITY_MEDIUM);
        schedule.setCreatorId(creatorId);
        schedule.setTeamId(req.getTeamId());
        schedule.setRemindTime(req.getRemindTime());
        scheduleMapper.insert(schedule);

        log.info("创建日程: scheduleId={}, creatorId={}, teamId={}, start={}",
                schedule.getScheduleId(), creatorId, schedule.getTeamId(), schedule.getStartTime());
        return schedule;
    }

    /**
     * 更新日程：只覆盖非空字段，仅创建人或 admin。
     *
     * <p>时间校验必须用「合并后的」起止时间：只传 startTime 时若只拿新值和自己比，
     * 就会放过「新开始时间晚于库里已有结束时间」的倒流数据。
     */
    @Transactional
    public void update(Long id, ScheduleDtos.UpdateRequest req, Long operatorId) {
        Schedule schedule = getById(id);
        requireOwnerOrAdmin(schedule, operatorId);

        boolean timeTouched = req.getStartTime() != null || req.getEndTime() != null;
        if (timeTouched) {
            LocalDateTime start = req.getStartTime() != null ? req.getStartTime() : schedule.getStartTime();
            LocalDateTime end = req.getEndTime() != null ? req.getEndTime() : schedule.getEndTime();
            assertTimeRange(start, end);
            schedule.setStartTime(start);
            schedule.setEndTime(end);
        }
        if (StringUtils.hasText(req.getTitle())) {
            schedule.setTitle(req.getTitle().trim());
        }
        // 内容/地点用 != null 判断：允许传空串清空正文，hasText 会把它当「不修改」
        if (req.getContent() != null) {
            schedule.setContent(req.getContent());
        }
        if (req.getLocation() != null) {
            schedule.setLocation(req.getLocation());
        }
        if (StringUtils.hasText(req.getPriority())) {
            schedule.setPriority(req.getPriority());
        }
        if (req.getTeamId() != null) {
            requireTeamExists(req.getTeamId());
            schedule.setTeamId(req.getTeamId());
        }
        if (req.getRemindTime() != null) {
            schedule.setRemindTime(req.getRemindTime());
        }
        scheduleMapper.updateById(schedule);
        log.info("更新日程: scheduleId={}, operatorId={}", id, operatorId);
    }

    /**
     * 状态流转。
     *
     * <p>不做创建者校验：日程状态是团队协作中的共享事实，看板拖拽、负责人推进会签都要改它；
     * 真正的越权风险在「改内容/删日程」，那两处才收紧到创建人或 admin。
     */
    @Transactional
    public void updateStatus(Long id, String status) {
        Schedule schedule = getById(id);
        String targetStatus = validStatus(status);
        int affected = scheduleMapper.updateStatus(schedule.getScheduleId(), targetStatus);
        if (affected == 0) {
            // 期间被并发删除：按「资源不存在」返回，比静默成功更利于前端提示
            throw BizException.of(ErrorCode.NOT_FOUND, "日程不存在或已被删除: " + id);
        }
        // 正向联动：日程状态变了，看板上那张卡必须挪到对应列，否则同一个任务在两处状态不一致。
        // 同步失败不应阻断状态流转本身（看板可手动拖动纠正），因此吃掉异常并记日志。
        try {
            kanbanService.syncItemColumnByScheduleStatus(id, targetStatus);
        } catch (Exception ex) {
            log.warn("日程状态已更新，但同步看板卡片失败: scheduleId={}, status={}, reason={}",
                    id, targetStatus, ex.getMessage());
        }
        log.info("日程状态流转: scheduleId={}, {} -> {}", id, schedule.getStatus(), targetStatus);
    }

    @Transactional
    public void delete(Long id) {
        Schedule schedule = getById(id);
        Long operatorId = SecurityUtils.currentUserId();
        requireOwnerOrAdmin(schedule, operatorId);
        // 先清理看板卡片再删日程：卡片是日程的投影，主实体没了必须一起走
        kanbanService.removeItemsByScheduleId(id);
        scheduleMapper.deleteById(id);
        log.info("删除日程: scheduleId={}, operatorId={}", id, operatorId);
    }

    // ==================================================================
    // 三、统计（供概览/图表使用）
    // ==================================================================

    /** 某人创建的日程数 */
    public long countByCreator(Long creatorId) {
        Long count = scheduleMapper.selectCount(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getCreatorId, creatorId));
        return count == null ? 0L : count;
    }

    /**
     * 各状态日程数量，teamId 为空表示全公司口径。
     *
     * <p>只 {@code select status} 一列后在内存里计数：统计口径只有四个状态，
     * 为此写一条 GROUP BY 字符串 SQL 的收益不抵维护成本，而单列查询的数据量也可控。
     * 返回 LinkedHashMap 并按 todo→doing→done→canceled 初始化，
     * 缺失的状态直接是 0，前端图表不用再补空值。
     */
    public Map<String, Long> statusSummary(Long teamId) {
        List<Schedule> rows = scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                .select(Schedule::getStatus)
                .eq(teamId != null, Schedule::getTeamId, teamId));

        Map<String, Long> summary = new LinkedHashMap<>();
        ALL_STATUSES.forEach(status -> summary.put(status, 0L));
        rows.forEach(row -> summary.merge(row.getStatus(), 1L, Long::sum));
        return summary;
    }

    // ==================================================================
    // 四、内部工具
    // ==================================================================

    private Schedule getById(Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "日程不存在: " + id);
        }
        return schedule;
    }

    private void requireOwnerOrAdmin(Schedule schedule, Long operatorId) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        if (!schedule.getCreatorId().equals(operatorId)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "只有日程创建人或管理员可以执行该操作");
        }
    }

    /** 结束时间必须严格晚于开始时间：零时长日程会让日历视图与看板进度失去意义 */
    private void assertTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "开始时间与结束时间不能为空");
        }
        if (!end.isAfter(start)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
    }

    /** 状态值直接内嵌在枚举常量里校验，非法值按参数错误处理而不是查库后返回空 */
    private static String validStatus(String status) {
        String value = status == null ? "" : status.trim();
        if (!ALL_STATUSES.contains(value)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "状态只能是 todo/doing/done/canceled");
        }
        return value;
    }

    /** DDL 没有外键，团队归属的完整性只能由服务层兜住，避免出现指向不存在团队的日程 */
    private void requireTeamExists(Long teamId) {
        if (teamMapper.selectById(teamId) == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "所属团队不存在: " + teamId);
        }
    }

    /** 实体 → VO，批量补齐创建人姓名、团队名与看板卡片 ID（3 条附加 SQL，与页大小无关） */
    private List<ScheduleDtos.ScheduleVO> toVOs(List<Schedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return List.of();
        }
        Set<Long> creatorIds = schedules.stream()
                .map(Schedule::getCreatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> teamIds = schedules.stream()
                .map(Schedule::getTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = creatorIds.isEmpty()
                ? Map.<Long, User>of()
                : userMapper.selectBatchIds(creatorIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, Team> teamMap = teamIds.isEmpty()
                ? Map.<Long, Team>of()
                : teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(Team::getTeamId, Function.identity()));
        Map<Long, Long> kanbanItemMap = kanbanItemIds(schedules);

        List<ScheduleDtos.ScheduleVO> rows = new ArrayList<>(schedules.size());
        for (Schedule schedule : schedules) {
            ScheduleDtos.ScheduleVO vo = new ScheduleDtos.ScheduleVO();
            vo.setScheduleId(schedule.getScheduleId());
            vo.setTitle(schedule.getTitle());
            vo.setContent(schedule.getContent());
            vo.setStartTime(schedule.getStartTime());
            vo.setEndTime(schedule.getEndTime());
            vo.setLocation(schedule.getLocation());
            vo.setStatus(schedule.getStatus());
            vo.setPriority(schedule.getPriority());
            vo.setCreatorId(schedule.getCreatorId());
            vo.setTeamId(schedule.getTeamId());
            vo.setRemindTime(schedule.getRemindTime());
            vo.setCreatedAt(schedule.getCreatedAt());
            vo.setKanbanItemId(kanbanItemMap.get(schedule.getScheduleId()));
            User creator = schedule.getCreatorId() == null ? null : userMap.get(schedule.getCreatorId());
            // 创建人被逻辑删除时批量查询查不到，兜底成「用户{id}」保证列表不出现空白单元格
            vo.setCreatorName(creator != null ? displayName(creator) : "用户" + schedule.getCreatorId());
            Team team = schedule.getTeamId() == null ? null : teamMap.get(schedule.getTeamId());
            vo.setTeamName(team != null ? team.getTeamName() : null);
            rows.add(vo);
        }
        return rows;
    }

    /**
     * 批量查「日程 → 看板卡片」映射。
     *
     * <p>看板域只提供了按单条日程的写操作（状态同步、删除联动），没有批量查询接口；
     * 列表页要为每条日程补卡片 ID，走 N 次调用就是 N+1，因此这里用一条只读聚合 SQL
     * 直接取 kanban_item，并用 Number 兜住驱动返回的类型差异。
     */
    private Map<Long, Long> kanbanItemIds(List<Schedule> schedules) {
        List<Long> scheduleIds = schedules.stream()
                .map(Schedule::getScheduleId)
                .filter(Objects::nonNull)
                .toList();
        if (scheduleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : scheduleMapper.selectKanbanItemIds(scheduleIds)) {
            Object scheduleId = row.get("scheduleId");
            Object itemId = row.get("itemId");
            if (scheduleId instanceof Number sid && itemId instanceof Number iid) {
                result.put(sid.longValue(), iid.longValue());
            }
        }
        return result;
    }

    private static String displayName(User user) {
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
