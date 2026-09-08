package com.urbanpulse.controller;

import com.urbanpulse.dto.CreateStaffRequest;
import com.urbanpulse.dto.CreateWorkerRequest;
import com.urbanpulse.dto.UserResponse;
import com.urbanpulse.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserManagementController {

    private final UserService userService;

    public UserManagementController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Admin endpoint to create staff accounts (SUPERVISOR or WORKER) for any department.
     */
    @PostMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createStaffByAdmin(@Valid @RequestBody CreateStaffRequest request) {
        UserResponse response = userService.createStaffUserByAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Supervisor endpoint to create WORKER accounts within the supervisor's department.
     * The department is determined automatically from the authenticated supervisor's record.
     */
    @PostMapping("/workers")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<UserResponse> createWorkerBySupervisor(
            @Valid @RequestBody CreateWorkerRequest request,
            Authentication authentication) {
        String supervisorMobileNumber = authentication.getName();
        UserResponse response = userService.createWorkerBySupervisor(supervisorMobileNumber, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
