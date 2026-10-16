package com.example.user.handler;

import com.example.common.result.Result;
import com.example.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理，保证异常也返回统一的 Result 结构
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, DuplicateKeyException.class})
    public Result<Void> handleBadRequest(Exception e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.fail(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("请求处理失败", e);
        return Result.fail(500, "服务器内部错误：" + e.getMessage());
    }
}
