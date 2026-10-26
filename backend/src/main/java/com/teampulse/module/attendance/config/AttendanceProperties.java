package com.teampulse.module.attendance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 考勤业务参数（与 MQTT 传输参数分开，便于单独调整）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "teampulse.attendance")
public class AttendanceProperties {

    /** 是否强制校验设备报文签名。关闭仅用于本地调试，生产必须开启 */
    private boolean signEnabled = true;

    /** 允许的设备时钟漂移窗口（秒）。超过说明设备 NTP 未同步，应拒收而不是记错时间 */
    private long clockDriftSeconds = 300;

    /** nonce 去重窗口，应大于设备重发间隔 */
    private Duration nonceTtl = Duration.ofMinutes(10);

    /** 心跳超时判定离线 */
    private Duration offlineThreshold = Duration.ofMinutes(2);

    /** 每日汇总任务 cron */
    private String dailySummaryCron = "0 5 0 * * ?";
}
