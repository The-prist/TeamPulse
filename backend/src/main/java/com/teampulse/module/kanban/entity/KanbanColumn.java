package com.teampulse.module.kanban.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 看板列。
 *
 * <p>不继承 {@code BaseEntity}：表里只有 created_at / updated_at，没有 deleted。
 * 若强行继承，MyBatis-Plus 会把 {@code deleted} 拼进每条 SQL，运行期直接
 * 「Unknown column 'deleted'」——这也是本实体手写两个时间字段的原因。
 *
 * <p>{@code columnName} 不只是展示文案：卡片挪列后要用它反推日程状态，
 * 因此默认列名固定为「待办 / 进行中 / 已完成」（见 KanbanService 的列名判定规则）。
 */
@Data
@TableName("kanban_column")
public class KanbanColumn {

    @TableId(value = "column_id", type = IdType.AUTO)
    private Long columnId;

    private Long kanbanId;

    /** 列名，同时是卡片挪列 → 日程状态的判定依据 */
    private String columnName;

    /** 列排序，从 1 开始连续（删列后由 Service 重排，保证前端拖拽落位稳定） */
    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
