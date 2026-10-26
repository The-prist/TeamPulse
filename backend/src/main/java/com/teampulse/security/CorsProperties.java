package com.teampulse.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置。
 *
 * <p>生产环境采用「Nginx 同源反代前后端」，此时不需要放行任何跨域来源（留空即可）；
 * 本地开发由 Vite 代理转发，理论上也不需要，但保留 localhost:5173 以便直连调试。
 */
@Data
@Component
@ConfigurationProperties(prefix = "teampulse.cors")
public class CorsProperties {

    /** 允许的来源，支持精确域名；留空表示不开启跨域 */
    private List<String> allowedOrigins = new ArrayList<>();

    /** 去掉配置里可能存在的空白项（例如环境变量写成逗号结尾） */
    public List<String> effectiveOrigins() {
        if (allowedOrigins == null) {
            return List.of();
        }
        return allowedOrigins.stream()
                .filter(o -> o != null && !o.isBlank())
                .map(String::trim)
                .toList();
    }
}
