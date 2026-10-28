package com.teampulse.module.captcha.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 验证码相关的传输对象。
 */
public final class CaptchaDtos {

    private CaptchaDtos() {
    }

    /**
     * 一道题：两张图 + 几何参数。
     *
     * <p>注意这里**不含答案**：缺口位置已经画进 {@code background} 里，
     * 答案只在服务端保存。
     *
     * @param token     题目编号，校验时回传
     * @param width     底图宽
     * @param height    底图高
     * @param pieceR    拼图块半径
     * @param pieceTop  拼图块的 top（相对底图）
     * @param maxOffset 滑块最大偏移
     * @param background 带缺口的底图（data URI）
     * @param piece     拼图块（data URI，透明背景）
     */
    @Schema(name = "CaptchaChallenge", description = "滑块验证码题目")
    public record Challenge(
            String token,
            int width,
            int height,
            int pieceR,
            int pieceTop,
            int maxOffset,
            String background,
            String piece
    ) {
    }

    /** 校验通过后发放的一次性票据 */
    @Schema(name = "CaptchaTicket", description = "验证码通过后的票据")
    public record Ticket(String ticket) {
    }

    /** 提交滑块偏移 */
    @Schema(name = "CaptchaVerifyRequest", description = "滑块校验请求")
    public record VerifyRequest(String token, Integer offset) {
    }
}
