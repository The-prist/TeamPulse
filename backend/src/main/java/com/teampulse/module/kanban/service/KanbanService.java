package com.teampulse.module.kanban.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teampulse.common.api.PageResult;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.kanban.dto.KanbanDtos;
import com.teampulse.module.kanban.entity.Kanban;
import com.teampulse.module.kanban.entity.KanbanColumn;
import com.teampulse.module.kanban.entity.KanbanItem;
import com.teampulse.module.kanban.mapper.KanbanColumnMapper;
import com.teampulse.module.kanban.mapper.KanbanItemMapper;
import com.teampulse.module.kanban.mapper.KanbanMapper;
import com.teampulse.module.kanban.vo.KanbanCardVO;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 看板领域服务：看板 / 列 / 卡片的增删改查，以及「列 ≡ 日程状态」的双向同步。
 *
 * <p>这个模块的核心难点不是 CRUD，而是两处状态判定的口径，先在这里讲清楚：
 *
 * <ol>
 *   <li><b>挪卡片 → 日程状态（正向）</b>：卡片只是日程的投影，日程才是内容与状态的唯一事实来源。
 *       用户把卡片拖到「已完成」，等价于宣布任务完成，因此必须在同一事务里把 schedule.status 改成 done。
 *       判定依据是**目标列的名字**，而不是像旧版那样在卡片上再存一份状态字段
 *       （两份状态迟早不一致，且用户改列名时语义会漂移）。列名无法识别时宁可不改，也不猜。</li>
 *   <li><b>日程状态 → 卡片列（反向）</b>：日程模块把状态从 todo 改成 doing 后，看板上那张卡
 *       必须自动挪到「进行中」，否则同一个任务在看板里还是「待办」。反向匹配优先用列名，
 *       匹配不到再按索引兜底（第 1 列 / 第 2 列 / 最后一列），保证用户把列名改成「需求池/开发/验收」
 *       这类自由文案时，卡片仍然有落点。</li>
 * </ol>
 *
 * <p>权限口径：看板元信息（改名、换团队、删除）仅创建者或 admin 可操作；
 * 列与卡片的增删、拖拽、指派属于团队协作行为，不额外限制（能看见看板就能协同推进）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KanbanService {

    /** 单页上限，防止前端传 size=999999 打爆内存（与 MyBatis-Plus 分页插件的 maxLimit 一致） */
    private static final long MAX_PAGE_SIZE = 200;

    /**
     * 新建看板默认三列。
     *
     * <p>列名同时是日程状态的判定关键词，改动文案会直接影响「拖卡片改状态」的行为，
     * 因此这里集中定义，不要在别处再写一遍字面量。
     */
    private static final List<String> DEFAULT_COLUMNS = List.of("待办", "进行中", "已完成");

    private static final String STATUS_TODO = "todo";
    private static final String STATUS_DOING = "doing";
    private static final String STATUS_DONE = "done";

    private final KanbanMapper kanbanMapper;
    private final KanbanColumnMapper columnMapper;
    private final KanbanItemMapper itemMapper;
    private final UserMapper userMapper;

    // ==================================================================
    // 一、看板
    // ==================================================================

    /** 分页查询：teamId 与 keyword（名称/描述）都是可选过滤条件 */
    public PageResult<Kanban> page(Long teamId, String keyword, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        var wrapper = Wrappers.<Kanban>lambdaQuery().orderByDesc(Kanban::getKanbanId);
        if (teamId != null) {
            wrapper.eq(Kanban::getTeamId, teamId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Kanban::getKanbanName, kw)
                    .or().like(Kanban::getDescription, kw));
        }
        IPage<Kanban> result = kanbanMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.from(result, k -> k);
    }

    /**
     * 当前用户可见的看板：自己创建的 + 自己所在（status=active）团队名下的。
     *
     * <p>用 inSql 子查询把「团队归属」交给数据库一次性判断，而不是先查出 teamId 列表再回表：
     * 后者会产生两次往返，且团队一多就会拼出上千个 IN 参数。
     * userId 是 Long，拼进子查询没有注入面（CONVENTIONS 允许 inSql 这种整段子查询写法）。
     */
    public List<Kanban> listByUser(Long userId) {
        var wrapper = Wrappers.<Kanban>lambdaQuery()
                .and(w -> w.eq(Kanban::getCreatorId, userId)
                        .or().inSql(Kanban::getTeamId,
                                "SELECT team_id FROM team_member WHERE status = 'active' AND user_id = " + userId))
                .orderByDesc(Kanban::getKanbanId);
        return kanbanMapper.selectList(wrapper);
    }

    /**
     * 看板详情：一次返回看板、列、卡片三段数据。
     *
     * <p>三次查询（看板 / 列 / 卡片联表）而不是「查卡片时逐卡查列」，前端拿到就能直接渲染。
     */
    public Map<String, Object> detail(Long kanbanId) {
        Kanban kanban = getById(kanbanId);
        List<KanbanColumn> columns = columnMapper.selectList(Wrappers.<KanbanColumn>lambdaQuery()
                .eq(KanbanColumn::getKanbanId, kanbanId)
                .orderByAsc(KanbanColumn::getSortOrder));
        List<KanbanCardVO> cards = itemMapper.selectCardsWithSchedule(kanbanId);

        KanbanDtos.KanbanDetailVO view = new KanbanDtos.KanbanDetailVO();
        view.setKanban(kanban);
        view.setColumns(columns);
        view.setCards(cards);
        return view.toMap();
    }

    /** 创建看板，并预置「待办 / 进行中 / 已完成」三列 */
    @Transactional
    public Kanban create(KanbanDtos.CreateRequest req, Long creatorId) {
        if (kanbanMapper.countTeam(req.getTeamId()) == 0) {
            throw BizException.of(ErrorCode.NOT_FOUND, "团队不存在: " + req.getTeamId());
        }

        Kanban kanban = new Kanban();
        kanban.setKanbanName(req.getKanbanName().trim());
        kanban.setDescription(req.getDescription());
        kanban.setTeamId(req.getTeamId());
        kanban.setCreatorId(creatorId);
        kanbanMapper.insert(kanban);

        // 预置三列：新看板立刻可用，前端不必先建列再建卡；
        // 列顺序即「todo / doing / done」的默认落位，保证反向同步的索引兜底有意义。
        List<KanbanColumn> columns = new ArrayList<>(DEFAULT_COLUMNS.size());
        for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
            KanbanColumn column = new KanbanColumn();
            column.setKanbanId(kanban.getKanbanId());
            column.setColumnName(DEFAULT_COLUMNS.get(i));
            column.setSortOrder(i + 1);
            columnMapper.insert(column);
            columns.add(column);
        }
        log.info("创建看板: kanbanId={}, teamId={}, creatorId={}, 默认列数={}",
                kanban.getKanbanId(), kanban.getTeamId(), creatorId, columns.size());
        return kanban;
    }

    /** 更新看板元信息：仅创建者或 admin */
    @Transactional
    public void update(Long kanbanId, KanbanDtos.UpdateRequest req, Long operatorId) {
        Kanban kanban = getById(kanbanId);
        requireOwnerOrAdmin(kanban.getCreatorId(), operatorId);

        if (StringUtils.hasText(req.getKanbanName())) {
            kanban.setKanbanName(req.getKanbanName().trim());
        }
        if (req.getDescription() != null) {
            kanban.setDescription(req.getDescription());
        }
        if (req.getTeamId() != null && !req.getTeamId().equals(kanban.getTeamId())) {
            // 换团队会改变整块看板的可见范围，必须校验新团队真实存在，否则看板会「消失」在无人可见的位置
            if (kanbanMapper.countTeam(req.getTeamId()) == 0) {
                throw BizException.of(ErrorCode.NOT_FOUND, "团队不存在: " + req.getTeamId());
            }
            kanban.setTeamId(req.getTeamId());
        }
        kanbanMapper.updateById(kanban);
    }

    /**
     * 删除看板：级联清理列与卡片。
     *
     * <p>列与卡片在 DDL 中没有 deleted 列（它们是看板的附属结构），所以物理删除；
     * 看板本身走逻辑删除，保留操作痕迹。
     */
    @Transactional
    public void delete(Long kanbanId) {
        Kanban kanban = getById(kanbanId);
        // 签名里没有操作人参数，删除是不可逆操作，权限判定直接取 JWT，
        // 避免任何一个调用方漏传 operatorId 就绕过了校验
        requireOwnerOrAdmin(kanban.getCreatorId(), SecurityUtils.currentUserId());

        // 顺序固定：先卡片后列。反过来先删列会让卡片在事务中间态指向不存在的列
        itemMapper.delete(Wrappers.<KanbanItem>lambdaQuery().eq(KanbanItem::getKanbanId, kanbanId));
        columnMapper.delete(Wrappers.<KanbanColumn>lambdaQuery().eq(KanbanColumn::getKanbanId, kanbanId));
        kanbanMapper.deleteById(kanbanId);
        log.info("删除看板: kanbanId={}", kanbanId);
    }

    // ==================================================================
    // 二、列
    // ==================================================================

    /** 新增列：排位取当前最大值 +1，保证追加在最后 */
    @Transactional
    public KanbanColumn addColumn(Long kanbanId, KanbanDtos.ColumnCreateRequest req) {
        getById(kanbanId);

        KanbanColumn last = columnMapper.selectOne(Wrappers.<KanbanColumn>lambdaQuery()
                .eq(KanbanColumn::getKanbanId, kanbanId)
                .orderByDesc(KanbanColumn::getSortOrder)
                .last("LIMIT 1"));
        int nextSort = last == null || last.getSortOrder() == null ? 1 : last.getSortOrder() + 1;

        KanbanColumn column = new KanbanColumn();
        column.setKanbanId(kanbanId);
        column.setColumnName(req.getColumnName().trim());
        column.setSortOrder(nextSort);
        columnMapper.insert(column);
        log.info("新增看板列: kanbanId={}, columnId={}, sortOrder={}", kanbanId, column.getColumnId(), nextSort);
        return column;
    }

    /**
     * 删除列及其卡片，然后把同看板剩余列压成 1..n。
     *
     * <p>不重排会留下「1,2,4」这种断档：前端拖拽按数组下标算位次，
     * 断档会让第 4 列算出来的落位一直插不进去。
     */
    @Transactional
    public void deleteColumn(Long columnId) {
        KanbanColumn column = getColumn(columnId);
        Long kanbanId = column.getKanbanId();

        // 删列会连带删掉列内全部卡片，破坏性和删看板同级，
        // 因此与 delete(kanbanId) 使用同一套「创建人 / 管理员」判定。
        Kanban kanban = getById(kanbanId);
        requireOwnerOrAdmin(kanban.getCreatorId(), SecurityUtils.currentUserId());

        itemMapper.delete(Wrappers.<KanbanItem>lambdaQuery().eq(KanbanItem::getColumnId, columnId));
        columnMapper.deleteById(columnId);
        renumberColumns(kanbanId);
        log.info("删除看板列: kanbanId={}, columnId={}", kanbanId, columnId);
    }

    /**
     * 把剩余列的 sortOrder 连续化为 1..n。
     *
     * <p>逐条 update 而不是拼一条 CASE WHEN：列的个数只有个位数，可读性优先；
     * 这里虽然循环但只在内存里遍历已查出的列表，没有任何循环内查库。
     */
    private void renumberColumns(Long kanbanId) {
        List<KanbanColumn> columns = columnMapper.selectList(Wrappers.<KanbanColumn>lambdaQuery()
                .eq(KanbanColumn::getKanbanId, kanbanId)
                .orderByAsc(KanbanColumn::getSortOrder));
        int sort = 1;
        for (KanbanColumn column : columns) {
            if (!Objects.equals(column.getSortOrder(), sort)) {
                KanbanColumn update = new KanbanColumn();
                update.setColumnId(column.getColumnId());
                update.setSortOrder(sort);
                columnMapper.updateById(update);
            }
            sort++;
        }
    }

    // ==================================================================
    // 三、卡片
    // ==================================================================

    /** 新增卡片：columnId 为空时落到第一列，排位取该列最大值 +1（追加到列尾） */
    @Transactional
    public KanbanItem addItem(Long kanbanId, KanbanDtos.ItemCreateRequest req) {
        getById(kanbanId);
        if (itemMapper.countSchedule(req.getScheduleId()) == 0) {
            throw BizException.of(ErrorCode.NOT_FOUND, "关联日程不存在: " + req.getScheduleId());
        }
        // 卡片是日程的投影：同一日程在同一看板里只留一张卡，
        // 否则同一任务会出现两张卡各自被拖到不同列，反向同步时会互相打架
        if (itemMapper.exists(Wrappers.<KanbanItem>lambdaQuery()
                .eq(KanbanItem::getKanbanId, kanbanId)
                .eq(KanbanItem::getScheduleId, req.getScheduleId()))) {
            throw BizException.of(ErrorCode.CONFLICT, "该日程已在当前看板中");
        }

        KanbanColumn targetColumn = resolveColumn(kanbanId, req.getColumnId());

        KanbanItem item = new KanbanItem();
        item.setKanbanId(kanbanId);
        item.setScheduleId(req.getScheduleId());
        item.setColumnId(targetColumn.getColumnId());
        item.setSortOrder(nextSortOrder(targetColumn.getColumnId()));
        if (req.getAssigneeId() != null) {
            requireUser(req.getAssigneeId());
            item.setAssigneeId(req.getAssigneeId());
            // 创建即指派也视为一次承接，记录时间便于后续统计「多久被认领」
            item.setAssignTime(LocalDateTime.now());
        }
        itemMapper.insert(item);
        log.info("新增看板卡片: kanbanId={}, itemId={}, scheduleId={}, columnId={}",
                kanbanId, item.getItemId(), item.getScheduleId(), item.getColumnId());
        return item;
    }

    /**
     * 移动卡片——本模块最关键的一条链路：
     * <ol>
     *   <li>校验卡片存在、目标列属于同一看板</li>
     *   <li>给目标列腾位（把位次 >= 目标位次的卡片整体 +1）</li>
     *   <li>更新卡片的 columnId / sortOrder</li>
     *   <li>按目标列名反推日程状态并写回 schedule</li>
     * </ol>
     */
    @Transactional
    public void moveItem(Long itemId, KanbanDtos.MoveRequest req) {
        KanbanItem item = getItem(itemId);
        KanbanColumn target = columnMapper.selectById(req.getColumnId());
        if (target == null || !item.getKanbanId().equals(target.getKanbanId())) {
            // 跨看板挪卡会把两张看板的排序和状态搅乱，直接拒绝
            throw BizException.of(ErrorCode.BAD_REQUEST, "目标列不属于该看板: " + req.getColumnId());
        }

        // 位次从 1 开始，前端拖到列首时可能传 0，这里统一兜到 1
        int sortOrder = req.getSortOrder() == null ? 1 : Math.max(1, req.getSortOrder());

        // 先腾位再落位：同列内前移时本行也会被 +1，随后被自己的新位次覆盖，不会出现重复位次
        itemMapper.shiftSortOrder(target.getColumnId(), sortOrder);

        // 只提交会变的列，避免把整行旧值（含承接时间）重新写回数据库
        KanbanItem update = new KanbanItem();
        update.setItemId(item.getItemId());
        update.setColumnId(target.getColumnId());
        update.setSortOrder(sortOrder);
        itemMapper.updateById(update);

        syncScheduleStatusByColumn(item.getScheduleId(), target.getColumnName());
    }

    /**
     * 指派 / 取消承接。
     *
     * <p>assignTime 与 assigneeId 同进同出：承接时写当前时间，取消承接时一起清空，
     * 避免留下「没有承接人却有承接时间」的脏数据。清空必须走 UpdateWrapper —— MyBatis-Plus
     * 的 updateById 会忽略 null 字段，直接置 null 是写不进数据库的。
     */
    @Transactional
    public void assignItem(Long itemId, Long assigneeId) {
        getItem(itemId);
        if (assigneeId == null) {
            itemMapper.update(null, Wrappers.<KanbanItem>lambdaUpdate()
                    .eq(KanbanItem::getItemId, itemId)
                    .set(KanbanItem::getAssigneeId, null)
                    .set(KanbanItem::getAssignTime, null));
            log.info("取消卡片承接: itemId={}", itemId);
            return;
        }

        requireUser(assigneeId);
        KanbanItem update = new KanbanItem();
        update.setItemId(itemId);
        update.setAssigneeId(assigneeId);
        update.setAssignTime(LocalDateTime.now());
        itemMapper.updateById(update);
        log.info("卡片指派: itemId={}, assigneeId={}", itemId, assigneeId);
    }

    /**
     * 反向同步：日程状态变化后，把看板上的卡片挪到对应列（供日程模块在状态流转后调用）。
     *
     * <p>匹配规则与正向保持一致：优先按列名认语义，认不出再按索引兜底
     * （todo → 第 1 列、doing → 第 2 列、done → 最后一列），
     * 这样即使用户把列名改成「需求池 / 开发 / 验收」，卡片也有落点。
     *
     * <p>幂等性：卡片已经在目标列时直接跳过，日程状态被重复提交也不会把卡片在列内反复顶到末尾。
     * 卡片一律追加到目标列末尾，不重排其它卡片——状态同步不该打乱用户手工排好的顺序。
     */
    @Transactional
    public void syncItemColumnByScheduleStatus(Long scheduleId, String status) {
        if (scheduleId == null || !StringUtils.hasText(status)) {
            return;
        }
        List<KanbanItem> items = itemMapper.selectList(Wrappers.<KanbanItem>lambdaQuery()
                .eq(KanbanItem::getScheduleId, scheduleId));
        if (items.isEmpty()) {
            return;
        }

        String normalized = status.trim().toLowerCase(Locale.ROOT);
        // 一次把涉及到的看板的列全部取回并按看板分组，避免逐卡片查列
        Set<Long> kanbanIds = items.stream()
                .map(KanbanItem::getKanbanId)
                .collect(Collectors.toSet());
        Map<Long, List<KanbanColumn>> columnsByKanban = columnMapper.selectList(
                        Wrappers.<KanbanColumn>lambdaQuery()
                                .in(KanbanColumn::getKanbanId, kanbanIds)
                                .orderByAsc(KanbanColumn::getSortOrder))
                .stream()
                .collect(Collectors.groupingBy(KanbanColumn::getKanbanId));

        // 第一轮只在内存里定列：已经在目标列的卡直接跳过（幂等），其余收集起来等下一轮统一落位
        List<KanbanItem> moving = new ArrayList<>(items.size());
        Map<Long, KanbanColumn> targetByItemId = new HashMap<>(items.size());
        Set<Long> targetColumnIds = new HashSet<>();
        for (KanbanItem item : items) {
            KanbanColumn target = resolveTargetColumn(
                    columnsByKanban.getOrDefault(item.getKanbanId(), List.of()), normalized);
            if (target == null || target.getColumnId().equals(item.getColumnId())) {
                continue;
            }
            moving.add(item);
            targetByItemId.put(item.getItemId(), target);
            targetColumnIds.add(target.getColumnId());
        }
        if (moving.isEmpty()) {
            return;
        }

        // 第二轮一条 SQL 取回目标列的当前最大位次：卡片要追加到列尾，位次不能靠逐卡查 max 得到
        Map<Long, Integer> cursorByColumn = new HashMap<>(targetColumnIds.size());
        itemMapper.selectList(Wrappers.<KanbanItem>lambdaQuery()
                        .select(KanbanItem::getColumnId, KanbanItem::getSortOrder)
                        .in(KanbanItem::getColumnId, targetColumnIds))
                .forEach(row -> cursorByColumn.merge(row.getColumnId(),
                        row.getSortOrder() == null ? 0 : row.getSortOrder(), Math::max));

        for (KanbanItem item : moving) {
            Long targetColumnId = targetByItemId.get(item.getItemId()).getColumnId();
            // 同一张日程在多张看板里时可能落到同一列，位次在本地游标上递增，避免互相覆盖
            int sortOrder = cursorByColumn.merge(targetColumnId, 1, Integer::sum);

            KanbanItem update = new KanbanItem();
            update.setItemId(item.getItemId());
            update.setColumnId(targetColumnId);
            update.setSortOrder(sortOrder);
            itemMapper.updateById(update);
            log.debug("日程状态反向同步卡片: itemId={}, status={}, columnId={}, sortOrder={}",
                    item.getItemId(), normalized, targetColumnId, sortOrder);
        }
    }

    // ==================================================================
    // 四、内部工具：状态判定
    // ==================================================================

    /**
     * 列名 → 日程状态。判定顺序为 待办 → 进行中 → 已完成。
     *
     * <p>顺序有讲究：「已完成」也包含「完成」，而「待办」「进行中」不会与其它关键词重叠，
     * 所以把范围最窄的规则放前面；统一转小写后匹配，兼容「TODO / Done / In Progress」这类英文列名。
     *
     * @return todo / doing / done；列名不含任何可识别语义时返回 null（调用方不猜测，宁可不改）
     */
    private String scheduleStatusOfColumn(String columnName) {
        if (!StringUtils.hasText(columnName)) {
            return null;
        }
        String name = columnName.trim().toLowerCase(Locale.ROOT);
        if (name.contains("待办") || name.contains("todo") || name.contains("to do")) {
            return STATUS_TODO;
        }
        if (name.contains("进行中") || name.contains("doing")
                || name.contains("progress") || name.contains("in progress")) {
            return STATUS_DOING;
        }
        if (name.contains("已完成") || name.contains("完成") || name.contains("done")) {
            return STATUS_DONE;
        }
        return null;
    }

    /** 按列名反推日程状态并写回；反推不出来就保持日程原状（列名是用户自由文案，不能拿来猜） */
    private void syncScheduleStatusByColumn(Long scheduleId, String columnName) {
        if (scheduleId == null) {
            return;
        }
        String status = scheduleStatusOfColumn(columnName);
        if (status == null) {
            log.debug("列名无法映射日程状态，跳过同步: scheduleId={}, columnName={}", scheduleId, columnName);
            return;
        }
        int affected = itemMapper.updateScheduleStatus(scheduleId, status);
        if (affected == 0) {
            // 日程已被删除时写回 0 行，这里只记日志：卡片保留，避免误删数据
            log.warn("同步日程状态未命中任何行（日程可能已删除）: scheduleId={}, status={}", scheduleId, status);
        }
    }

    /**
     * 反向同步的目标列：先按列名匹配，匹配不到按状态索引兜底。
     *
     * @return 目标列；status 为 canceled 等非三态值、或看板已无列时返回 null
     */
    private KanbanColumn resolveTargetColumn(List<KanbanColumn> columns, String status) {
        if (columns.isEmpty()) {
            return null;
        }
        for (KanbanColumn column : columns) {
            if (status.equals(scheduleStatusOfColumn(column.getColumnName()))) {
                return column;
            }
        }
        return switch (status) {
            case STATUS_TODO -> columns.get(0);
            // 只有一列时「第 2 列」不存在，退回最后一列，避免把卡片放到不存在的列
            case STATUS_DOING -> columns.size() >= 2 ? columns.get(1) : columns.get(columns.size() - 1);
            case STATUS_DONE -> columns.get(columns.size() - 1);
            // canceled 等状态没有对应的列语义，不强行落位
            default -> null;
        };
    }

    // ==================================================================
    // 五、内部工具：存在性、权限、位次
    // ==================================================================

    /**
     * 删除某条日程在所有看板上的卡片。
     *
     * <p>为什么需要显式清理：kanban_item.schedule_id 上没有数据库外键
     * （团队/日程/看板之间都是弱引用，避免级联删除误伤），
     * 因此日程被删除后必须由业务层兜底清理卡片，否则看板上会留下「标题为空」的幽灵卡片。
     *
     * @return 被清理的卡片数量
     */
    @Transactional
    public int removeItemsByScheduleId(Long scheduleId) {
        if (scheduleId == null) {
            return 0;
        }
        int removed = itemMapper.delete(Wrappers.<KanbanItem>lambdaQuery()
                .eq(KanbanItem::getScheduleId, scheduleId));
        if (removed > 0) {
            log.info("清理看板卡片: scheduleId={}, 共 {} 张", scheduleId, removed);
        }
        return removed;
    }

    public Kanban getById(Long kanbanId) {
        Kanban kanban = kanbanMapper.selectById(kanbanId);
        if (kanban == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "看板不存在: " + kanbanId);
        }
        return kanban;
    }

    private KanbanColumn getColumn(Long columnId) {
        KanbanColumn column = columnMapper.selectById(columnId);
        if (column == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "看板列不存在: " + columnId);
        }
        return column;
    }

    private KanbanItem getItem(Long itemId) {
        KanbanItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "看板卡片不存在: " + itemId);
        }
        return item;
    }

    /** 只有创建者或 admin 能改看板元信息与删除看板 */
    private void requireOwnerOrAdmin(Long creatorId, Long operatorId) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        if (operatorId == null || !operatorId.equals(creatorId)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "只有看板创建者或管理员可以执行该操作");
        }
    }

    private void requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "用户不存在: " + userId);
        }
    }

    /** columnId 为空时落到第一列；显式指定时校验该列确实属于本看板 */
    private KanbanColumn resolveColumn(Long kanbanId, Long columnId) {
        if (columnId == null) {
            KanbanColumn first = columnMapper.selectOne(Wrappers.<KanbanColumn>lambdaQuery()
                    .eq(KanbanColumn::getKanbanId, kanbanId)
                    .orderByAsc(KanbanColumn::getSortOrder)
                    .last("LIMIT 1"));
            if (first == null) {
                throw BizException.of(ErrorCode.CONFLICT, "看板还没有任何列，请先创建列");
            }
            return first;
        }
        KanbanColumn column = columnMapper.selectById(columnId);
        if (column == null || !kanbanId.equals(column.getKanbanId())) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "目标列不属于该看板: " + columnId);
        }
        return column;
    }

    /** 列内下一个位次 = 当前最大位次 + 1；空列从 1 开始 */
    private int nextSortOrder(Long columnId) {
        KanbanItem last = itemMapper.selectOne(Wrappers.<KanbanItem>lambdaQuery()
                .eq(KanbanItem::getColumnId, columnId)
                .orderByDesc(KanbanItem::getSortOrder)
                .last("LIMIT 1"));
        return last == null || last.getSortOrder() == null ? 1 : last.getSortOrder() + 1;
    }
}
