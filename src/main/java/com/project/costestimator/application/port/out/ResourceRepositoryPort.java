package com.project.costestimator.application.port.out;

import com.project.costestimator.domain.Resource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepositoryPort {
    <T extends Resource> T save(T resource);
    Optional<Resource> findById(UUID resourceId);
    List<Resource> findAll();
    void deleteById(UUID resourceId);
}
