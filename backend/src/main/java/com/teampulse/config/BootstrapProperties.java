package com.teampulse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 首次启动初始化参数。
 *
 * <p>这些值只在「数据库里还没有用户」时生效，生产环境应通过环境变量覆盖默认密码，
 * 或者把 {@code teampulse.bootstrap.enabled} 设为 false 后手工建号。
 */
@Data
@Component
@ConfigurationProperties(prefix = "teampulse.bootstrap")
public class BootstrapProperties {

    private boolean enabled = true;

    private String adminUsername = "admin";

    private String adminPassword = "admin123";

    private String adminEmployeeNo = "E1000";
}
