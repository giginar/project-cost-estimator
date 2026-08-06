package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.AssignmentUseCase;
import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.application.service.support.ProjectFinder;
import com.project.costestimator.application.service.support.ProjectViewMapper;
import com.project.costestimator.application.service.support.ResourceFinder;
import com.project.costestimator.application.service.support.ResourceRateSynchronizer;
import com.project.costestimator.domain.*;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.domain.enums.PersonnelAssignmentType;
import com.project.costestimator.domain.service.ActivitySchedulingPolicy;
import com.project.costestimator.domain.service.DateRangePolicy;
import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.exception.NotFoundException;

import java.time.LocalDate;
import java.util.UUID;

public final class AssignmentApplicationService implements AssignmentUseCase {
    private final ProjectRepositoryPort projects;
    private final ProjectFinder projectFinder;
    private final ResourceFinder resourceFinder;
    private final ProjectViewMapper views;
    private final ResourceRateSynchronizer rates;
    private final DateRangePolicy dateRanges;
    private final ActivitySchedulingPolicy scheduling;

    public AssignmentApplicationService(ProjectRepositoryPort projects,
                                        ProjectFinder projectFinder,
                                        ResourceFinder resourceFinder,
                                        ProjectViewMapper views,
                                        ResourceRateSynchronizer rates,
                                        DateRangePolicy dateRanges,
                                        ActivitySchedulingPolicy scheduling) {
        this.projects = projects;
        this.projectFinder = projectFinder;
        this.resourceFinder = resourceFinder;
        this.views = views;
        this.rates = rates;
        this.dateRanges = dateRanges;
        this.scheduling = scheduling;
    }

    @Override
    public AssignmentView assignResource(UUID projectId, UUID estimateId, UUID activityId,
                                         AssignmentRequest request) {
        EstimateVersion estimate = projectFinder.requireEstimate(projectId, estimateId);
        Activity activity = projectFinder.requireActivity(estimate, activityId);
        Resource resource = resourceFinder.requireAvailable(request.resourceId(), projectId, Resource.class);
        ensureNotAlreadyAssigned(activity, resource);

        ResourceAssignment assignment = newAssignment(resource);
        assignment.setId(UUID.randomUUID());
        assignment.setActivity(activity);
        applyValues(assignment, request, activity);
        activity.getResourceAssignments().add(assignment);
        rates.synchronize(estimate, resource, false);
        projects.save(estimate.getProject());
        return views.toAssignmentView(assignment);
    }

    @Override
    public AssignmentView updateAssignment(UUID projectId, UUID estimateId, UUID activityId,
                                           UUID assignmentId, AssignmentRequest request) {
        EstimateVersion estimate = projectFinder.requireEstimate(projectId, estimateId);
        Activity activity = projectFinder.requireActivity(estimate, activityId);
        ResourceAssignment assignment = activity.getResourceAssignments().stream()
                .filter(candidate -> candidate.getId().equals(assignmentId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + assignmentId));
        Resource assignedResource = views.resourceOf(assignment);
        if (!assignedResource.getId().equals(request.resourceId())) {
            throw new IllegalArgumentException("Assignment resource cannot be changed");
        }

        applyValues(assignment, request, activity);
        projects.save(estimate.getProject());
        return views.toAssignmentView(assignment);
    }

    @Override
    public void unassignResource(UUID projectId, UUID estimateId, UUID activityId, UUID assignmentId) {
        EstimateVersion estimate = projectFinder.requireEstimate(projectId, estimateId);
        Activity activity = projectFinder.requireActivity(estimate, activityId);
        boolean removed = activity.getResourceAssignments()
                .removeIf(assignment -> assignment.getId().equals(assignmentId));
        if (!removed) {
            throw new NotFoundException("Assignment not found: " + assignmentId);
        }
        projects.save(estimate.getProject());
    }

    @Override
    public CrewView addCrew(UUID projectId, UUID estimateId, UUID assignmentId, CrewRequest request) {
        EstimateVersion estimate = projectFinder.requireEstimate(projectId, estimateId);
        ActivityEquipmentAssignment assignment =
                projectFinder.requireEquipmentAssignment(estimate, assignmentId);
        PersonnelResource person = resourceFinder.requireAvailable(
                request.personnelResourceId(), projectId, PersonnelResource.class);

        EquipmentCrewAssignment crew = new EquipmentCrewAssignment();
        crew.setId(UUID.randomUUID());
        crew.setPersonnelResource(person);
        crew.setEquipmentAssignment(assignment);
        crew.setRoleName(request.roleName());
        crew.setQuantity(request.quantity());
        crew.setWorkingHoursPerDay(request.workingHoursPerDay());
        crew.setMandatory(!Boolean.FALSE.equals(request.mandatory()));
        assignment.getCrewAssignments().add(crew);
        rates.synchronize(estimate, person, false);
        projects.save(estimate.getProject());
        return views.toCrewView(crew);
    }

