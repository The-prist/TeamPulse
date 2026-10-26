package com.teampulse.common.api;

import lombok.Getter;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页返回结构，字段名与前端表格组件约定保持一致（records/total/page/size）。
 *
 * @param <T> 行数据类型
 */
@Getter
public class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final long page;
    private final long size;

    public PageResult(List<T> records, long total, long page, long size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0, page, size);
    }

    /**
     * 把 MyBatis-Plus 的 IPage 转换为对外分页结构，同时完成实体到 VO 的映射。
     */
    public static <E, V> PageResult<V> from(com.baomidou.mybatisplus.core.metadata.IPage<E> page,
                                            Function<E, V> mapper) {
        return new PageResult<>(
                page.getRecords().stream().map(mapper).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize());
    }
}
