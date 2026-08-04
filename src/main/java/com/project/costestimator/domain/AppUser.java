package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class AppUser {
    private UUID id;
    private String fullName;
    private String email;
    private String passwordHash;
    private UserRole role;
    private boolean emailVerified;
    private boolean active;
    private Instant createdAt;
}
