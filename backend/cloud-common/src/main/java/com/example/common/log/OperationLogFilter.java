package com.example.common.log;

import com.example.common.redis.OnlineUserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 接口调用日志过滤器。
 *
 * <p>Spring Security 的过滤器顺序为 -100，本过滤器默认处于链尾，
 * 因此执行时安全上下文已经建立，可以直接取到 JWT 中的用户身份。
 * 同时顺带刷新 Redis 中的在线状态（最后活跃时间）。
 *
 * <p>日志落库实现（{@link OperationLogRecorder}）不存在时只打印到应用日志，
 * 且任何异常都不会影响正常请求。
 */
@Component
public class OperationLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OperationLogFilter.class);

    private static final String CLAIM_USERNAME = "username";

    private final String serviceName;

    private final ObjectProvider<OperationLogRecorder> recorderProvider;

    private final ObjectProvider<OnlineUserStore> onlineUserStoreProvider;

    public OperationLogFilter(@Value("${spring.application.name:unknown-service}") String serviceName,
                              ObjectProvider<OperationLogRecorder> recorderProvider,
                              ObjectProvider<OnlineUserStore> onlineUserStoreProvider) {
        this.serviceName = serviceName;
        this.recorderProvider = recorderProvider;
        this.onlineUserStoreProvider = onlineUserStoreProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startAt = System.currentTimeMillis();
        // 进入时读取身份，返回响应阶段安全上下文可能已被清理
        CurrentUser currentUser = resolveCurrentUser();
        refreshOnlineStatus(currentUser);

        Exception error = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            error = e;
            throw e;
        } finally {
            writeOperationLog(request, response, currentUser, startAt, error);
        }
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || uri.startsWith("/actuator")
                || uri.startsWith("/favicon")
                || "/error".equals(uri);
    }

    private CurrentUser resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt)) {
            return null;
        }
        Jwt jwt = (Jwt) principal;
        Long userId = null;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ignored) {
            // 自定义令牌可能不使用数字作为 subject
        }
        return new CurrentUser(userId, jwt.getClaimAsString(CLAIM_USERNAME));
    }

    private void refreshOnlineStatus(CurrentUser currentUser) {
        if (currentUser == null || currentUser.userId == null) {
            return;
        }
        OnlineUserStore onlineUserStore = onlineUserStoreProvider.getIfAvailable();
        if (onlineUserStore == null) {
            return;
        }
        try {
            onlineUserStore.touch(currentUser.userId);
        } catch (Exception e) {
            log.warn("刷新 Redis 在线状态失败, userId={}", currentUser.userId, e);
        }
    }

    private void writeOperationLog(HttpServletRequest request, HttpServletResponse response,
                                   CurrentUser currentUser, long startAt, Exception error) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int statusCode = response.getStatus();
        long durationMs = System.currentTimeMillis() - startAt;
        boolean success = error == null && statusCode < 400;

        OperationLog operationLog = new OperationLog();
        operationLog.setServiceName(serviceName);
        operationLog.setUserId(currentUser == null ? null : currentUser.userId);
        operationLog.setUsername(currentUser == null ? null : currentUser.username);
        operationLog.setRequestMethod(method);
        operationLog.setRequestUri(WebRequestUtils.truncate(uri, 255));
        operationLog.setQueryString(WebRequestUtils.truncate(request.getQueryString(), 512));
        operationLog.setClientIp(WebRequestUtils.resolveClientIp(request));
        operationLog.setUserAgent(WebRequestUtils.resolveUserAgent(request));
        operationLog.setStatusCode(statusCode);
        operationLog.setDurationMs(durationMs);
        operationLog.setSuccess(success ? 1 : 0);
        operationLog.setErrorMessage(error == null ? null : WebRequestUtils.truncate(error.getMessage(), 512));

        OperationLogRecorder recorder = recorderProvider.getIfAvailable();
        if (recorder == null) {
            log.info("[接口调用] {} {} {} status={} cost={}ms user={}", serviceName, method, uri,
                    statusCode, durationMs, operationLog.getUsername());
            return;
        }
        try {
            recorder.record(operationLog);
        } catch (Exception e) {
            log.warn("接口调用日志写入失败: {} {}", method, uri, e);
        }
    }

    /**
     * 当前请求携带的用户身份
     */
    private static final class CurrentUser {

        private final Long userId;

        private final String username;

        private CurrentUser(Long userId, String username) {
            this.userId = userId;
            this.username = username;
        }
    }
}
