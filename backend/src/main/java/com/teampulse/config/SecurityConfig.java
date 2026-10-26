package com.teampulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.security.CorsProperties;
import com.teampulse.security.JwtAuthenticationFilter;
import com.teampulse.security.MigratingPasswordEncoder;
import com.teampulse.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置：无状态 JWT + 方法级鉴权。
 *
 * <p>关键点：
 * <ul>
 *   <li>关闭 CSRF：纯 Token 认证的前后端分离场景不存在 Cookie 会话，CSRF 攻击面不成立</li>
 *   <li>STATELESS：不创建 HttpSession，便于水平扩容</li>
 *   <li>{@code /iot/**} 放行：设备不带 JWT，改用设备密钥 HMAC 签名（见 DeviceSignatureVerifier）</li>
 *   <li>401/403 也返回统一 JSON，前端拦截器可统一处理</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new MigratingPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsServiceImpl userDetailsService,
                                                               PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // 登录成功后由框架回调，实现明文密码 → BCrypt 的自动升级
        provider.setUserDetailsPasswordService((UserDetailsPasswordService) userDetailsService);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 预检请求
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 登录 / 注册
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // 物联网设备侧（走 HMAC 签名，不走 JWT）
                        .requestMatchers("/iot/**").permitAll()
                        // 健康检查与接口文档
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, 401, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeJson(response, 403, ErrorCode.FORBIDDEN)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = corsProperties.effectiveOrigins();
        if (origins.isEmpty()) {
            // 同源部署：不注册任何跨域来源，浏览器同源请求不受影响
            return new UrlBasedCorsConfigurationSource();
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeJson(jakarta.servlet.http.HttpServletResponse response,
                           int httpStatus,
                           ErrorCode errorCode) throws java.io.IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }
}
