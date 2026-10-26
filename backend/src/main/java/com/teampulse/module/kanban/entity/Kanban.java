package com.teampulse.module.kanban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 看板实体：一个团队内看板任务的容器。
 *
 * <p>为什么要逻辑删除：列（{@code kanban_column}）与卡片（{@code kanban_item}）在 DDL 中
 * 没有 deleted 列，它们完全依附于看板存在；看板本身保留删除痕迹（可追溯「这个看板曾经存在」），
 * 列与卡片则在删除看板时物理清理，避免长期堆积指向已删看板的孤儿行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kanban")
public class Kanban extends BaseEntity {

    @TableId(value = "kanban_id", type = IdType.AUTO)
    private Long kanbanId;

    private String kanbanName;

    private String description;

    /** 所属团队，NOT NULL；团队维度的可见性判定（listByUser）就靠这一列 */
    private Long teamId;

    /** 创建人；仅创建者与 admin 能改名/换团队/删除看板 */
    private Long creatorId;
}
