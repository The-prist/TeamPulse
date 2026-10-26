package com.teampulse.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

/**
 * 统一响应包装体。
 *
 * <p>全站接口统一返回 {@code {code, message, data, timestamp}} 结构，
 * 前端 axios 拦截器据此判断成功/失败并自动剥离 data 外壳，
 * 从而保持页面侧「拿到的就是业务数据」的直觉用法。
 *
 * @param <T> 业务数据类型
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    /** 业务状态码，0 表示成功 */
    private final int code;
    /** 人类可读提示，成功时为 "ok" */
    private final String message;
    /** 业务数据 */
    private final T data;
    /** 服务端时间戳（毫秒），便于排查时钟偏差与链路耗时 */
    private final long timestamp;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
