package com.project.costestimator.repository;

import com.project.costestimator.application.port.out.UserRepositoryPort;
import com.project.costestimator.domain.AppUser;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserRepository implements UserRepositoryPort {
    private final Map<UUID, AppUser> users = new ConcurrentHashMap<>();

    @Override
    public AppUser save(AppUser user) {
        users.put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<AppUser> findById(UUID id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    @Override
    public List<AppUser> findAll() {
        return users.values().stream()
                .sorted(Comparator.comparing(AppUser::getFullName))
                .toList();
    }
}
