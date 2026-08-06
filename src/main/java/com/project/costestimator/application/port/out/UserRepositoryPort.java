package com.project.costestimator.application.port.out;

import com.project.costestimator.domain.AppUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    AppUser save(AppUser user);
    Optional<AppUser> findById(UUID userId);
    Optional<AppUser> findByEmail(String email);
    List<AppUser> findAll();
}
