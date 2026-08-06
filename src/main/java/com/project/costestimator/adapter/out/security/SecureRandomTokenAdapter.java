package com.project.costestimator.adapter.out.security;

import com.project.costestimator.application.port.out.SecureTokenPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public final class SecureRandomTokenAdapter implements SecureTokenPort {
    private static final int TOKEN_BYTES = 32;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
