package com.urbanpulse.controller;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.urbanpulse.dto.AuthResponse;
import com.urbanpulse.dto.FirebaseTokenResponse;
import com.urbanpulse.exception.GlobalExceptionHandler;
import com.urbanpulse.service.FirebaseAuthService;
import com.urbanpulse.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FirebaseAuthService firebaseAuthService;

    @MockBean
    private UserService userService;

    @MockBean
    private com.urbanpulse.repository.UserRepository userRepository;

    // ==========================================
    // POST /api/auth/login tests (Phase 6)
    // ==========================================

    @Test
    @DisplayName("POST /api/auth/login returns 400 Bad Request when idToken is missing in request body")
    void testLoginMissingIdToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Firebase ID token is required")));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 Bad Request when idToken is blank")
    void testLoginBlankIdToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 Unauthorized when idToken is invalid or expired")
    void testLoginInvalidIdToken() throws Exception {
        FirebaseAuthException firebaseAuthException = Mockito.mock(FirebaseAuthException.class);
        when(firebaseAuthException.getAuthErrorCode()).thenReturn(AuthErrorCode.INVALID_ID_TOKEN);
        when(firebaseAuthException.getMessage()).thenReturn("Firebase ID token is invalid.");

        when(firebaseAuthService.verifyIdToken("invalid-token-xyz"))
                .thenThrow(firebaseAuthException);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"invalid-token-xyz\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid or expired Firebase ID token")))
                .andExpect(jsonPath("$.firebaseUid").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 OK with isNewUser = false for existing user")
    void testLoginExistingUser() throws Exception {
        FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
        when(firebaseAuthService.verifyIdToken("valid-existing-user-token")).thenReturn(mockToken);

        AuthResponse mockAuthResponse = AuthResponse.builder()
                .authenticated(true)
                .isNewUser(false)
                .firebaseUid("uid-existing-123")
                .mobileNumber("+919876543210")
                .role("CITIZEN")
                .build();

        when(userService.loginOrRegister(mockToken)).thenReturn(mockAuthResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"valid-existing-user-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.isNewUser", is(false)))
                .andExpect(jsonPath("$.firebaseUid", is("uid-existing-123")))
                .andExpect(jsonPath("$.mobileNumber", is("+919876543210")))
                .andExpect(jsonPath("$.role", is("CITIZEN")));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 OK with isNewUser = true for newly created citizen")
    void testLoginNewUser() throws Exception {
        FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
        when(firebaseAuthService.verifyIdToken("valid-new-user-token")).thenReturn(mockToken);

        AuthResponse mockAuthResponse = AuthResponse.builder()
                .authenticated(true)
                .isNewUser(true)
                .firebaseUid("uid-new-456")
                .mobileNumber("+919123456789")
                .role("CITIZEN")
                .build();

        when(userService.loginOrRegister(mockToken)).thenReturn(mockAuthResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"valid-new-user-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.isNewUser", is(true)))
                .andExpect(jsonPath("$.firebaseUid", is("uid-new-456")))
                .andExpect(jsonPath("$.mobileNumber", is("+919123456789")))
                .andExpect(jsonPath("$.role", is("CITIZEN")));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 Bad Request when token lacks phone number")
    void testLoginTokenWithoutPhoneNumber() throws Exception {
        FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
        when(firebaseAuthService.verifyIdToken("token-without-phone")).thenReturn(mockToken);

        when(userService.loginOrRegister(mockToken))
                .thenThrow(new IllegalArgumentException("Firebase token does not contain a verified phone number"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"token-without-phone\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Firebase token does not contain a verified phone number")));
    }

    // ==========================================
    // POST /api/auth/test tests (Phase 5)
    // ==========================================

    @Test
    @DisplayName("POST /api/auth/test should return 400 Bad Request when idToken is missing in request body")
    void testMissingIdToken() throws Exception {
        mockMvc.perform(post("/api/auth/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Firebase ID token is required")));
    }

    @Test
    @DisplayName("POST /api/auth/test should return 400 Bad Request when idToken is blank")
    void testBlankIdToken() throws Exception {
        mockMvc.perform(post("/api/auth/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    @DisplayName("POST /api/auth/test should return 401 Unauthorized when idToken is invalid or expired")
    void testInvalidIdToken() throws Exception {
        FirebaseAuthException firebaseAuthException = Mockito.mock(FirebaseAuthException.class);
        when(firebaseAuthException.getAuthErrorCode()).thenReturn(AuthErrorCode.INVALID_ID_TOKEN);
        when(firebaseAuthException.getMessage()).thenReturn("Firebase ID token is invalid.");

        when(firebaseAuthService.verifyAndExtract(anyString()))
                .thenThrow(firebaseAuthException);

        mockMvc.perform(post("/api/auth/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"invalid-mock-token-xyz\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid or expired Firebase ID token")))
                .andExpect(jsonPath("$.firebaseUid").doesNotExist())
                .andExpect(jsonPath("$.privateKey").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/test should return 200 OK with safe user details for valid token")
    void testValidIdToken() throws Exception {
        FirebaseTokenResponse mockResponse = FirebaseTokenResponse.builder()
                .authenticated(true)
                .firebaseUid("firebase-uid-12345")
                .phoneNumber("+919876543210")
                .build();

        when(firebaseAuthService.verifyAndExtract("valid-firebase-id-token"))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"valid-firebase-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.firebaseUid", is("firebase-uid-12345")))
                .andExpect(jsonPath("$.phoneNumber", is("+919876543210")));
    }
}
