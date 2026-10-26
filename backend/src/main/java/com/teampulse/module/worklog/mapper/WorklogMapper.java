package com.teampulse.module.worklog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.worklog.entity.Worklog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 工作日志 Mapper。
 *
 * <p>日志主体的查询、分页、统计全部走 MyBatis-Plus 包装器，这里只保留两个批量投影查询，
 * 用于补齐列表/详情里的团队名与日程标题。
 *
 * <p>为什么返回 {@code Map} 而不新建实体：{@code team} 与 {@code schedule} 属于团队/日程模块，
 * 那些模块的实体和 Mapper 由并行任务维护，为了一张表的两列在这里重新定义实体会造成重复建模，
 * 直接引用别人的实体会在编译期强耦合。返回 Map 只依赖 SQL 别名，模块之间保持最小耦合。
 * 两个方法都按 ID 集合一次查完，禁止在循环里调用。
 */
@Mapper
public interface WorklogMapper extends BaseMapper<Worklog> {

    /** 批量取团队名；逻辑删除的团队不再返回，日志里该字段展示为空 */
    @Select("<script>"
            + "SELECT team_id AS teamId, team_name AS teamName FROM `team` "
            + "WHERE deleted = 0 AND team_id IN "
            + "<foreach collection='teamIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectTeamNames(@Param("teamIds") Collection<Long> teamIds);

    /** 批量取日程标题；同时可作为「关联日程是否存在」的校验依据 */
    @Select("<script>"
            + "SELECT schedule_id AS scheduleId, title AS scheduleTitle FROM schedule "
            + "WHERE deleted = 0 AND schedule_id IN "
            + "<foreach collection='scheduleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectScheduleTitles(@Param("scheduleIds") Collection<Long> scheduleIds);
}
