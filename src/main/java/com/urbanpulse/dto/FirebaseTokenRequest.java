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
public class FirebaseTokenRequest {

    @NotBlank(message = "Firebase ID token is required and cannot be empty")
    private String idToken;
}
