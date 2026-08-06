package com.project.costestimator.application.port.in;

import com.project.costestimator.domain.AppUser;
import com.project.costestimator.dto.AuthModels.*;

public interface AuthenticationUseCase {
    MessageResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    MessageResponse verify(String rawToken);
    MessageResponse forgotPassword(ForgotPasswordRequest request);
    MessageResponse resetPassword(ResetPasswordRequest request);
    AppUser authenticateToken(String rawToken);
    void logout(String rawToken);
    UserView userByEmail(String email);
}
