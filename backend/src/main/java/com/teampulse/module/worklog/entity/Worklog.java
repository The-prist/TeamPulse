package com.teampulse.module.worklog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 工作日志实体。
 *
 * <p>继承 {@code BaseEntity}：表里带 created_at / updated_at / deleted，
 * 日志是「写下来就不该消失」的记录，误删要能恢复，所以用逻辑删除。
 *
 * <p>关联字段都可空：日志可以只是随手记（不挂日程、不挂团队），
 * 也可以同时挂一条日程和一个团队；团队名/日程标题不落库，展示时批量回查，
 * 避免日程改名后日志里还留着旧标题。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("worklog")
public class Worklog extends BaseEntity {

    @TableId(value = "worklog_id", type = IdType.AUTO)
    private Long worklogId;

    private String title;

    private String content;

    /** 所属日期（DATE，不含时间）：统计口径按天，用时间戳反而要对每行做大范围格式转换 */
    private LocalDate logDate;

    /** 记录人；仅本人或 admin 能改/删 */
    private Long creatorId;

    private Long relatedScheduleId;

    private Long relatedTeamId;
}
