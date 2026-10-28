package com.teampulse.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 取访客真实 IP。
 *
 * <p>nginx 会把真实来源写进 {@code X-Real-IP} / {@code X-Forwarded-For}；
 * 本机或内网地址不参与限流（直接返回 remoteAddr，让上层按内网处理）。
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String real = trimToNull(request.getHeader("X-Real-IP"));
        if (real != null) {
            return real;
        }
        String forwarded = trimToNull(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            for (String part : forwarded.split(",")) {
                String candidate = trimToNull(part);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        String remote = trimToNull(request.getRemoteAddr());
        return remote == null ? "unknown" : remote;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
