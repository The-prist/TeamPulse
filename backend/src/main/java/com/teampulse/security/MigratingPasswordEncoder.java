package com.teampulse.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 兼容历史明文密码的密码编码器。
 *
 * <p>老系统把密码以明文存在 user.password 里，直接切换到 BCrypt 会导致所有存量账号失效。
 * 这里采用「读时兼容、写时升级」策略：
 * <ol>
 *   <li>{@link #matches} 同时支持 BCrypt 与明文校验；</li>
 *   <li>{@link #upgradeEncoding} 对明文返回 true；</li>
 *   <li>Spring Security 的 {@code DaoAuthenticationProvider} 在登录成功后调用
 *       {@code UserDetailsPasswordService.updatePassword} 完成落库升级。</li>
 * </ol>
 * 因此用户无需做任何操作，下次登录即自动迁移为 BCrypt。
 */
@Component
@RequiredArgsConstructor
public class MigratingPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    private static boolean isBcrypt(String encoded) {
        return encoded != null
                && (encoded.startsWith("$2a$") || encoded.startsWith("$2b$") || encoded.startsWith("$2y$"));
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        if (isBcrypt(encodedPassword)) {
            return delegate.matches(rawPassword, encodedPassword);
        }
        // 历史明文密码：定长比较交给 BCrypt 的常量时间实现不适用，这里直接等值比较
        return rawPassword.toString().equals(encodedPassword);
    }

    /** 明文密码需要升级为 BCrypt */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return !isBcrypt(encodedPassword);
    }
}
