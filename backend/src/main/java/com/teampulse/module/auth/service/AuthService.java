package com.teampulse.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.auth.dto.AuthDtos;
import com.teampulse.module.user.dto.UserDtos;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
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
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final UserMapper userMapper;

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

            LoginUser principal = (LoginUser) authentication.getPrincipal();
            String token = tokenProvider.generateToken(principal);
            User user = userMapper.selectById(principal.getUserId());

            log.info("用户登录成功: userId={}, username={}", principal.getUserId(), principal.getUsername());
            return AuthDtos.LoginResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .expiresIn(tokenProvider.getAccessTokenTtlSeconds())
                    .userId(principal.getUserId())
                    .username(principal.getUsername())
                    .nickname(principal.getNickname())
                    .role(principal.getRole())
                    .employeeNo(user != null ? user.getEmployeeNo() : null)
                    .build();
        } catch (AuthenticationException ex) {
            // 不区分「用户不存在」与「密码错误」，避免账号枚举
            log.warn("登录失败: username={}, reason={}", request.getUsername(), ex.getMessage());
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
