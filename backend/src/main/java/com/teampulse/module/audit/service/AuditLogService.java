package com.teampulse.module.audit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.audit.dto.AuditDtos;
import com.teampulse.module.audit.entity.AuditLog;
import com.teampulse.module.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志的写入与查询。
 *
 * <p>写入：由 {@code AuditLogAspect} 调用，本身不做任何业务校验，
 * 写失败也不能影响主流程（切面里已 try/catch 兜住）。
 *
 * <p>查询：只提供给拥有 {@code audit:view} 的角色，按人 / 动作 / 模块 / 结果 / 时间 / traceId 过滤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /** 追加一条审计记录；调用方负责容错，这里遇到异常向上抛 */
    public void record(AuditLog entry) {
        if (entry.getCreatedAt() == null) {
            entry.setCreatedAt(LocalDateTime.now());
        }
        auditLogMapper.insert(entry);
    }

    public PageResult<AuditDtos.AuditVO> page(AuditDtos.Query query) {
        long safePage = Math.max(1, query.getPage());
        long safeSize = Math.min(Math.max(1, query.getSize()), 200);

        var wrapper = Wrappers.<AuditLog>lambdaQuery().orderByDesc(AuditLog::getId);
        if (query.getActorId() != null) {
            wrapper.eq(AuditLog::getActorId, query.getActorId());
        }
        if (StringUtils.hasText(query.getAction())) {
            wrapper.like(AuditLog::getAction, query.getAction().trim());
        }
        if (StringUtils.hasText(query.getModule())) {
            wrapper.eq(AuditLog::getModule, query.getModule().trim());
        }
        if (StringUtils.hasText(query.getResult())) {
            wrapper.eq(AuditLog::getResult, query.getResult().trim().toUpperCase());
        }
        if (StringUtils.hasText(query.getTraceId())) {
            wrapper.eq(AuditLog::getTraceId, query.getTraceId().trim());
        }
        LocalDate from = parseDate(query.getFrom());
        if (from != null) {
            wrapper.ge(AuditLog::getCreatedAt, from.atStartOfDay());
        }
        LocalDate to = parseDate(query.getTo());
        if (to != null) {
            // 「到」含当天：用小于次日零点，避免漏掉当天 23:59 的记录
            wrapper.lt(AuditLog::getCreatedAt, to.plusDays(1).atStartOfDay());
        }

        IPage<AuditLog> result = auditLogMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<AuditDtos.AuditVO> rows = result.getRecords().stream().map(AuditLogService::toVO).toList();
        return PageResult.of(rows, result.getTotal(), safePage, safeSize);
    }

    public AuditDtos.AuditVO detail(Long id) {
        AuditLog log = auditLogMapper.selectById(id);
        return log == null ? null : toVO(log);
    }

    private static AuditDtos.AuditVO toVO(AuditLog src) {
        AuditDtos.AuditVO vo = new AuditDtos.AuditVO();
        vo.setId(src.getId());
        vo.setTraceId(src.getTraceId());
        vo.setActorId(src.getActorId());
        vo.setActorName(src.getActorName());
        vo.setActorRole(src.getActorRole());
        vo.setModule(src.getModule());
        vo.setAction(src.getAction());
        vo.setResource(src.getResource());
        vo.setResourceId(src.getResourceId());
        vo.setMethod(src.getMethod());
        vo.setPath(src.getPath());
        vo.setDetail(src.getDetail());
        vo.setResult(src.getResult());
        vo.setErrorMsg(src.getErrorMsg());
        vo.setCostMs(src.getCostMs());
        vo.setIp(src.getIp());
        vo.setUserAgent(src.getUserAgent());
        vo.setCreatedAt(src.getCreatedAt());
        return vo;
    }

    private static LocalDate parseDate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
