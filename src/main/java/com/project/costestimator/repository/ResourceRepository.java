package com.project.costestimator.repository;

import com.project.costestimator.domain.Resource;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ResourceRepository {
    private final Map<UUID, Resource> resources = new ConcurrentHashMap<>();
    public <T extends Resource> T save(T resource) { resources.put(resource.getId(), resource); return resource; }
    public Optional<Resource> findById(UUID id) { return Optional.ofNullable(resources.get(id)); }
    public List<Resource> findAll() { return resources.values().stream().sorted(Comparator.comparing(Resource::getCode)).toList(); }
    public void deleteById(UUID id) { resources.remove(id); }
}
