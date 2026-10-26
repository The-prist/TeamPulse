package com.teampulse.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 安全相关配置项，全部可通过环境变量覆盖（部署时无需改代码）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "teampulse.jwt")
public class JwtProperties {

    /**
     * 签名密钥。HS256 要求长度 >= 32 字节；生产环境必须由环境变量 TPP_JWT_SECRET 注入。
     */
    private String secret = "teampulse-dev-only-secret-change-me-0123456789";

    /** 访问令牌有效期 */
    private Duration accessTokenTtl = Duration.ofHours(12);

    /** 签发者，写入 iss 声明 */
    private String issuer = "teampulse";
}
