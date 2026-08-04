package com.project.costestimator.repository;

import com.project.costestimator.domain.AppUser;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserRepository {
    private final Map<UUID, AppUser> users = new ConcurrentHashMap<>();

    public AppUser save(AppUser user) { users.put(user.getId(), user); return user; }
    public Optional<AppUser> findById(UUID id) { return Optional.ofNullable(users.get(id)); }
    public Optional<AppUser> findByEmail(String email) { return users.values().stream().filter(user -> user.getEmail().equalsIgnoreCase(email)).findFirst(); }
    public List<AppUser> findAll() { return users.values().stream().sorted(Comparator.comparing(AppUser::getFullName)).toList(); }
}
