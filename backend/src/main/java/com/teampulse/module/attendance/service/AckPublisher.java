package com.teampulse.module.attendance.service;

import com.teampulse.module.attendance.dto.DeviceMessages.AckMessage;

/**
 * 打卡回执发布器。
 *
 * <p>抽成接口是为了让「MQTT 关闭」的环境（单元测试、无 broker 的本地调试）
 * 依然能注入一个空实现，业务代码无需关心传输层是否存在。
 */
public interface AckPublisher {

    void publish(AckMessage ack);
}