    @Override
    public StaffView addStaff(UUID projectId, UUID estimateId, StaffRequest request) {
        EstimateVersion estimate = projectFinder.requireEstimate(projectId, estimateId);
        PersonnelResource person = resourceFinder.requireAvailable(
                request.personnelResourceId(), projectId, PersonnelResource.class);
        dateRanges.validate(request.startDate(), request.endDate());

        ProjectStaffAssignment staff = new ProjectStaffAssignment();
        staff.setId(UUID.randomUUID());
        staff.setEstimateVersion(estimate);
        staff.setPersonnelResource(person);
        staff.setProjectRole(request.projectRole());
        staff.setQuantity(request.quantity());
        staff.setAllocationPercentage(request.allocationPercentage());
        staff.setStartDate(request.startDate());
        staff.setEndDate(request.endDate());
        estimate.getProjectStaffAssignments().add(staff);
        rates.synchronize(estimate, person, false);
        projects.save(estimate.getProject());
        return views.toStaffView(staff);
    }

    private void ensureNotAlreadyAssigned(Activity activity, Resource resource) {
        boolean alreadyAssigned = activity.getResourceAssignments().stream()
                .map(views::resourceOf)
                .anyMatch(assigned -> assigned.getId().equals(resource.getId()));
        if (alreadyAssigned) {
            throw new IllegalArgumentException("Resource is already assigned to this activity");
        }
    }

    private ResourceAssignment newAssignment(Resource resource) {
        if (resource instanceof EquipmentResource equipment) {
            ActivityEquipmentAssignment assignment = new ActivityEquipmentAssignment();
            assignment.setEquipmentResource(equipment);
            return assignment;
        }
        if (resource instanceof PersonnelResource personnel) {
            ActivityPersonnelAssignment assignment = new ActivityPersonnelAssignment();
            assignment.setPersonnelResource(personnel);
            return assignment;
        }
        if (resource instanceof MaterialResource material) {
            ActivityMaterialAssignment assignment = new ActivityMaterialAssignment();
            assignment.setMaterialResource(material);
            return assignment;
        }
        throw new IllegalArgumentException("Unsupported resource type");
    }

    private void applyValues(ResourceAssignment assignment, AssignmentRequest request, Activity activity) {
        LocalDate startDate = request.startDate() == null
                ? activity.getPlannedStartDate()
                : request.startDate();
        LocalDate endDate = request.endDate() == null
                ? activity.getPlannedEndDate()
                : request.endDate();
        dateRanges.validate(startDate, endDate);

        assignment.setQuantity(request.quantity());
        assignment.setPlannedWork(request.plannedWork());
        assignment.setWorkUnit(request.workUnit());
        assignment.setUtilizationRate(request.utilizationRate());
        assignment.setStartDate(startDate);
        assignment.setEndDate(endDate);
        assignment.setOvertimeAllowed(Boolean.TRUE.equals(request.overtimeAllowed()));
        applySubtypeValues(assignment, request);
        if (assignment.getPlannedWork() == null) {
            assignment.setPlannedWork(scheduling.deriveWork(assignment));
        }
    }

    private void applySubtypeValues(ResourceAssignment assignment, AssignmentRequest request) {
        if (assignment instanceof ActivityEquipmentAssignment equipment) {
            equipment.setOperatingHoursPerDay(request.operatingHoursPerDay());
            equipment.setStandbyHoursPerDay(request.standbyHoursPerDay());
        } else if (assignment instanceof ActivityPersonnelAssignment personnel) {
            personnel.setAssignmentType(request.personnelAssignmentType() == null
                    ? PersonnelAssignmentType.DIRECT_LABOR
                    : request.personnelAssignmentType());
        } else if (assignment instanceof ActivityMaterialAssignment material) {
            material.setRequiredQuantity(request.requiredQuantity());
            material.setWastePercentage(request.wastePercentage() == null
                    ? material.getMaterialResource().getDefaultWastePercentage()
                    : request.wastePercentage());
        }
    }
}
