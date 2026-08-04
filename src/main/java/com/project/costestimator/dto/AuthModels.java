package com.project.costestimator.dto;

import com.project.costestimator.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class AuthModels {
    private AuthModels() {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record RegisterRequest(@NotBlank String fullName, @Email @NotBlank String email, @Size(min = 10) String password) {}
    public record ForgotPasswordRequest(@Email @NotBlank String email) {}
    public record ResetPasswordRequest(@NotBlank String token, @Size(min = 10) String newPassword) {}
    public record AdminCreateUserRequest(@NotBlank String fullName, @Email @NotBlank String email,
                                         @Size(min = 10) String password, @NotNull UserRole role) {}
    public record UserView(UUID id, String fullName, String email, UserRole role, boolean emailVerified, boolean active) {}
    public record AuthResponse(String accessToken, UserView user) {}
    public record MessageResponse(String message) {}
    public record MailOutboxView(String recipient, String subject, String verificationUrl) {}
    public record UserList(List<UserView> users) {}
}
