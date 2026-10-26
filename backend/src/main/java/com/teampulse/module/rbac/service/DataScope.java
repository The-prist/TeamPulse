package com.teampulse.module.rbac.service;

import java.util.Set;

/**
 * 数据范围：把「这个用户能看到哪些数据」变成一个显式对象，而不是散在各处的 if。
 *
 * <p>三种状态：
 * <ul>
 *   <li>{@code unrestricted=true}：不限制（ALL 范围角色）</li>
 *   <li>{@code userIds} 非空：只能看这些人的数据（DEPT 范围 = 本部门成员；SELF 范围 = 自己）</li>
 *   <li>{@code userIds} 为空且不受限：什么都看不到（例如未登录）</li>
 * </ul>
 */
public final class DataScope {

    private final boolean unrestricted;
    private final Set<Long> userIds;
    private final Long departmentId;

    private DataScope(boolean unrestricted, Set<Long> userIds, Long departmentId) {
        this.unrestricted = unrestricted;
        this.userIds = userIds;
        this.departmentId = departmentId;
    }

    public static DataScope all() {
        return new DataScope(true, Set.of(), null);
    }

    public static DataScope ofUsers(Set<Long> userIds) {
        return new DataScope(false, userIds == null ? Set.of() : Set.copyOf(userIds), null);
    }

    public static DataScope ofDepartment(Long departmentId, Set<Long> userIds) {
        return new DataScope(false, userIds == null ? Set.of() : Set.copyOf(userIds), departmentId);
    }

    public boolean isUnrestricted() {
        return unrestricted;
    }

    public Set<Long> userIds() {
        return userIds;
    }

    public Long departmentId() {
        return departmentId;
    }

    /** 受限范围内是否包含某人 */
    public boolean includes(Long userId) {
        return unrestricted || (userId != null && userIds.contains(userId));
    }
}
