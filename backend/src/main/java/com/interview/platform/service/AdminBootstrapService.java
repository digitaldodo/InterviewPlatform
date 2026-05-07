package com.interview.platform.service;

import com.interview.platform.model.User;
import com.interview.platform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Component
public class AdminBootstrapService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountIdentityService accountIdentityService;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AccountIdentityService accountIdentityService,
                                 @Value("${app.admin.username:}") String adminUsername,
                                 @Value("${app.admin.email:}") String adminEmail,
                                 @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountIdentityService = accountIdentityService;
        this.adminUsername = adminUsername == null ? "" : adminUsername.trim();
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.findByRole("ADMIN").isEmpty()) {
            return;
        }
        if (adminUsername.isBlank() || adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("Skipping admin bootstrap because ADMIN_USERNAME, ADMIN_EMAIL, or ADMIN_PASSWORD is missing.");
            return;
        }
        if (userRepository.existsByEmail(adminEmail.toLowerCase(Locale.ROOT))) {
            return;
        }

        User admin = new User();
        admin.setDisplayName(accountIdentityService.cleanDisplayName(adminUsername));
        admin.setUsername(accountIdentityService.usernameForRegistration(adminUsername, adminUsername, adminEmail));
        admin.setEmail(adminEmail.toLowerCase(Locale.ROOT));
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRoles(List.of("ADMIN"));
        admin.setRole("ADMIN");
        admin.setActiveWorkspace("ADMIN");
        admin.setIsVerified(true);
        admin.setInterviewerVerified(true);
        admin.setAccountEnabled(true);
        admin.setPublicProfileVisible(false);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);
        log.info("Bootstrapped default admin account for {}", admin.getEmail());
    }
}
