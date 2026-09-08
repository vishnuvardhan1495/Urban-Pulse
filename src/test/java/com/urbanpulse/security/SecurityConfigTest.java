package com.urbanpulse.security;

import com.google.firebase.auth.FirebaseToken;
import com.urbanpulse.entity.Department;
import com.urbanpulse.entity.Role;
import com.urbanpulse.entity.User;
import com.urbanpulse.repository.DepartmentRepository;
import com.urbanpulse.repository.UserRepository;
import com.urbanpulse.service.FirebaseAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FirebaseAuthService firebaseAuthService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DepartmentRepository departmentRepository;

    private Department mockDepartment;
    private User mockCitizen;
    private User mockWorker;
    private User mockSupervisor;
    private User mockAdmin;

    @BeforeEach
    void setUp() {
        mockDepartment = Department.builder()
                .id(1L)
                .name("ROADS")
                .description("Roads Department")
                .build();

        mockCitizen = User.builder()
                .mobileNumber("+919000000001")
                .name("Test Citizen")
                .role(Role.CITIZEN)
                .build();

        mockWorker = User.builder()
                .mobileNumber("+919000000002")
                .name("Test Worker")
                .role(Role.WORKER)
                .department(mockDepartment)
                .build();

        mockSupervisor = User.builder()
                .mobileNumber("+919000000003")
                .name("Test Supervisor")
                .role(Role.SUPERVISOR)
                .department(mockDepartment)
                .build();

        mockAdmin = User.builder()
                .mobileNumber("+919000000004")
                .name("Test Admin")
                .role(Role.ADMIN)
                .build();
    }

    private FirebaseToken createMockFirebaseToken(String uid, String phoneNumber) throws Exception {
        FirebaseToken mockToken = org.mockito.Mockito.mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn(uid);
        when(mockToken.getClaims()).thenReturn(Map.of("phone_number", phoneNumber));
        return mockToken;
    }

    @Test
    @DisplayName("Public security-test endpoint allows unauthenticated request")
    void testPublicEndpointUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/security-test/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Public endpoint accessible without authentication"));
    }

    @Test
    @DisplayName("Protected endpoint without Authorization header returns HTTP 401 Unauthorized")
    void testProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/security-test/citizen"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Protected endpoint with invalid Firebase token returns HTTP 401 Unauthorized")
    void testProtectedEndpointWithInvalidToken() throws Exception {
        when(firebaseAuthService.verifyIdToken("invalid-token"))
                .thenThrow(new IllegalArgumentException("Invalid Firebase token"));

        mockMvc.perform(get("/api/security-test/admin")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Verified Firebase token for phone number NOT in PostgreSQL returns HTTP 401 Unauthorized")
    void testVerifiedTokenUnknownUserReturnsUnauthorized() throws Exception {
        FirebaseToken mockToken = createMockFirebaseToken("uid-unknown", "+919999999999");
        when(firebaseAuthService.verifyIdToken("valid-token-unknown")).thenReturn(mockToken);
        when(userRepository.findByMobileNumber("+919999999999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/security-test/worker")
                        .header("Authorization", "Bearer valid-token-unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("CITIZEN can access citizen endpoint but is forbidden from admin endpoint")
    void testCitizenRolePermissions() throws Exception {
        FirebaseToken mockToken = createMockFirebaseToken("uid-citizen", "+919000000001");
        when(firebaseAuthService.verifyIdToken("citizen-token")).thenReturn(mockToken);
        when(userRepository.findByMobileNumber("+919000000001")).thenReturn(Optional.of(mockCitizen));

        // 1. Citizen endpoint -> 200 OK
        mockMvc.perform(get("/api/security-test/citizen")
                        .header("Authorization", "Bearer citizen-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Citizen endpoint accessed successfully"));

        // 2. Admin endpoint -> 403 Forbidden
        mockMvc.perform(get("/api/security-test/admin")
                        .header("Authorization", "Bearer citizen-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("WORKER can access worker endpoint but cannot access admin endpoint or create staff")
    void testWorkerRolePermissions() throws Exception {
        FirebaseToken mockToken = createMockFirebaseToken("uid-worker", "+919000000002");
        when(firebaseAuthService.verifyIdToken("worker-token")).thenReturn(mockToken);
        when(userRepository.findByMobileNumber("+919000000002")).thenReturn(Optional.of(mockWorker));

        // 1. Worker endpoint -> 200 OK
        mockMvc.perform(get("/api/security-test/worker")
                        .header("Authorization", "Bearer worker-token"))
                .andExpect(status().isOk());

        // 2. Admin endpoint -> 403 Forbidden
        mockMvc.perform(get("/api/security-test/admin")
                        .header("Authorization", "Bearer worker-token"))
                .andExpect(status().isForbidden());

        // 3. Worker create staff -> 403 Forbidden
        mockMvc.perform(post("/api/users/staff")
                        .header("Authorization", "Bearer worker-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "+919111111111",
                                  "name": "New Staff",
                                  "role": "WORKER",
                                  "departmentId": 1
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPERVISOR can access supervisor endpoint and create workers, but cannot create department or staff via admin endpoint")
    void testSupervisorRolePermissions() throws Exception {
        FirebaseToken mockToken = createMockFirebaseToken("uid-supervisor", "+919000000003");
        when(firebaseAuthService.verifyIdToken("supervisor-token")).thenReturn(mockToken);
        when(userRepository.findByMobileNumber("+919000000003")).thenReturn(Optional.of(mockSupervisor));

        // 1. Supervisor endpoint -> 200 OK
        mockMvc.perform(get("/api/security-test/supervisor")
                        .header("Authorization", "Bearer supervisor-token"))
                .andExpect(status().isOk());

        // 2. Supervisor create department -> 403 Forbidden (Only ADMIN can create department)
        mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer supervisor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "NEW_DEPT",
                                  "description": "Unauthorized Dept"
                                }
                                """))
                .andExpect(status().isForbidden());

        // 3. Supervisor create staff via Admin endpoint -> 403 Forbidden
        mockMvc.perform(post("/api/users/staff")
                        .header("Authorization", "Bearer supervisor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "+919222222222",
                                  "name": "New Supervisor Staff",
                                  "role": "SUPERVISOR",
                                  "departmentId": 1
                                }
                                """))
                .andExpect(status().isForbidden());

        // 4. Supervisor create worker via Supervisor endpoint -> 201 Created
        when(userRepository.existsById("+919333333333")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/users/workers")
                        .header("Authorization", "Bearer supervisor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "+919333333333",
                                  "name": "Supervisor Created Worker"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mobileNumber").value("+919333333333"))
                .andExpect(jsonPath("$.role").value("WORKER"))
                .andExpect(jsonPath("$.departmentName").value("ROADS"));
    }

    @Test
    @DisplayName("ADMIN can access admin endpoint, create department, and create staff")
    void testAdminRolePermissions() throws Exception {
        FirebaseToken mockToken = createMockFirebaseToken("uid-admin", "+919000000004");
        when(firebaseAuthService.verifyIdToken("admin-token")).thenReturn(mockToken);
        when(userRepository.findByMobileNumber("+919000000004")).thenReturn(Optional.of(mockAdmin));

        // 1. Admin endpoint -> 200 OK
        mockMvc.perform(get("/api/security-test/admin")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());

        // 2. Admin create department -> 201 Created
        when(departmentRepository.existsByName("HEALTH")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> {
            Department d = i.getArgument(0);
            d.setId(2L);
            return d;
        });

        mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "HEALTH",
                                  "description": "Health & Sanitation"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("HEALTH"));

        // 3. Admin create SUPERVISOR staff -> 201 Created
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(mockDepartment));
        when(userRepository.existsById("+919444444444")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/users/staff")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobileNumber": "+919444444444",
                                  "name": "Admin Created Supervisor",
                                  "role": "SUPERVISOR",
                                  "departmentId": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mobileNumber").value("+919444444444"))
                .andExpect(jsonPath("$.role").value("SUPERVISOR"))
                .andExpect(jsonPath("$.departmentName").value("ROADS"));
    }
}
