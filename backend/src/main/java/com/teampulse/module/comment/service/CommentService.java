package com.teampulse.module.comment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teampulse.common.api.PageResult;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.comment.dto.CommentDtos;
import com.teampulse.module.comment.entity.Comment;
import com.teampulse.module.comment.mapper.CommentMapper;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import com.teampulse.security.LoginUser;
import com.teampulse.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论服务：日程与工作日志共用的讨论区。
 *
 * <p>三个设计要点：
 * <ol>
 *   <li><b>写时过滤</b>：入库前就把敏感词替换掉（content 存过滤后、originalContent 存原文），
 *       读侧无需再处理词库，列表/详情的耗时与词库大小无关。</li>
 *   <li><b>两级回复</b>：DDL 只有一个 reply_to，产品上也只要求「评论 + 回复」两层。
 *       因此回复一条回复时会被压平挂到根评论下，避免前端递归渲染出现无限缩进。</li>
 *   <li><b>批量补人</b>：昵称/头像用 selectBatchIds 一次性取回，绝不在循环里查库。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    /** 允许评论的业务类型，和 comment.related_type 的取值域保持一致 */
    private static final Set<String> RELATED_TYPES = Set.of("schedule", "worklog");
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 200L;

    private final CommentMapper commentMapper;
    private final SensitiveWordService sensitiveWordService;
    private final UserMapper userMapper;

    // ==================================================================
    // 一、写入
    // ==================================================================

    /**
     * 发表评论或回复。
     *
     * @param req       请求体（relatedType/relatedId/content/replyTo）
     * @param creatorId 当前登录人，由 Controller 从 JWT 取，绝不接受请求参数传入
     */
    @Transactional
    public boolean add(CommentDtos.AddRequest req, Long creatorId) {
        String relatedType = requireRelatedType(req.getRelatedType());
        String original = req.getContent().trim();
        Long parentId = normalizeParent(req.getReplyTo(), relatedType, req.getRelatedId());

        Comment comment = new Comment();
        comment.setRelatedType(relatedType);
        comment.setRelatedId(req.getRelatedId());
        comment.setCreatorId(creatorId);
        comment.setContent(sensitiveWordService.filter(original));
        comment.setOriginalContent(original);
        comment.setReplyTo(parentId);
        commentMapper.insert(comment);

        log.info("新增评论: commentId={}, relatedType={}, relatedId={}, creatorId={}, replyTo={}",
                comment.getCommentId(), relatedType, req.getRelatedId(), creatorId, parentId);
        return true;
    }

    /**
     * 编辑评论：只允许作者本人改，且必须重新过一遍敏感词。
     *
     * <p>不放开给管理员是刻意的——管理员要「消灭」一条不当内容应该删除，
     * 而不是悄悄改写别人的发言，否则审计记录会失真。
     */
    @Transactional
    public void update(Long commentId, String content, Long operatorId) {
        Comment comment = getOrThrow(commentId);
        if (!Objects.equals(comment.getCreatorId(), operatorId)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "只有评论作者本人可以编辑该评论");
        }
        if (!StringUtils.hasText(content)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "评论内容不能为空");
        }
        String original = content.trim();
        // 编辑后必须重新过滤：旧内容可能已过滤，但新内容没过滤过
        comment.setContent(sensitiveWordService.filter(original));
        comment.setOriginalContent(original);
        commentMapper.updateById(comment);
        log.info("编辑评论: commentId={}, operatorId={}", commentId, operatorId);
    }

    /**
     * 删除评论（逻辑删除），仅本人或 admin。
     *
     * <p>同时逻辑删除它的直接子回复：父评论消失后子回复无处挂载，
     * 若留着会在树里被当成顶层评论浮上来，表现为「莫名其妙的孤立发言」。
     */
    @Transactional
    public void delete(Long commentId, Long operatorId) {
        Comment comment = getOrThrow(commentId);
        if (!Objects.equals(comment.getCreatorId(), operatorId) && !SecurityUtils.isAdmin()) {
            throw BizException.of(ErrorCode.FORBIDDEN, "只有评论作者或管理员可以删除该评论");
        }
        commentMapper.deleteById(commentId);
        commentMapper.delete(Wrappers.<Comment>lambdaQuery().eq(Comment::getReplyTo, commentId));
        log.info("删除评论: commentId={}, operatorId={}", commentId, operatorId);
    }

    // ==================================================================
    // 二、查询
    // ==================================================================

    /**
     * 平铺分页查询（管理端/详情页「查看全部评论」用），createdTime 倒序。
     *
     * @param keyword 可选，按过滤后正文模糊匹配
     */
    public PageResult<CommentDtos.CommentVO> page(String relatedType, Long relatedId, String keyword,
                                                  long page, long size) {
        String type = requireRelatedType(relatedType);
        if (relatedId == null) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "评论对象ID不能为空");
        }
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size), MAX_PAGE_SIZE);

        var wrapper = Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getRelatedType, type)
                .eq(Comment::getRelatedId, relatedId)
                .orderByDesc(Comment::getCommentId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Comment::getContent, keyword.trim());
        }
        IPage<Comment> result = commentMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.of(toVos(result.getRecords()), result.getTotal(), safePage, safeSize);
    }

    /**
     * 管理端全量评论检索。
     *
     * <p>与 {@link #page} 的区别：那个必须锁定某个日程/日志（详情页用），
     * 这个可以不限关联对象，跨全部评论按条件筛——管理端要看的是「所有讨论」。
     *
     * <p>筛选维度：
     * <ul>
     *   <li>日期区间：按 {@code created_at} 的日期部分</li>
     *   <li>时间段：按 {@code created_at} 的时刻部分（TIME(created_at)），
     *       与日期区间是**且**关系，即「每天的这个时段」</li>
     *   <li>人物：creator_id</li>
     *   <li>关键词：同时匹配过滤后正文与原文（管理员需要能按被替换的词反查）</li>
     * </ul>
     *
     * @param order asc=时间正序（默认）/ desc=时间倒序
     */
    public PageResult<CommentDtos.CommentVO> adminPage(String relatedType, Long relatedId, Long creatorId,
                                                       String keyword, LocalDate from, LocalDate to,
                                                       String timeFrom, String timeTo, String order,
                                                       long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size), MAX_PAGE_SIZE);

        var wrapper = Wrappers.<Comment>lambdaQuery();
        if (StringUtils.hasText(relatedType)) {
            wrapper.eq(Comment::getRelatedType, requireRelatedType(relatedType));
        }
        if (relatedId != null) {
            wrapper.eq(Comment::getRelatedId, relatedId);
        }
        if (creatorId != null) {
            wrapper.eq(Comment::getCreatorId, creatorId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Comment::getContent, kw).or().like(Comment::getOriginalContent, kw));
        }
        if (from != null) {
            wrapper.ge(Comment::getCreatedAt, from.atStartOfDay());
        }
        if (to != null) {
            // 结束日期含当天全天，所以比较的是「次日零点之前」
            wrapper.lt(Comment::getCreatedAt, to.plusDays(1).atStartOfDay());
        }
        String tf = normalizeTime(timeFrom, false);
        if (tf != null) {
            wrapper.apply("TIME(created_at) >= {0}", tf);
        }
        String tt = normalizeTime(timeTo, true);
        if (tt != null) {
            wrapper.apply("TIME(created_at) <= {0}", tt);
        }

        // 时间正序：comment_id 自增，与创建时间同序，且不受同秒冲突影响
        boolean asc = !"desc".equalsIgnoreCase(order);
        wrapper.orderBy(true, asc, Comment::getCommentId);

        IPage<Comment> result = commentMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.of(toVos(result.getRecords()), result.getTotal(), safePage, safeSize);
    }

    /**
     * 把 "HH:mm" / "HH:mm:ss" 规范成 MySQL 能比较的 "HH:mm:ss"。
     *
     * @param endOfMinute true 时把 "HH:mm" 补成 "HH:mm:59"，
     *                    否则「时间段结束 = 10:30」会把 10:30:15 的评论漏掉
     */
    private String normalizeTime(String raw, boolean endOfMinute) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String v = raw.trim();
        if (v.matches("\\d{2}:\\d{2}")) {
            return v + (endOfMinute ? ":59" : ":00");
        }
        if (v.matches("\\d{2}:\\d{2}:\\d{2}")) {
            return v;
        }
        throw BizException.of(ErrorCode.BAD_REQUEST, "时间段格式应为 HH:mm 或 HH:mm:ss");
    }

    /**
     * 组装两级回复树：顶层评论按时间正序，children 依次挂在其下。
     *
     * <p>一次查询取回该业务对象下的全部评论后在内存里建索引挂载，
     * 而不是「先查顶层、再逐条查子回复」——后者会产生 1+N 次查询。
     */
    public List<CommentDtos.CommentVO> tree(String relatedType, Long relatedId) {
        String type = requireRelatedType(relatedType);
        if (relatedId == null) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "评论对象ID不能为空");
        }
        List<Comment> comments = commentMapper.selectList(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getRelatedType, type)
                .eq(Comment::getRelatedId, relatedId)
                .orderByAsc(Comment::getCommentId));

        List<CommentDtos.CommentVO> vos = toVos(comments);
        Map<Long, CommentDtos.CommentVO> index = new HashMap<>(vos.size() * 2);
        for (CommentDtos.CommentVO vo : vos) {
            index.put(vo.getCommentId(), vo);
        }

        List<CommentDtos.CommentVO> roots = new ArrayList<>();
        for (CommentDtos.CommentVO vo : vos) {
            CommentDtos.CommentVO parent = vo.getReplyTo() == null ? null : index.get(vo.getReplyTo());
            if (parent == null) {
                // 父评论被删除（逻辑删除后查不到）时把子回复提到顶层，
                // 否则这串讨论会从页面上整段消失，用户只看到「评论数量对不上」
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }

    // ==================================================================
    // 三、内部工具
    // ==================================================================

    /**
     * 校验父评论并返回实际挂载点。
     *
     * <p>两个判断：
     * <ul>
     *   <li>父评论必须属于同一个业务对象，否则会出现「在日程 A 下回复日程 B 的评论」这种串台数据；</li>
     *   <li>父评论本身已是回复时，把挂载点提升到它的根评论，把树压平为两级。</li>
     * </ul>
     */
    private Long normalizeParent(Long replyTo, String relatedType, Long relatedId) {
        if (replyTo == null) {
            return null;
        }
        Comment parent = commentMapper.selectById(replyTo);
        if (parent == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "回复的评论不存在或已删除: " + replyTo);
        }
        if (!relatedType.equals(parent.getRelatedType()) || !Objects.equals(parent.getRelatedId(), relatedId)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "不能回复其他日程或日志下的评论");
        }
        return parent.getReplyTo() != null ? parent.getReplyTo() : parent.getCommentId();
    }

    /** 实体转 VO，并一次性批量补齐评论人昵称/头像 */
    private List<CommentDtos.CommentVO> toVos(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }
        Set<Long> creatorIds = comments.stream()
                .map(Comment::getCreatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = creatorIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(creatorIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        // 原文只回给作者本人与管理员：对普通浏览者回传未过滤文本，等于把敏感词又从接口泄回前端
        LoginUser viewer = SecurityUtils.currentUserOrNull();
        Long viewerId = viewer == null ? null : viewer.getUserId();
        boolean viewerIsAdmin = viewer != null && "admin".equalsIgnoreCase(viewer.getRole());

        List<CommentDtos.CommentVO> vos = new ArrayList<>(comments.size());
        for (Comment comment : comments) {
            CommentDtos.CommentVO vo = new CommentDtos.CommentVO();
            vo.setCommentId(comment.getCommentId());
            vo.setRelatedType(comment.getRelatedType());
            vo.setRelatedId(comment.getRelatedId());
            vo.setCreatorId(comment.getCreatorId());
            User creator = comment.getCreatorId() == null ? null : userMap.get(comment.getCreatorId());
            if (creator != null) {
                // 昵称为空的历史账号回落到用户名，保证前端永远有字可显示
                vo.setCreatorName(StringUtils.hasText(creator.getNickname())
                        ? creator.getNickname() : creator.getUsername());
                vo.setCreatorAvatar(creator.getAvatar());
            }
            vo.setContent(comment.getContent());
            boolean visibleOriginal = viewerIsAdmin || Objects.equals(viewerId, comment.getCreatorId());
            vo.setOriginalContent(visibleOriginal ? comment.getOriginalContent() : null);
            vo.setReplyTo(comment.getReplyTo());
            vo.setCreateTime(comment.getCreatedAt());
            vos.add(vo);
        }
        return vos;
    }

    private Comment getOrThrow(Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "评论不存在: " + commentId);
        }
        return comment;
    }

    /** relatedType 来自查询参数，没有 Bean Validation 兜底，这里统一校验一次 */
    private static String requireRelatedType(String relatedType) {
        if (!StringUtils.hasText(relatedType)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "评论对象类型不能为空");
        }
        String trimmed = relatedType.trim();
        if (!RELATED_TYPES.contains(trimmed)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "评论对象类型只能是 schedule 或 worklog");
        }
        return trimmed;
    }
}
