package com.teampulse.module.team.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teampulse.common.api.PageResult;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.team.dto.TeamDtos;
import com.teampulse.module.team.entity.Team;
import com.teampulse.module.team.entity.TeamMember;
import com.teampulse.module.team.mapper.TeamMapper;
import com.teampulse.module.team.mapper.TeamMemberMapper;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import com.teampulse.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 团队领域服务。
 *
 * <p>权限模型只有两档（系统 {@code admin} 视为跨团队运维，始终放行）：
 * <ul>
 *   <li>{@code leader}：改团队资料、增删成员、调整成员角色</li>
 *   <li>{@code member}：只读</li>
 * </ul>
 * 「删除团队」比「改资料」更敏感，额外收紧为「创建人或 admin」，防止负责人轮流把别人的团队删掉。
 *
 * <p>成员数、负责人姓名这类派生字段统一由 {@link #toVOs} 补齐：一次 {@code team_id IN (...)}
 * 取回整页团队的成员关系，再一次性批量取用户，全程不出现「每个团队查一次」的 N+1。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private static final String ROLE_LEADER = "leader";
    private static final String ROLE_MEMBER = "member";
    private static final String STATUS_ACTIVE = "active";

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;

    // ==================================================================
    // 一、查询
    // ==================================================================

    /** 管理端分页：团队名模糊搜索，成员数与负责人由 toVOs 统一补齐 */
    public PageResult<TeamDtos.TeamVO> page(String keyword, long page, long size) {
        var wrapper = Wrappers.<Team>lambdaQuery().orderByDesc(Team::getTeamId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Team::getTeamName, keyword.trim());
        }
        IPage<Team> result = teamMapper.selectPage(new Page<>(page, size), wrapper);
        List<TeamDtos.TeamVO> rows = toVOs(result.getRecords(), null);
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 我参与的团队（team_member 中 status = active，最近加入的排在前面） */
    public List<TeamDtos.TeamVO> listByUser(Long userId) {
        List<Long> teamIds = teamMemberMapper.selectList(Wrappers.<TeamMember>lambdaQuery()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, STATUS_ACTIVE)
                        .orderByDesc(TeamMember::getJoinTime))
                .stream()
                .map(TeamMember::getTeamId)
                .toList();
        if (teamIds.isEmpty()) {
            // 没有团队时直接返回，省掉一次注定为空的 selectBatchIds
            return List.of();
        }
        // selectBatchIds 不保证顺序且会自动带上 deleted = 0，因此按 teamIds 的顺序重组：
        // 既保留了「最近加入优先」，也顺带过滤掉已逻辑删除的团队（map 里取不到即为 null）
        Map<Long, Team> teamMap = teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(Team::getTeamId, Function.identity()));
        List<Team> teams = teamIds.stream()
                .map(teamMap::get)
                .filter(Objects::nonNull)
                .toList();
        return toVOs(teams, userId);
    }

    /** 团队详情，附带「我的角色」以便前端决定是否展示管理按钮 */
    public TeamDtos.TeamVO detail(Long teamId) {
        Team team = getById(teamId);
        return toVOs(List.of(team), SecurityUtils.currentUserId()).get(0);
    }

    /** 成员列表：负责人置顶，其余按加入时间（SQL 已排序，这里只做一次稳定分组） */
    public List<TeamDtos.MemberVO> members(Long teamId) {
        getById(teamId);
        List<TeamMember> members = teamMemberMapper.selectList(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getStatus, STATUS_ACTIVE)
                .orderByAsc(TeamMember::getJoinTime));
        if (members.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = members.stream().map(TeamMember::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        List<TeamDtos.MemberVO> rows = new ArrayList<>(members.size());
        for (TeamMember member : members) {
            User user = userMap.get(member.getUserId());
            TeamDtos.MemberVO vo = new TeamDtos.MemberVO();
            vo.setUserId(member.getUserId());
            vo.setUsername(user != null ? user.getUsername() : null);
            vo.setNickname(user != null ? user.getNickname() : null);
            vo.setEmployeeNo(user != null ? user.getEmployeeNo() : null);
            vo.setTeamRole(member.getTeamRole());
            vo.setStatus(member.getStatus());
            vo.setJoinTime(member.getJoinTime());
            rows.add(vo);
        }
        // 负责人置顶：成员管理页的第一屏就应该能看到「谁能改配置」
        rows.sort(Comparator.comparingInt((TeamDtos.MemberVO vo) ->
                ROLE_LEADER.equals(vo.getTeamRole()) ? 0 : 1));
        return rows;
    }

    /** 某人在某团队的角色，用于接口层的「我能不能管理」判断；不是成员返回 null */
    public String memberRole(Long teamId, Long userId) {
        TeamMember member = teamMemberMapper.selectOne(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        return member == null ? null : member.getTeamRole();
    }

    // ==================================================================
    // 二、写操作
    // ==================================================================

    /**
     * 创建团队。
     *
     * <p>两步必须在同一个事务里：团队插入成功后立刻把创建人写成 leader，
     * 否则一旦第二步失败就会留下一个「没有任何人能管理」的孤儿团队。
     */
    @Transactional
    public Team create(TeamDtos.CreateRequest req, Long creatorId) {
        String teamName = req.getTeamName().trim();
        if (teamMapper.countByNameIncludingDeleted(teamName) > 0) {
            throw BizException.of(ErrorCode.CONFLICT, "团队名称已存在: " + teamName);
        }

        Team team = new Team();
        team.setTeamName(teamName);
        team.setDescription(req.getDescription());
        team.setCreatorId(creatorId);
        teamMapper.insert(team);

        TeamMember leader = new TeamMember();
        leader.setTeamId(team.getTeamId());
        leader.setUserId(creatorId);
        leader.setTeamRole(ROLE_LEADER);
        leader.setStatus(STATUS_ACTIVE);
        // 显式写 joinTime：该列虽有默认值，但成员列表按它排序，显式赋值可避免依赖数据库时钟
        leader.setJoinTime(LocalDateTime.now());
        teamMemberMapper.insert(leader);

        log.info("创建团队成功: teamId={}, teamName={}, creatorId={}",
                team.getTeamId(), teamName, creatorId);
        return team;
    }

    /** 更新团队资料：仅 leader 或 admin */
    @Transactional
    public void update(Long teamId, TeamDtos.UpdateRequest req) {
        Team team = getById(teamId);
        requireLeaderOrAdmin(teamId);

        if (StringUtils.hasText(req.getTeamName())) {
            String teamName = req.getTeamName().trim();
            if (!teamName.equals(team.getTeamName())
                    && teamMapper.countByNameExcludingSelf(teamName, teamId) > 0) {
                throw BizException.of(ErrorCode.CONFLICT, "团队名称已被占用: " + teamName);
            }
            team.setTeamName(teamName);
        }
        if (req.getDescription() != null) {
            // 描述用 != null 判断：允许传空串把描述清空，hasText 会把它当「不修改」而忽略
            team.setDescription(req.getDescription());
        }
        teamMapper.updateById(team);
    }

    /**
     * 删除团队：仅创建人或 admin。
     *
     * <p>团队走逻辑删除，但 team_member 没有 deleted 列、无法跟随逻辑删除；
     * 不清理就会留下指向已删除团队的成员行——成员数、角色判定、重复加入检查
     * 都还会被这些孤儿行命中，因此这里显式物理清理。
     */
    @Transactional
    public void delete(Long teamId) {
        Team team = getById(teamId);
        Long operatorId = SecurityUtils.currentUserId();
        if (!SecurityUtils.isAdmin() && !operatorId.equals(team.getCreatorId())) {
            throw BizException.of(ErrorCode.FORBIDDEN, "只有团队创建人或管理员可以删除团队");
        }
        teamMemberMapper.delete(Wrappers.<TeamMember>lambdaQuery().eq(TeamMember::getTeamId, teamId));
        teamMapper.deleteById(teamId);
        log.info("删除团队: teamId={}, operatorId={}", teamId, operatorId);
    }

    /** 添加成员：团队存在、用户存在、未重复加入，操作者须为 leader/admin */
    @Transactional
    public void addMember(Long teamId, Long operatorId, TeamDtos.MemberAddRequest req) {
        getById(teamId);
        requireLeaderOrAdmin(teamId, operatorId);
        // 请求体与路径必须指向同一个团队，防止前端把 A 团队的成员加到 B 团队（越权写入）
        if (req.getTeamId() != null && !teamId.equals(req.getTeamId())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "请求体中的团队ID与路径不一致");
        }
        if (userMapper.selectById(req.getUserId()) == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "用户不存在: " + req.getUserId());
        }
        // 移除成员是物理删除，因此只要存在记录就是真正的重复，不需要再看 status
        boolean joined = teamMemberMapper.exists(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, req.getUserId()));
        if (joined) {
            throw BizException.of(ErrorCode.CONFLICT, "该用户已是团队成员");
        }

        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(req.getUserId());
        member.setTeamRole(StringUtils.hasText(req.getTeamRole()) ? req.getTeamRole() : ROLE_MEMBER);
        member.setStatus(STATUS_ACTIVE);
        member.setJoinTime(LocalDateTime.now());
        teamMemberMapper.insert(member);
        log.info("添加团队成员: teamId={}, userId={}, role={}, operatorId={}",
                teamId, req.getUserId(), member.getTeamRole(), operatorId);
    }

    /** 调整成员角色；把最后一个负责人降级会让团队失去管理入口，因此直接拒绝 */
    @Transactional
    public void updateMemberRole(Long teamId, Long operatorId, Long userId,
                                 TeamDtos.MemberRoleUpdateRequest req) {
        getById(teamId);
        requireLeaderOrAdmin(teamId, operatorId);
        TeamMember member = findMember(teamId, userId);
        String targetRole = req.getTeamRole();
        String previousRole = member.getTeamRole();
        if (targetRole.equals(previousRole)) {
            // 幂等：重复提交同一个角色不报错，也不产生无意义的 UPDATE
            return;
        }
        if (ROLE_LEADER.equals(previousRole)) {
            ensureAnotherLeaderExists(teamId, "团队至少需要保留一名负责人，请先指定其他负责人再降级");
        }
        member.setTeamRole(targetRole);
        teamMemberMapper.updateById(member);
        log.info("调整团队成员角色: teamId={}, userId={}, role={} -> {}, operatorId={}",
                teamId, userId, previousRole, targetRole, operatorId);
    }

    /** 移除成员：物理删除（表无 deleted 列），同样禁止把最后一个负责人移出去 */
    @Transactional
    public void removeMember(Long teamId, Long operatorId, Long userId) {
        getById(teamId);
        requireLeaderOrAdmin(teamId, operatorId);
        TeamMember member = findMember(teamId, userId);
        if (ROLE_LEADER.equals(member.getTeamRole())) {
            ensureAnotherLeaderExists(teamId, "团队至少需要保留一名负责人，请先转移负责人角色再移出");
        }
        teamMemberMapper.deleteById(member.getId());
        log.info("移除团队成员: teamId={}, userId={}, operatorId={}", teamId, userId, operatorId);
    }

    // ==================================================================
    // 三、鉴权与内部工具
    // ==================================================================

    /** 当前登录人是否为该团队的 leader 或系统管理员，否则 403 */
    public void requireLeaderOrAdmin(Long teamId) {
        requireLeaderOrAdmin(teamId, SecurityUtils.currentUserId());
    }

    /** 按显式传入的操作者鉴权：addMember 等接口的操作者是方法参数，便于在日志里追溯 */
    private void requireLeaderOrAdmin(Long teamId, Long operatorId) {
        if (SecurityUtils.isAdmin()) {
            // 管理员是跨团队运维角色，不要求自己也在团队里
            return;
        }
        if (!ROLE_LEADER.equals(memberRole(teamId, operatorId))) {
            throw BizException.of(ErrorCode.FORBIDDEN, "只有团队负责人或管理员可以执行该操作");
        }
    }

    /** 团队存在性校验，顺带把实体返回给调用方复用 */
    public Team getById(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "团队不存在: " + teamId);
        }
        return team;
    }

    private TeamMember findMember(Long teamId, Long userId) {
        TeamMember member = teamMemberMapper.selectOne(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "该用户不在此团队中: " + userId);
        }
        return member;
    }

    /** 团队至少要有一名 leader，用计数判断而不是「查另一个 leader 是否存在」，语义更直白 */
    private void ensureAnotherLeaderExists(Long teamId, String message) {
        Long leaderCount = teamMemberMapper.selectCount(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getTeamRole, ROLE_LEADER)
                .eq(TeamMember::getStatus, STATUS_ACTIVE));
        if (leaderCount == null || leaderCount <= 1) {
            throw BizException.of(ErrorCode.BAD_REQUEST, message);
        }
    }

    /**
     * 实体 → VO，并补齐成员数 / 负责人 / 我的角色。
     *
     * <p>成员关系只查一次（{@code team_id IN (...)}），成员数、负责人、"我的角色"全部在这份
     * 结果上内存聚合；用户姓名再批量查一次。一页多少条团队都只多 2 条 SQL，不会随行数增长。
     *
     * @param viewerId 需要填充 myRole 的视角用户，传 null 表示管理端列表视角
     */
    private List<TeamDtos.TeamVO> toVOs(List<Team> teams, Long viewerId) {
        if (teams == null || teams.isEmpty()) {
            return List.of();
        }
        List<Long> teamIds = teams.stream().map(Team::getTeamId).toList();
        List<TeamMember> members = teamMemberMapper.selectList(Wrappers.<TeamMember>lambdaQuery()
                .in(TeamMember::getTeamId, teamIds)
                .eq(TeamMember::getStatus, STATUS_ACTIVE));

        Map<Long, Long> memberCountMap = members.stream()
                .collect(Collectors.groupingBy(TeamMember::getTeamId, Collectors.counting()));
        // 同一团队出现多个 leader 属于脏数据，取第一个即可，不要让整个接口报错
        Map<Long, Long> leaderIdMap = members.stream()
                .filter(m -> ROLE_LEADER.equals(m.getTeamRole()))
                .collect(Collectors.toMap(TeamMember::getTeamId, TeamMember::getUserId, (a, b) -> a));
        Map<Long, String> myRoleMap = viewerId == null
                ? Map.<Long, String>of()
                : members.stream()
                .filter(m -> viewerId.equals(m.getUserId()))
                .collect(Collectors.toMap(TeamMember::getTeamId, TeamMember::getTeamRole, (a, b) -> a));

        Set<Long> leaderIds = new HashSet<>(leaderIdMap.values());
        Map<Long, User> userMap = leaderIds.isEmpty()
                ? Map.<Long, User>of()
                : userMapper.selectBatchIds(leaderIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        List<TeamDtos.TeamVO> rows = new ArrayList<>(teams.size());
        for (Team team : teams) {
            TeamDtos.TeamVO vo = new TeamDtos.TeamVO();
            vo.setTeamId(team.getTeamId());
            vo.setTeamName(team.getTeamName());
            vo.setDescription(team.getDescription());
            vo.setCreatorId(team.getCreatorId());
            vo.setCreatedAt(team.getCreatedAt());
            vo.setMemberCount(memberCountMap.getOrDefault(team.getTeamId(), 0L));
            vo.setMyRole(myRoleMap.get(team.getTeamId()));
            Long leaderId = leaderIdMap.get(team.getTeamId());
            if (leaderId != null) {
                User leader = userMap.get(leaderId);
                // 用户被删除（逻辑删除）时 selectBatchIds 查不到，兜底成「用户{id}」而不是空字符串
                vo.setLeaderName(leader != null ? displayName(leader) : "用户" + leaderId);
            }
            rows.add(vo);
        }
        return rows;
    }

    private static String displayName(User user) {
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
