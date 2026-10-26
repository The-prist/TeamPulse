package com.teampulse.module.attendance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 设备报文签名校验（HMAC-SHA256）。
 *
 * <p>为什么设备侧不用 JWT：ESP32 算力和存储都有限，且设备是长期在线的固定终端，
 * 用「预共享密钥 + HMAC」比维护令牌生命周期更适合嵌入式场景。
 *
 * <p>防重放由两个机制共同保证：
 * <ol>
 *   <li>签名里带非重复 nonce，服务端在时间窗内去重（见 {@link NonceDedupService}）</li>
 *   <li>punchTime 必须落在允许的时钟漂移窗口内，超窗直接拒收</li>
 * </ol>
 */
@Slf4j
@Component
public class DeviceSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * 构造待签名串。字段顺序必须与 ESP32 固件完全一致：
     * {@code deviceSn|employeeNo|punchTime|nonce}
     */
    public String canonicalPunch(String deviceSn, String employeeNo, String punchTime, String nonce) {
        return String.join("|", nullToEmpty(deviceSn), nullToEmpty(employeeNo),
                nullToEmpty(punchTime), nullToEmpty(nonce));
    }

    /** 心跳的待签名串：{@code deviceSn|nonce} */
    public String canonicalHeartbeat(String deviceSn, String nonce) {
        return String.join("|", nullToEmpty(deviceSn), nullToEmpty(nonce));
    }

    /** 计算签名，输出小写十六进制 */
    public String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC 计算失败", ex);
        }
    }

    /**
     * 校验签名。使用常量时间比较，避免通过响应时间侧信道爆破签名。
     */
    public boolean verify(String secret, String payload, String providedSign) {
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(providedSign)) {
            return false;
        }
        String expected = sign(secret, payload);
        boolean match = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSign.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        if (!match) {
            log.warn("设备报文验签失败, payload={}", payload);
        }
        return match;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
