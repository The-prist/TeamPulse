package com.teampulse.module.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报文 nonce 去重（防重放）。
 *
 * <p>优先使用 Redis 的 {@code SET key value NX EX ttl} 原子语义；
 * 当 Redis 不可用（本地没起、云端重启）时自动降级为进程内 Map，
 * 保证打卡主链路不被缓存故障拖垮——降级期间多实例部署的去重能力会减弱，
 * 但数据库上的 {@code uk_record_emp_time} 唯一索引仍是最后一道防线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NonceDedupService {

    private static final String KEY_PREFIX = "tp:nonce:";

    private final StringRedisTemplate redisTemplate;

    /** 降级通道：key -> 过期时间戳（毫秒） */
    private final Map<String, Long> localCache = new ConcurrentHashMap<>();

    /**
     * 首次使用返回 true，重复使用返回 false。
     */
    public boolean firstUse(String nonceKey, Duration ttl) {
        if (nonceKey == null || nonceKey.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + nonceKey;
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception ex) {
            log.debug("Redis 不可用，nonce 去重降级为本地缓存: {}", ex.getMessage());
            return firstUseLocally(key, ttl);
        }
    }

    private boolean firstUseLocally(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        // 惰性清理，避免长期运行后 Map 无界增长
        if (localCache.size() > 10_000) {
            purgeExpired(now);
        }
        long expireAt = now + ttl.toMillis();
        Long previous = localCache.putIfAbsent(key, expireAt);
        if (previous == null) {
            return true;
        }
        if (previous < now) {
            // 已过期，允许重新使用
            localCache.put(key, expireAt);
            return true;
        }
        return false;
    }

    private void purgeExpired(long now) {
        Iterator<Map.Entry<String, Long>> iterator = localCache.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() < now) {
                iterator.remove();
            }
        }
    }
}
