package com.teampulse.module.attendance.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 考勤业务指标。
 *
 * <p>为什么单独抽出来：接口级指标（请求数/耗时/状态码）由过滤器统一记录，
 * 但「幂等命中」「MQTT 重连」这类**业务语义**在 HTTP 层看不出来，
 * 需要在业务代码里显式埋点，否则出问题时只能靠翻日志数行数。
 *
 * <p>命名遵循 Prometheus 约定：小写、下划线分隔、以 _total 结尾表示计数器。
 */
@Component
@RequiredArgsConstructor
public class AttendanceMetrics {

    private final MeterRegistry registry;

    /** 幂等命中：layer = message（报文重复）/ employee_time（同人同秒） */
    public void idempotentHit(String layer) {
        registry.counter("teampulse_attendance_idempotent_total", "layer", layer).increment();
    }

    /** MQTT 连接建立（含首次连接与重连），用于观察 broker 抖动 */
    public void mqttConnected() {
        registry.counter("teampulse_mqtt_connected_total").increment();
    }

    /** MQTT 连接失败，reason 用短枚举避免高基数 */
    public void mqttConnectFailed(String reason) {
        registry.counter("teampulse_mqtt_connect_failed_total", "reason", reason).increment();
    }
}
