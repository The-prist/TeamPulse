package com.teampulse.module.attendance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teampulse.common.api.PageResult;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.attendance.dto.AttendanceDtos;
import com.teampulse.module.attendance.dto.MakeupDtos;
import com.teampulse.module.attendance.entity.AttendanceRecord;
import com.teampulse.module.attendance.entity.MakeupRequest;
import com.teampulse.module.attendance.mapper.MakeupRequestMapper;
import com.teampulse.module.rbac.service.DataScope;
import com.teampulse.module.rbac.service.DataScopeService;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import com.teampulse.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 补卡申请与审批。
 *
 * <p>链路：员工提交申请（pending）→ 部门经理/HR 审批 →
 * 通过时**复用** {@link AttendanceService#manualPunch} 生成正式打卡记录（source=MANUAL），
 * 并在备注里写明审批单号，保证「记录 → 审批」可回溯。
 *
 * <p>数据范围：审批列表只显示自己范围内（本部门 / 全公司）的申请，
 * 避免跨部门审批。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MakeupRequestService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final DateTimeFormatter PUNCH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_PAGE_SIZE = 200;

    private final MakeupRequestMapper makeupRequestMapper;
    private final AttendanceService attendanceService;
    private final UserMapper userMapper;
    private final DataScopeService dataScopeService;

    /** 员工提交补卡申请 */
    public Long submit(MakeupDtos.SubmitRequest request) {
        Long userId = SecurityUtils.currentUserId();
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getEmployeeNo())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "当前账号没有绑定工号，无法申请补卡");
        }

        LocalDateTime punchTime;
        try {
            punchTime = LocalDateTime.parse(request.getPunchTime().trim(), PUNCH_TIME_FORMAT);
        } catch (Exception ex) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "打卡时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
        if (punchTime.isAfter(LocalDateTime.now())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "不能为将来的时间申请补卡");
        }

        boolean duplicated = makeupRequestMapper.exists(Wrappers.<MakeupRequest>lambdaQuery()
                .eq(MakeupRequest::getUserId, userId)
                .eq(MakeupRequest::getPunchTime, punchTime)
                .eq(MakeupRequest::getStatus, STATUS_PENDING));
        if (duplicated) {
            throw BizException.of(ErrorCode.CONFLICT, "该时间点已有待审批的补卡申请");
        }

        MakeupRequest entity = new MakeupRequest();
        entity.setUserId(userId);
        entity.setEmployeeNo(user.getEmployeeNo());
        entity.setPunchTime(punchTime);
        entity.setPunchType(StringUtils.hasText(request.getPunchType()) ? request.getPunchType() : "AUTO");
        entity.setReason(request.getReason().trim());
        entity.setStatus(STATUS_PENDING);
        makeupRequestMapper.insert(entity);
        log.info("补卡申请已提交: requestId={}, userId={}, punchTime={}",
                entity.getRequestId(), userId, punchTime);
        return entity.getRequestId();
    }

    /** 我的申请 */
    public PageResult<MakeupDtos.MakeupVO> mine(long page, long size) {
        Long userId = SecurityUtils.currentUserId();
        long safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long safePage = Math.max(1, page);
        IPage<MakeupRequest> result = makeupRequestMapper.selectPage(new Page<>(safePage, safeSize),
                Wrappers.<MakeupRequest>lambdaQuery()
                        .eq(MakeupRequest::getUserId, userId)
                        .orderByDesc(MakeupRequest::getRequestId));
        return PageResult.of(toVOs(result.getRecords()), result.getTotal(), safePage, safeSize);
    }

    /** 待我审批（按数据范围过滤） */
    public PageResult<MakeupDtos.MakeupVO> pending(long page, long size) {
        long safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long safePage = Math.max(1, page);

        DataScope scope = dataScopeService.current();
        var wrapper = Wrappers.<MakeupRequest>lambdaQuery()
                .eq(MakeupRequest::getStatus, STATUS_PENDING)
                .orderByDesc(MakeupRequest::getRequestId);
        if (!scope.isUnrestricted()) {
            if (scope.userIds().isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(MakeupRequest::getUserId, scope.userIds());
            }
        }
        IPage<MakeupRequest> result = makeupRequestMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.of(toVOs(result.getRecords()), result.getTotal(), safePage, safeSize);
    }

    /** 历史审批记录（已处理） */
    public PageResult<MakeupDtos.MakeupVO> reviewed(long page, long size) {
        long safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        long safePage = Math.max(1, page);

        DataScope scope = dataScopeService.current();
        var wrapper = Wrappers.<MakeupRequest>lambdaQuery()
                .in(MakeupRequest::getStatus, List.of(STATUS_APPROVED, STATUS_REJECTED))
                .orderByDesc(MakeupRequest::getRequestId);
        if (!scope.isUnrestricted()) {
            if (scope.userIds().isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(MakeupRequest::getUserId, scope.userIds());
            }
        }
        IPage<MakeupRequest> result = makeupRequestMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.of(toVOs(result.getRecords()), result.getTotal(), safePage, safeSize);
    }

    /**
     * 审批。
     *
     * <p>通过时生成正式打卡记录：复用 manualPunch，保证补卡与手工补卡走同一套判定逻辑
     * （迟到/早退分钟、日汇总重建都不会漏）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(Long requestId, MakeupDtos.ReviewRequest request) {
        MakeupRequest entity = makeupRequestMapper.selectById(requestId);
        if (entity == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "补卡申请不存在: " + requestId);
        }
        if (!STATUS_PENDING.equals(entity.getStatus())) {
            throw BizException.of(ErrorCode.CONFLICT, "该申请已处理，不能重复审批");
        }

        DataScope scope = dataScopeService.current();
        if (!scope.includes(entity.getUserId())) {
            throw BizException.of(ErrorCode.FORBIDDEN, "该申请不在你的审批范围内");
        }

        Long operatorId = SecurityUtils.currentUserId();
        if (request.isApproved()) {
            AttendanceDtos.ManualPunchRequest punch = new AttendanceDtos.ManualPunchRequest();
            punch.setEmployeeNo(entity.getEmployeeNo());
            punch.setPunchTime(entity.getPunchTime().format(PUNCH_TIME_FORMAT));
            punch.setPunchType(entity.getPunchType());
            punch.setRemark("补卡审批通过 #" + requestId
                    + (StringUtils.hasText(request.getRemark()) ? "：" + request.getRemark() : ""));
            AttendanceRecord record = attendanceService.manualPunch(punch);
            entity.setRecordId(record.getId());
            entity.setStatus(STATUS_APPROVED);
        } else {
            entity.setStatus(STATUS_REJECTED);
        }
        entity.setApproverId(operatorId);
        entity.setApproveTime(LocalDateTime.now());
        entity.setApproveRemark(request.getRemark());
        makeupRequestMapper.updateById(entity);
        log.info("补卡申请已处理: requestId={}, approved={}, approver={}",
                requestId, request.isApproved(), operatorId);
    }

    /** 批量补齐申请人/审批人昵称 */
    private List<MakeupDtos.MakeupVO> toVOs(List<MakeupRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        for (MakeupRequest r : rows) {
            if (r.getUserId() != null) {
                userIds.add(r.getUserId());
            }
            if (r.getApproverId() != null) {
                userIds.add(r.getApproverId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(userIds)) {
                names.put(u.getUserId(), StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername());
            }
        }
        List<MakeupDtos.MakeupVO> result = new ArrayList<>(rows.size());
        for (MakeupRequest r : rows) {
            MakeupDtos.MakeupVO vo = new MakeupDtos.MakeupVO();
            vo.setRequestId(r.getRequestId());
            vo.setUserId(r.getUserId());
            vo.setUserName(names.getOrDefault(r.getUserId(), "用户#" + r.getUserId()));
            vo.setEmployeeNo(r.getEmployeeNo());
            vo.setPunchTime(r.getPunchTime());
            vo.setPunchType(r.getPunchType());
            vo.setReason(r.getReason());
            vo.setStatus(r.getStatus());
            vo.setApproverId(r.getApproverId());
            vo.setApproverName(r.getApproverId() == null ? null : names.get(r.getApproverId()));
            vo.setApproveTime(r.getApproveTime());
            vo.setApproveRemark(r.getApproveRemark());
            vo.setRecordId(r.getRecordId());
            vo.setCreatedAt(r.getCreatedAt());
            result.add(vo);
        }
        return result;
    }
}
