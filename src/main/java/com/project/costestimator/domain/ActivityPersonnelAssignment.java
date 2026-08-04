package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.PersonnelAssignmentType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class ActivityPersonnelAssignment extends ResourceAssignment {
    private PersonnelAssignmentType assignmentType;
    private PersonnelResource personnelResource;
}
