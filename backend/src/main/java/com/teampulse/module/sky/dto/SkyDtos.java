package com.teampulse.module.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 登录页「天空上下文」相关的传输对象。
 *
 * <p>这些接口只服务于登录页的氛围渲染（云量、天色、城市场景），
 * 不涉及任何业务数据，因此全部为公开接口。
 */
public final class SkyDtos {

    private SkyDtos() {
    }

    /**
     * 访客所在地＋当前天气。
     *
     * @param source      ip=按访客 IP 定位；manual=访客手动选择；fallback=兜底默认城市
     * @param ipCity      IP 定位到的城市（用于界面上「定位：xxx」的展示；手动选择时也会返回，便于对照）
     * @param province    生效省份
     * @param city        生效城市
     * @param district    生效区县（可能为空）
     * @param lat         生效纬度
     * @param lon         生效经度
     * @param cloudCover  云量百分比 0~100
     * @param weatherCode WMO 天气代码
     * @param precipitation 当前降水量 mm
     * @param temperature 当前气温 ℃
     * @param isDay       是否白天（1/0）
     * @param updatedAt   数据取得时间（ISO-8601，东八区）
     */
    @Schema(name = "SkyContext", description = "访客位置与当前天气")
    public record SkyContext(
            String source,
            String ipCity,
            String province,
            String city,
            String district,
            double lat,
            double lon,
            Integer cloudCover,
            Integer weatherCode,
            Double precipitation,
            Double temperature,
            Integer isDay,
            String updatedAt
    ) {
    }

    /** 三级行政区划节点：省 → 市 → 区县 */
    @Schema(name = "SkyRegion", description = "行政区划节点")
    public record SkyRegion(String code, String name, List<SkyRegion> children) {
    }
}
