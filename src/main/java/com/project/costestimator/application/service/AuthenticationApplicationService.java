package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.AuthenticationUseCase;
import com.project.costestimator.application.port.in.UserAdministrationUseCase;
import com.project.costestimator.application.port.out.*;
import com.project.costestimator.domain.AppUser;
import com.project.costestimator.domain.enums.UserRole;
import com.project.costestimator.dto.AuthModels.*;
import com.project.costestimator.exception.NotFoundException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public final class AuthenticationApplicationService
        implements AuthenticationUseCase, UserAdministrationUseCase {
    private static final Duration SESSION_LIFETIME = Duration.ofHours(8);
    private static final Duration VERIFICATION_LIFETIME = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_LIFETIME = Duration.ofMinutes(30);
    private static final Duration PASSWORD_RESET_THROTTLE = Duration.ofSeconds(60);
    private static final String RESET_RESPONSE =
            "If an active verified account exists for that email, a password reset link has been sent.";

    private final UserRepositoryPort users;
    private final PasswordHashPort passwords;
    private final SecureTokenPort secureTokens;
    private final AuthenticationTokenStore tokenStore;
    private final MailDeliveryPort mail;
    private final Clock clock;

    public AuthenticationApplicationService(UserRepositoryPort users,
                                            PasswordHashPort passwords,
                                            SecureTokenPort secureTokens,
                                            AuthenticationTokenStore tokenStore,
                                            MailDeliveryPort mail,
                                            Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.secureTokens = secureTokens;
        this.tokenStore = tokenStore;
        this.mail = mail;
        this.clock = clock;
    }

    @Override
    public MessageResponse register(RegisterRequest request) {
        createUnverified(request.fullName(), request.email(), request.password(), UserRole.MANAGER);
        return new MessageResponse("Registration received. Check your email to verify the account.");
    }

    @Override
    public UserView createUser(AdminCreateUserRequest request) {
        return toView(createUnverified(
                request.fullName(), request.email(), request.password(), request.role()));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(normalize(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        ensureCanSignIn(user);

        String rawToken = secureTokens.generate();
        Instant expiresAt = clock.instant().plus(SESSION_LIFETIME);
        tokenStore.saveSession(digest(rawToken), new AuthenticationTokenStore.Token(user.getId(), expiresAt));
        return new AuthResponse(rawToken, toView(user));
    }

    @Override
    public MessageResponse verify(String rawToken) {
        AuthenticationTokenStore.Token token = tokenStore.consumeVerification(digest(rawToken))
                .orElseThrow(this::invalidVerificationToken);
        if (token.expiresAt().isBefore(clock.instant())) {
            throw invalidVerificationToken();
        }
        AppUser user = requireUser(token.userId());
        user.setEmailVerified(true);
        users.save(user);
        return new MessageResponse("Email verified. You can now sign in.");
    }

    @Override
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalize(request.email());
        Instant now = clock.instant();
        boolean throttled = tokenStore.replacePasswordResetRequestTime(email, now)
                .filter(previous -> previous.isAfter(now.minus(PASSWORD_RESET_THROTTLE)))
                .isPresent();
        if (throttled) {
            return new MessageResponse(RESET_RESPONSE);
        }

        users.findByEmail(email)
                .filter(AppUser::isActive)
                .filter(AppUser::isEmailVerified)
                .ifPresent(this::sendPasswordReset);
        return new MessageResponse(RESET_RESPONSE);
    }

    @Override
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        AuthenticationTokenStore.Token token = tokenStore.consumePasswordReset(digest(request.token()))
                .orElseThrow(this::invalidPasswordResetToken);
        if (token.expiresAt().isBefore(clock.instant())) {
            throw invalidPasswordResetToken();
        }

        AppUser user = requireUser(token.userId());
        user.setPasswordHash(passwords.hash(request.newPassword()));
        users.save(user);
        tokenStore.deleteSessionsForUser(user.getId());
        tokenStore.deletePasswordResetsForUser(user.getId());
        return new MessageResponse("Password updated. Sign in with your new password.");
    }

    @Override
    public AppUser authenticateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        String tokenDigest = digest(rawToken);
        AuthenticationTokenStore.Token token = tokenStore.findSession(tokenDigest).orElse(null);
        if (token == null) {
            return null;
        }
        if (token.expiresAt().isBefore(clock.instant())) {
            tokenStore.deleteSession(tokenDigest);
            return null;
        }
        return users.findById(token.userId())
                .filter(AppUser::isActive)
                .filter(AppUser::isEmailVerified)
                .orElse(null);
    }

    @Override
    public void logout(String rawToken) {
        if (rawToken != null) {
            tokenStore.deleteSession(digest(rawToken));
        }
    }

    @Override
    public UserView userByEmail(String email) {
        return toView(users.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found")));
    }

    @Override
    public UserList listUsers() {
        return new UserList(users.findAll().stream().map(this::toView).toList());
    }

    @Override
    public AppUser createVerifiedSeed(String fullName, String email, String password, UserRole role) {
        return users.findByEmail(normalize(email)).orElseGet(() -> {
            AppUser user = newUser(fullName, email, password, role);
            user.setEmailVerified(true);
            return users.save(user);
        });
    }

    private AppUser createUnverified(String fullName, String email, String password, UserRole role) {
        String normalizedEmail = normalize(email);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        AppUser user = users.save(newUser(fullName, normalizedEmail, password, role));
        String rawToken = secureTokens.generate();
        tokenStore.saveVerification(
                digest(rawToken),
                new AuthenticationTokenStore.Token(
                        user.getId(), clock.instant().plus(VERIFICATION_LIFETIME)));
        mail.sendVerification(user, rawToken);
        return user;
    }

    private AppUser newUser(String fullName, String email, String password, UserRole role) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setFullName(fullName.trim());
        user.setEmail(normalize(email));
        user.setPasswordHash(passwords.hash(password));
        user.setRole(role);
        user.setActive(true);
        user.setCreatedAt(clock.instant());
        return user;
    }

    private void sendPasswordReset(AppUser user) {
        tokenStore.deletePasswordResetsForUser(user.getId());
        String rawToken = secureTokens.generate();
        tokenStore.savePasswordReset(
                digest(rawToken),
                new AuthenticationTokenStore.Token(
                        user.getId(), clock.instant().plus(PASSWORD_RESET_LIFETIME)));
        mail.sendPasswordReset(user, rawToken);
    }

    private void ensureCanSignIn(AppUser user) {
        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Verify your email before signing in");
        }
        if (!user.isActive()) {
            throw new IllegalStateException("This account is inactive");
        }
    }

    private AppUser requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserView toView(AppUser user) {
        return new UserView(
                user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                user.isEmailVerified(), user.isActive());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String digest(String rawToken) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private IllegalArgumentException invalidCredentials() {
        return new IllegalArgumentException("Invalid email or password");
    }

    private IllegalArgumentException invalidVerificationToken() {
        return new IllegalArgumentException("Verification link is invalid or expired");
    }

    private IllegalArgumentException invalidPasswordResetToken() {
        return new IllegalArgumentException("Password reset link is invalid or expired");
    }
}
