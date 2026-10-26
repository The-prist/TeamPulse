package com.teampulse.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色。
 *
 * <p>关键设计：**数据范围（dataScope）挂在角色上**，而不是散落在各接口的 if-else 里。
 * 新增一个角色只需要插一行数据 + 配权限点，不用改代码。
 *
 * <p>dataScope 取值：
 * <ul>
 *   <li>{@code ALL}：全公司（系统管理员 / 人事 / 审计）</li>
 *   <li>{@code DEPT}：本部门（部门经理 / 团队负责人）</li>
 *   <li>{@code SELF}：仅本人（普通员工）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("role")
public class Role extends BaseEntity {

    @TableId(value = "role_id", type = IdType.AUTO)
    private Long roleId;

    /** 角色标识，与 user.role 对应 */
    private String roleKey;

    private String roleName;

    private String description;

    /** ALL / DEPT / SELF */
    private String dataScope;

    /** 内置角色不允许删除 */
    private Integer builtIn;
}
