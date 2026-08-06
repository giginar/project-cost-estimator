package com.project.costestimator.adapter.out.security;

import com.project.costestimator.application.port.out.AuthenticationTokenStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class InMemoryAuthenticationTokenStore implements AuthenticationTokenStore {
    private final Map<String, Token> sessions = new ConcurrentHashMap<>();
    private final Map<String, Token> verifications = new ConcurrentHashMap<>();
    private final Map<String, Token> passwordResets = new ConcurrentHashMap<>();
    private final Map<String, Instant> passwordResetRequests = new ConcurrentHashMap<>();

    @Override
    public void saveSession(String tokenDigest, Token token) {
        sessions.put(tokenDigest, token);
    }

    @Override
    public Optional<Token> findSession(String tokenDigest) {
        return Optional.ofNullable(sessions.get(tokenDigest));
    }

    @Override
    public void deleteSession(String tokenDigest) {
        sessions.remove(tokenDigest);
    }

    @Override
    public void deleteSessionsForUser(UUID userId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
    }

    @Override
    public void saveVerification(String tokenDigest, Token token) {
        verifications.put(tokenDigest, token);
    }

    @Override
    public Optional<Token> consumeVerification(String tokenDigest) {
        return Optional.ofNullable(verifications.remove(tokenDigest));
    }

    @Override
    public void savePasswordReset(String tokenDigest, Token token) {
        passwordResets.put(tokenDigest, token);
    }

    @Override
    public Optional<Token> consumePasswordReset(String tokenDigest) {
        return Optional.ofNullable(passwordResets.remove(tokenDigest));
    }

    @Override
    public void deletePasswordResetsForUser(UUID userId) {
        passwordResets.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
    }

    @Override
    public Optional<Instant> replacePasswordResetRequestTime(String normalizedEmail, Instant requestedAt) {
        return Optional.ofNullable(passwordResetRequests.put(normalizedEmail, requestedAt));
    }
}
