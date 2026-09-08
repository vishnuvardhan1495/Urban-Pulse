package com.urbanpulse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWorkerRequest {

    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    private String name;
}
