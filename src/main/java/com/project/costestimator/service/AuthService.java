package com.project.costestimator.service;

import com.project.costestimator.domain.AppUser;
import com.project.costestimator.domain.enums.UserRole;
import com.project.costestimator.dto.AuthModels.*;
import com.project.costestimator.exception.NotFoundException;
import com.project.costestimator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final VerificationMailService mailService;
    private final Map<String, ExpiringUserToken> sessions = new ConcurrentHashMap<>();
    private final Map<String, ExpiringUserToken> verificationTokens = new ConcurrentHashMap<>();
    private final Map<String, ExpiringUserToken> passwordResetTokens = new ConcurrentHashMap<>();
    private final Map<String, Instant> passwordResetRequests = new ConcurrentHashMap<>();

    public MessageResponse register(RegisterRequest request) {
        createUnverified(request.fullName(), request.email(), request.password(), UserRole.MANAGER);
        return new MessageResponse("Registration received. Check your email to verify the account.");
    }

    public UserView createUser(AdminCreateUserRequest request) {
        return view(createUnverified(request.fullName(), request.email(), request.password(), request.role()));
    }

    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(normalize(request.email()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()))
            throw new IllegalArgumentException("Invalid email or password");
        if (!user.isEmailVerified()) throw new IllegalStateException("Verify your email before signing in");
        if (!user.isActive()) throw new IllegalStateException("This account is inactive");
        String rawToken = randomToken();
        sessions.put(digest(rawToken), new ExpiringUserToken(user.getId(), Instant.now().plus(8, ChronoUnit.HOURS)));
        return new AuthResponse(rawToken, view(user));
    }

    public MessageResponse verify(String rawToken) {
        ExpiringUserToken token = verificationTokens.remove(digest(rawToken));
        if (token == null || token.expiresAt().isBefore(Instant.now())) throw new IllegalArgumentException("Verification link is invalid or expired");
        AppUser user = users.findById(token.userId()).orElseThrow(() -> new NotFoundException("User not found"));
        user.setEmailVerified(true); users.save(user);
        return new MessageResponse("Email verified. You can now sign in.");
    }

    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalize(request.email()); Instant now = Instant.now();
        Instant previousRequest = passwordResetRequests.put(email, now);
        if (previousRequest != null && previousRequest.isAfter(now.minus(60, ChronoUnit.SECONDS)))
            return new MessageResponse("If an active verified account exists for that email, a password reset link has been sent.");
        users.findByEmail(email).filter(AppUser::isActive).filter(AppUser::isEmailVerified).ifPresent(user -> {
            passwordResetTokens.entrySet().removeIf(entry -> entry.getValue().userId().equals(user.getId()));
            String rawToken = randomToken();
            passwordResetTokens.put(digest(rawToken), new ExpiringUserToken(user.getId(), Instant.now().plus(30, ChronoUnit.MINUTES)));
            mailService.sendPasswordReset(user, rawToken);
        });
        return new MessageResponse("If an active verified account exists for that email, a password reset link has been sent.");
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        ExpiringUserToken token = passwordResetTokens.remove(digest(request.token()));
        if (token == null || token.expiresAt().isBefore(Instant.now())) throw new IllegalArgumentException("Password reset link is invalid or expired");
        AppUser user = users.findById(token.userId()).orElseThrow(() -> new NotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword())); users.save(user);
        sessions.entrySet().removeIf(entry -> entry.getValue().userId().equals(user.getId()));
        passwordResetTokens.entrySet().removeIf(entry -> entry.getValue().userId().equals(user.getId()));
        return new MessageResponse("Password updated. Sign in with your new password.");
    }

    public AppUser authenticateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        ExpiringUserToken token = sessions.get(digest(rawToken));
        if (token == null || token.expiresAt().isBefore(Instant.now())) {
            if (token != null) sessions.remove(digest(rawToken));
            return null;
        }
        return users.findById(token.userId()).filter(AppUser::isActive).filter(AppUser::isEmailVerified).orElse(null);
    }

    public void logout(String rawToken) { if (rawToken != null) sessions.remove(digest(rawToken)); }
    public UserView userByEmail(String email) { return view(users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"))); }
    public UserList listUsers() { return new UserList(users.findAll().stream().map(this::view).toList()); }

    public AppUser createVerifiedSeed(String fullName, String email, String password, UserRole role) {
        return users.findByEmail(normalize(email)).orElseGet(() -> {
            AppUser user = newUser(fullName, email, password, role); user.setEmailVerified(true); return users.save(user);
        });
    }

    private AppUser createUnverified(String fullName, String email, String password, UserRole role) {
        String normalized = normalize(email);
        if (users.findByEmail(normalized).isPresent()) throw new IllegalArgumentException("An account with this email already exists");
        AppUser user = users.save(newUser(fullName, normalized, password, role));
        String rawToken = randomToken();
        verificationTokens.put(digest(rawToken), new ExpiringUserToken(user.getId(), Instant.now().plus(24, ChronoUnit.HOURS)));
        mailService.sendVerification(user, rawToken);
        return user;
    }

    private AppUser newUser(String fullName, String email, String password, UserRole role) {
        var user = new AppUser(); user.setId(UUID.randomUUID()); user.setFullName(fullName.trim()); user.setEmail(normalize(email));
        user.setPasswordHash(passwordEncoder.encode(password)); user.setRole(role); user.setActive(true); user.setCreatedAt(Instant.now());
        return user;
    }

    private UserView view(AppUser user) { return new UserView(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isEmailVerified(), user.isActive()); }
    private String normalize(String email) { return email.trim().toLowerCase(java.util.Locale.ROOT); }
    private String randomToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String digest(String rawToken) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private record ExpiringUserToken(UUID userId, Instant expiresAt) {}
}
