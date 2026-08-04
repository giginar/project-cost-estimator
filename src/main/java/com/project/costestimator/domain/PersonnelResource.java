package com.project.costestimator.domain;

import com.project.costestimator.domain.enums.SkillLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class PersonnelResource extends Resource {
    private String profession;
    private String roleName;
    private SkillLevel skillLevel;
    private boolean genericResource;
}
