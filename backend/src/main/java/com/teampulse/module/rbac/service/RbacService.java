package com.teampulse.module.rbac.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teampulse.module.rbac.entity.Department;
import com.teampulse.module.rbac.entity.Role;
import com.teampulse.module.rbac.entity.RolePermission;
import com.teampulse.module.rbac.mapper.DepartmentMapper;
import com.teampulse.module.rbac.mapper.RoleMapper;
import com.teampulse.module.rbac.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 角色与权限的读取入口。
 *
 * <p>Roles 与 role_permission 是低频变更的字典数据，但每个请求都要读，
 * 因此放在进程内缓存里；管理员改了角色权限后调用 {@link #evict} 失效即可。
 * 单机部署足够，多实例部署时应换成 Redis 广播失效（已在方法注释里标明）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final DepartmentMapper departmentMapper;

    /** roleKey → 权限点集合 */
    private final Map<String, Set<String>> permCache = new ConcurrentHashMap<>();
    /** roleKey → 角色定义（含 dataScope） */
    private final Map<String, Role> roleCache = new ConcurrentHashMap<>();

    public Role roleOf(String roleKey) {
        if (!StringUtils.hasText(roleKey)) {
            return null;
        }
        return roleCache.computeIfAbsent(roleKey, k -> roleMapper.selectOne(
                Wrappers.<Role>lambdaQuery().eq(Role::getRoleKey, k)));
    }

    /** 数据范围：ALL / DEPT / SELF，取不到角色时按最小权限 SELF 处理 */
    public String dataScopeOf(String roleKey) {
        Role role = roleOf(roleKey);
        if (role == null || !StringUtils.hasText(role.getDataScope())) {
            return "SELF";
        }
        return role.getDataScope().toUpperCase();
    }

    public Set<String> permissionsOf(String roleKey) {
        if (!StringUtils.hasText(roleKey)) {
            return Set.of();
        }
        return permCache.computeIfAbsent(roleKey, k -> rolePermissionMapper
                .selectList(Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleKey, k))
                .stream()
                .map(RolePermission::getPermKey)
                .collect(Collectors.toUnmodifiableSet()));
    }

    /** 角色权限被修改后调用；不传 roleKey 表示全量失效 */
    public void evict(String roleKey) {
        if (StringUtils.hasText(roleKey)) {
            permCache.remove(roleKey);
            roleCache.remove(roleKey);
        } else {
            permCache.clear();
            roleCache.clear();
        }
        log.info("角色权限缓存已失效: {}", StringUtils.hasText(roleKey) ? roleKey : "ALL");
    }

    public List<Role> listRoles() {
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getRoleId));
    }

    public List<Department> listDepartments() {
        return departmentMapper.selectList(
                Wrappers.<Department>lambdaQuery().orderByAsc(Department::getSortNo).orderByAsc(Department::getDepartmentId));
    }

    /** 部门 id → 名称，供列表展示批量补齐 */
    public Map<Long, String> departmentNameMap() {
        Map<Long, String> map = new HashMap<>();
        for (Department d : listDepartments()) {
            map.put(d.getDepartmentId(), d.getDeptName());
        }
        return map;
    }

    /** 角色展示名：查不到时回落成 roleKey，避免前端显示空白 */
    public String roleNameOf(String roleKey) {
        Role role = roleOf(roleKey);
        return role == null ? roleKey : role.getRoleName();
    }

    public Map<String, String> roleNameMap() {
        Map<String, String> map = new HashMap<>();
        for (Role r : listRoles()) {
            map.put(r.getRoleKey(), r.getRoleName());
        }
        return map;
    }
}
