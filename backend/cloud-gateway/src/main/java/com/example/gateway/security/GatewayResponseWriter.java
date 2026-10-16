package com.example.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关侧认证/授权失败时输出统一的 JSON 结构
 */
final class GatewayResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GatewayResponseWriter() {
    }

    static Mono<Void> write(ServerHttpResponse response, HttpStatus status, int code, String message) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        byte[] bytes;
        try {
            bytes = OBJECT_MAPPER.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"code\":500,\"message\":\"响应序列化失败\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
