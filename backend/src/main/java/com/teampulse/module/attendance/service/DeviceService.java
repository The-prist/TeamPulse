package com.teampulse.module.attendance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teampulse.common.api.PageResult;
import com.teampulse.common.exception.BizException;
import com.teampulse.common.exception.ErrorCode;
import com.teampulse.module.attendance.dto.AttendanceDtos;
import com.teampulse.module.attendance.entity.IotDevice;
import com.teampulse.module.attendance.mapper.IotDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

/**
 * 设备管理服务：注册、改配、密钥轮换、心跳维护、离线巡检。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final IotDeviceMapper deviceMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public PageResult<IotDevice> page(String keyword, String status, long page, long size) {
        var wrapper = Wrappers.<IotDevice>lambdaQuery().orderByDesc(IotDevice::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(IotDevice::getDeviceSn, kw)
                    .or().like(IotDevice::getDeviceName, kw)
                    .or().like(IotDevice::getLocation, kw));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(IotDevice::getStatus, status);
        }
        IPage<IotDevice> result = deviceMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.from(result, device -> {
            // 密钥不下发到前端，避免被浏览器的抓包/调试工具顺手拿走
            device.setSecret(null);
            return device;
        });
    }

    public IotDevice getById(Long id) {
        IotDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "设备不存在: " + id);
        }
        return device;
    }

    /** 打卡链路按 SN 查设备；未注册返回 null 由调用方决定如何回执 */
    public IotDevice findBySn(String deviceSn) {
        IotDevice device = deviceMapper.selectOne(Wrappers.<IotDevice>lambdaQuery()
                .eq(IotDevice::getDeviceSn, deviceSn)
                .last("LIMIT 1"));
        if (device != null) {
            device.setSecret(null);
            return device;
        }
        return null;
    }

    /** 打卡链路需要密钥做验签，单独取原始记录 */
    public IotDevice findBySnWithSecret(String deviceSn) {
        return deviceMapper.selectOne(Wrappers.<IotDevice>lambdaQuery()
                .eq(IotDevice::getDeviceSn, deviceSn)
                .last("LIMIT 1"));
    }

    @Transactional
    public DeviceWithSecret create(AttendanceDtos.DeviceCreateRequest request) {
        if (deviceMapper.exists(Wrappers.<IotDevice>lambdaQuery()
                .eq(IotDevice::getDeviceSn, request.getDeviceSn()))) {
            throw BizException.of(ErrorCode.CONFLICT, "该设备序列号已注册");
        }
        IotDevice device = new IotDevice();
        device.setDeviceSn(request.getDeviceSn().trim());
        device.setDeviceName(StringUtils.hasText(request.getDeviceName())
                ? request.getDeviceName() : request.getDeviceSn());
        device.setLocation(request.getLocation());
        device.setRemark(request.getRemark());
        device.setSecret(generateSecret());
        device.setStatus("OFFLINE");
        deviceMapper.insert(device);
        log.info("注册设备: sn={}, 名称={}", device.getDeviceSn(), device.getDeviceName());
        // 密钥只在创建/轮换的响应里返回一次，便于现场烧录到设备
        return new DeviceWithSecret(device, device.getSecret());
    }

    @Transactional
    public void update(Long id, AttendanceDtos.DeviceUpdateRequest request) {
        IotDevice device = getById(id);
        if (StringUtils.hasText(request.getDeviceName())) {
            device.setDeviceName(request.getDeviceName());
        }
        if (request.getLocation() != null) {
            device.setLocation(request.getLocation());
        }
        if (request.getRemark() != null) {
            device.setRemark(request.getRemark());
        }
        if (StringUtils.hasText(request.getStatus())) {
            device.setStatus(request.getStatus());
        }
        deviceMapper.updateById(device);
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        deviceMapper.deleteById(id);
    }

    /**
     * 轮换密钥：设备丢失或密钥疑似泄露时使用。
     * 轮换后旧固件会验签失败，需要同步更新设备端配置。
     */
    @Transactional
    public String rotateSecret(Long id) {
        IotDevice device = getById(id);
        String newSecret = generateSecret();
        IotDevice patch = new IotDevice();
        patch.setId(id);
        patch.setSecret(newSecret);
        deviceMapper.updateById(patch);
        log.warn("设备密钥已轮换: sn={}", device.getDeviceSn());
        return newSecret;
    }

    /** 心跳：更新在线状态与固件版本 */
    public boolean touchHeartbeat(String deviceSn, String firmwareVersion, String ipAddress) {
        return deviceMapper.touchHeartbeat(deviceSn, LocalDateTime.now(), firmwareVersion, ipAddress) > 0;
    }

    /** 巡检：把超时未心跳的设备置为离线 */
    public int markOfflineBefore(LocalDateTime deadline) {
        return deviceMapper.markOfflineBefore(deadline);
    }

    public Map<String, Long> statistics() {
        long total = deviceMapper.selectCount(Wrappers.<IotDevice>lambdaQuery());
        long online = deviceMapper.selectCount(Wrappers.<IotDevice>lambdaQuery()
                .eq(IotDevice::getStatus, "ONLINE"));
        long disabled = deviceMapper.selectCount(Wrappers.<IotDevice>lambdaQuery()
                .eq(IotDevice::getStatus, "DISABLED"));
        return Map.of("total", total, "online", online, "disabled", disabled);
    }

    /** 32 字节随机密钥，十六进制 64 字符，满足 HMAC-SHA256 要求 */
    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** 创建设备时一次性返回明文密钥 */
    public record DeviceWithSecret(IotDevice device, String secret) {
    }
}
