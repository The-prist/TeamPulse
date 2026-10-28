package com.teampulse.module.captcha.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败计数（防撞库限流）。
 *
 * <p>按「账号」和「IP」两条线分别计数，任一达到阈值即要求验证码：
 * 只按账号计数挡不住「撞很多账号但每个只试几次」；
 * 只按 IP 计数又会让同一个出口（公司 NAT）下的正常用户互相牵连，
 * 所以两条线都要看。
 *
 * <p>纯内存实现：单实例部署够用，重启即清零；
 * 多实例部署时应换成 Redis（同一个 key 结构）。
 */
@Slf4j
@Service
public class LoginAttemptTracker {

    /** 失败阈值 */
    private static final int THRESHOLD = 5;
    /** 统计窗口 */
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Counter> byUser = new ConcurrentHashMap<>();
    private final Map<String, Counter> byIp = new ConcurrentHashMap<>();

    /** 是否需要验证码 */
    public boolean isCaptchaRequired(String username, String ip) {
        return exceeded(byUser.get(key("u", username))) || exceeded(byIp.get(key("ip", ip)));
    }

    public void recordFailure(String username, String ip) {
        bump(byUser, key("u", username));
        bump(byIp, key("ip", ip));
    }

    public void recordSuccess(String username, String ip) {
        byUser.remove(key("u", username));
        byIp.remove(key("ip", ip));
    }

    /** 调试用：当前计数 */
    public Map<String, Integer> snapshot(String username, String ip) {
        Counter u = byUser.get(key("u", username));
        Counter i = byIp.get(key("ip", ip));
        return Map.of(
                "user", u == null ? 0 : u.count,
                "ip", i == null ? 0 : i.count,
                "threshold", THRESHOLD);
    }

    private static boolean exceeded(Counter counter) {
        if (counter == null) {
            return false;
        }
        if (System.currentTimeMillis() - counter.firstAt > WINDOW.toMillis()) {
            return false;
        }
        return counter.count >= THRESHOLD;
    }

    private static void bump(Map<String, Counter> map, String key) {
        long now = System.currentTimeMillis();
        map.compute(key, (k, old) -> {
            if (old == null || now - old.firstAt > WINDOW.toMillis()) {
                return new Counter(1, now);
            }
            return new Counter(old.count + 1, old.firstAt);
        });
        if (map.size() > 50000) {
            map.clear();
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
