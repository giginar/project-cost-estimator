package com.project.costestimator.application.port.in;

import com.project.costestimator.domain.AppUser;
import com.project.costestimator.domain.enums.UserRole;
import com.project.costestimator.dto.AuthModels.AdminCreateUserRequest;
import com.project.costestimator.dto.AuthModels.UserList;
import com.project.costestimator.dto.AuthModels.UserView;

public interface UserAdministrationUseCase {
    UserView createUser(AdminCreateUserRequest request);
    UserList listUsers();
    AppUser createVerifiedSeed(String fullName, String email, String password, UserRole role);
}
