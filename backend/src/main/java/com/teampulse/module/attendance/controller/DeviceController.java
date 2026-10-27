package com.teampulse.module.attendance.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.attendance.dto.AttendanceDtos;
import com.teampulse.module.attendance.entity.IotDevice;
import com.teampulse.module.attendance.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.teampulse.module.audit.annotation.Audited;

/**
 * 物联网设备管理接口（仅管理员）。
 *
 * <p>密钥只会在「创建设备」和「轮换密钥」两个响应里出现一次，
 * 其余任何列表/详情接口都不会回传 secret。
 */
@Tag(name = "打卡设备", description = "设备注册、密钥轮换、在线状态")
@RestController
@RequestMapping("/api/v1/devices")
@PreAuthorize("hasAuthority('device:view')")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "设备列表")
    @GetMapping
    public ApiResponse<PageResult<IotDevice>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(deviceService.page(keyword, status, Math.max(1, page),
                Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "设备统计")
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Long>> statistics() {
        return ApiResponse.ok(deviceService.statistics());
    }

    @Operation(summary = "注册设备", description = "响应中的 secret 仅此一次返回，请立即写入设备")
    @PostMapping
    @PreAuthorize("hasAuthority('device:manage')")
    @Audited(module = "device", action = "device.create", detail = "注册打卡设备")
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody AttendanceDtos.DeviceCreateRequest request) {
        DeviceService.DeviceWithSecret created = deviceService.create(request);
        return ApiResponse.ok(Map.of(
                "device", created.device(),
                "secret", created.secret()));
    }

    @Operation(summary = "更新设备信息")
    // 类上是 device:view（只读），但改设备信息是写操作，方法级覆盖为 device:manage
    @PreAuthorize("hasAuthority('device:manage')")
    @PutMapping("/{id}")
    @Audited(module = "device", action = "device.update", detail = "修改设备信息")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody AttendanceDtos.DeviceUpdateRequest request) {
        deviceService.update(id, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "轮换设备密钥", description = "旧密钥立即失效，需要同步更新设备端")
    @PutMapping("/{id}/secret")
    @PreAuthorize("hasAuthority('device:manage')")
    @Audited(module = "device", action = "device.secret.rotate", detail = "轮换设备密钥")
    public ApiResponse<Map<String, String>> rotateSecret(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("secret", deviceService.rotateSecret(id)));
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('device:manage')")
    @Audited(module = "device", action = "device.delete", detail = "删除设备")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ApiResponse.ok();
    }
}
