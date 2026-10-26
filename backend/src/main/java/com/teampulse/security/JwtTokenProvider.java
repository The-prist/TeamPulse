package com.teampulse.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。
 *
 * <p>使用 jjwt 0.12.x 新 API（{@code verifyWith} / {@code parseSignedClaims}）。
 * 令牌声明包含 uid / role / nickname，解析后可直接重建 {@link LoginUser}，无需回查数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NICKNAME = "nickname";

    private final JwtProperties properties;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥不少于 256 位，这里提前失败，避免线上才发现
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "teampulse.jwt.secret 长度不足 32 字节（当前 " + keyBytes.length + "），请通过环境变量 TPP_JWT_SECRET 配置更长的密钥");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT 初始化完成，accessToken 有效期={}", properties.getAccessTokenTtl());
    }

    /** 签发访问令牌 */
    public String generateToken(LoginUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getAccessTokenTtl().toMillis());
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getUsername())
                .claim(CLAIM_UID, user.getUserId())
                .claim(CLAIM_ROLE, user.getRole())
                .claim(CLAIM_NICKNAME, user.getNickname())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌。
     *
     * @return 解析出的认证主体；令牌无效/过期时返回 null（由过滤器决定放行或拒绝）
     */
    public LoginUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Number uid = claims.get(CLAIM_UID, Number.class);
            if (uid == null) {
                return null;
            }
            return LoginUser.builder()
                    .userId(uid.longValue())
                    .username(claims.getSubject())
                    .role(claims.get(CLAIM_ROLE, String.class))
                    .nickname(claims.get(CLAIM_NICKNAME, String.class))
                    .status("active")
                    .build();
        } catch (Exception ex) {
            // 过期、签名不符、结构损坏都走这里；不打印堆栈避免日志噪音
            log.debug("JWT 解析失败: {}", ex.getMessage());
            return null;
        }
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }
}
