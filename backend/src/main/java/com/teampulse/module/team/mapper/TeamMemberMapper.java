package com.teampulse.module.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.team.entity.TeamMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 团队成员 Mapper。
 *
 * <p>成员数统计、负责人定位都靠「按 teamId 批量 selectList 后在 Java 里聚合」完成
 * （见 {@code TeamService}），因此这里不需要任何自定义 SQL：
 * 一条 {@code team_id IN (...)} 查询就能覆盖整页团队，避免 N+1。
 */
@Mapper
public interface TeamMemberMapper extends BaseMapper<TeamMember> {
}
