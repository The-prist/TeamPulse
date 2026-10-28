package com.teampulse.module.captcha.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录防护状态查询与"无效尝试"登记用的传输对象。
 */
public final class GuardDtos {

    private GuardDtos() {
    }

    /**
     * 登记一次"点了登录但没真正认证"的尝试。
     *
     * <p>典型场景：账号或密码为空、格式不对——前端在表单校验阶段就拦下了，
     * 请求不会走到认证接口，服务端因此统计不到。这个接口让两边的计数保持一致。
     *
     * @param username 当时填写的用户名（可能为空）
     */
    @Schema(name = "GuardAttemptRequest", description = "登记一次无效登录尝试")
    public record AttemptRequest(String username) {
    }
}
