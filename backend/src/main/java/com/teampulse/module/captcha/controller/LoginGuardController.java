package com.teampulse.module.captcha.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.common.util.ClientIp;
import com.teampulse.module.captcha.dto.GuardDtos;
import com.teampulse.module.captcha.service.LoginGuardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录防护状态查询与「无效尝试」登记（公开）。
 *
 * <p>只涉及**调用方自己**的状态：失败次数、是否需要验证码、是否被拉黑、剩余冷却秒数。
 * 不返回别人的计数，也不返回任何 IP 明文——IP 在服务端只以加盐摘要形式存在。
 *
 * <p>存在的意义：让登录页能把「还剩几次到限制」显示出来。
 * 否则用户只会看到验证码反复弹出，不知道机制在生效。
 */
@Tag(name = "登录防护状态", description = "查询当前访客的失败计数与冷却状态")
@RestController
@RequestMapping("/api/v1/public/login-guard")
@RequiredArgsConstructor
public class LoginGuardController {

    private final LoginGuardService loginGuard;

    @Operation(summary = "查询当前访客的登录防护状态")
    @GetMapping
    public ApiResponse<Map<String, Object>> status(HttpServletRequest request,
                                                   @RequestParam(required = false) String username) {
        return ApiResponse.ok(buildStatus(request, username));
    }

    /**
     * 登记一次「点了登录但没有真正认证」的尝试。
     *
     * <p>表单没填、格式不对时，前端在提交前就拦下了，请求到不了认证接口，
     * 服务端统计不到。这个接口让两边计数一致，用户点 10 次就真的会被限制。
     *
     * <p>只影响调用方自己的 IP，且做了同源校验，避免被第三方页面借用户的浏览器刷黑名单。
     */
    @Operation(summary = "登记一次无效登录尝试（表单未填等）")
    @PostMapping("/attempt")
    public ApiResponse<Map<String, Object>> attempt(HttpServletRequest request,
                                                    @RequestBody(required = false) GuardDtos.AttemptRequest body) {
        verifySameOrigin(request);
        String ip = ClientIp.resolve(request);
        String username = body == null || body.username() == null ? "" : body.username().trim();
        loginGuard.recordFailure(username, ip);
        return ApiResponse.ok(buildStatus(request, username));
    }

    // ------------------------------------------------------------------ 内部

    private Map<String, Object> buildStatus(HttpServletRequest request, String username) {
        String ip = ClientIp.resolve(request);
        Map<String, Object> body = new LinkedHashMap<>(loginGuard.snapshot(username == null ? "" : username, ip));
        body.put("ipBlockThreshold", 10);
        body.put("captchaThreshold", 5);
        body.put("cooldownMinutes", 10);
        return body;
    }

    /** 同源校验：有 Origin 时必须与请求 Host 一致；没有 Origin（curl/同源表单）放行 */
    private void verifySameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            return;
        }
        try {
            String originHost = URI.create(origin).getHost();
            String host = request.getHeader("Host");
            if (originHost == null || host == null || !host.startsWith(originHost)) {
                throw BizException.of(ErrorCode.FORBIDDEN, "跨站请求被拒绝");
            }
        } catch (IllegalArgumentException e) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "Origin 不合法");
        }
    }
}
