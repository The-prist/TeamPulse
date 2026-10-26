package com.teampulse.module.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.team.entity.Team;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 团队 Mapper。
 *
 * <p>常规增删改查全走 MyBatis-Plus；这里只保留两处手写 SQL，都是「唯一名占用检查」
 * 的场景——因为 MyBatis-Plus 的 {@code exists()} 会自动加 {@code deleted = 0}，
 * 而数据库上的 {@code uk_team_name} 是覆盖所有行的，两者口径不一致就必须显式查。
 */
@Mapper
public interface TeamMapper extends BaseMapper<Team> {

    /**
     * 统计同名团队数量，<b>包含逻辑删除行</b>。
     *
     * <p>为什么不直接用 {@code exists(lambdaQuery())}：逻辑删除后 {@code deleted = 1} 的行
     * 仍然占着 {@code uk_team_name}，如果用带 {@code deleted = 0} 的检查，「删除后用同名重建」
     * 会通过前置校验却死在 insert 的唯一键上，最终被全局异常处理器翻成 500。
     * 显式忽略 deleted，才能把这种冲突提前翻译成 409。
     */
    @Select("SELECT COUNT(*) FROM `team` WHERE team_name = #{teamName}")
    long countByNameIncludingDeleted(@Param("teamName") String teamName);

    /** 改名时的占用检查：排除自己，同样要把逻辑删除行算进去 */
    @Select("SELECT COUNT(*) FROM `team` WHERE team_name = #{teamName} AND team_id <> #{teamId}")
    long countByNameExcludingSelf(@Param("teamName") String teamName, @Param("teamId") Long teamId);
}
