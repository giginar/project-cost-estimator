package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.MailOutboxQuery;
import com.project.costestimator.application.port.in.UserAdministrationUseCase;
import com.project.costestimator.dto.AuthModels.AdminCreateUserRequest;
import com.project.costestimator.dto.AuthModels.MailOutboxView;
import com.project.costestimator.dto.AuthModels.UserList;
import com.project.costestimator.dto.AuthModels.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {
    private final UserAdministrationUseCase users;
    private final MailOutboxQuery mailOutbox;

    public UserAdminController(UserAdministrationUseCase users, MailOutboxQuery mailOutbox) {
        this.users = users;
        this.mailOutbox = mailOutbox;
    }

    @GetMapping
    public UserList list() {
        return users.listUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody AdminCreateUserRequest request) {
        return users.createUser(request);
    }

    @GetMapping("/mail-outbox")
    public List<MailOutboxView> mailOutbox() {
        return mailOutbox.developmentOutbox();
    }
}
