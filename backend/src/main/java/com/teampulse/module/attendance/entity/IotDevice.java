package com.teampulse.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 物联网打卡设备。
 *
 * <p>{@code secret} 是设备与后端共享的 HMAC-SHA256 密钥，出厂烧录在 ESP32 的 NVS 中。
 * 该密钥只用于报文签名，不参与用户登录体系，因此泄露影响面被限制在「伪造打卡」而非「登录系统」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_device")
public class IotDevice extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备序列号，报文中的 deviceSn */
    private String deviceSn;

    private String deviceName;

    /** 安装位置，如「一楼大厅」 */
    private String location;

    /** HMAC 共享密钥（生产环境可考虑加密存储） */
    private String secret;

    /** ONLINE / OFFLINE / DISABLED */
    private String status;

    private String firmwareVersion;

    private String ipAddress;

    private LocalDateTime lastHeartbeat;

    private String remark;
}
