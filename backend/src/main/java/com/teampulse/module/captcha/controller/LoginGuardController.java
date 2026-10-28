package com.teampulse.module.captcha.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.util.ClientIp;
import com.teampulse.module.captcha.service.LoginGuardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录防护状态查询（公开）。
 *
 * <p>只返回**调用方自己**的状态：失败次数、是否需要验证码、是否被拉黑、剩余冷却秒数。
 * 不返回别人的计数，也不返回任何 IP 明文——IP 在服务端只以加盐摘要形式存在。
 *
 * <p>存在的意义：让登录页能把「还剩几次到限制」显示出来，
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
        String ip = ClientIp.resolve(request);
        Map<String, Object> body = new LinkedHashMap<>(loginGuard.snapshot(username == null ? "" : username, ip));
        body.put("ipBlockThreshold", 10);
        body.put("captchaThreshold", 5);
        body.put("cooldownMinutes", 10);
        return ApiResponse.ok(body);
    }
}
