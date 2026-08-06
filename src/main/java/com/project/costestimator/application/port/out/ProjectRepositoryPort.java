package com.project.costestimator.application.port.out;

import com.project.costestimator.domain.Project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepositoryPort {
    Project save(Project project);
    Optional<Project> findById(UUID projectId);
    List<Project> findAll();
    void deleteById(UUID projectId);
}
