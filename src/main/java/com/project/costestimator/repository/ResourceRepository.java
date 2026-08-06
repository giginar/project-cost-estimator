package com.project.costestimator.repository;

import com.project.costestimator.application.port.out.ResourceRepositoryPort;
import com.project.costestimator.domain.Resource;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ResourceRepository implements ResourceRepositoryPort {
    private final Map<UUID, Resource> resources = new ConcurrentHashMap<>();

    @Override
    public <T extends Resource> T save(T resource) {
        resources.put(resource.getId(), resource);
        return resource;
    }

    @Override
    public Optional<Resource> findById(UUID id) {
        return Optional.ofNullable(resources.get(id));
    }

    @Override
    public List<Resource> findAll() {
        return resources.values().stream()
                .sorted(Comparator.comparing(Resource::getCode))
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        resources.remove(id);
    }
}
