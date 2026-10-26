package com.teampulse.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求可观测性：一次请求同时解决「串起来」和「量出来」两件事。
 *
 * <p>1) traceId：从上游请求头 {@code X-Trace-Id} 继承，没有就生成，写进 MDC（日志格式里带 %X{traceId}），
 * 并回写到响应头，前端报错时可以把 traceId 一起反馈，日志里能定位到那一次请求。
 *
 * <p>2) 指标（RED 三件套）：请求数 / 错误数 / 耗时。tag 用**路由模板**而不是真实路径，
 * 避免 {@code /attendance/employee/42} 这种把用户 id 变成 tag 导致时间序列爆炸。
 *
 * <p>3) 考勤打卡流水：对 {@code /iot/v1/punch} 额外记一条结果计数，
 * 让看板能直接算设备上报成功率。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RequestObservabilityFilter extends OncePerRequestFilter {

    /** 与前端约定：响应头回传，便于把一次报错和日志对上 */
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String TRACE_MDC_KEY = "traceId";

    private final MeterRegistry meterRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_MDC_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            chain.doFilter(request, response);
        } finally {
            record(request, response, sample);
            MDC.remove(TRACE_MDC_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(TRACE_HEADER);
        if (incoming != null) {
            String trimmed = incoming.trim();
            // 只接受合理长度的值，避免外部传入超长串污染日志
            if (trimmed.length() >= 8 && trimmed.length() <= 64) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private void record(HttpServletRequest request, HttpServletResponse response, Timer.Sample sample) {
        String path = request.getRequestURI();
        // 监控端点自身不计数，否则 /actuator/prometheus 会把指标刷爆
        if (path != null && path.startsWith("/actuator")) {
            return;
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String uri = pattern != null ? pattern.toString() : "unmatched";
        String status = String.valueOf(response.getStatus());
        String method = request.getMethod();

        meterRegistry.counter("teampulse_api_requests_total",
                "method", method, "uri", uri, "status", status).increment();
        // 4xx/5xx 单独一个计数器，便于告警规则直接按 result 过滤
        if (response.getStatus() >= 400) {
            meterRegistry.counter("teampulse_api_failures_total",
                    "uri", uri, "status", status).increment();
        }
        sample.stop(meterRegistry.timer("teampulse_api_latency_seconds", "uri", uri));

        // 设备打卡流水：成功/失败按 HTTP 状态粗分，细节原因见 teampulse_api_errors_total 的 code 标签
        if (uri.startsWith("/iot/v1/punch")) {
            meterRegistry.counter("teampulse_attendance_punch_total",
                    "result", response.getStatus() < 400 ? "success" : "failure").increment();
        }
    }
}
