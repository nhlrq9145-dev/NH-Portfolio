package com.nh.customermanager.config;

import com.nh.customermanager.entity.AdminUser;
import com.nh.customermanager.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AdminUserInitializer.class);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminUserInitializer(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            Environment environment
    ) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = readUsername();
        String rawPassword = readPassword();

        AdminUser adminUser = adminUserRepository
                .findByUsernameIgnoreCase(username)
                .orElse(null);

        if (adminUser == null) {
            adminUserRepository.save(new AdminUser(
                    username,
                    passwordEncoder.encode(rawPassword)
            ));

            LOGGER.info(
                    "预置管理员账号已创建，用户名：{}",
                    username
            );
            return;
        }

        if (!passwordEncoder.matches(
                rawPassword,
                adminUser.getPasswordHash()
        )) {
            adminUser.changePasswordHash(
                    passwordEncoder.encode(rawPassword)
            );
            adminUserRepository.save(adminUser);

            LOGGER.info(
                    "预置管理员密码哈希已更新，用户名：{}",
                    username
            );
            return;
        }

        LOGGER.info(
                "预置管理员账号已存在，用户名：{}",
                username
        );
    }

    private String readUsername() {
        String username = environment.getProperty(
                "ADMIN_USERNAME",
                "admin"
        );

        String normalizedUsername = username == null
                ? ""
                : username.trim().toLowerCase(Locale.ROOT);

        if (normalizedUsername.length() < 3
                || normalizedUsername.length() > 50) {
            throw new IllegalStateException(
                    "ADMIN_USERNAME 长度必须为 3～50 个字符"
            );
        }

        return normalizedUsername;
    }

    private String readPassword() {
        String rawPassword = environment.getProperty(
                "ADMIN_PASSWORD"
        );

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalStateException(
                    "未配置 ADMIN_PASSWORD，无法创建预置管理员"
            );
        }

        if (rawPassword.length() < 10) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD 至少需要 10 个字符"
            );
        }

        return rawPassword;
    }
}