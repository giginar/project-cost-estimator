package com.project.costestimator.application.service.support;

import com.project.costestimator.application.port.out.ResourceRepositoryPort;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.exception.NotFoundException;

import java.util.UUID;

public final class ResourceFinder {
    private final ResourceRepositoryPort resources;

    public ResourceFinder(ResourceRepositoryPort resources) {
        this.resources = resources;
    }

    public <T extends Resource> T require(UUID resourceId, Class<T> expectedType) {
        Resource resource = resources.findById(resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found: " + resourceId));
        if (!expectedType.isInstance(resource)) {
            throw new IllegalArgumentException(
                    "Resource " + resourceId + " must be " + expectedType.getSimpleName());
        }
        return expectedType.cast(resource);
    }

    public <T extends Resource> T requireAvailable(UUID resourceId, UUID projectId, Class<T> expectedType) {
        T resource = require(resourceId, expectedType);
        if (!isAvailable(resource, projectId)) {
            throw new IllegalArgumentException("Resource is not available to this project");
        }
        return resource;
    }

    public boolean isAvailable(Resource resource, UUID projectId) {
        return resource.isShared() || projectId.equals(resource.getOwnerProjectId());
    }
}
