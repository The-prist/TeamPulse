package com.teampulse.module.audit.aspect;

import com.teampulse.module.audit.annotation.Audited;
import com.teampulse.module.audit.entity.AuditLog;
import com.teampulse.module.audit.service.AuditLogService;
import com.teampulse.security.LoginUser;
import com.teampulse.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计切面：拦截 {@link Audited} 注解的方法，把「谁、在什么时候、做了什么、成没成」写进 audit_log。
 *
 * <p>三条设计约束：
 * <ol>
 *   <li><b>不影响主流程</b>：写审计失败只打 warn，绝不把业务异常带出去；</li>
 *   <li><b>失败也要记</b>：业务异常穿过切面时，先把 FAILURE 记下来再原样抛出；</li>
 *   <li><b>与日志对齐</b>：traceId 取自 MDC（由 RequestObservabilityFilter 写入），
 *       页面上的 traceId 能直接对应到服务端日志，也能对应到这条审计。</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final int MAX_TEXT = 250;

    private final AuditLogService auditLogService;

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            write(audited, pjp, "SUCCESS", null, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            // 失败也要留痕：否则「谁尝试删了但被拒绝」这类关键线索会丢
            write(audited, pjp, "FAILURE", ex.getMessage(), System.currentTimeMillis() - start);
            throw ex;
        }
    }

    private void write(Audited audited, ProceedingJoinPoint pjp, String result, String errorMsg, long costMs) {
        try {
            AuditLog entry = new AuditLog();
            entry.setModule(audited.module());
            entry.setAction(audited.action());
            entry.setDetail(truncate(audited.detail()));
            entry.setResult(result);
            entry.setErrorMsg(truncate(errorMsg));
            entry.setCostMs((int) costMs);
            entry.setTraceId(MDC.get("traceId"));

            LoginUser user = SecurityUtils.currentUserOrNull();
            if (user != null) {
                entry.setActorId(user.getUserId());
                entry.setActorName(user.getNickname() != null ? user.getNickname() : user.getUsername());
                entry.setActorRole(user.getRole());
            }

            // 资源类型与 id：约定写操作第一个简单参数是被操作对象的 id
            entry.setResource(audited.module());
            entry.setResourceId(extractResourceId(pjp.getArgs()));

            fillRequest(entry);
            auditLogService.record(entry);
        } catch (Exception ex) {
            // 审计失败不能影响业务返回
            log.warn("审计日志写入失败 action={}：{}", audited.action(), ex.getMessage());
        }
    }

    private void fillRequest(AuditLog entry) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        entry.setMethod(request.getMethod());
        entry.setPath(truncate(request.getRequestURI()));
        entry.setIp(clientIp(request));
        String ua = request.getHeader("User-Agent");
        entry.setUserAgent(truncate(ua));
    }

    /** 反向代理下优先取 X-Forwarded-For 的第一段 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncate(first);
            }
        }
        return truncate(request.getRemoteAddr());
    }

    /** 取第一个 String/Long/Integer 参数作为资源 id；拿不到就算了（不猜） */
    private String extractResourceId(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Long || arg instanceof Integer || arg instanceof String) {
                return truncate(String.valueOf(arg));
            }
        }
        return null;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= MAX_TEXT ? trimmed : trimmed.substring(0, MAX_TEXT) + "…";
    }
}
