package com.urbanpulse.controller;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.urbanpulse.dto.AuthResponse;
import com.urbanpulse.dto.FirebaseTokenRequest;
import com.urbanpulse.dto.FirebaseTokenResponse;
import com.urbanpulse.service.FirebaseAuthService;
import com.urbanpulse.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final FirebaseAuthService firebaseAuthService;
    private final UserService userService;

    public AuthController(FirebaseAuthService firebaseAuthService, UserService userService) {
        this.firebaseAuthService = firebaseAuthService;
        this.userService = userService;
    }

    /**
     * Primary citizen authentication endpoint.
     * Verifies Firebase ID token, finds existing user or creates a new citizen,
     * and returns safe application user details.
     *
     * @param request FirebaseTokenRequest containing the ID token
     * @return AuthResponse with authenticated status, isNewUser flag, UID, mobileNumber, and role
     * @throws FirebaseAuthException if token verification fails
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginOrRegister(@Valid @RequestBody FirebaseTokenRequest request)
            throws FirebaseAuthException {
        FirebaseToken decodedToken = firebaseAuthService.verifyIdToken(request.getIdToken());
        AuthResponse response = userService.loginOrRegister(decodedToken);
        return ResponseEntity.ok(response);
    }

    /**
     * Test endpoint for verifying Firebase ID tokens.
     * Development/verification only.
     *
     * @param request FirebaseTokenRequest containing the ID token
     * @return FirebaseTokenResponse with authenticated status, UID, and phone number
     * @throws FirebaseAuthException if token verification fails (handled by GlobalExceptionHandler -> 401)
     */
    @PostMapping("/test")
    public ResponseEntity<FirebaseTokenResponse> testTokenVerification(@Valid @RequestBody FirebaseTokenRequest request)
            throws FirebaseAuthException {
        FirebaseTokenResponse response = firebaseAuthService.verifyAndExtract(request.getIdToken());
        return ResponseEntity.ok(response);
    }
}
