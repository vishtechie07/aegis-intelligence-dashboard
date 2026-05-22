package com.aegis.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

public final class ClientAddressResolver {

    private ClientAddressResolver() {}

    public static String resolve(ServerHttpRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isBlank()) {
                return truncate(first, 64);
            }
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim(), 64);
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return truncate(request.getRemoteAddress().getAddress().getHostAddress(), 64);
        }
        return "unknown";
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
