package com.teampulse.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码表。
 *
 * <p>码段规划：
 * <ul>
 *   <li>0            成功</li>
 *   <li>4xxxx        客户端错误（参数、鉴权、资源）</li>
 *   <li>5xxxx        服务端错误</li>
 *   <li>6xxxx        物联网设备侧错误（独立码段，便于设备厂商对接排障）</li>
 * </ul>
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok", HttpStatus.OK),

    BAD_REQUEST(40000, "请求参数不合法", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    /** 连续登录失败，必须先通过验证码 */
    LOGIN_CAPTCHA_REQUIRED(40101, "连续登录失败，请先完成安全验证", HttpStatus.LOCKED),
    /** 验证码不正确或已过期 */
    CAPTCHA_INVALID(40102, "验证码不正确或已过期", HttpStatus.BAD_REQUEST),
    FORBIDDEN(40300, "没有该操作的权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT(40900, "资源冲突", HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(42900, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    DEVICE_NOT_REGISTERED(60001, "设备未注册", HttpStatus.UNAUTHORIZED),
    DEVICE_DISABLED(60002, "设备已被禁用", HttpStatus.FORBIDDEN),
    DEVICE_SIGN_INVALID(60003, "设备报文签名校验失败", HttpStatus.UNAUTHORIZED),
    DEVICE_REPLAY(60004, "重复报文（重放保护命中）", HttpStatus.CONFLICT),
    DEVICE_PAYLOAD_INVALID(60005, "设备报文格式不合法", HttpStatus.BAD_REQUEST),
    DEVICE_CLOCK_DRIFT(60006, "设备时间戳超出允许范围，请校准设备时钟", HttpStatus.BAD_REQUEST),

    EMPLOYEE_NOT_FOUND(61001, "员工工号不存在或已离职", HttpStatus.NOT_FOUND),
    ATTENDANCE_RULE_MISSING(61002, "未配置考勤规则", HttpStatus.CONFLICT),
    ATTENDANCE_DUPLICATE(61003, "该员工在该时刻已有打卡记录", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
