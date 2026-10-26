package com.teampulse.module.worklog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teampulse.common.api.PageResult;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import com.teampulse.module.worklog.dto.WorklogDtos;
import com.teampulse.module.worklog.entity.Worklog;
import com.teampulse.module.worklog.mapper.WorklogMapper;
import com.teampulse.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
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
 * 工作日志领域服务：增删改查 + 个人日志统计。
 *
 * <p>两个必须守住的口径：
 * <ol>
 *   <li><b>列表不产生 N+1</b>：作者名、团队名、日程标题都在一次分页查完后
 *       用 ID 集合批量回查再内存拼接（与考勤模块 enrich 的做法一致）。</li>
 *   <li><b>统计口径写死在这里</b>：「覆盖天数」按 logDate 去重计数（一天多条也只算一天），
 *       「平均字数」用正文字符数近似（中文按字符计，不引入分词/去空格），
 *       避免不同调用方各自实现算出不同结果。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorklogService {

    /** 单页上限，与 MyBatis-Plus 分页插件的 maxLimit 对齐 */
    private static final long MAX_PAGE_SIZE = 200;

    /** 统计默认窗口：近 30 天（含今天），覆盖 5 个工作周，足够看趋势 */
    private static final int DEFAULT_STAT_DAYS = 30;

    private final WorklogMapper worklogMapper;
    private final UserMapper userMapper;

    // ==================================================================
    // 一、查询
    // ==================================================================

    /**
     * 分页查询。creatorId / teamId / keyword / from-to / 时间段 / 排序 都是可选条件，
     * 传 creatorId 即为「我的日志」，不传则由调用方（管理员视图）看全量。
     *
     * <p>筛选语义：
     * <ul>
     *   <li>{@code from/to}：按**日志日期**（log_date）过滤</li>
     *   <li>{@code timeFrom/timeTo}：按**提交时间**（created_at 的时刻部分）过滤，
     *       与日期区间是「且」的关系。日志日期是 DATE 类型没有时刻，
     *       所以"时间段"只能落在提交时间上</li>
     *   <li>{@code order}：asc=日期正序（默认）/ desc=日期倒序，同日按 ID 同向</li>
     * </ul>
     */
    public PageResult<WorklogDtos.WorklogVO> page(Long creatorId, Long teamId, String keyword,
                                                  LocalDate from, LocalDate to,
                                                  String timeFrom, String timeTo, String order,
                                                  long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        boolean asc = !"desc".equalsIgnoreCase(order);
        LambdaQueryWrapper<Worklog> wrapper = Wrappers.<Worklog>lambdaQuery()
                .orderBy(true, asc, Worklog::getLogDate)
                .orderBy(true, asc, Worklog::getWorklogId);
        if (creatorId != null) {
            wrapper.eq(Worklog::getCreatorId, creatorId);
        }
        if (teamId != null) {
            wrapper.eq(Worklog::getRelatedTeamId, teamId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Worklog::getTitle, kw)
                    .or().like(Worklog::getContent, kw));
        }
        if (from != null) {
            wrapper.ge(Worklog::getLogDate, from);
        }
        if (to != null) {
            wrapper.le(Worklog::getLogDate, to);
        }
        String tf = normalizeTime(timeFrom, false);
        if (tf != null) {
            wrapper.apply("TIME(created_at) >= {0}", tf);
        }
        String tt = normalizeTime(timeTo, true);
        if (tt != null) {
            wrapper.apply("TIME(created_at) <= {0}", tt);
        }

        IPage<Worklog> result = worklogMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<WorklogDtos.WorklogVO> rows = enrich(result.getRecords());
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 规范时间段字符串。
     *
     * @param endOfMinute true 时把 "HH:mm" 补成 "HH:mm:59"，
     *                    否则「到 10:30」会把 10:30:15 的日志漏掉
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

    public WorklogDtos.WorklogVO detail(Long id) {
        // 单条详情复用批量补齐逻辑，保证列表与详情的字段口径完全一致
        return enrich(List.of(getById(id))).get(0);
    }

    // ==================================================================
    // 二、写操作
    // ==================================================================

    @Transactional
    public Worklog create(WorklogDtos.CreateRequest req, Long creatorId) {
        validateRelation(req.getRelatedTeamId(), req.getRelatedScheduleId());

        Worklog worklog = new Worklog();
        worklog.setTitle(req.getTitle().trim());
        worklog.setContent(req.getContent());
        worklog.setLogDate(req.getLogDate());
        worklog.setCreatorId(creatorId);
        worklog.setRelatedScheduleId(req.getRelatedScheduleId());
        worklog.setRelatedTeamId(req.getRelatedTeamId());
        worklogMapper.insert(worklog);
        log.info("新增工作日志: worklogId={}, creatorId={}, logDate={}",
                worklog.getWorklogId(), creatorId, worklog.getLogDate());
        return worklog;
    }

    /** 更新日志：仅本人或 admin；关联字段传 null 表示保持原值（不提供「清空关联」语义） */
    @Transactional
    public void update(Long id, WorklogDtos.UpdateRequest req, Long operatorId) {
        Worklog worklog = getById(id);
        requireOwnerOrAdmin(worklog.getCreatorId(), operatorId);
        validateRelation(req.getRelatedTeamId(), req.getRelatedScheduleId());

        if (StringUtils.hasText(req.getTitle())) {
            worklog.setTitle(req.getTitle().trim());
        }
        if (req.getContent() != null) {
            worklog.setContent(req.getContent());
        }
        if (req.getLogDate() != null) {
            worklog.setLogDate(req.getLogDate());
        }
        if (req.getRelatedScheduleId() != null) {
            worklog.setRelatedScheduleId(req.getRelatedScheduleId());
        }
        if (req.getRelatedTeamId() != null) {
            worklog.setRelatedTeamId(req.getRelatedTeamId());
        }
        worklogMapper.updateById(worklog);
    }

    /** 删除日志：仅本人或 admin，逻辑删除（误删可恢复） */
    @Transactional
    public void delete(Long id, Long operatorId) {
        Worklog worklog = getById(id);
        requireOwnerOrAdmin(worklog.getCreatorId(), operatorId);
        worklogMapper.deleteById(id);
        log.info("删除工作日志: worklogId={}, operatorId={}", id, operatorId);
    }

    // ==================================================================
    // 三、统计
    // ==================================================================

    /**
     * 日志统计：条数、覆盖天数、平均字数。
     *
     * <p>creatorId 为空时统计全体（管理员视图）；from/to 为空时按近 30 天兜底，
     * 保证接口永远有确定的时间窗口，不会因为漏传参数把历史全量拉出来。
     */
    public Map<String, Object> statistics(Long creatorId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_STAT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }

        // 只取统计需要的两列（日期 + 正文），不把整行日志（含长文本以外的列）带进内存
        var wrapper = Wrappers.<Worklog>lambdaQuery()
                .select(Worklog::getLogDate, Worklog::getContent)
                .ge(Worklog::getLogDate, start)
                .le(Worklog::getLogDate, end);
        if (creatorId != null) {
            wrapper.eq(Worklog::getCreatorId, creatorId);
        }
        List<Worklog> rows = worklogMapper.selectList(wrapper);

        long logCount = rows.size();
        long coveredDays = rows.stream()
                .map(Worklog::getLogDate)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long totalChars = rows.stream()
                .map(Worklog::getContent)
                .filter(Objects::nonNull)
                .mapToLong(String::length)
                .sum();
        // 保留一位小数：平均字数出现 123.4 比 123 更能说明「这段时间写得够不够细」
        double avgChars = logCount == 0 ? 0D : Math.round((double) totalChars / logCount * 10) / 10.0;

        Map<String, Object> stat = new LinkedHashMap<>(8);
        stat.put("creatorId", creatorId);
        stat.put("from", start);
        stat.put("to", end);
        stat.put("logCount", logCount);
        stat.put("coveredDays", coveredDays);
        stat.put("totalChars", totalChars);
        stat.put("avgChars", avgChars);
        return stat;
    }

    // ==================================================================
    // 四、内部工具
    // ==================================================================

    public Worklog getById(Long id) {
        Worklog worklog = worklogMapper.selectById(id);
        if (worklog == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "工作日志不存在: " + id);
        }
        return worklog;
    }

    /**
     * 批量补齐作者名、团队名、日程标题。
     *
     * <p>三次批量查询（用户 / 团队 / 日程）覆盖整页数据，任何情况下都不在循环里查库。
     */
    private List<WorklogDtos.WorklogVO> enrich(List<Worklog> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> creatorIds = rows.stream()
                .map(Worklog::getCreatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = creatorIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(creatorIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        Set<Long> teamIds = rows.stream()
                .map(Worklog::getRelatedTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> teamNames = teamNameMap(teamIds);

        Set<Long> scheduleIds = rows.stream()
                .map(Worklog::getRelatedScheduleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> scheduleTitles = scheduleTitleMap(scheduleIds);

        List<WorklogDtos.WorklogVO> result = new ArrayList<>(rows.size());
        for (Worklog row : rows) {
            WorklogDtos.WorklogVO vo = new WorklogDtos.WorklogVO();
            vo.setWorklogId(row.getWorklogId());
            vo.setTitle(row.getTitle());
            vo.setContent(row.getContent());
            vo.setLogDate(row.getLogDate());
            vo.setCreatorId(row.getCreatorId());
            User creator = row.getCreatorId() == null ? null : userMap.get(row.getCreatorId());
            vo.setCreatorName(creator == null ? null
                    : (StringUtils.hasText(creator.getNickname()) ? creator.getNickname() : creator.getUsername()));
            vo.setRelatedTeamId(row.getRelatedTeamId());
            vo.setTeamName(row.getRelatedTeamId() == null ? null : teamNames.get(row.getRelatedTeamId()));
            vo.setRelatedScheduleId(row.getRelatedScheduleId());
            vo.setScheduleTitle(row.getRelatedScheduleId() == null
                    ? null : scheduleTitles.get(row.getRelatedScheduleId()));
            vo.setCreatedAt(row.getCreatedAt());
            vo.setUpdatedAt(row.getUpdatedAt());
            result.add(vo);
        }
        return result;
    }

    /** team_id → team_name；空集合直接短路，避免拼出 `IN ()` 这种非法 SQL */
    private Map<Long, String> teamNameMap(Set<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (Map<String, Object> row : worklogMapper.selectTeamNames(teamIds)) {
            Long id = asLong(row.get("teamId"));
            if (id != null) {
                names.put(id, asString(row.get("teamName")));
            }
        }
        return names;
    }

    /** schedule_id → title */
    private Map<Long, String> scheduleTitleMap(Set<Long> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> titles = new HashMap<>();
        for (Map<String, Object> row : worklogMapper.selectScheduleTitles(scheduleIds)) {
            Long id = asLong(row.get("scheduleId"));
            if (id != null) {
                titles.put(id, asString(row.get("scheduleTitle")));
            }
        }
        return titles;
    }

    /**
     * 关联的团队 / 日程必须存在。
     *
     * <p>日志详情要展示团队名与日程标题，关联到不存在的 ID 只会得到一片空白，
     * 而且按团队筛选统计时这类日志会永久丢数据，所以在写入前就拦下来。
     */
    private void validateRelation(Long relatedTeamId, Long relatedScheduleId) {
        if (relatedTeamId != null && teamNameMap(Set.of(relatedTeamId)).isEmpty()) {
            throw BizException.of(ErrorCode.NOT_FOUND, "关联团队不存在: " + relatedTeamId);
        }
        if (relatedScheduleId != null && scheduleTitleMap(Set.of(relatedScheduleId)).isEmpty()) {
            throw BizException.of(ErrorCode.NOT_FOUND, "关联日程不存在: " + relatedScheduleId);
        }
    }

    /** 仅日志作者或 admin 可以修改/删除 */
    private void requireOwnerOrAdmin(Long creatorId, Long operatorId) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        if (operatorId == null || !operatorId.equals(creatorId)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "只有日志作者或管理员可以执行该操作");
        }
    }

    /** JDBC 对 BIGINT 可能返回 Long 也可能返回 BigInteger，统一收敛成 Long */
    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
