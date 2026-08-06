package com.project.costestimator.repository;

import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.domain.Project;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProjectRepository implements ProjectRepositoryPort {
    private final Map<UUID, Project> projects = new ConcurrentHashMap<>();

    @Override
    public Project save(Project project) {
        projects.put(project.getId(), project);
        return project;
    }

    @Override
    public Optional<Project> findById(UUID id) {
        return Optional.ofNullable(projects.get(id));
    }

    @Override
    public List<Project> findAll() {
        return projects.values().stream()
                .sorted(Comparator.comparing(Project::getCode))
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        projects.remove(id);
    }
}
