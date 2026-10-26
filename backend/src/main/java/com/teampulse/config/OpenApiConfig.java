package com.teampulse.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 文档配置。
 *
 * <p>解决两个实际问题：
 * <ol>
 *   <li>原来的 /v3/api-docs 没有声明鉴权方案，Swagger UI 里没有「Authorize」按钮，
 *       调试受保护接口只能手写 curl；</li>
 *   <li>设备接入接口（/iot/**）用的是 HMAC 验签而不是用户令牌，不能和业务接口一样标 Bearer，
 *       否则按文档调用会 401。</li>
 * </ol>
 *
 * <p>注意：本配置只在 TPP_OPENAPI_ENABLED=true 时才有意义（生产默认关闭），
 * 但 Bean 本身无副作用，开关关闭时只是不暴露端点。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    public OpenAPI teamPulseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TeamPulse API")
                        .version("1.0.0")
                        .description("""
                                团队协作与物联网考勤平台接口。
                                
                                - 业务接口：`Authorization: Bearer <JWT>`，登录后从 /api/v1/auth/login 获取
                                - 设备接口：`/iot/v1/**` 不使用 JWT，改为 HMAC-SHA256 验签
                                  （签名串 = deviceSn|employeeNo|punchTime|nonce，密钥由设备注册/轮换接口下发）
                                - 统一响应：`{ code, message, data, timestamp }`，code=0 表示成功
                                """)
                        .contact(new Contact().name("TeamPulse"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("/").description("当前部署（经 Nginx 同源访问）")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token，直接填进 Value 即可，不用加 Bearer 前缀")));
    }

    /**
     * 给所有「非设备」端点加上 Bearer 安全声明。
     *
     * <p>设备端点的验签在服务里实现（HMAC），文档里标成 Bearer 会误导调用方。
     */
    @Bean
    public OpenApiCustomizer teamPulseSecurityCustomizer() {
        return openApi -> {
            SecurityRequirement requirement = new SecurityRequirement().addList(BEARER);
            openApi.getPaths().forEach((path, item) -> {
                if (path.startsWith("/iot/")) {
                    return;
                }
                item.readOperations().forEach(op -> op.addSecurityItem(requirement));
            });
        };
    }
}
