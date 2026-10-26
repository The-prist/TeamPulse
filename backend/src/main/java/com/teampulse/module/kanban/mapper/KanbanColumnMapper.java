package com.teampulse.module.kanban.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.kanban.entity.KanbanColumn;
import org.apache.ibatis.annotations.Mapper;

/**
 * 看板列 Mapper。
 *
 * <p>列上的取最大排序、按看板取列等操作都能用 Lambda 包装器表达，
 * 因此这里保持为空，不额外写 SQL。
 */
@Mapper
public interface KanbanColumnMapper extends BaseMapper<KanbanColumn> {
}
