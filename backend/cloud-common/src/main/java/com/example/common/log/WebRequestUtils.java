package com.example.common.log;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求中提取客户端信息的小工具
 */
public final class WebRequestUtils {

    private static final String UNKNOWN = "unknown";

    private WebRequestUtils() {
    }

    /**
     * 优先取网关透传的转发头，取不到再回退到直连地址
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !UNKNOWN.equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                String ip = comma > 0 ? value.substring(0, comma) : value;
                return truncate(ip, 64);
            }
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    public static String resolveUserAgent(HttpServletRequest request) {
        return truncate(request.getHeader("User-Agent"), 512);
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
