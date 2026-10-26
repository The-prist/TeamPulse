package com.teampulse.module.kanban.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.kanban.entity.Kanban;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 看板 Mapper。
 *
 * <p>看板自身的查询全部走 MyBatis-Plus 包装器（含 listByUser 的 inSql 子查询），
 * 这里只保留一处手写 SQL：创建看板前确认团队真实存在。
 * 团队模块的实体与 Mapper 由并行任务维护，直接引用会在编译期强耦合，
 * 用一条最小 COUNT 就能完成存在性校验。
 */
@Mapper
public interface KanbanMapper extends BaseMapper<Kanban> {

    /** 团队是否存在（逻辑删除的团队视为不存在，避免看板挂到已解散团队上） */
    @Select("SELECT COUNT(1) FROM `team` WHERE team_id = #{teamId} AND deleted = 0")
    int countTeam(@Param("teamId") Long teamId);
}
