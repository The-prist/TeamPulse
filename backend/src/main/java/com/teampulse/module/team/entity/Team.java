package com.teampulse.module.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 团队实体（team）。
 *
 * <p>团队本身只保存「归属 + 唯一名称」这类弱信息，成员关系放在 {@link TeamMember} 里——
 * 这样一个用户能同时属于多个团队，且团队内角色与系统角色互不干扰。
 *
 * <p>{@code team_name} 在 DDL 上有唯一索引，团队名是展示与搜索的主键，不允许重名。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team")
public class Team extends BaseEntity {

    @TableId(value = "team_id", type = IdType.AUTO)
    private Long teamId;

    private String teamName;

    private String description;

    /** 创建人，删除团队时唯一的「非管理员」放行依据 */
    private Long creatorId;
}
