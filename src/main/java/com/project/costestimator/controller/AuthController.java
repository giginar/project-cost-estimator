package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.AuthenticationUseCase;
import com.project.costestimator.dto.AuthModels.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationUseCase authentication;

    public AuthController(AuthenticationUseCase authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authentication.login(request);
    }

    @PostMapping("/register")
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return authentication.register(request);
    }

    @GetMapping("/verify")
    public MessageResponse verify(@RequestParam String token) {
        return authentication.verify(token);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authentication.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authentication.resetPassword(request);
    }

    @GetMapping("/me")
    public UserView me(Authentication principal) {
        return authentication.userByEmail(principal.getName());
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authentication.logout(bearerToken(authorization));
    }

    private String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length())
                : null;
    }
}
