package com.teampulse.module.attendance.scheduler;

import com.teampulse.module.attendance.config.AttendanceProperties;
import com.teampulse.module.attendance.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 设备离线巡检。
 *
 * <p>设备每 60 秒发一次心跳；连续超过阈值（默认 2 分钟）未上报即判定离线。
 * 用「定时扫描 + 单条 UPDATE」而不是给每台设备起定时器，设备数量增长时开销恒定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceHealthScheduler {

    private final DeviceService deviceService;
    private final AttendanceProperties properties;

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void scanOfflineDevices() {
        LocalDateTime deadline = LocalDateTime.now().minus(properties.getOfflineThreshold());
        int changed = deviceService.markOfflineBefore(deadline);
        if (changed > 0) {
            log.info("设备巡检：{} 台设备超过 {} 未心跳，已置为离线", changed, properties.getOfflineThreshold());
        }
    }
}
