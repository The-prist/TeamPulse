package com.teampulse.module.kanban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 看板卡片：日程在看板上的投影。
 *
 * <p>核心设计取舍：卡片自己不保存「任务标题/状态」，只保存 {@code scheduleId}。
 * 任务内容的唯一事实来源是 {@code schedule} 表，卡片只负责「放在哪一列、排在什么位置、谁承接」。
 * 因此挪列时要把列名反推成日程状态写回 schedule，日程状态变化时也要把卡片挪到对应列
 * （两个方向见 KanbanService.moveItem / syncItemColumnByScheduleStatus）。
 *
 * <p>不继承 {@code BaseEntity}：DDL 中没有 deleted 列，删除即物理删除。
 */
@Data
@TableName("kanban_item")
public class KanbanItem {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    private Long kanbanId;

    /** 关联日程，NOT NULL；联表查询时用它取标题/状态/优先级/截止时间 */
    private Long scheduleId;

    /** 所在列，NOT NULL */
    private Long columnId;

    /** 承接人，可为空（没人认领） */
    private Long assigneeId;

    /** 承接时间，与 assigneeId 同时写入/清空，避免出现「有承接人但没有承接时间」的脏数据 */
    private LocalDateTime assignTime;

    /** 列内排序，从 1 开始；移动时先给目标位次腾位再落位 */
    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
