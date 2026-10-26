package com.teampulse.module.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.comment.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词 Mapper。
 *
 * <p>词库总量在千级以内，加载策略是「一次性全量取 active 词到内存」，
 * 因此不需要按前缀/分词做 SQL 侧优化，保持纯 BaseMapper 即可。
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}
