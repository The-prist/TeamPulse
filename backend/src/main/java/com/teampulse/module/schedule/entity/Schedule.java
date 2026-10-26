package com.teampulse.module.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 日程实体（schedule）。
 *
 * <p>日程是整个协作域的中心：工作日志可以关联它，看板卡片是它在看板上的投影
 * （{@code kanban_item.schedule_id}），因此 {@code status} 是唯一状态源——
 * 看板拖拽改的是这张表的 status，而不是卡片自己的字段。
 *
 * <p>{@code teamId} 为 null 表示个人日程，非 null 表示团队日程；
 * DDL 上没有外键，关联完整性由服务层校验。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule")
public class Schedule extends BaseEntity {

    @TableId(value = "schedule_id", type = IdType.AUTO)
    private Long scheduleId;

    private String title;

    private String content;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String location;

    /** 状态：todo 待办 / doing 进行中 / done 已完成 / canceled 已取消 */
    private String status;

    /** 优先级：low / medium / high */
    private String priority;

    /** 创建人，个人日程的唯一起草者，也是修改/删除的鉴权依据 */
    private Long creatorId;

    /** 所属团队，null 表示个人日程 */
    private Long teamId;

    /** 提醒时间，可为空；留给后续提醒推送使用 */
    private LocalDateTime remindTime;
}
