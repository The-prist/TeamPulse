package com.teampulse.module.kanban.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 看板卡片展示对象：卡片自身字段 + 关联日程与承接人的展示字段。
 *
 * <p>字段名与 {@code KanbanItemMapper.selectCardsWithSchedule} 的 SQL 别名严格一一对应
 * （MyBatis 用列别名直接映射属性，别名写错只会在运行期变成 null，编译期发现不了）。
 * 一次联表把标题/状态/优先级/截止时间/承接人姓名都带出来，避免前端为每张卡再发一次请求。
 */
@Data
public class KanbanCardVO {

    private Long itemId;
    private Long kanbanId;
    private Long scheduleId;
    private Long columnId;
    private Long assigneeId;
    private LocalDateTime assignTime;
    private Integer sortOrder;

    /** 以下来自 LEFT JOIN schedule：日程被删或数据异常时为 null，前端按空卡片兜底 */
    private String scheduleTitle;
    /** todo / doing / done / canceled */
    private String scheduleStatus;
    /** low / medium / high */
    private String schedulePriority;
    private LocalDateTime scheduleEndTime;

    /** 来自 LEFT JOIN user，未承接时为 null */
    private String assigneeName;
}
