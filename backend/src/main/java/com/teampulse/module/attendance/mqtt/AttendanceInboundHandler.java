package com.teampulse.module.attendance.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.attendance.config.MqttProperties;
import com.teampulse.module.attendance.dto.DeviceMessages;
import com.teampulse.module.attendance.service.AckPublisher;
import com.teampulse.module.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * MQTT 入站处理器：把设备上行报文分流到打卡链路或心跳链路。
 *
 * <p>设计要点：
 * <ol>
 *   <li>按主题头 {@code mqtt_receivedTopic} 分流，一个通道处理多个主题</li>
 *   <li>所有异常在这里被吃掉并转成回执——MQTT 没有 HTTP 状态码，
 *       异常抛出只会让消息被丢弃，设备侧永远等不到结果</li>
 *   <li>回执里带 code，设备据此决定「播报成功」还是「播报异常且不重试」</li>
 * </ol>
 *
 * <p>错误码语义约定：{@code 0} 成功；{@code 6xxxx} 为设备侧永久错误（不重试）；
 * {@code 5xxxx} 为服务端临时错误（设备可重试）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceInboundHandler {

    private final ObjectMapper objectMapper;
    private final AttendanceService attendanceService;
    private final AckPublisher ackPublisher;
    private final MqttProperties mqttProperties;

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<?> message) {
        String topic = String.valueOf(message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC));
        String payload = String.valueOf(message.getPayload());
        log.debug("收到 MQTT 报文 topic={}, payload={}", topic, payload);

        try {
            if (topic != null && topic.endsWith(mqttProperties.getTopicHeartbeat())) {
                handleHeartbeat(payload);
            } else {
                handlePunch(payload);
            }
        } catch (BizException ex) {
            log.warn("设备报文处理失败 topic={}, code={}, reason={}", topic,
                    ex.getErrorCode().getCode(), ex.getMessage());
            publishError(payload, ex.getErrorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("设备报文处理异常 topic={}, payload={}", topic, payload, ex);
            publishError(payload, ErrorCode.INTERNAL_ERROR, "服务端处理失败，请稍后重试");
        }
    }

    private void handlePunch(String payload) throws Exception {
        DeviceMessages.PunchMessage punch = objectMapper.readValue(payload, DeviceMessages.PunchMessage.class);
        // 回执在 service 内部成功路径已发过一次，这里只负责异常路径
        attendanceService.handlePunch(punch, "MQTT");
    }

    private void handleHeartbeat(String payload) throws Exception {
        DeviceMessages.HeartbeatMessage heartbeat = objectMapper.readValue(payload, DeviceMessages.HeartbeatMessage.class);
        attendanceService.handleHeartbeat(heartbeat);
    }

    /**
     * 失败回执：尽量从原始报文里提取 deviceSn，让设备知道是哪台机器出错。
     */
    private void publishError(String payload, ErrorCode errorCode, String detail) {
        DeviceMessages.AckMessage ack = new DeviceMessages.AckMessage();
        try {
            DeviceMessages.PunchMessage parsed = objectMapper.readValue(payload, DeviceMessages.PunchMessage.class);
            ack.setDeviceSn(parsed.getDeviceSn());
            ack.setEmployeeNo(parsed.getEmployeeNo());
            ack.setMessageId(parsed.getMessageId());
        } catch (Exception ignore) {
            // 报文本身无法解析时，回执里只能带错误码
            log.debug("失败回执无法解析原始报文");
        }
        ack.setCode(errorCode.getCode());
        ack.setMessage(detail);
        ack.setServerTime(System.currentTimeMillis());
        ackPublisher.publish(ack);
    }
}
