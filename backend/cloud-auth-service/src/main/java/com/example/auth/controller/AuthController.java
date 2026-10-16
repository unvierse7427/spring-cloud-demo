package com.example.auth.controller;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RefreshTokenRequest;
import com.example.auth.dto.TokenResponse;
import com.example.auth.security.JwtTokenService;
import com.example.auth.security.LoginUser;
import com.example.auth.security.LoginUserService;
import com.example.common.log.LoginLog;
import com.example.common.log.WebRequestUtils;
import com.example.common.log.mapper.LoginLogMapper;
import com.example.common.redis.OnlineUser;
import com.example.common.redis.OnlineUserStore;
import com.example.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证接口：登录、刷新令牌、登出、获取当前用户、在线用户
 *
 * <p>登录成功会写入 Redis 在线状态，并记录登录日志（成功与失败都会记录）。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;

    private final JwtTokenService jwtTokenService;

    private final LoginUserService loginUserService;

    private final JwtDecoder jwtDecoder;

    private final OnlineUserStore onlineUserStore;

    private final LoginLogMapper loginLogMapper;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenService jwtTokenService,
                          LoginUserService loginUserService,
                          JwtDecoder jwtDecoder,
                          OnlineUserStore onlineUserStore,
                          LoginLogMapper loginLogMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.loginUserService = loginUserService;
        this.jwtDecoder = jwtDecoder;
        this.onlineUserStore = onlineUserStore;
        this.loginLogMapper = loginLogMapper;
    }

    /**
     * 账号密码登录，签发访问令牌与刷新令牌，并维护 Redis 在线状态
     */
    @PostMapping("/login")
    public Result<TokenResponse> login(@RequestBody @Validated LoginRequest request, HttpServletRequest servletRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (AuthenticationException ex) {
            saveLoginLog(null, request.getUsername(), LoginLog.TYPE_LOGIN,
                    LoginLog.STATUS_FAILURE, ex.getMessage(), servletRequest);
            throw ex;
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userId = loginUser.getUser().getId();
        JwtTokenService.TokenPair pair = jwtTokenService.createToken(
                userId, loginUser.getUsername(), loginUser.getAuthorityValues());

        markOnline(loginUser, servletRequest);
        saveLoginLog(userId, loginUser.getUsername(), LoginLog.TYPE_LOGIN,
                LoginLog.STATUS_SUCCESS, "登录成功", servletRequest);
        return Result.success("登录成功", toResponse(pair));
    }

    /**
     * 使用刷新令牌换取新的令牌对，权限从数据库重新加载
     */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@RequestBody @Validated RefreshTokenRequest request,
                                         HttpServletRequest servletRequest) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(request.getRefreshToken());
        } catch (Exception ex) {
            throw new BadCredentialsException("刷新令牌无效或已过期");
        }
        if (!JwtTokenService.TOKEN_TYPE_REFRESH.equals(jwt.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE))) {
            throw new BadCredentialsException("令牌类型错误，需要刷新令牌");
        }
        Long userId = parseUserId(jwt.getSubject());
        String username = jwt.getClaimAsString(JwtTokenService.CLAIM_USERNAME);
        JwtTokenService.TokenPair pair = jwtTokenService.createToken(
                userId, username, loginUserService.loadAuthorities(userId));

        touchOnline(userId);
        saveLoginLog(userId, username, LoginLog.TYPE_REFRESH,
                LoginLog.STATUS_SUCCESS, "刷新令牌", servletRequest);
        return Result.success(toResponse(pair));
    }

    /**
     * 登出：移除 Redis 在线状态（无状态 JWT 由客户端丢弃令牌）
     */
    @PostMapping("/logout")
    public Result<Void> logout(JwtAuthenticationToken authentication, HttpServletRequest servletRequest) {
        Jwt jwt = authentication.getToken();
        Long userId = parseUserId(jwt.getSubject());
        String username = jwt.getClaimAsString(JwtTokenService.CLAIM_USERNAME);

        offline(userId);
        saveLoginLog(userId, username, LoginLog.TYPE_LOGOUT,
                LoginLog.STATUS_SUCCESS, "退出登录", servletRequest);
        return Result.success("登出成功", null);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", jwt.getSubject());
        data.put("username", jwt.getClaimAsString(JwtTokenService.CLAIM_USERNAME));
        data.put("authorities", jwt.getClaimAsStringList(JwtTokenService.CLAIM_AUTHORITIES));
        return Result.success(data);
    }

    /**
     * 查询当前在线用户（来自 Redis）
     */
    @GetMapping("/online")
    @PreAuthorize("hasAuthority('user:list')")
    public Result<List<OnlineUser>> onlineUsers() {
        return Result.success(onlineUserStore.listOnlineUsers());
    }

    private void markOnline(LoginUser loginUser, HttpServletRequest request) {
        OnlineUser onlineUser = new OnlineUser(loginUser.getUser().getId(), loginUser.getUsername(),
                loginUser.getUser().getNickname());
        onlineUser.setClientIp(WebRequestUtils.resolveClientIp(request));
        onlineUser.setUserAgent(WebRequestUtils.resolveUserAgent(request));
        try {
            onlineUserStore.online(onlineUser);
        } catch (Exception e) {
            // Redis 异常不应该影响登录本身
            log.warn("写入 Redis 在线状态失败, userId={}", loginUser.getUser().getId(), e);
        }
    }

    private void touchOnline(Long userId) {
        try {
            onlineUserStore.touch(userId);
        } catch (Exception e) {
            log.warn("刷新 Redis 在线状态失败, userId={}", userId, e);
        }
    }

    private void offline(Long userId) {
        try {
            onlineUserStore.offline(userId);
        } catch (Exception e) {
            log.warn("移除 Redis 在线状态失败, userId={}", userId, e);
        }
    }

    private void saveLoginLog(Long userId, String username, String loginType, int status,
                              String message, HttpServletRequest request) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(safeUsername(username));
            loginLog.setLoginType(loginType);
            loginLog.setStatus(status);
            loginLog.setMessage(WebRequestUtils.truncate(message, 255));
            loginLog.setClientIp(WebRequestUtils.resolveClientIp(request));
            loginLog.setUserAgent(WebRequestUtils.resolveUserAgent(request));
            loginLog.setLoginTime(LocalDateTime.now());
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.warn("写入登录日志失败, username={}", username, e);
        }
    }

    private String safeUsername(String username) {
        String value = WebRequestUtils.truncate(username, 64);
        return (value == null || value.isEmpty()) ? "unknown" : value;
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private TokenResponse toResponse(JwtTokenService.TokenPair pair) {
        return new TokenResponse(pair.getAccessToken(), pair.getRefreshToken(),
                pair.getExpiresIn(), pair.getRefreshExpiresIn());
    }
}
