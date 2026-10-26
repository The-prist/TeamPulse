package com.teampulse.module.attendance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MQTT 接入配置。
 *
 * <p>主题规划（生产建议加租户/环境前缀，如 {@code prod/attendance/checkin}）：
 * <ul>
 *   <li>{@code attendance/checkin}   —— 设备上行打卡报文</li>
 *   <li>{@code attendance/heartbeat} —— 设备上行心跳</li>
 *   <li>{@code attendance/ack}       —— 服务端下行回执（设备可订阅用于提示音/离线重试）</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "teampulse.mqtt")
public class MqttProperties {

    private boolean enabled = true;

    private String brokerUrl = "tcp://localhost:1883";

    /**
     * 客户端 ID 必须全局唯一；多实例部署时每个实例要配不同值，
     * 否则 broker 会互相踢下线（同 ID 后连接者胜）。
     */
    private String clientId = "teampulse-backend-1";

    private String username = "";

    private String password = "";

    private String topicCheckin = "attendance/checkin";

    private String topicHeartbeat = "attendance/heartbeat";

    private String topicAck = "attendance/ack";

    private int qos = 1;

    private int keepAliveSeconds = 30;

    private int completionTimeoutMs = 5000;

    private int connectionTimeoutSeconds = 10;

    /** 是否使用持久会话（断线重连后补收离线期间的消息） */
    private boolean cleanSession = false;
}
