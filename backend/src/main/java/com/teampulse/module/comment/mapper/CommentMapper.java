package com.teampulse.module.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.comment.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论 Mapper。
 *
 * <p>评论的所有查询（按 related_type + related_id 过滤、按 reply_to 找子回复）
 * 都能用 LambdaQueryWrapper 表达，且这两组条件都有 DDL 索引覆盖，
 * 因此这里不写任何 @Select —— 少一处手写 SQL 就少一处字段改名时的漏改点。
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
