package com.teampulse.module.kanban.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.kanban.entity.KanbanItem;
import com.teampulse.module.kanban.vo.KanbanCardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 看板卡片 Mapper。
 *
 * <p>手写 SQL 都放在这里，原因是卡片「不持有任务内容」：
 * 展示要联 schedule / user，挪列要按 column_id 批量错位，反推的状态要写回 schedule，
 * 这些都无法用单表 Lambda 包装器表达。
 */
@Mapper
public interface KanbanItemMapper extends BaseMapper<KanbanItem> {

    /**
     * 取看板内全部卡片（含关联日程与承接人）。
     *
     * <p>排序用 column_id, sort_order：列内顺序由 sort_order 决定，
     * 列与列之间由前端按 columns 的 sortOrder 归位，SQL 只需保证同列内有序。
     * 两个 LEFT JOIN 都带 deleted = 0，日程被逻辑删除时卡片仍可见但标题为 null（不丢卡）。
     */
    @Select("SELECT ki.item_id AS itemId, ki.kanban_id AS kanbanId, ki.schedule_id AS scheduleId, "
            + "ki.column_id AS columnId, ki.assignee_id AS assigneeId, ki.assign_time AS assignTime, ki.sort_order AS sortOrder, "
            + "s.title AS scheduleTitle, s.status AS scheduleStatus, s.priority AS schedulePriority, s.end_time AS scheduleEndTime, "
            + "u.nickname AS assigneeName "
            + "FROM kanban_item ki LEFT JOIN schedule s ON ki.schedule_id = s.schedule_id AND s.deleted = 0 "
            + "LEFT JOIN `user` u ON ki.assignee_id = u.user_id "
            + "WHERE ki.kanban_id = #{kanbanId} ORDER BY ki.column_id, ki.sort_order")
    List<KanbanCardVO> selectCardsWithSchedule(@Param("kanbanId") Long kanbanId);

    /**
     * 挪卡片前给目标列腾位：位次 >= 目标位次的卡片整体后移一格。
     *
     * <p>放在一条 UPDATE 里完成，而不是先查出来再逐条改：并发拖拽时丢更新的窗口更小。
     * 同列内前移也走这条 SQL —— 本行会被 +1，随后被自己的新位次覆盖，不会产生重复位次。
     */
    @Update("UPDATE kanban_item SET sort_order = sort_order + 1 WHERE column_id = #{columnId} AND sort_order >= #{sortOrder}")
    int shiftSortOrder(@Param("columnId") Long columnId, @Param("sortOrder") Integer sortOrder);

    /**
     * 把列名反推出的状态写回日程：卡片只是日程的投影，状态唯一事实来源仍是 schedule。
     *
     * <p>手写 SQL 绕过了 MyBatis-Plus 的审计字段填充，因此显式写 updated_at；
     * 带 deleted = 0 条件，避免把状态写进已删除的日程。
     */
    @Update("UPDATE schedule SET status = #{status}, updated_at = NOW() WHERE schedule_id = #{scheduleId} AND deleted = 0")
    int updateScheduleStatus(@Param("scheduleId") Long scheduleId, @Param("status") String status);

    /** 卡片必须指向真实日程，否则联表出来是一张没有标题、点不开的空白卡 */
    @Select("SELECT COUNT(1) FROM schedule WHERE schedule_id = #{scheduleId} AND deleted = 0")
    int countSchedule(@Param("scheduleId") Long scheduleId);
}
