package com.teampulse.module.attendance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * ESP32 上行的 MQTT 报文契约。
 *
 * <p><b>打卡报文</b>（主题 {@code attendance/checkin}）：
 * <pre>{@code
 * {
 *   "deviceSn":  "TP-ESP32-0001",
 *   "employeeNo": "E1001",
 *   "punchTime": "2025-06-01 09:03:12",
 *   "punchType": "AUTO",
 *   "nonce":     "a1b2c3d4e5",
 *   "sign":      "3f9c...（小写十六进制 HMAC-SHA256）",
 *   "firmwareVersion": "1.0.0"
 * }
 * }</pre>
 *
 * <p>签名规范化字符串（字段顺序固定，用 {@code |} 连接）：
 * <pre>{@code
 * deviceSn|employeeNo|punchTime|nonce
 * }</pre>
 * 设备端与后端必须完全一致，否则验签失败（错误码 60003）。
 */
public final class DeviceMessages {

    private DeviceMessages() {
    }

    /** 打卡上行报文 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PunchMessage {
        private String deviceSn;
        private String employeeNo;
        /** 设备本地时间，格式 yyyy-MM-dd HH:mm:ss（设备需 NTP 校时） */
        private String punchTime;
        /** AUTO / IN / OUT，缺省 AUTO 由服务端按当天首末次判定 */
        private String punchType;
        private String nonce;
        private String sign;
        private String firmwareVersion;
        /** 可选：设备端生成的消息 ID，不传则由服务端按 deviceSn+nonce 派生 */
        private String messageId;
        /** 指纹/刷卡匹配分数，仅用于审计留痕 */
        private Integer matchScore;
    }

    /** 心跳上行报文 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HeartbeatMessage {
        private String deviceSn;
        private String firmwareVersion;
        private String ip;
        private String nonce;
        private String sign;
        /** 设备本地时间的 Unix 秒，便于发现设备时钟故障 */
        private Long ts;
    }

    /** 下行回执 */
    @Data
    public static class AckMessage {
        private String deviceSn;
        private String employeeNo;
        private String messageId;
        /** 0 成功，非 0 为错误码（与 ErrorCode 一致） */
        private int code;
        private String message;
        private Long recordId;
        private String punchType;
        private String status;
        private Integer lateMinutes;
        private Long serverTime;
    }
}
