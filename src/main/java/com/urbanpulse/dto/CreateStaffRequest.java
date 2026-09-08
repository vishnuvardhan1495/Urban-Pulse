package com.urbanpulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStaffRequest {

    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    private String name;

    @NotBlank(message = "Role is required")
    private String role; // Must be SUPERVISOR or WORKER

    @NotNull(message = "Department ID is required for staff account creation")
    private Long departmentId;
}
