package com.project.costestimator.application.port.in;

import com.project.costestimator.dto.ApiModels.AssignmentRequest;
import com.project.costestimator.dto.ApiModels.AssignmentView;
import com.project.costestimator.dto.ApiModels.CrewRequest;
import com.project.costestimator.dto.ApiModels.CrewView;
import com.project.costestimator.dto.ApiModels.StaffRequest;
import com.project.costestimator.dto.ApiModels.StaffView;

import java.util.UUID;

public interface AssignmentUseCase {
    AssignmentView assignResource(UUID projectId, UUID estimateId, UUID activityId, AssignmentRequest request);
    AssignmentView updateAssignment(UUID projectId, UUID estimateId, UUID activityId, UUID assignmentId,
                                    AssignmentRequest request);
    void unassignResource(UUID projectId, UUID estimateId, UUID activityId, UUID assignmentId);
    CrewView addCrew(UUID projectId, UUID estimateId, UUID assignmentId, CrewRequest request);
    StaffView addStaff(UUID projectId, UUID estimateId, StaffRequest request);
}
