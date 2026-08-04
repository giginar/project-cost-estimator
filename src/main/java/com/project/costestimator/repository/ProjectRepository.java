package com.project.costestimator.repository;

import com.project.costestimator.domain.Project;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProjectRepository {
    private final Map<UUID, Project> projects = new ConcurrentHashMap<>();
    public Project save(Project project) { projects.put(project.getId(), project); return project; }
    public Optional<Project> findById(UUID id) { return Optional.ofNullable(projects.get(id)); }
    public List<Project> findAll() { return projects.values().stream().sorted(Comparator.comparing(Project::getCode)).toList(); }
    public void deleteById(UUID id) { projects.remove(id); }
}
