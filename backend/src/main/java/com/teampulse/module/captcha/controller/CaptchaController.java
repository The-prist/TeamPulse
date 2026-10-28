package com.teampulse.module.captcha.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.captcha.dto.CaptchaDtos;
import com.teampulse.module.captcha.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 滑块验证码（公开接口）。
 *
 * <p>答案只存在服务端：前端拿到的是两张渲染好的 PNG，
 * 想通过就得真的把拼图块对齐缺口。
 */
@Tag(name = "验证码", description = "滑块拼图验证码")
@RestController
@RequestMapping("/api/v1/public/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    @Operation(summary = "取一道新题", description = "返回 token 与两张图片，答案不下发")
    @GetMapping
    public ApiResponse<CaptchaDtos.Challenge> issue() {
        return ApiResponse.ok(captchaService.issue());
    }

    @Operation(summary = "校验滑块偏移", description = "通过则返回一次性票据，登录时带上")
    @PostMapping("/verify")
    public ApiResponse<CaptchaDtos.Ticket> verify(@RequestBody CaptchaDtos.VerifyRequest request) {
        if (request == null || request.token() == null || request.offset() == null) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "缺少 token 或 offset");
        }
        String ticket = captchaService.verify(request.token(), request.offset());
        if (ticket == null) {
            throw BizException.of(ErrorCode.CAPTCHA_INVALID);
        }
        return ApiResponse.ok(new CaptchaDtos.Ticket(ticket));
    }
}
