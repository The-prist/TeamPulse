package com.teampulse.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 权限点。命名约定 {@code module:action}，例如 {@code attendance:rule}。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("permission")
public class Permission extends BaseEntity {

    @TableId(value = "permission_id", type = IdType.AUTO)
    private Long permissionId;

    private String permKey;

    private String permName;

    private String module;
}
