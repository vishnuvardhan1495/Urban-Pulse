package com.urbanpulse.service;

import com.google.firebase.auth.FirebaseToken;
import com.urbanpulse.dto.AuthResponse;
import com.urbanpulse.entity.Role;
import com.urbanpulse.entity.User;
import com.urbanpulse.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        userService = new UserService(userRepository);
    }

    @Test
    @DisplayName("loginOrRegister returns isNewUser = false when user already exists in PostgreSQL")
    void testExistingUserLogin() {
        FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("firebase-uid-existing");
        Map<String, Object> claims = new HashMap<>();
        claims.put("phone_number", "+919876543210");
        when(mockToken.getClaims()).thenReturn(claims);

        User existingUser = User.builder()
                .mobileNumber("+919876543210")
                .name("Existing Citizen")
                .role(Role.CITIZEN)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByMobileNumber("+919876543210")).thenReturn(Optional.of(existingUser));

        AuthResponse response = userService.loginOrRegister(mockToken);

        assertNotNull(response);
        assertTrue(response.isAuthenticated());
        assertFalse(response.isNewUser());
        assertEquals("firebase-uid-existing", response.getFirebaseUid());
        assertEquals("+919876543210", response.getMobileNumber());
        assertEquals("CITIZEN", response.getRole());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("loginOrRegister creates and saves new user with role CITIZEN when user does not exist")
    void testNewUserRegistration() {
        FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("firebase-uid-new");
        Map<String, Object> claims = new HashMap<>();
        claims.put("phone_number", "+919123456780");
        when(mockToken.getClaims()).thenReturn(claims);

        when(userRepository.findByMobileNumber("+919123456780")).thenReturn(Optional.empty());

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            return User.builder()
                    .mobileNumber(userToSave.getMobileNumber())
                    .name(userToSave.getName())
                    .role(userToSave.getRole())
                    .createdAt(LocalDateTime.now())
                    .build();
        });

        AuthResponse response = userService.loginOrRegister(mockToken);

        assertNotNull(response);
        assertTrue(response.isAuthenticated());
        assertTrue(response.isNewUser());
        assertEquals("firebase-uid-new", response.getFirebaseUid());
        assertEquals("+919123456780", response.getMobileNumber());
        assertEquals("CITIZEN", response.getRole());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User savedUser = captor.getValue();
        assertEquals("+919123456780", savedUser.getMobileNumber());
        assertEquals(Role.CITIZEN, savedUser.getRole());
    }

    @Test
    @DisplayName("loginOrRegister throws IllegalArgumentException when token lacks phone_number claim")
    void testTokenWithoutPhoneNumberThrows() {
        FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("firebase-uid-no-phone");
        when(mockToken.getClaims()).thenReturn(Collections.emptyMap());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.loginOrRegister(mockToken)
        );

        assertTrue(exception.getMessage().contains("Firebase token does not contain a verified phone number"));
        verify(userRepository, never()).findByMobileNumber(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("loginOrRegister handles race-condition/concurrent registration with isNewUser = false")
    void testConcurrentRegistrationHandledAsExistingUser() {
        FirebaseToken mockToken = Mockito.mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn("firebase-uid-concurrent");
        Map<String, Object> claims = new HashMap<>();
        claims.put("phone_number", "+919999888877");
        when(mockToken.getClaims()).thenReturn(claims);

        User concurrentlySavedUser = User.builder()
                .mobileNumber("+919999888877")
                .name("Citizen")
                .role(Role.CITIZEN)
                .createdAt(LocalDateTime.now())
                .build();

        // 1. First findByMobileNumber returns empty (race condition start)
        // 2. Second findByMobileNumber inside catch block returns the concurrently saved user
        when(userRepository.findByMobileNumber("+919999888877"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrentlySavedUser));

        // save throws DataIntegrityViolationException because concurrent transaction committed first
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        AuthResponse response = userService.loginOrRegister(mockToken);

        assertNotNull(response);
        assertTrue(response.isAuthenticated());
        assertFalse(response.isNewUser(), "Should be false when concurrent request already created the user");
        assertEquals("firebase-uid-concurrent", response.getFirebaseUid());
        assertEquals("+919999888877", response.getMobileNumber());
        assertEquals("CITIZEN", response.getRole());

        verify(userRepository, times(2)).findByMobileNumber("+919999888877");
    }
}
