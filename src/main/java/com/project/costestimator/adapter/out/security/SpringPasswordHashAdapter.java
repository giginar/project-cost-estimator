package com.project.costestimator.adapter.out.security;

import com.project.costestimator.application.port.out.PasswordHashPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public final class SpringPasswordHashAdapter implements PasswordHashPort {
    private final PasswordEncoder encoder;

    public SpringPasswordHashAdapter(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
