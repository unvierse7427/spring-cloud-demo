package com.example.auth.handler;

import com.example.common.result.Result;
import com.example.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证中心统一异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public Result<Void> handleBadCredentials(AuthenticationException ex) {
        return Result.fail(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public Result<Void> handleDisabled(AuthenticationException ex) {
        return Result.fail(ResultCode.FORBIDDEN.getCode(), "账号已被禁用或锁定");
    }

    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthentication(AuthenticationException ex) {
        return Result.fail(ResultCode.UNAUTHORIZED.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidation(BindException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : ResultCode.PARAM_ERROR.getMessage();
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("认证中心处理请求异常", ex);
        return Result.fail(ResultCode.FAIL.getCode(), ex.getMessage());
    }
}
