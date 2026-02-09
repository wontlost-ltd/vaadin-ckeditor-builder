package com.wontlost.ckeditor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.regex.Matcher;

/**
 * 管理员服务
 * 处理密码修改等管理功能
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final InMemoryUserDetailsManager userDetailsManager;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.username:admin}")
    private String adminUsername;

    // 当前密码的 BCrypt 编码（内存中）
    private String currentEncodedPassword;

    public AdminService(InMemoryUserDetailsManager userDetailsManager,
                        PasswordEncoder passwordEncoder,
                        @Value("${admin.password:}") String initialPassword) {
        this.userDetailsManager = userDetailsManager;
        this.passwordEncoder = passwordEncoder;
        this.currentEncodedPassword = passwordEncoder.encode(initialPassword);
    }

    /**
     * 修改管理员密码
     * @param oldPassword 当前密码
     * @param newPassword 新密码
     * @return 是否成功
     */
    public boolean changePassword(String oldPassword, String newPassword) {
        // 验证当前密码（使用 BCrypt 匹配）
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, currentEncodedPassword)) {
            log.warn("密码修改失败：当前密码错误");
            return false;
        }

        try {
            // 更新内存中的用户（encode 一次，复用编码结果）
            String newEncodedPassword = passwordEncoder.encode(newPassword);
            UserDetails updatedUser = User.builder()
                .username(adminUsername)
                .password(newEncodedPassword)
                .roles("ADMIN")
                .build();

            userDetailsManager.updateUser(updatedUser);
            currentEncodedPassword = newEncodedPassword;

            // 尝试持久化到 .env 文件
            persistPasswordToEnv(newPassword);

            log.info("管理员密码修改成功");
            return true;
        } catch (Exception e) {
            log.error("密码修改失败", e);
            return false;
        }
    }

    /**
     * 将新密码持久化到 .env 文件
     */
    private void persistPasswordToEnv(String newPassword) {
        // 验证密码不含换行和特殊 .env 字符
        if (newPassword.contains("\n") || newPassword.contains("\r")) {
            log.warn("密码包含换行符，跳过 .env 持久化");
            return;
        }
        String safePassword = Matcher.quoteReplacement(newPassword);
        try {
            Path envPath = Path.of(".env");
            String content;

            if (Files.exists(envPath)) {
                content = Files.readString(envPath);
                // 替换或添加 ADMIN_PASSWORD
                if (content.contains("ADMIN_PASSWORD=")) {
                    content = content.replaceFirst("ADMIN_PASSWORD=.*", "ADMIN_PASSWORD=" + safePassword);
                } else {
                    content = content + "\nADMIN_PASSWORD=" + safePassword;
                }
            } else {
                content = "ADMIN_PASSWORD=" + safePassword + "\n";
            }

            Files.writeString(envPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // 限制 .env 文件权限为仅所有者可读写（600）
            try {
                Files.setPosixFilePermissions(envPath, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException e) {
                // Windows 等不支持 POSIX 权限的平台，忽略
                log.debug("当前平台不支持 POSIX 文件权限设置");
            }

            log.info("密码已保存到 .env 文件");
        } catch (IOException e) {
            log.warn("无法将密码保存到 .env 文件: {}", e.getMessage());
        }
    }

    /**
     * 获取当前登录的用户名
     */
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "anonymous";
    }
}
