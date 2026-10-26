package com.teampulse.module.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teampulse.common.api.PageResult;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.user.dto.UserDtos;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户领域服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /** 分页查询，keyword 同时匹配用户名 / 昵称 / 邮箱 / 工号 */
    public PageResult<User> page(String keyword, long page, long size) {
        var wrapper = Wrappers.<User>lambdaQuery().orderByDesc(User::getUserId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getNickname, kw)
                    .or().like(User::getEmail, kw)
                    .or().like(User::getEmployeeNo, kw));
        }
        IPage<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.from(result, u -> u);
    }

    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "用户不存在: " + id);
        }
        return user;
    }

    public List<User> listOptions() {
        return userMapper.listOptions();
    }

    /** 按工号查询，供打卡链路使用；未找到返回 null（由调用方决定降级策略） */
    public User findByEmployeeNo(String employeeNo) {
        return userMapper.findByEmployeeNo(employeeNo);
    }

    @Transactional
    public User create(UserDtos.CreateRequest req) {
        if (userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, req.getUsername()))) {
            throw BizException.of(ErrorCode.CONFLICT, "用户名已存在");
        }
        if (userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getEmail, req.getEmail()))) {
            throw BizException.of(ErrorCode.CONFLICT, "邮箱已被占用");
        }
        if (StringUtils.hasText(req.getEmployeeNo())
                && userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getEmployeeNo, req.getEmployeeNo()))) {
            throw BizException.of(ErrorCode.CONFLICT, "工号已被占用");
        }

        User user = new User();
        user.setUsername(req.getUsername().trim());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setEmail(req.getEmail().trim());
        user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname() : req.getUsername());
        user.setPhone(req.getPhone());
        user.setEmployeeNo(StringUtils.hasText(req.getEmployeeNo()) ? req.getEmployeeNo() : null);
        user.setRole(req.getRole());
        user.setStatus(req.getStatus());
        userMapper.insert(user);
        log.info("创建用户成功: userId={}, username={}, employeeNo={}",
                user.getUserId(), user.getUsername(), user.getEmployeeNo());
        return user;
    }

    @Transactional
    public void update(Long id, UserDtos.UpdateRequest req) {
        User user = getById(id);
        if (StringUtils.hasText(req.getEmail()) && !req.getEmail().equals(user.getEmail())) {
            if (userMapper.exists(Wrappers.<User>lambdaQuery()
                    .eq(User::getEmail, req.getEmail())
                    .ne(User::getUserId, id))) {
                throw BizException.of(ErrorCode.CONFLICT, "邮箱已被占用");
            }
            user.setEmail(req.getEmail());
        }
        if (StringUtils.hasText(req.getEmployeeNo()) && !req.getEmployeeNo().equals(user.getEmployeeNo())) {
            if (userMapper.exists(Wrappers.<User>lambdaQuery()
                    .eq(User::getEmployeeNo, req.getEmployeeNo())
                    .ne(User::getUserId, id))) {
                throw BizException.of(ErrorCode.CONFLICT, "工号已被占用");
            }
            user.setEmployeeNo(req.getEmployeeNo());
        }
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getRole() != null) {
            user.setRole(req.getRole());
        }
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        userMapper.updateById(user);
    }

    /** 自助更新个人资料：只允许昵称、邮箱、电话、头像 */
    @Transactional
    public void updateSelf(Long id, UserDtos.SelfUpdateRequest req) {
        User user = getById(id);
        if (StringUtils.hasText(req.getEmail()) && !req.getEmail().equals(user.getEmail())) {
            if (userMapper.exists(Wrappers.<User>lambdaQuery()
                    .eq(User::getEmail, req.getEmail())
                    .ne(User::getUserId, id))) {
                throw BizException.of(ErrorCode.CONFLICT, "邮箱已被占用");
            }
            user.setEmail(req.getEmail());
        }
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar());
        }
        userMapper.updateById(user);
    }

    /**
     * 修改密码：先校验旧密码（同时兼容明文与 BCrypt），再写入新密文。
     */
    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = getById(id);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "旧密码不正确");
        }
        User update = new User();
        update.setUserId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    /** 管理员重置密码 */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        getById(id);
        User update = new User();
        update.setUserId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        userMapper.deleteById(id);
    }

    /**
     * 统计各状态用户数，供管理端概览使用。
     */
    public long countAll() {
        return userMapper.selectCount(Wrappers.<User>lambdaQuery());
    }
}
