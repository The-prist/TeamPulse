package com.teampulse.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公共审计字段基类。
 *
 * <p>{@code createdAt/updatedAt} 由 {@code AuditMetaObjectHandler} 自动填充，
 * 业务代码不需要手工 set；{@code deleted} 为 MyBatis-Plus 逻辑删除标记。
 *
 * <p>注意：逻辑删除会让 {@code selectById} 自动追加 {@code deleted = 0}，
 * 因此历史脏数据必须保证 deleted 列为 0 而非 NULL。
 */
@Data
public abstract class BaseEntity implements Serializable {

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(value = "deleted", select = false)
    private Integer deleted;
}
