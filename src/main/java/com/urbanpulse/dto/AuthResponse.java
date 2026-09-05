package com.urbanpulse.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private boolean authenticated;

    @JsonProperty("isNewUser")
    private boolean isNewUser;

    private String firebaseUid;
    private String mobileNumber;
    private String role;
}
