package com.teampulse.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门（树形，parent_id 自关联）。
 *
 * <p>企业里「公司 → 部门 → 小组」通常 2~3 层，这里不做层级深度限制，
 * 由 {@code parent_id} 自关联表达；查询时用 DeptService 的 childrenOf 逐层展开。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("department")
public class Department extends BaseEntity {

    @TableId(value = "department_id", type = IdType.AUTO)
    private Long departmentId;

    /** 上级部门，NULL 表示顶级 */
    private Long parentId;

    private String deptName;

    private String deptCode;

    /** 部门负责人 userId */
    private Long leaderId;

    private Integer sortNo;
}
