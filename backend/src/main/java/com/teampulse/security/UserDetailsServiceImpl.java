package com.teampulse.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teampulse.module.rbac.service.RbacService;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 认证用户加载 + 密码自动升级。
 *
 * <p>同时实现 {@link UserDetailsPasswordService}：当 {@code MigratingPasswordEncoder}
 * 判定存量密码需要升级时，Spring Security 会在登录成功后回调此处，
 * 把 BCrypt 密文写回数据库，实现「用户无感知」的平滑迁移。
 *
 * <p>RBAC 改造后，这里还负责把**角色、数据范围、权限点**装配进 LoginUser：
 * 权限点来自 role_permission（RbacService 内部有缓存），因此角色权限调整后
 * 用户下一次请求即可生效，不需要重新登录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService, UserDetailsPasswordService {

    private final UserMapper userMapper;
    private final RbacService rbacService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return toLoginUser(user);
    }

    /** 按主键加载（JWT 过滤器每次请求用它刷新权限） */
    public LoginUser loadById(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? null : toLoginUser(user);
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

        return toLoginUser(entity);
    }

    private LoginUser toLoginUser(User user) {
        String role = user.getRole();
        Set<String> permissions = rbacService.permissionsOf(role);
        return LoginUser.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .password(user.getPassword())
                .nickname(user.getNickname())
                .role(role)
                .roleName(rbacService.roleNameOf(role))
                .status(user.getStatus())
                .departmentId(user.getDepartmentId())
                .dataScope(rbacService.dataScopeOf(role))
                .permissions(permissions)
                .build();
    }
}
