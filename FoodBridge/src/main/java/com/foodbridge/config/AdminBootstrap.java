package com.foodbridge.config;

import com.foodbridge.enums.Role;
import com.foodbridge.repository.UserRepository;
import com.foodbridge.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Optional: creates the FIRST administrator from environment variables ADMIN_EMAIL and ADMIN_PASSWORD.
 * Nothing is created when they are not set, and no fixed default password exists.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final UserService userService;

    @Value("${foodbridge.admin.email:}")
    private String email;

    @Value("${foodbridge.admin.password:}")
    private String password;

    public AdminBootstrap(UserRepository users, UserService userService) {
        this.users = users;
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (password.length() < 10) {
            log.warn("ADMIN_PASSWORD is shorter than 10 characters: admin account NOT created.");
            return;
        }
        if (users.countByRole(Role.ADMIN) > 0 || users.existsByEmailIgnoreCase(email)) {
            return;
        }
        userService.createAdmin(email, "Platform Administrator", password);
        log.info("Administrator account created for {}", email);
    }
}
