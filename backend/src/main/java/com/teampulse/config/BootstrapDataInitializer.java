package com.teampulse.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teampulse.module.attendance.entity.IotDevice;
import com.teampulse.module.attendance.mapper.IotDeviceMapper;
import com.teampulse.module.rbac.entity.Department;
import com.teampulse.module.rbac.mapper.DepartmentMapper;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 首次启动数据初始化。
 *
 * <p>解决旧项目的一个真实痛点：旧版 init.sql 里写的是
 * {@code '$2a$10$placeholderHash'} 这种假散列，导致「按文档建库后根本登不进去」。
 * 现在密码在运行期用 {@link PasswordEncoder} 真实加密生成，脚本里不再出现占位散列。
 *
 * <p>同时预置一台演示设备并打印其密钥，使设备模拟器开箱即可跑通整条链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "teampulse.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapDataInitializer implements ApplicationRunner {

    private static final String DEMO_DEVICE_SN = "TP-ESP32-DEMO";

    private final BootstrapProperties properties;
    private final UserMapper userMapper;
    private final IotDeviceMapper deviceMapper;
    private final DepartmentMapper departmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initAdminUser();
        initDemoDevice();
    }

    private void initAdminUser() {
        Long existing = userMapper.selectCount(Wrappers.<User>lambdaQuery());
        if (existing != null && existing > 0) {
            log.debug("用户表已有数据，跳过管理员初始化");
            return;
        }
        User admin = new User();
        admin.setUsername(properties.getAdminUsername());
        admin.setPassword(passwordEncoder.encode(properties.getAdminPassword()));
        admin.setNickname("系统管理员");
        admin.setEmail(properties.getAdminUsername() + "@teampulse.local");
        admin.setPhone("13800000000");
        admin.setEmployeeNo(properties.getAdminEmployeeNo());
        // 必须是 sysadmin：RBAC 只认 role 表里的角色 key，
        // 写旧的 "admin" 会导致查不到角色、权限为 0，管理员登录后几乎全部接口 403（全新库部署时踩过）。
        admin.setRole("sysadmin");
        admin.setStatus("active");
        admin.setDepartmentId(rootDepartmentId());
        userMapper.insert(admin);

        log.warn("""

                ============================================================
                 已创建初始管理员账号，请登录后立即修改密码
                   用户名: {}
                   密码  : {}
                   工号  : {}（打卡机上报此工号）
                ============================================================
                """, admin.getUsername(), properties.getAdminPassword(), admin.getEmployeeNo());
    }

    /**
     * 根部门（总经办）的 id；部门表还没种子时返回 null，不让初始化失败。
     * 管理员挂在根部门下，用户列表与数据范围展示才不会出现空部门。
     */
    private Long rootDepartmentId() {
        Department root = departmentMapper.selectOne(Wrappers.<Department>lambdaQuery()
                .eq(Department::getDeptCode, "ROOT")
                .last("LIMIT 1"));
        return root == null ? null : root.getDepartmentId();
    }

    private void initDemoDevice() {
        Long count = deviceMapper.selectCount(Wrappers.<IotDevice>lambdaQuery());
        if (count != null && count > 0) {
            return;
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String secret = HexFormat.of().formatHex(bytes);

        IotDevice device = new IotDevice();
        device.setDeviceSn(DEMO_DEVICE_SN);
        device.setDeviceName("演示打卡机（ESP32）");
        device.setLocation("一楼大厅");
        device.setSecret(secret);
        device.setStatus("OFFLINE");
        device.setRemark("系统初始化自动创建，可用于设备模拟器联调");
        deviceMapper.insert(device);

        log.warn("""

                ============================================================
                 已创建演示打卡设备（仅用于联调，生产请删除）
                   设备序列号: {}
                   设备密钥  : {}
                 把这两项填进 device/simulator/config.json 即可模拟上报
                ============================================================
                """, DEMO_DEVICE_SN, secret);
    }
}
