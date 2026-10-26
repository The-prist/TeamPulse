package com.teampulse.module.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.attendance.entity.IotDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface IotDeviceMapper extends BaseMapper<IotDevice> {

    /**
     * 心跳上报：只更新运行态字段，避免整行 update 覆盖管理员刚改的名称/位置。
     */
    @Update("UPDATE iot_device SET status = 'ONLINE', last_heartbeat = #{heartbeat}, "
            + "firmware_version = COALESCE(#{firmwareVersion}, firmware_version), "
            + "ip_address = COALESCE(#{ipAddress}, ip_address), updated_at = NOW() "
            + "WHERE device_sn = #{deviceSn} AND deleted = 0 AND status <> 'DISABLED'")
    int touchHeartbeat(@Param("deviceSn") String deviceSn,
                       @Param("heartbeat") LocalDateTime heartbeat,
                       @Param("firmwareVersion") String firmwareVersion,
                       @Param("ipAddress") String ipAddress);

    /** 巡检：把超过阈值未心跳的 ONLINE 设备置为 OFFLINE，返回受影响行数 */
    @Update("UPDATE iot_device SET status = 'OFFLINE', updated_at = NOW() "
            + "WHERE deleted = 0 AND status = 'ONLINE' AND (last_heartbeat IS NULL OR last_heartbeat < #{deadline})")
    int markOfflineBefore(@Param("deadline") LocalDateTime deadline);
}
