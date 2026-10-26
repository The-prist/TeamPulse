package com.teampulse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TeamPulse 应用入口。
 *
 * <p>TeamPulse 是一个「团队协作 + 物联网考勤」一体化平台：
 * <ul>
 *   <li>协作域：用户、团队、日程、看板、工作日志、评论（含敏感词过滤）</li>
 *   <li>考勤域：ESP32 打卡机通过 MQTT 上行打卡报文，服务端实时判定迟到/早退并落库</li>
 * </ul>
 *
 * <p>{@code @EnableScheduling} 用于设备离线巡检与考勤日汇总；{@code @EnableAsync} 用于
 * 把 MQTT 下行回执等旁路动作从主链路剥离。
 */
@SpringBootApplication
@MapperScan("com.teampulse.**.mapper")
@EnableScheduling
@EnableAsync
public class TeamPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamPulseApplication.class, args);
    }
}
