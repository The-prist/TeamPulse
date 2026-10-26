package com.teampulse.module.attendance.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.module.attendance.dto.DeviceMessages;
import com.teampulse.module.attendance.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备 HTTP 兜底接入（{@code /iot/**} 在 SecurityConfig 中已放行）。
 *
 * <p>适用场景：
 * <ul>
 *   <li>现场调试：用 curl / Postman 直接模拟一条打卡，不必搭 MQTT 客户端</li>
 *   <li>降级通道：MQTT broker 故障时，设备可切换到 HTTP 上报</li>
 * </ul>
 *
 * <p>安全模型与 MQTT 完全一致——仍然强制 HMAC 签名与时钟漂移检查，
 * 「放行」指的是不需要用户 JWT，而不是不校验身份。
 */
@Tag(name = "设备接入（HTTP 兜底）")
@RestController
@RequestMapping("/iot/v1")
@RequiredArgsConstructor
public class IotIngestController {

    private final AttendanceService attendanceService;

    @Operation(summary = "HTTP 上报打卡")
    @PostMapping("/punch")
    public ApiResponse<DeviceMessages.AckMessage> punch(@RequestBody DeviceMessages.PunchMessage message) {
        return ApiResponse.ok(attendanceService.handlePunch(message, "HTTP"));
    }

    @Operation(summary = "HTTP 上报心跳")
    @PostMapping("/heartbeat")
    public ApiResponse<Void> heartbeat(@RequestBody DeviceMessages.HeartbeatMessage message) {
        attendanceService.handleHeartbeat(message);
        return ApiResponse.ok();
    }
}
