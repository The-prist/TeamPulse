package com.teampulse.module.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.schedule.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 日程 Mapper。
 *
 * <p>常规查询全部交给 MyBatis-Plus；这里只有两处必须手写 SQL：
 * <ol>
 *   <li>状态流转——只更新状态与时间戳，避免整行读改写造成的丢失更新</li>
 *   <li>批量查看板卡片 ID——kanban_item 属于看板域，日程域不需要（也不应该）
 *       引入看板的 Java 实体，一条只读聚合 SQL 即可</li>
 * </ol>
 */
@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /**
     * 原子更新状态。
     *
     * <p>为什么不用 {@code updateById}：状态变更可能来自看板拖拽，属于「最后写入生效」的高频操作；
     * 手写 SQL 只动 {@code status / updated_at} 两列，既不会把并发期间别人改过的标题覆盖回去，
     * 也省掉一次 select。
     *
     * <p>{@code deleted = 0} 必须显式写：注解 SQL 不经过 MyBatis-Plus 的逻辑删除拦截，
     * 少了它就会把已删除的日程改成新状态。
     */
    @Update("UPDATE schedule SET status = #{status}, updated_at = NOW() "
            + "WHERE schedule_id = #{id} AND deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 批量取日程对应的看板卡片 ID（<script> 动态 IN，避免 for 循环查库）。
     *
     * <p>用 MIN(item_id) 兜底重复卡片：理论上一条日程只该投影成一张卡片，
     * 但历史数据可能重复，聚合后至少保证返回值唯一、不会让 toMap 抛重复键。
     */
    @Select("<script>"
            + "SELECT schedule_id AS scheduleId, MIN(item_id) AS itemId FROM kanban_item "
            + "WHERE schedule_id IN "
            + "<foreach collection='scheduleIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> "
            + "GROUP BY schedule_id"
            + "</script>")
    List<Map<String, Object>> selectKanbanItemIds(@Param("scheduleIds") List<Long> scheduleIds);
}
