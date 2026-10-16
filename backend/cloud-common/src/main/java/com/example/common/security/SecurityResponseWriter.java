package com.example.common.security;

import com.example.common.result.Result;
import com.example.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 认证/授权失败时统一以 JSON 结构输出
 */
public final class SecurityResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecurityResponseWriter() {
    }

    public static void write(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(resultCode.getCode());
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Result.fail(resultCode.getCode(), resultCode.getMessage()));
    }
}
