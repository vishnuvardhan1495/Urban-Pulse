package com.urbanpulse.service;

import com.google.firebase.auth.FirebaseToken;
import com.urbanpulse.dto.AuthResponse;
import com.urbanpulse.entity.Role;
import com.urbanpulse.entity.User;
import com.urbanpulse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Authenticates or registers a user using verified FirebaseToken claims.
     *
     * @param token the verified FirebaseToken
     * @return AuthResponse containing safe user details, role, and whether the user is newly registered
     */
    @Transactional
    public AuthResponse loginOrRegister(FirebaseToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Firebase token cannot be null");
        }

        String firebaseUid = token.getUid();
        Map<String, Object> claims = token.getClaims();

        String phoneNumber = null;
        if (claims != null && claims.containsKey("phone_number")) {
            Object phoneObj = claims.get("phone_number");
            if (phoneObj != null) {
                phoneNumber = phoneObj.toString().trim();
            }
        }

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            logger.warn("Authentication rejected: Firebase token for UID {} lacks a verified phone number", firebaseUid);
            throw new IllegalArgumentException("Firebase token does not contain a verified phone number");
        }

        // 1. Search PostgreSQL using verified mobile_number
        Optional<User> existingUserOpt = userRepository.findByMobileNumber(phoneNumber);

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            logger.info("Existing user logged in successfully for UID: {}", firebaseUid);
            return AuthResponse.builder()
                    .authenticated(true)
                    .isNewUser(false)
                    .firebaseUid(firebaseUid)
                    .mobileNumber(existingUser.getMobileNumber())
                    .role(existingUser.getRole().name())
                    .build();
        }

        // 2. New user registration: default role CITIZEN
        String name = "Citizen";
        if (claims != null && claims.containsKey("name")) {
            Object nameObj = claims.get("name");
            if (nameObj != null && !nameObj.toString().trim().isEmpty()) {
                name = nameObj.toString().trim();
            }
        }

        User newUser = User.builder()
                .mobileNumber(phoneNumber)
                .name(name)
                .role(Role.CITIZEN)
                .build();

        try {
            User savedUser = userRepository.save(newUser);
            logger.info("New citizen registered successfully for UID: {}", firebaseUid);
            return AuthResponse.builder()
                    .authenticated(true)
                    .isNewUser(true)
                    .firebaseUid(firebaseUid)
                    .mobileNumber(savedUser.getMobileNumber())
                    .role(savedUser.getRole().name())
                    .build();
        } catch (DataIntegrityViolationException ex) {
            // Handle race condition where another concurrent request registered the same mobile number
            logger.warn("Concurrent registration detected for mobile number; falling back to existing user");
            User concurrentUser = userRepository.findByMobileNumber(phoneNumber)
                    .orElseThrow(() -> ex);

            return AuthResponse.builder()
                    .authenticated(true)
                    .isNewUser(false)
                    .firebaseUid(firebaseUid)
                    .mobileNumber(concurrentUser.getMobileNumber())
                    .role(concurrentUser.getRole().name())
                    .build();
        }
    }
}
