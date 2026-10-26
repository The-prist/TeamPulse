package com.teampulse.module.kanban.dto;

import com.teampulse.module.kanban.entity.Kanban;
import com.teampulse.module.kanban.entity.KanbanColumn;
import com.teampulse.module.kanban.vo.KanbanCardVO;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板模块的请求体与详情视图。
 *
 * <p>错误信息全部写成面向用户的中文，Controller 只加 {@code @Valid}，不手写判空。
 */
public final class KanbanDtos {

    private KanbanDtos() {
    }

    /** 创建看板：teamId 必填，因为看板可见性按团队判定，没有团队就没有协作范围 */
    @Data
    public static class CreateRequest {

        @NotBlank(message = "看板名称不能为空")
        @Size(max = 100, message = "看板名称不能超过 100 字")
        private String kanbanName;

        @Size(max = 2000, message = "看板描述不能超过 2000 字")
        private String description;

        @NotNull(message = "所属团队不能为空")
        private Long teamId;
    }

    /** 更新看板：字段为 null 表示不修改，避免前端只改描述时把名称清空 */
    @Data
    public static class UpdateRequest {

        @Size(max = 100, message = "看板名称不能超过 100 字")
        private String kanbanName;

        @Size(max = 2000, message = "看板描述不能超过 2000 字")
        private String description;

        private Long teamId;
    }

    /** 新增列：只有列名，排位由后端取当前最大值 +1，前端不需要关心 order */
    @Data
    public static class ColumnCreateRequest {

        @NotBlank(message = "列名不能为空")
        @Size(max = 50, message = "列名不能超过 50 字")
        private String columnName;
    }

    /**
     * 新增卡片：columnId 为空时落到第一列（新建看板默认有「待办」，即第一列），
     * assigneeId 为空表示暂不指派。
     */
    @Data
    public static class ItemCreateRequest {

        @NotNull(message = "关联日程不能为空")
        private Long scheduleId;

        private Long columnId;

        private Long assigneeId;
    }

    /** 移动卡片：目标列与目标位次都必须明确，缺一就无法确定落位 */
    @Data
    public static class MoveRequest {

        @NotNull(message = "目标列不能为空")
        private Long columnId;

        @NotNull(message = "目标位置不能为空")
        @Min(value = 0, message = "目标位置不能为负数")
        private Integer sortOrder;
    }

    /**
     * 看板详情视图。
     *
     * <p>对外接口返回 Map（前端按 kanban / columns / cards 三段取数），
     * 这里保留强类型定义用于组装与文档说明，由 {@link #toMap()} 转成响应体。
     */
    @Data
    public static class KanbanDetailVO {

        private Kanban kanban;

        /** 按 sortOrder 升序，前端按数组顺序渲染列 */
        private List<KanbanColumn> columns;

        /** 按 columnId、sortOrder 升序，前端按 columnId 分组塞进各列 */
        private List<KanbanCardVO> cards;

        /** 保持字段顺序，便于排查响应体时肉眼比对 */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>(4);
            map.put("kanban", kanban);
            map.put("columns", columns);
            map.put("cards", cards);
            return map;
        }
    }
}
