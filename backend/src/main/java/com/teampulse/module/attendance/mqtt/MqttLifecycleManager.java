package com.teampulse.module.attendance.mqtt;

import com.teampulse.module.attendance.config.MqttProperties;
import com.teampulse.module.attendance.service.AttendanceMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.Lifecycle;
import org.springframework.integration.core.MessageProducer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MQTT 入站适配器的启停管家。
 *
 * <p>适配器被设置为不自动启动，改由这里「启动失败不致命 + 定时重试」：
 * 本地没装 broker、云端 broker 重启、网络抖动，都不会影响 Web 接口可用性。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "teampulse.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttLifecycleManager implements ApplicationRunner {

    private final MessageProducer inboundAdapter;
    private final MqttProperties properties;
    private final AttendanceMetrics metrics;

    public MqttLifecycleManager(@Qualifier("mqttInboundAdapter") MessageProducer inboundAdapter,
                                MqttProperties properties,
                                AttendanceMetrics metrics) {
        this.inboundAdapter = inboundAdapter;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void run(ApplicationArguments args) {
        tryStart("启动阶段");
    }

    /** 断线/首次连接失败后的兜底重试；已运行时什么都不做 */
    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void retryIfNotRunning() {
        if (inboundAdapter instanceof Lifecycle lifecycle && !lifecycle.isRunning()) {
            tryStart("定时重试");
        }
    }

    private void tryStart(String phase) {
        try {
            if (inboundAdapter instanceof Lifecycle lifecycle) {
                if (lifecycle.isRunning()) {
                    return;
                }
                lifecycle.start();
                metrics.mqttConnected();
                log.info("MQTT 入站已启动 [{}] broker={}, topics=[{}, {}]",
                        phase, properties.getBrokerUrl(),
                        properties.getTopicCheckin(), properties.getTopicHeartbeat());
            }
        } catch (Exception ex) {
            metrics.mqttConnectFailed("start-failed");
            log.warn("MQTT 入站启动失败 [{}] broker={}，原因：{}。Web 接口不受影响，30s 后自动重试",
                    phase, properties.getBrokerUrl(), ex.getMessage());
        }
    }
}
