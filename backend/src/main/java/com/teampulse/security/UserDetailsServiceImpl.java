package com.teampulse.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 认证用户加载 + 密码自动升级。
 *
 * <p>同时实现 {@link UserDetailsPasswordService}：当 {@code MigratingPasswordEncoder}
 * 判定存量密码需要升级时，Spring Security 会在登录成功后回调此处，
 * 把 BCrypt 密文写回数据库，实现「用户无感知」的平滑迁移。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService, UserDetailsPasswordService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return toLoginUser(user);
    }

    @Override
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        User entity = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, user.getUsername()));
        if (entity == null) {
            throw new UsernameNotFoundException("用户不存在: " + user.getUsername());
        }
        User patch = new User();
        patch.setUserId(entity.getUserId());
        patch.setPassword(newPassword);
        userMapper.updateById(patch);
        log.info("历史明文密码已自动升级为 BCrypt: userId={}", entity.getUserId());

        return LoginUser.builder()
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .password(newPassword)
                .nickname(entity.getNickname())
                .role(entity.getRole())
                .status(entity.getStatus())
                .build();
    }

    private LoginUser toLoginUser(User user) {
        return LoginUser.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .password(user.getPassword())
                .nickname(user.getNickname())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }
}
