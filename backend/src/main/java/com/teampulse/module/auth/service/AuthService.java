package com.teampulse.module.auth.service;

import com.teampulse.common.util.ClientIp;
import com.teampulse.module.captcha.service.CaptchaService;
import com.teampulse.module.captcha.service.LoginGuardService;
import jakarta.servlet.http.HttpServletRequest;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.auth.dto.AuthDtos;
import com.teampulse.module.user.dto.UserDtos;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import com.teampulse.module.rbac.service.RbacService;
import com.teampulse.module.user.service.UserService;
import com.teampulse.security.JwtTokenProvider;
import com.teampulse.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务：登录签发 JWT、注册建号、查询当前登录人。
 *
 * <p>登录走 Spring Security 的 {@link AuthenticationManager}，而不是手写
 * 「查库 + 比对密码」，好处是密码升级（明文→BCrypt）、账号禁用、凭证过期等
 * 策略全部由框架统一处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CaptchaService captchaService;
    private final LoginGuardService loginGuard;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final UserMapper userMapper;
    private final RbacService rbacService;

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request, HttpServletRequest httpRequest) {
        String ip = ClientIp.resolve(httpRequest);
        String username = request.getUsername();

        // ① IP 拉黑（失败过多）：冷却期内直接拒绝，连验证码都不给
        if (loginGuard.isBlocked(ip)) {
            long left = loginGuard.blockedRemainingSeconds(ip);
            long minutes = Math.max(1, (left + 59) / 60);
            log.warn("IP 已拉黑，拒绝登录: ipDigest={} 剩余 {}s", loginGuard.ipDigestPrefix(ip), left);
            throw BizException.of(ErrorCode.LOGIN_IP_BLOCKED,
                    "登录尝试过于频繁，请 " + minutes + " 分钟后再试");
        }

        // ② 账号或 IP 失败达阈值 → 每一次尝试都必须带新的验证码票据（一次性）。
        //    注意这里不重置计数：验证通过后计数依旧保留，所以「错一次就要验一次」，
        //    直到真正登录成功才清零。
        if (loginGuard.isCaptchaRequired(username, ip)
                && !captchaService.consumeTicket(request.getCaptchaTicket())) {
            // 没带有效票据就继续撞，同样算一次失败：否则计数卡在阈值上，
            // IP 永远到不了拉黑线（脚本可以无限重试 40101）
            loginGuard.recordFailure(username, ip);
            log.warn("登录需要验证码: username={}, ipDigest={}", username, loginGuard.ipDigestPrefix(ip));
            throw BizException.of(ErrorCode.LOGIN_CAPTCHA_REQUIRED);
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

            LoginUser principal = (LoginUser) authentication.getPrincipal();
            String token = tokenProvider.generateToken(principal);
            User user = userMapper.selectById(principal.getUserId());

            loginGuard.recordSuccess(username, ip);
            log.info("用户登录成功: userId={}, username={}, role={}",
                    principal.getUserId(), principal.getUsername(), principal.getRole());
            String departmentName = null;
            if (principal.getDepartmentId() != null) {
                departmentName = rbacService.departmentNameMap().get(principal.getDepartmentId());
            }
            return AuthDtos.LoginResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .expiresIn(tokenProvider.getAccessTokenTtlSeconds())
                    .userId(principal.getUserId())
                    .username(principal.getUsername())
                    .nickname(principal.getNickname())
                    .role(principal.getRole())
                    .roleName(principal.getRoleName())
                    .employeeNo(user != null ? user.getEmployeeNo() : null)
                    .departmentId(principal.getDepartmentId())
                    .departmentName(departmentName)
                    .dataScope(principal.getDataScope())
                    .permissions(principal.getPermissions() == null ? List.of() : List.copyOf(principal.getPermissions()))
                    .build();
        } catch (AuthenticationException ex) {
            // 不区分「用户不存在」与「密码错误」，避免账号枚举
            loginGuard.recordFailure(username, ip);
            log.warn("登录失败: username={}, ipDigest={}, reason={}",
                    username, loginGuard.ipDigestPrefix(ip), ex.getMessage());
            throw BizException.of(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
    }

    public Long register(AuthDtos.RegisterRequest request) {
        if (userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, request.getUsername()))) {
            throw BizException.of(ErrorCode.CONFLICT, "用户名已存在");
        }
        UserDtos.CreateRequest create = new UserDtos.CreateRequest();
        create.setUsername(request.getUsername());
        create.setPassword(request.getPassword());
        create.setEmail(request.getEmail());
        create.setNickname(request.getNickname());
        create.setPhone(request.getPhone());
        // 自助注册只能是普通成员，防止越权注册管理员
        create.setRole("member");
        create.setStatus("active");
        User user = userService.create(create);
        return user.getUserId();
    }

    /** 当前登录人信息（含最新工号，便于前端展示） */
    public User currentUser(Long userId) {
        return userService.getById(userId);
    }
}
