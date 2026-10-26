package com.teampulse.module.rbac.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import com.teampulse.security.LoginUser;
import com.teampulse.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析当前登录人的数据范围。
 *
 * <p>规则来自角色上的 {@code data_scope}：
 * ALL → 不限制；DEPT → 本部门所有成员；SELF → 只有自己。
 * 业务查询只要拿到 {@link DataScope}，用 {@code creator_id IN userIds} 一类条件过滤即可，
 * 不需要在每个 Service 里重复判断角色名。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final UserMapper userMapper;

    private final RbacService rbacService;

    public DataScope current() {
        LoginUser user = SecurityUtils.currentUserOrNull();
        if (user == null) {
            return DataScope.ofUsers(Set.of());
        }
        return of(user.getRole(), user.getDepartmentId(), user.getUserId());
    }

    public DataScope of(String roleKey, Long departmentId, Long userId) {
        String scope = rbacService.dataScopeOf(roleKey);
        if ("ALL".equals(scope)) {
            return DataScope.all();
        }
        if ("DEPT".equals(scope) && departmentId != null) {
            List<User> members = userMapper.selectList(Wrappers.<User>lambdaQuery()
                    .select(User::getUserId)
                    .eq(User::getDepartmentId, departmentId));
            Set<Long> ids = new HashSet<>();
            for (User u : members) {
                ids.add(u.getUserId());
            }
            // 部门里一个人都没有时，至少能看到自己，避免直接把页面清空
            if (userId != null) {
                ids.add(userId);
            }
            return DataScope.ofDepartment(departmentId, ids);
        }
        return DataScope.ofUsers(userId == null ? Set.of() : Set.of(userId));
    }

}
