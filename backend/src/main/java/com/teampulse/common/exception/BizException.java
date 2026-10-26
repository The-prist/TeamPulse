package com.teampulse.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>约定：Service 层只抛 {@code BizException}，由 {@code GlobalExceptionHandler}
 * 统一翻译成 HTTP 状态码 + {@code ApiResponse}，Controller 内不写 try/catch。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String detail) {
        return new BizException(errorCode, detail);
    }
}
