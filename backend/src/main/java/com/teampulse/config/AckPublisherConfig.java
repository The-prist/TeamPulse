package com.teampulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampulse.module.attendance.config.MqttProperties;
import com.teampulse.module.attendance.dto.DeviceMessages.AckMessage;
import com.teampulse.module.attendance.service.AckPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 回执发布器的条件装配。
 *
 * <p>用 {@code havingValue} 显式二选一，而不是依赖 {@code @ConditionalOnMissingBean} 的
 * 装配顺序，避免不同 Spring 版本下行为漂移。
 */
@Configuration
public class AckPublisherConfig {

    @Bean
    @ConditionalOnProperty(prefix = "teampulse.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AckPublisher mqttAckPublisher(@Qualifier("mqttOutboundChannel") MessageChannel outboundChannel,
                                         ObjectMapper objectMapper,
                                         MqttProperties mqttProperties) {
        return new MqttAckPublisher(outboundChannel, objectMapper, mqttProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "teampulse.mqtt", name = "enabled", havingValue = "false")
    public AckPublisher noopAckPublisher() {
        return new NoopAckPublisher();
    }

    /** MQTT 下行：把回执发到 attendance/ack，设备订阅后可播报「打卡成功 / 迟到」 */
    @Slf4j
    static class MqttAckPublisher implements AckPublisher {

        private final MessageChannel outboundChannel;
        private final ObjectMapper objectMapper;
        private final MqttProperties properties;

        MqttAckPublisher(MessageChannel outboundChannel, ObjectMapper objectMapper, MqttProperties properties) {
            this.outboundChannel = outboundChannel;
            this.objectMapper = objectMapper;
            this.properties = properties;
        }

        @Override
        public void publish(AckMessage ack) {
            try {
                String json = objectMapper.writeValueAsString(ack);
                outboundChannel.send(MessageBuilder.withPayload(json)
                        .setHeader(MqttHeaders.TOPIC, properties.getTopicAck())
                        .setHeader(MqttHeaders.QOS, properties.getQos())
                        .build());
                log.debug("已下发打卡回执: {}", json);
            } catch (Exception ex) {
                // 回执是旁路动作，失败不影响打卡已落库的事实
                log.warn("下发打卡回执失败: deviceSn={}, reason={}", ack.getDeviceSn(), ex.getMessage());
            }
        }
    }

    /** 无 MQTT 环境下的空实现 */
    @Slf4j
    static class NoopAckPublisher implements AckPublisher {
        @Override
        public void publish(AckMessage ack) {
            log.debug("MQTT 已关闭，跳过回执下发: deviceSn={}, code={}", ack.getDeviceSn(), ack.getCode());
        }
    }
}
