package com.project.costestimator.config;

import com.project.costestimator.domain.enums.UserRole;
import com.project.costestimator.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthDataInitializer implements ApplicationRunner {
    private final AuthService auth;

    @Override
    public void run(ApplicationArguments args) {
        auth.createVerifiedSeed("Demo Engineer", "engineer@example.com", "Engineer123!", UserRole.ENGINEER);
        auth.createVerifiedSeed("Demo Manager", "manager@example.com", "Manager123!", UserRole.MANAGER);
        auth.createVerifiedSeed("System Administrator", "admin@example.com", "Admin123!", UserRole.ADMIN);
    }
}
