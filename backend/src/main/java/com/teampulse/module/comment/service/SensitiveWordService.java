package com.teampulse.module.comment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teampulse.common.api.PageResult;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.comment.entity.SensitiveWord;
import com.teampulse.module.comment.mapper.SensitiveWordMapper;
import com.teampulse.security.SecurityUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感词过滤与词库管理。
 *
 * <p>为什么把词库放进内存：评论是全站写入最频繁的接口之一，如果每次发评论都去
 * {@code SELECT * FROM sensitive_word} 过滤，词库表会被放大成热点查询。因此把
 * status=active 的词一次性读出来并**预编译成正则**缓存在进程内，之后过滤就是纯内存计算。
 *
 * <p>缓存一致性策略（60 秒 TTL）：
 * <ul>
 *   <li>本节点增删改后立即置空缓存，管理端改完下一秒就能看到效果；</li>
 *   <li>TTL 兜住「其他节点/他人直接改库」的场景，最长 60 秒后自动对齐；</li>
 *   <li>回源失败时继续沿用上一份快照，并刷新时间戳，避免数据库抖动时每个评论请求都去打库。</li>
 * </ul>
 *
 * <p>降级取舍：过滤只是评论链路上的「附加能力」，不能成为可用性单点。
 * 一旦词库加载或匹配抛异常，{@link #filter(String)} 会返回原文并打 warn 日志。
 * 宁可放过一条未过滤的评论（原文同时落库在 original_content，管理员可事后删除、
 * 可把词补进词库复盘），也不能让用户因为词库问题发不出评论。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    /** status 取值：生效中 */
    private static final String STATUS_ACTIVE = "active";
    /** status 取值：已停用 */
    private static final String STATUS_INACTIVE = "inactive";
    /** 词条未配置替换文本时的默认值，与 DDL 默认值保持一致 */
    private static final String DEFAULT_REPLACEMENT = "***";
    /** 本地缓存存活时间：越短越实时、越长越省数据库，60 秒是本项目接受的折中 */
    private static final long CACHE_TTL_MILLIS = 60_000L;

    private final SensitiveWordMapper sensitiveWordMapper;

    /** 词规则快照。只在 synchronized 块里整体替换引用，读侧无锁，volatile 保证可见性 */
    private volatile List<WordRule> cachedRules = List.of();

    /** 快照加载完成的时刻（毫秒）。置 0 表示「已失效，下次访问必须回源」 */
    private volatile long cacheLoadedAt = 0L;

    /**
     * 启动预热：把词库读进内存，避免第一批评论请求承担回源延迟。
     *
     * <p>预热失败不允许影响启动——建表脚本可能还没跑完，或数据库暂时不可达，
     * 这种情况下让应用正常起来、等首次使用时再回源更符合运维预期。
     */
    @PostConstruct
    public void warmUp() {
        try {
            List<WordRule> rules = loadRules();
            cachedRules = rules;
            cacheLoadedAt = System.currentTimeMillis();
            log.info("敏感词缓存预热完成，生效词条 {} 条", rules.size());
        } catch (Exception ex) {
            log.warn("敏感词缓存预热失败，将在首次评论时回源加载: {}", ex.getMessage());
        }
    }

    // ==================================================================
    // 一、过滤能力（评论链路直接调用）
    // ==================================================================

    /**
     * 把命中的敏感词替换为其配置的替换文本。
     *
     * <p>入参为 null 或空白时原样返回，调用方不必先判空。
     */
    public String filter(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        try {
            String result = content;
            for (WordRule rule : rules()) {
                // Matcher.quoteReplacement：替换文本是管理员自由输入的，
                // 里面出现 $ 或 \ 时直接传给 replaceAll 会抛 IllegalArgumentException
                result = rule.pattern().matcher(result).replaceAll(Matcher.quoteReplacement(rule.replacement()));
            }
            return result;
        } catch (Exception ex) {
            log.warn("敏感词过滤失败，降级为原文放行（评论不能被词库问题拖垮）: {}", ex.getMessage());
            return content;
        }
    }

    /** 文本是否命中任一敏感词。异常时按「未命中」降级，保证前端提示不会 500 */
    public boolean contains(String content) {
        return !findWords(content).isEmpty();
    }

    /**
     * 返回命中的敏感词列表（去重、按词库加载顺序）。
     *
     * <p>供前端输入框做实时提示，也便于管理端排查「这条评论为什么被替换」。
     */
    public List<String> findWords(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        try {
            Set<String> hits = new LinkedHashSet<>();
            for (WordRule rule : rules()) {
                if (rule.pattern().matcher(content).find()) {
                    hits.add(rule.word());
                }
            }
            return List.copyOf(hits);
        } catch (Exception ex) {
            log.warn("敏感词命中检查失败，降级为未命中: {}", ex.getMessage());
            return List.of();
        }
    }

    // ==================================================================
    // 二、词库管理（管理端）
    // ==================================================================

    /** 分页查询词库，keyword 同时匹配词与替换文本 */
    public PageResult<SensitiveWord> page(String keyword, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 200);
        var wrapper = Wrappers.<SensitiveWord>lambdaQuery().orderByDesc(SensitiveWord::getWordId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SensitiveWord::getWord, kw)
                    .or().like(SensitiveWord::getReplacement, kw));
        }
        IPage<SensitiveWord> result = sensitiveWordMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return PageResult.from(result, w -> w);
    }

    /**
     * 新增敏感词，重复录入直接冲突。
     *
     * <p>唯一性判断交给数据库 uk_word（默认 collation 大小写不敏感），
     * 这样 "Fuck" 与 "fuck" 会被认成同一个词，不会出现两个只差大小写的规则互相覆盖。
     */
    @Transactional
    public SensitiveWord add(String word, String replacement) {
        String trimmedWord = requireWord(word);
        if (sensitiveWordMapper.exists(Wrappers.<SensitiveWord>lambdaQuery()
                .eq(SensitiveWord::getWord, trimmedWord))) {
            throw BizException.of(ErrorCode.CONFLICT, "敏感词已存在: " + trimmedWord);
        }

        SensitiveWord entity = new SensitiveWord();
        entity.setWord(trimmedWord);
        entity.setReplacement(StringUtils.hasText(replacement) ? replacement.trim() : DEFAULT_REPLACEMENT);
        entity.setStatus(STATUS_ACTIVE);
        // 词库是管理端功能，能走到这里必然是已登录的 admin；记录录入人便于事后追责
        entity.setCreatorId(SecurityUtils.currentUserId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        sensitiveWordMapper.insert(entity);

        invalidateCache();
        log.info("新增敏感词: wordId={}, word={}, replacement={}",
                entity.getWordId(), entity.getWord(), entity.getReplacement());
        return entity;
    }

    /** 更新词库条目：字段传 null 表示不修改，改完立刻失效缓存 */
    @Transactional
    public void update(Long wordId, String word, String replacement, String status) {
        SensitiveWord entity = getOrThrow(wordId);
        if (StringUtils.hasText(word)) {
            String trimmedWord = requireWord(word);
            if (!trimmedWord.equals(entity.getWord())
                    && sensitiveWordMapper.exists(Wrappers.<SensitiveWord>lambdaQuery()
                    .eq(SensitiveWord::getWord, trimmedWord)
                    .ne(SensitiveWord::getWordId, wordId))) {
                throw BizException.of(ErrorCode.CONFLICT, "敏感词已存在: " + trimmedWord);
            }
            entity.setWord(trimmedWord);
        }
        if (StringUtils.hasText(replacement)) {
            entity.setReplacement(replacement.trim());
        }
        if (StringUtils.hasText(status)) {
            String normalized = status.trim().toLowerCase();
            if (!STATUS_ACTIVE.equals(normalized) && !STATUS_INACTIVE.equals(normalized)) {
                throw BizException.of(ErrorCode.BAD_REQUEST, "状态只能是 active 或 inactive");
            }
            entity.setStatus(normalized);
        }
        entity.setUpdatedAt(LocalDateTime.now());
        sensitiveWordMapper.updateById(entity);

        invalidateCache();
        log.info("更新敏感词: wordId={}, word={}, status={}", entity.getWordId(), entity.getWord(), entity.getStatus());
    }

    /** 删除词条。历史评论里被替换掉的内容不会因此恢复，恢复展示需要人工按 original_content 处理 */
    @Transactional
    public void delete(Long wordId) {
        getOrThrow(wordId);
        sensitiveWordMapper.deleteById(wordId);

        invalidateCache();
        log.info("删除敏感词: wordId={}", wordId);
    }

    // ==================================================================
    // 三、缓存与内部工具
    // ==================================================================

    /** 词规则快照：词、替换文本、预编译正则 */
    private record WordRule(String word, String replacement, Pattern pattern) {
    }

    /**
     * 取当前生效的词规则，必要时回源刷新。
     *
     * <p>用「双重检查 + 只替换引用」的方式实现：绝大多数请求走无锁快路径，
     * 只有缓存过期的那一个线程进入同步块回源，其余线程不会排队等数据库。
     */
    private List<WordRule> rules() {
        if (System.currentTimeMillis() - cacheLoadedAt < CACHE_TTL_MILLIS) {
            return cachedRules;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - cacheLoadedAt < CACHE_TTL_MILLIS) {
                return cachedRules;
            }
            List<WordRule> loaded;
            try {
                loaded = loadRules();
            } catch (Exception ex) {
                // 回源失败：沿用旧快照并刷新时间戳，防止数据库故障期间每个请求都重试一次
                log.warn("敏感词词库回源失败，沿用上一份快照（{} 条）: {}", cachedRules.size(), ex.getMessage());
                loaded = cachedRules;
            }
            cachedRules = loaded;
            cacheLoadedAt = System.currentTimeMillis();
            return loaded;
        }
    }

    private List<WordRule> loadRules() {
        List<SensitiveWord> words = sensitiveWordMapper.selectList(Wrappers.<SensitiveWord>lambdaQuery()
                .eq(SensitiveWord::getStatus, STATUS_ACTIVE));
        List<WordRule> rules = new ArrayList<>(words.size());
        for (SensitiveWord word : words) {
            if (!StringUtils.hasText(word.getWord())) {
                continue;
            }
            String replacement = StringUtils.hasText(word.getReplacement())
                    ? word.getReplacement()
                    : DEFAULT_REPLACEMENT;
            rules.add(new WordRule(word.getWord().trim(), replacement, compile(word.getWord().trim())));
        }
        return List.copyOf(rules);
    }

    /**
     * 预编译单个词的正则。
     *
     * <p>{@code (?i)} 让匹配忽略大小写；{@code Pattern.quote} 把词当字面量处理，
     * 否则管理员录入一个 {@code a.*b} 就会被当成正则语法，既会误伤正常文本，
     * 也可能构造出灾难性回溯（ReDoS）把 CPU 打满。
     */
    private static Pattern compile(String word) {
        return Pattern.compile("(?i)" + Pattern.quote(word));
    }

    /** 失效缓存：时间戳置 0，下一次读取必然回源，从而做到「改完立即可见」 */
    private void invalidateCache() {
        cachedRules = List.of();
        cacheLoadedAt = 0L;
    }

    private SensitiveWord getOrThrow(Long wordId) {
        SensitiveWord entity = sensitiveWordMapper.selectById(wordId);
        if (entity == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "敏感词不存在: " + wordId);
        }
        return entity;
    }

    private static String requireWord(String word) {
        if (!StringUtils.hasText(word)) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "敏感词不能为空");
        }
        String trimmed = word.trim();
        if (trimmed.length() > 100) {
            throw BizException.of(ErrorCode.BAD_REQUEST, "敏感词不能超过 100 字");
        }
        return trimmed;
    }
}
