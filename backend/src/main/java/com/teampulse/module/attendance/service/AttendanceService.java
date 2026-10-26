package com.teampulse.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampulse.common.api.PageResult;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.attendance.config.AttendanceProperties;
import com.teampulse.module.attendance.dto.AttendanceDtos;
import com.teampulse.module.attendance.dto.DeviceMessages;
import com.teampulse.module.attendance.entity.AttendanceDaily;
import com.teampulse.module.attendance.entity.AttendanceRecord;
import com.teampulse.module.attendance.entity.AttendanceRule;
import com.teampulse.module.attendance.entity.IotDevice;
import com.teampulse.module.attendance.mapper.AttendanceDailyMapper;
import com.teampulse.module.attendance.mapper.AttendanceRecordMapper;
import com.teampulse.module.attendance.mapper.IotDeviceMapper;
import com.teampulse.module.attendance.vo.AttendanceRecordVO;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
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
 * 考勤核心服务：打卡处理、手工补卡、记录查询、看板统计。
 *
 * <p>打卡处理是本项目最需要「讲清楚」的一条链路，顺序如下：
 * <ol>
 *   <li>报文基础字段校验</li>
 *   <li>设备存在性 / 是否禁用</li>
 *   <li>设备时钟漂移检查（防止把设备时间错乱记成考勤）</li>
 *   <li>HMAC 签名校验（防伪造）</li>
 *   <li>幂等检查（messageId / 员工+时间）+ nonce 防重放</li>
 *   <li>工号匹配系统用户（未匹配仍留痕，状态 INVALID）</li>
 *   <li>判定签到/签退，并按考勤规则计算迟到/早退分钟数</li>
 *   <li>落库 + 立即重算当日汇总 + 下发 MQTT 回执</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final DateTimeFormatter PUNCH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SOURCE_MANUAL = "MANUAL";

    private final AttendanceRecordMapper recordMapper;
    private final AttendanceDailyMapper dailyMapper;
    private final IotDeviceMapper deviceMapper;
    private final UserMapper userMapper;
    private final AttendanceRuleService ruleService;
    private final AttendanceSummaryService summaryService;
    private final DeviceService deviceService;
    private final DeviceSignatureVerifier signatureVerifier;
    private final NonceDedupService nonceDedupService;
    private final AckPublisher ackPublisher;
    private final AttendanceProperties properties;
    private final ObjectMapper objectMapper;

    // ==================================================================
    // 一、打卡主链路
    // ==================================================================

    /**
     * 处理一条设备打卡报文。
     *
     * <p>除「报文无法解析」这类致命错误外，业务性失败也以 {@link DeviceMessages.AckMessage}
     * 的 code 返回，而不是抛异常——设备需要明确知道成功/失败原因。
     */
    @Transactional
    public DeviceMessages.AckMessage handlePunch(DeviceMessages.PunchMessage message, String source) {
        String deviceSn = trimToNull(message.getDeviceSn());
        String employeeNo = trimToNull(message.getEmployeeNo());
        String punchTimeText = trimToNull(message.getPunchTime());

        if (deviceSn == null || employeeNo == null || punchTimeText == null) {
            throw BizException.of(ErrorCode.DEVICE_PAYLOAD_INVALID, "deviceSn/employeeNo/punchTime 均为必填");
        }

        LocalDateTime punchTime;
        try {
            punchTime = LocalDateTime.parse(punchTimeText, PUNCH_TIME_FORMAT);
        } catch (Exception ex) {
            throw BizException.of(ErrorCode.DEVICE_PAYLOAD_INVALID, "punchTime 格式应为 yyyy-MM-dd HH:mm:ss");
        }

        // --- 设备校验 ---
        IotDevice device = deviceService.findBySnWithSecret(deviceSn);
        if (device == null) {
            throw BizException.of(ErrorCode.DEVICE_NOT_REGISTERED, "设备未注册: " + deviceSn);
        }
        if ("DISABLED".equalsIgnoreCase(device.getStatus())) {
            throw BizException.of(ErrorCode.DEVICE_DISABLED, "设备已被禁用: " + deviceSn);
        }

        // --- 时钟漂移：设备没做 NTP 时时间会越来越偏，必须拦在入库前 ---
        long driftSeconds = Math.abs(Duration.between(punchTime, LocalDateTime.now()).getSeconds());
        if (driftSeconds > properties.getClockDriftSeconds()) {
            throw BizException.of(ErrorCode.DEVICE_CLOCK_DRIFT,
                    "设备时间与服务器相差 " + driftSeconds + " 秒，超出允许范围 "
                            + properties.getClockDriftSeconds() + " 秒");
        }

        // --- 验签 ---
        boolean signVerified = false;
        if (properties.isSignEnabled()) {
            String payload = signatureVerifier.canonicalPunch(deviceSn, employeeNo, punchTimeText,
                    message.getNonce());
            signVerified = signatureVerifier.verify(device.getSecret(), payload, message.getSign());
            if (!signVerified) {
                throw BizException.of(ErrorCode.DEVICE_SIGN_INVALID);
            }
        }

        // --- 幂等：同一报文重复投递直接返回已有结果 ---
        String messageId = resolveMessageId(message, deviceSn, employeeNo, punchTimeText);
        AttendanceRecord existing = recordMapper.selectOne(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getMessageId, messageId)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("重复报文，直接返回既有结果: messageId={}", messageId);
            return buildAck(existing, 0, "duplicate message, already processed");
        }

        // 同一员工同一秒只允许一条记录（数据库唯一索引兜底，这里先给出友好提示）
        boolean sameSecond = recordMapper.exists(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getEmployeeNo, employeeNo)
                .eq(AttendanceRecord::getPunchTime, punchTime));
        if (sameSecond) {
            throw BizException.of(ErrorCode.ATTENDANCE_DUPLICATE, "该员工在该时刻已有打卡记录");
        }

        // --- 防重放 ---
        String nonce = trimToNull(message.getNonce());
        if (nonce != null && !nonceDedupService.firstUse(deviceSn + ":" + nonce, properties.getNonceTtl())) {
            throw BizException.of(ErrorCode.DEVICE_REPLAY);
        }

        LocalDate workDate = punchTime.toLocalDate();
        User user = userMapper.findByEmployeeNo(employeeNo);

        // --- 判定签到 / 签退 ---
        long todayRecords = recordMapper.selectCount(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getEmployeeNo, employeeNo)
                .eq(AttendanceRecord::getWorkDate, workDate));
        String punchType = AttendanceCalculator.resolvePunchType(message.getPunchType(), todayRecords);

        AttendanceRule rule = ruleService.getOrNull();
        int lateMinutes = 0;
        int earlyLeaveMinutes = 0;
        String status;
        String remark = null;

        if (user == null) {
            // 工号未注册：仍然留痕，方便现场排查「卡机识别到了但系统没这个人」
            status = AttendanceCalculator.STATUS_INVALID;
            remark = "工号未在系统中注册";
        } else if (rule == null) {
            status = AttendanceCalculator.STATUS_INVALID;
            remark = "未配置考勤规则，无法判定迟到/早退";
        } else {
            if (AttendanceCalculator.TYPE_CHECK_IN.equals(punchType)) {
                lateMinutes = AttendanceCalculator.lateMinutes(punchTime, rule.getWorkStart(),
                        nvl(rule.getLateGraceMinutes()));
            } else {
                earlyLeaveMinutes = AttendanceCalculator.earlyLeaveMinutes(punchTime, rule.getWorkEnd(),
                        nvl(rule.getEarlyGraceMinutes()));
            }
            status = AttendanceCalculator.punchStatus(punchType, lateMinutes, earlyLeaveMinutes);
            if (!AttendanceCalculator.isWorkDay(workDate, rule.getWorkDays())) {
                remark = "非工作日打卡";
            }
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setUserId(user != null ? user.getUserId() : null);
        record.setEmployeeNo(employeeNo);
        record.setDeviceSn(deviceSn);
        record.setPunchType(punchType);
        record.setPunchTime(punchTime);
        record.setWorkDate(workDate);
        record.setStatus(status);
        record.setLateMinutes(lateMinutes);
        record.setEarlyLeaveMinutes(earlyLeaveMinutes);
        record.setSource(source);
        record.setMessageId(messageId);
        record.setSignVerified(signVerified ? 1 : 0);
        record.setRemark(remark);
        record.setCreatedAt(LocalDateTime.now());
        record.setRawPayload(writeJsonQuietly(message));
        recordMapper.insert(record);

        // 设备指纹分数只做审计，不参与判定；需要时可在日志中回溯
        if (message.getMatchScore() != null) {
            log.debug("打卡报文携带识别分数: employeeNo={}, score={}", employeeNo, message.getMatchScore());
        }

        // --- 立即重算当日汇总，保证前端轮询看到的就是最新状态 ---
        if (user != null) {
            summaryService.rebuild(user.getUserId(), employeeNo, workDate);
        }

        deviceService.touchHeartbeat(deviceSn, message.getFirmwareVersion(), null);

        int ackCode = user == null ? ErrorCode.EMPLOYEE_NOT_FOUND.getCode() : 0;
        String ackMessage = user == null ? "工号未注册，记录已留痕" : "打卡成功";
        log.info("打卡入库: employeeNo={}, deviceSn={}, type={}, status={}, late={}, early={}",
                employeeNo, deviceSn, punchType, status, lateMinutes, earlyLeaveMinutes);

        DeviceMessages.AckMessage ack = buildAck(record, ackCode, ackMessage);
        ackPublisher.publish(ack);
        return ack;
    }

    /** 处理设备心跳：刷新在线状态 */
    public void handleHeartbeat(DeviceMessages.HeartbeatMessage message) {
        String deviceSn = trimToNull(message.getDeviceSn());
        if (deviceSn == null) {
            throw BizException.of(ErrorCode.DEVICE_PAYLOAD_INVALID, "deviceSn 不能为空");
        }
        IotDevice device = deviceService.findBySnWithSecret(deviceSn);
        if (device == null) {
            throw BizException.of(ErrorCode.DEVICE_NOT_REGISTERED, "设备未注册: " + deviceSn);
        }
        if (properties.isSignEnabled()) {
            String payload = signatureVerifier.canonicalHeartbeat(deviceSn, message.getNonce());
            if (!signatureVerifier.verify(device.getSecret(), payload, message.getSign())) {
                throw BizException.of(ErrorCode.DEVICE_SIGN_INVALID);
            }
        }
        deviceService.touchHeartbeat(deviceSn, message.getFirmwareVersion(), message.getIp());
    }

    // ==================================================================
    // 二、手工补卡
    // ==================================================================

    @Transactional
    public AttendanceRecord manualPunch(AttendanceDtos.ManualPunchRequest request) {
        String employeeNo = request.getEmployeeNo().trim();
        User user = userMapper.findByEmployeeNo(employeeNo);
        if (user == null) {
            throw BizException.of(ErrorCode.EMPLOYEE_NOT_FOUND, "工号不存在: " + employeeNo);
        }
        LocalDateTime punchTime;
        try {
            punchTime = LocalDateTime.parse(request.getPunchTime().trim(), PUNCH_TIME_FORMAT);
        } catch (Exception ex) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "打卡时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
        LocalDate workDate = punchTime.toLocalDate();

        long todayRecords = recordMapper.selectCount(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getEmployeeNo, employeeNo)
                .eq(AttendanceRecord::getWorkDate, workDate));
        String punchType = AttendanceCalculator.resolvePunchType(request.getPunchType(), todayRecords);

        AttendanceRule rule = ruleService.currentRule();
        int lateMinutes = 0;
        int earlyLeaveMinutes = 0;
        if (AttendanceCalculator.TYPE_CHECK_IN.equals(punchType)) {
            lateMinutes = AttendanceCalculator.lateMinutes(punchTime, rule.getWorkStart(),
                    nvl(rule.getLateGraceMinutes()));
        } else {
            earlyLeaveMinutes = AttendanceCalculator.earlyLeaveMinutes(punchTime, rule.getWorkEnd(),
                    nvl(rule.getEarlyGraceMinutes()));
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setUserId(user.getUserId());
        record.setEmployeeNo(employeeNo);
        record.setDeviceSn("MANUAL");
        record.setPunchType(punchType);
        record.setPunchTime(punchTime);
        record.setWorkDate(workDate);
        record.setStatus(AttendanceCalculator.punchStatus(punchType, lateMinutes, earlyLeaveMinutes));
        record.setLateMinutes(lateMinutes);
        record.setEarlyLeaveMinutes(earlyLeaveMinutes);
        record.setSource(SOURCE_MANUAL);
        record.setMessageId("MANUAL-" + employeeNo + "-" + punchTime.format(PUNCH_TIME_FORMAT));
        record.setSignVerified(1);
        record.setRemark(request.getRemark());
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);

        summaryService.rebuild(user.getUserId(), employeeNo, workDate);
        log.info("手工补卡: employeeNo={}, punchTime={}, type={}", employeeNo, punchTime, punchType);
        return record;
    }

    // ==================================================================
    // 三、查询与统计
    // ==================================================================

    public PageResult<AttendanceRecordVO> pageRecords(AttendanceDtos.RecordQuery query) {
        long size = Math.min(Math.max(1, query.getSize() == null ? 10 : query.getSize()), 200);
        long page = Math.max(1, query.getPage() == null ? 1 : query.getPage());

        LambdaQueryWrapper<AttendanceRecord> wrapper = Wrappers.<AttendanceRecord>lambdaQuery()
                .orderByDesc(AttendanceRecord::getPunchTime);
        if (StringUtils.hasText(query.getEmployeeNo())) {
            wrapper.like(AttendanceRecord::getEmployeeNo, query.getEmployeeNo().trim());
        }
        if (query.getUserId() != null) {
            wrapper.eq(AttendanceRecord::getUserId, query.getUserId());
        }
        // 姓名模糊查询：打卡表里只有 userId/工号，没有姓名列，
        // 所以先在 user 表按 昵称/账号/工号 模糊匹配出 userId，再用 in 过滤。
        // 匹配不到时直接返回空页，避免生成 `IN ()` 这种非法 SQL。
        if (StringUtils.hasText(query.getUserName())) {
            String kw = query.getUserName().trim();
            List<Long> matchedIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
                            .and(w -> w.like(User::getNickname, kw)
                                    .or().like(User::getUsername, kw)
                                    .or().like(User::getEmployeeNo, kw)))
                    .stream()
                    .map(User::getUserId)
                    .toList();
            if (matchedIds.isEmpty()) {
                return PageResult.of(List.of(), 0, page, size);
            }
            wrapper.in(AttendanceRecord::getUserId, matchedIds);
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AttendanceRecord::getStatus, query.getStatus());
        }
        if (query.getFrom() != null) {
            wrapper.ge(AttendanceRecord::getWorkDate, query.getFrom());
        }
        if (query.getTo() != null) {
            wrapper.le(AttendanceRecord::getWorkDate, query.getTo());
        }

        IPage<AttendanceRecord> result = recordMapper.selectPage(new Page<>(page, size), wrapper);
        List<AttendanceRecordVO> rows = enrich(result.getRecords());
        return PageResult.of(rows, result.getTotal(), page, size);
    }

    /** 实时打卡流：前端每 5 秒轮询一次，取最近 N 条 */
    public List<AttendanceRecordVO> recentPunches(int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 50);
        List<AttendanceRecord> records = recordMapper.selectList(Wrappers.<AttendanceRecord>lambdaQuery()
                .orderByDesc(AttendanceRecord::getPunchTime)
                .last("LIMIT " + safeLimit));
        return enrich(records);
    }

    /** 看板顶部指标卡 */
    public AttendanceDtos.OverviewResponse overview(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        List<AttendanceDaily> dailies = dailyMapper.selectList(Wrappers.<AttendanceDaily>lambdaQuery()
                .eq(AttendanceDaily::getWorkDate, target));

        long checkedIn = dailies.stream().filter(d -> d.getFirstCheckIn() != null).count();
        long late = dailies.stream().filter(d -> nvl(d.getLateMinutes()) > 0).count();
        long absent = dailies.stream()
                .filter(d -> AttendanceCalculator.STATUS_ABSENT.equals(d.getAttendanceStatus()))
                .count();
        long totalEmployees = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .isNotNull(User::getEmployeeNo)
                .eq(User::getStatus, "active"));

        Map<String, Long> deviceStats = deviceService.statistics();

        AttendanceRecord latest = recordMapper.selectOne(Wrappers.<AttendanceRecord>lambdaQuery()
                .orderByDesc(AttendanceRecord::getPunchTime)
                .last("LIMIT 1"));

        AttendanceDtos.OverviewResponse response = new AttendanceDtos.OverviewResponse();
        response.setWorkDate(target);
        response.setTotalEmployees(totalEmployees);
        response.setCheckedIn(checkedIn);
        response.setLateCount(late);
        response.setAbsentCount(absent);
        response.setOnlineDevices(deviceStats.getOrDefault("online", 0L));
        response.setTotalDevices(deviceStats.getOrDefault("total", 0L));
        response.setLastPunchTime(latest != null ? latest.getPunchTime() : null);
        return response;
    }

    /** 近 N 天趋势（默认 7 天），供折线图使用 */
    public List<AttendanceDtos.DailyTrendItem> trend(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(6);

        List<AttendanceDaily> dailies = dailyMapper.selectList(Wrappers.<AttendanceDaily>lambdaQuery()
                .ge(AttendanceDaily::getWorkDate, start)
                .le(AttendanceDaily::getWorkDate, end));

        Map<LocalDate, List<AttendanceDaily>> grouped = dailies.stream()
                .collect(Collectors.groupingBy(AttendanceDaily::getWorkDate));

        List<AttendanceDtos.DailyTrendItem> items = new ArrayList<>();
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
            List<AttendanceDaily> dayRows = grouped.getOrDefault(cursor, List.of());
            long normal = dayRows.stream()
                    .filter(d -> AttendanceCalculator.STATUS_NORMAL.equals(d.getAttendanceStatus()))
                    .count();
            long late = dayRows.stream().filter(d -> nvl(d.getLateMinutes()) > 0).count();
            long absent = dayRows.stream()
                    .filter(d -> AttendanceCalculator.STATUS_ABSENT.equals(d.getAttendanceStatus()))
                    .count();
            AttendanceDtos.DailyTrendItem item = new AttendanceDtos.DailyTrendItem();
            item.setWorkDate(cursor);
            item.setNormalCount(normal);
            item.setLateCount(late);
            item.setAbsentCount(absent);
            items.add(item);
        }
        return items;
    }

    /** 某员工近期考勤（个人中心用） */
    public List<AttendanceDaily> myDaily(Long userId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        return dailyMapper.selectList(Wrappers.<AttendanceDaily>lambdaQuery()
                .eq(AttendanceDaily::getUserId, userId)
                .ge(AttendanceDaily::getWorkDate, start)
                .le(AttendanceDaily::getWorkDate, end)
                .orderByDesc(AttendanceDaily::getWorkDate));
    }

    /**
     * 考勤日历：给定日期区间内「每人每天」的考勤结果。
     *
     * <p>数据源是 {@code attendance_daily}（日汇总），不是打卡明细：
     * 日历要的是「这天这个人算正常还是缺勤」，汇总表已经算好了，直接取即可。
     * 姓名用一次 {@code selectBatchIds} 批量补齐，避免逐行查库。
     *
     * @param from 起始日期，为空则取一个月前的 1 号
     * @param to   结束日期，为空则取今天
     */
    public List<AttendanceDtos.CalendarDay> calendar(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusMonths(1).withDayOfMonth(1);

        List<AttendanceDaily> rows = dailyMapper.selectList(Wrappers.<AttendanceDaily>lambdaQuery()
                .ge(AttendanceDaily::getWorkDate, start)
                .le(AttendanceDaily::getWorkDate, end)
                .orderByAsc(AttendanceDaily::getWorkDate));
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = rows.stream()
                .map(AttendanceDaily::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        List<AttendanceDtos.CalendarDay> result = new ArrayList<>(rows.size());
        for (AttendanceDaily daily : rows) {
            User user = daily.getUserId() == null ? null : userMap.get(daily.getUserId());
            result.add(toCalendarDay(daily, user));
        }
        return result;
    }

    /**
     * 单个员工的考勤详情（管理员在打卡记录里点人名时打开）。
     *
     * <p>一次返回「汇总统计 + 按天明细 + 原始流水」三块，
     * 前端打开抽屉只需要一个请求，不用为同一个人连打三次。
     */
    public AttendanceDtos.EmployeeAttendance employeeDetail(Long userId, LocalDate from, LocalDate to) {
        if (userId == null) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "员工ID不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "员工不存在: " + userId);
        }

        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);

        List<AttendanceDaily> days = dailyMapper.selectList(Wrappers.<AttendanceDaily>lambdaQuery()
                .eq(AttendanceDaily::getUserId, userId)
                .ge(AttendanceDaily::getWorkDate, start)
                .le(AttendanceDaily::getWorkDate, end)
                .orderByAsc(AttendanceDaily::getWorkDate));

        List<AttendanceDtos.CalendarDay> dayVos = new ArrayList<>(days.size());
        AttendanceDtos.Summary summary = new AttendanceDtos.Summary();
        long workMinutes = 0;
        long attendedDays = 0;
        for (AttendanceDaily daily : days) {
            dayVos.add(toCalendarDay(daily, user));
            String status = daily.getAttendanceStatus();
            if ("ABSENT".equals(status)) {
                summary.setAbsentCount(summary.getAbsentCount() + 1);
            } else {
                attendedDays++;
                if ("LATE".equals(status) || "LATE_AND_EARLY".equals(status)) {
                    summary.setLateCount(summary.getLateCount() + 1);
                }
                if ("EARLY_LEAVE".equals(status) || "LATE_AND_EARLY".equals(status)) {
                    summary.setEarlyLeaveCount(summary.getEarlyLeaveCount() + 1);
                }
                if ("NORMAL".equals(status)) {
                    summary.setNormalCount(summary.getNormalCount() + 1);
                }
                workMinutes += daily.getWorkMinutes() == null ? 0 : daily.getWorkMinutes();
            }
        }
        summary.setWorkDays(days.size());
        summary.setWorkMinutes(workMinutes);
        summary.setAvgWorkMinutes(attendedDays == 0 ? 0 : workMinutes / attendedDays);

        // 原始流水：区间内该员工的全部打卡，时间正序
        List<AttendanceRecord> rawRecords = recordMapper.selectList(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getUserId, userId)
                .ge(AttendanceRecord::getWorkDate, start)
                .le(AttendanceRecord::getWorkDate, end)
                .orderByAsc(AttendanceRecord::getPunchTime));

        AttendanceDtos.EmployeeAttendance vo = new AttendanceDtos.EmployeeAttendance();
        vo.setUserId(userId);
        vo.setEmployeeNo(user.getEmployeeNo());
        vo.setUserName(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        vo.setFrom(start);
        vo.setTo(end);
        vo.setSummary(summary);
        vo.setDays(dayVos);
        vo.setRecords(enrich(rawRecords));
        return vo;
    }

    /** AttendanceDaily → CalendarDay（日历与员工详情共用，保证两处口径一致） */
    private AttendanceDtos.CalendarDay toCalendarDay(AttendanceDaily daily, User user) {
        AttendanceDtos.CalendarDay item = new AttendanceDtos.CalendarDay();
        item.setUserId(daily.getUserId());
        item.setEmployeeNo(daily.getEmployeeNo());
        if (user == null) {
            item.setUserName("未知员工");
        } else {
            item.setUserName(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        }
        item.setWorkDate(daily.getWorkDate());
        item.setFirstCheckIn(daily.getFirstCheckIn());
        item.setLastCheckOut(daily.getLastCheckOut());
        item.setWorkMinutes(daily.getWorkMinutes());
        item.setLateMinutes(daily.getLateMinutes());
        item.setEarlyLeaveMinutes(daily.getEarlyLeaveMinutes());
        item.setAttendanceStatus(daily.getAttendanceStatus());
        return item;
    }

    /** 所有需要纳入考勤统计的员工（有工号且启用） */
    public List<AttendanceSummaryService.Worker> attendanceWorkers() {
        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .isNotNull(User::getEmployeeNo)
                .eq(User::getStatus, "active"));
        return users.stream()
                .map(u -> new AttendanceSummaryService.Worker(u.getUserId(), u.getEmployeeNo()))
                .filter(w -> StringUtils.hasText(w.employeeNo()))
                .toList();
    }

    // ==================================================================
    // 四、内部工具
    // ==================================================================

    /** 批量补齐姓名与设备位置，避免逐行查询 */
    private List<AttendanceRecordVO> enrich(List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = records.stream()
                .map(AttendanceRecord::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        Set<String> deviceSns = records.stream()
                .map(AttendanceRecord::getDeviceSn)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, IotDevice> deviceMap = new HashMap<>();
        if (!deviceSns.isEmpty()) {
            deviceMapper.selectList(Wrappers.<IotDevice>lambdaQuery()
                            .in(IotDevice::getDeviceSn, deviceSns))
                    .forEach(d -> deviceMap.put(d.getDeviceSn(), d));
        }

        List<AttendanceRecordVO> rows = new ArrayList<>(records.size());
        for (AttendanceRecord record : records) {
            AttendanceRecordVO vo = new AttendanceRecordVO();
            vo.setId(record.getId());
            vo.setUserId(record.getUserId());
            vo.setEmployeeNo(record.getEmployeeNo());
            User user = record.getUserId() == null ? null : userMap.get(record.getUserId());
            vo.setUserName(user != null
                    ? (StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername())
                    : null);
            vo.setDeviceSn(record.getDeviceSn());
            IotDevice device = deviceMap.get(record.getDeviceSn());
            vo.setDeviceLocation(device != null ? device.getLocation() : null);
            vo.setPunchType(record.getPunchType());
            vo.setPunchTime(record.getPunchTime());
            vo.setWorkDate(record.getWorkDate());
            vo.setStatus(record.getStatus());
            vo.setLateMinutes(record.getLateMinutes());
            vo.setEarlyLeaveMinutes(record.getEarlyLeaveMinutes());
            vo.setSource(record.getSource());
            vo.setSignVerified(record.getSignVerified());
            vo.setRemark(record.getRemark());
            vo.setCreatedAt(record.getCreatedAt());
            rows.add(vo);
        }
        return rows;
    }

    private String resolveMessageId(DeviceMessages.PunchMessage message, String deviceSn,
                                    String employeeNo, String punchTimeText) {
        if (StringUtils.hasText(message.getMessageId())) {
            return message.getMessageId().trim();
        }
        if (StringUtils.hasText(message.getNonce())) {
            return deviceSn + "-" + message.getNonce().trim();
        }
        return deviceSn + "-" + employeeNo + "-" + punchTimeText.replace(' ', 'T');
    }

    private DeviceMessages.AckMessage buildAck(AttendanceRecord record, int code, String message) {
        DeviceMessages.AckMessage ack = new DeviceMessages.AckMessage();
        ack.setDeviceSn(record.getDeviceSn());
        ack.setEmployeeNo(record.getEmployeeNo());
        ack.setMessageId(record.getMessageId());
        ack.setRecordId(record.getId());
        ack.setPunchType(record.getPunchType());
        ack.setStatus(record.getStatus());
        ack.setLateMinutes(record.getLateMinutes());
        ack.setCode(code);
        ack.setMessage(message);
        ack.setServerTime(System.currentTimeMillis());
        return ack;
    }

    private String writeJsonQuietly(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return String.valueOf(payload);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
