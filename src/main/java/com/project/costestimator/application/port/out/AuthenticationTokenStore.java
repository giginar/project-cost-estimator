package com.project.costestimator.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthenticationTokenStore {
    record Token(UUID userId, Instant expiresAt) {}

    void saveSession(String tokenDigest, Token token);
    Optional<Token> findSession(String tokenDigest);
    void deleteSession(String tokenDigest);
    void deleteSessionsForUser(UUID userId);

    void saveVerification(String tokenDigest, Token token);
    Optional<Token> consumeVerification(String tokenDigest);

    void savePasswordReset(String tokenDigest, Token token);
    Optional<Token> consumePasswordReset(String tokenDigest);
    void deletePasswordResetsForUser(UUID userId);

    Optional<Instant> replacePasswordResetRequestTime(String normalizedEmail, Instant requestedAt);
}
