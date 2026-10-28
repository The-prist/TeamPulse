package com.teampulse.module.captcha.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录防护：失败计数 + 验证码强制 + IP 拉黑。
 *
 * <p><b>计数策略</b>：按「账号」和「IP」两条线分别计数——只按账号挡不住
 * 「撞很多账号但每个只试几次」，只按 IP 又会误伤 NAT 后的正常用户。
 *
 * <p><b>验证码是粘性的</b>：任一线路达到 5 次失败后，后续**每一次**登录尝试都必须
 * 带一个新鲜的 ticket（票据一次性），直到登录成功才清零。这样「错一次就要验一次」。
 *
 * <p><b>IP 拉黑</b>：同一 IP 累计 10 次失败 → 拉黑 10 分钟，期间直接拒绝登录。
 * 黑名单里**不存原始 IP**：键是「进程启动时随机生成的盐 + SHA-256」的摘要，
 * 进程重启即失效，即使内存被 dump 也无法反查具体 IP。
 *
 * <p><b>存储</b>：纯内存，单实例够用；多实例部署需换成 Redis（key 结构见下）。
 */
@Slf4j
@Service
public class LoginGuardService {

    /** 触发验证码的失败次数 */
    private static final int CAPTCHA_THRESHOLD = 5;
    /** 触发 IP 拉黑的失败次数 */
    private static final int IP_BLOCK_THRESHOLD = 10;
    /** 计数窗口 */
    private static final Duration WINDOW = Duration.ofMinutes(15);
    /** IP 拉黑冷却时间 */
    private static final Duration IP_BLOCK_COOLDOWN = Duration.ofMinutes(10);

    private final Map<String, Counter> byUser = new ConcurrentHashMap<>();
    private final Map<String, Counter> byIp = new ConcurrentHashMap<>();
    /** IP 摘要 → 解封时间戳 */
    private final Map<String, Long> ipBlacklist = new ConcurrentHashMap<>();

    /** 每次启动随机生成，用于对 IP 做不可逆摘要 */
    private final byte[] ipSalt = new byte[32];

    public LoginGuardService() {
        new SecureRandom().nextBytes(ipSalt);
    }

    // ------------------------------------------------------------------ 查询

    /** 该 IP 是否在拉黑期内 */
    public boolean isBlocked(String ip) {
        String key = hashIp(ip);
        Long until = ipBlacklist.get(key);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            ipBlacklist.remove(key);
            return false;
        }
        return true;
    }

    /** 距离解封还有多少秒（未拉黑返回 0） */
    public long blockedRemainingSeconds(String ip) {
        Long until = ipBlacklist.get(hashIp(ip));
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    /** 是否需要验证码（账号或 IP 任一超限，粘性保持到登录成功） */
    public boolean isCaptchaRequired(String username, String ip) {
        return exceeded(byUser.get(key("u", username))) || exceeded(byIp.get(key("ip", ip)));
    }

    /** 日志里用的 IP 摘要前缀（避免把原始 IP 写进日志） */
    public String ipDigestPrefix(String ip) {
        return hashIp(ip).substring(0, 12);
    }

    /** 调试用：当前计数与状态 */
    public Map<String, Object> snapshot(String username, String ip) {
        Counter u = byUser.get(key("u", username));
        Counter i = byIp.get(key("ip", ip));
        return Map.of(
                "userFailures", u == null ? 0 : u.count,
                "ipFailures", i == null ? 0 : i.count,
                "captchaThreshold", CAPTCHA_THRESHOLD,
                "ipBlockThreshold", IP_BLOCK_THRESHOLD,
                "blocked", isBlocked(ip),
                "blockedRemainingSeconds", blockedRemainingSeconds(ip),
                "ipDigestPrefix", hashIp(ip).substring(0, 8));
    }

    // ------------------------------------------------------------------ 记录

    public void recordFailure(String username, String ip) {
        bump(byUser, key("u", username));
        Counter ipCounter = bump(byIp, key("ip", ip));
        if (ipCounter != null && ipCounter.count >= IP_BLOCK_THRESHOLD) {
            String hashed = hashIp(ip);
            ipBlacklist.put(hashed, System.currentTimeMillis() + IP_BLOCK_COOLDOWN.toMillis());
            // 只记摘要前缀，日志里不出现原始 IP
            log.warn("IP 触发拉黑: digest={} 计数={} 冷却={}分钟", hashed.substring(0, 8),
                    ipCounter.count, IP_BLOCK_COOLDOWN.toMinutes());
        }
    }

    public void recordSuccess(String username, String ip) {
        byUser.remove(key("u", username));
        byIp.remove(key("ip", ip));
    }

    // ------------------------------------------------------------------ 内部

    private static boolean exceeded(Counter counter) {
        if (counter == null) {
            return false;
        }
        if (System.currentTimeMillis() - counter.firstAt > WINDOW.toMillis()) {
            return false;
        }
        return counter.count >= CAPTCHA_THRESHOLD;
    }

    private static Counter bump(Map<String, Counter> map, String key) {
        long now = System.currentTimeMillis();
        Counter updated = map.compute(key, (k, old) -> {
            if (old == null || now - old.firstAt > WINDOW.toMillis()) {
                return new Counter(1, now);
            }
            return new Counter(old.count + 1, old.firstAt);
        });
        if (map.size() > 50000) {
            map.clear();
        }
        return updated;
    }

    /** IP 摘要：随机盐 + SHA-256，内存里只有摘要，重启即失效 */
    private String hashIp(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(ipSalt);
            byte[] raw = md.digest((ip == null ? "unknown" : ip.trim()).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(String.valueOf(ip).hashCode());
        }
    }

    private static String key(String prefix, String value) {
        return prefix + ":" + (value == null ? "unknown" : value.toLowerCase());
    }

    private static final class Counter {
        private final int count;
        private final long firstAt;

        private Counter(int count, long firstAt) {
            this.count = count;
            this.firstAt = firstAt;
        }
    }
}
