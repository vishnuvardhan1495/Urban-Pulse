package com.urbanpulse.service;

import com.google.firebase.auth.FirebaseToken;
import com.urbanpulse.dto.AuthResponse;
import com.urbanpulse.dto.CreateStaffRequest;
import com.urbanpulse.dto.CreateWorkerRequest;
import com.urbanpulse.dto.UserResponse;
import com.urbanpulse.entity.Department;
import com.urbanpulse.entity.Role;
import com.urbanpulse.entity.User;
import com.urbanpulse.repository.DepartmentRepository;
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
    private final DepartmentRepository departmentRepository;

    public UserService(UserRepository userRepository, DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Finds a user by mobile number.
     *
     * @param mobileNumber the primary key mobile number
     * @return Optional containing the User if present
     */
    @Transactional(readOnly = true)
    public Optional<User> findByMobileNumber(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.trim().isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByMobileNumber(mobileNumber.trim());
    }

    /**
     * Creates a new citizen user if the mobile number does not exist.
     * Preserves existing user role if user already exists.
     *
     * @param mobileNumber the primary key mobile number
     * @return Existing user or newly saved Citizen user
     */
    @Transactional
    public User createCitizenIfNotExists(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Mobile number cannot be null or empty");
        }
        String formattedMobile = mobileNumber.trim();
        Optional<User> existingUserOpt = userRepository.findByMobileNumber(formattedMobile);
        if (existingUserOpt.isPresent()) {
            return existingUserOpt.get();
        }

        User newUser = User.builder()
                .mobileNumber(formattedMobile)
                .name(null)
                .role(Role.CITIZEN)
                .build();

        try {
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException ex) {
            logger.warn("Concurrent creation detected for mobile number {}; falling back to database record", formattedMobile);
            return userRepository.findByMobileNumber(formattedMobile)
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Finds existing user or creates a new citizen user. Alias for createCitizenIfNotExists.
     *
     * @param mobileNumber the primary key mobile number
     * @return Existing user or newly created Citizen user
     */
    @Transactional
    public User getOrCreateCitizen(String mobileNumber) {
        return createCitizenIfNotExists(mobileNumber);
    }

    /**
     * Admin creates a staff account (SUPERVISOR or WORKER) for a specific department.
     * Admin CANNOT create another ADMIN or CITIZEN using this staff endpoint.
     */
    @Transactional
    public UserResponse createStaffUserByAdmin(CreateStaffRequest request) {
        String roleStr = request.getRole() != null ? request.getRole().trim().toUpperCase() : "";
        if (!roleStr.equals("SUPERVISOR") && !roleStr.equals("WORKER")) {
            throw new IllegalArgumentException("Admin can only create SUPERVISOR or WORKER staff accounts");
        }

        String mobileNumber = request.getMobileNumber().trim();
        if (userRepository.existsById(mobileNumber)) {
            throw new IllegalArgumentException("User with mobile number " + mobileNumber + " already exists");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Department not found with ID: " + request.getDepartmentId()));

        Role role = Role.valueOf(roleStr);

        User staffUser = User.builder()
                .mobileNumber(mobileNumber)
                .name(request.getName() != null ? request.getName().trim() : null)
                .role(role)
                .department(department)
                .build();

        User saved = userRepository.save(staffUser);
        logger.info("Admin created staff user ({}) with mobile number {}", role, mobileNumber);
        return mapToUserResponse(saved);
    }

    /**
     * Supervisor creates a WORKER user strictly within the supervisor's own department.
     * The supervisor's department is obtained automatically from PostgreSQL.
     */
    @Transactional
    public UserResponse createWorkerBySupervisor(String supervisorMobileNumber, CreateWorkerRequest request) {
        User supervisor = userRepository.findByMobileNumber(supervisorMobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("Supervisor user record not found in database"));

        if (supervisor.getRole() != Role.SUPERVISOR) {
            throw new IllegalArgumentException("Authenticated user is not a SUPERVISOR");
        }

        Department department = supervisor.getDepartment();
        if (department == null) {
            throw new IllegalStateException("Supervisor is not assigned to any department");
        }

        String mobileNumber = request.getMobileNumber().trim();
        if (userRepository.existsById(mobileNumber)) {
            throw new IllegalArgumentException("User with mobile number " + mobileNumber + " already exists");
        }

        User workerUser = User.builder()
                .mobileNumber(mobileNumber)
                .name(request.getName() != null ? request.getName().trim() : null)
                .role(Role.WORKER)
                .department(department)
                .build();

        User saved = userRepository.save(workerUser);
        logger.info("Supervisor {} created worker with mobile number {} under department {}",
                supervisorMobileNumber, mobileNumber, department.getName());
        return mapToUserResponse(saved);
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
            logger.info("Existing user logged in successfully for UID: {} (Role: {})", firebaseUid, existingUser.getRole());
            return AuthResponse.builder()
                    .authenticated(true)
                    .isNewUser(false)
                    .firebaseUid(firebaseUid)
                    .mobileNumber(existingUser.getMobileNumber())
                    .name(existingUser.getName())
                    .role(existingUser.getRole().name())
                    .build();
        }

        // 2. New user registration: default role CITIZEN
        String name = null;
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
                    .name(savedUser.getName())
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
                    .name(concurrentUser.getName())
                    .role(concurrentUser.getRole().name())
                    .build();
        }
    }

    public UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .mobileNumber(user.getMobileNumber())
                .name(user.getName())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}


