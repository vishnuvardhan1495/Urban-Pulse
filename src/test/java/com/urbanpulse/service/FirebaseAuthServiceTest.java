package com.urbanpulse.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.urbanpulse.dto.FirebaseTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebaseAuthServiceTest {

    private FirebaseAuth firebaseAuth;
    private FirebaseAuthService firebaseAuthService;

    @BeforeEach
    void setUp() {
        firebaseAuth = Mockito.mock(FirebaseAuth.class);
        firebaseAuthService = new FirebaseAuthService(firebaseAuth);
    }

    @Test
    @DisplayName("verifyIdToken should throw IllegalArgumentException when token is null or empty")
    void testVerifyIdTokenThrowsOnNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> firebaseAuthService.verifyIdToken(null));
        assertThrows(IllegalArgumentException.class, () -> firebaseAuthService.verifyIdToken(""));
        assertThrows(IllegalArgumentException.class, () -> firebaseAuthService.verifyIdToken("   "));
    }

    @Test
    @DisplayName("verifyAndExtract should return safe FirebaseTokenResponse when token is valid")
    void testVerifyAndExtractSuccess() throws Exception {
        FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("test-uid-789");
        Map<String, Object> claims = new HashMap<>();
        claims.put("phone_number", "+919988776655");
        when(mockToken.getClaims()).thenReturn(claims);

        when(firebaseAuth.verifyIdToken("valid-token-xyz")).thenReturn(mockToken);

        FirebaseTokenResponse response = firebaseAuthService.verifyAndExtract("valid-token-xyz");

        assertNotNull(response);
        assertTrue(response.isAuthenticated());
        assertEquals("test-uid-789", response.getFirebaseUid());
        assertEquals("+919988776655", response.getPhoneNumber());
    }

    @Test
    @DisplayName("verifyAndExtract should handle missing phone number claim safely")
    void testVerifyAndExtractMissingPhone() throws Exception {
        FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("test-uid-no-phone");
        when(mockToken.getClaims()).thenReturn(Collections.emptyMap());

        when(firebaseAuth.verifyIdToken("token-no-phone")).thenReturn(mockToken);

        FirebaseTokenResponse response = firebaseAuthService.verifyAndExtract("token-no-phone");

        assertNotNull(response);
        assertTrue(response.isAuthenticated());
        assertEquals("test-uid-no-phone", response.getFirebaseUid());
        assertNull(response.getPhoneNumber());
    }
}
