package com.teampulse.module.team.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队成员关系（team_member）。
 *
 * <p>这里刻意<b>不</b>继承 {@code BaseEntity}：DDL 中该表没有 {@code deleted} 列，
 * 一旦继承，MyBatis-Plus 会给每条语句自动追加 {@code deleted = 0}，
 * 直接报 Unknown column 'deleted' 而全线崩掉。
 *
 * <p>没有逻辑删除又决定了「移除成员」只能物理删除：唯一键
 * {@code uk_team_user (team_id, user_id)} 不区分状态，留下任何残行都会让该用户
 * 永远无法重新加入同一个团队。
 */
@Data
@TableName("team_member")
public class TeamMember {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long teamId;

    private Long userId;

    /** 团队内角色：leader 负责人 / member 普通成员 */
    private String teamRole;

    /** 成员状态：active 正常 / inactive 停用（停用后不计入成员数、不参与鉴权） */
    private String status;

    /** 加入时间，成员列表按它排序，便于看出谁是后进的人 */
    private LocalDateTime joinTime;

    /** 审计字段：本表无逻辑删除，所以只保留时间戳，由 AuditMetaObjectHandler 自动填充 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
