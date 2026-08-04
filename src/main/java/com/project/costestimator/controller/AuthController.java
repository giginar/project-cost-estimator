package com.project.costestimator.controller;

import com.project.costestimator.dto.AuthModels.*;
import com.project.costestimator.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }
    @PostMapping("/register") public MessageResponse register(@Valid @RequestBody RegisterRequest request) { return auth.register(request); }
    @GetMapping("/verify") public MessageResponse verify(@RequestParam String token) { return auth.verify(token); }
    @PostMapping("/forgot-password") public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) { return auth.forgotPassword(request); }
    @PostMapping("/reset-password") public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) { return auth.resetPassword(request); }
    @GetMapping("/me") public UserView me(Authentication authentication) { return auth.userByEmail(authentication.getName()); }
    @PostMapping("/logout") public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null);
    }
}
