package com.project.costestimator.controller;

import com.project.costestimator.dto.AuthModels.*;
import com.project.costestimator.service.AuthService;
import com.project.costestimator.service.VerificationMailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController {
    private final AuthService auth;
    private final VerificationMailService mail;

    @GetMapping public UserList list() { return auth.listUsers(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public UserView create(@Valid @RequestBody AdminCreateUserRequest request) { return auth.createUser(request); }
    @GetMapping("/mail-outbox") public List<MailOutboxView> mailOutbox() { return mail.developmentOutbox(); }
}
