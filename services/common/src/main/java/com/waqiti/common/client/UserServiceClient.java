package com.waqiti.common.client;

import com.waqiti.common.api.StandardApiResponse;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * User Service Client
 * 
 * Provides standardized communication with the User Service
 */
@Component
@Slf4j
public class UserServiceClient extends ServiceClient {

    public UserServiceClient(RestTemplate restTemplate, 
                           @Value("${services.user-service.url}") String baseUrl) {
        super(restTemplate, baseUrl, "user-service");
    }

    /**
     * Get user by ID
     */
    public CompletableFuture<ServiceResponse<UserDTO>> getUserById(UUID userId) {
        return get("/api/v1/users/" + userId, UserDTO.class, null);
    }

    /**
     * Create new user
     */
    public CompletableFuture<ServiceResponse<UserDTO>> createUser(CreateUserRequest request) {
        return post("/api/v1/users", request, UserDTO.class);
    }

    /**
     * Update user
     */
    public CompletableFuture<ServiceResponse<UserDTO>> updateUser(UUID userId, UpdateUserRequest request) {
        return put("/api/v1/users/" + userId, request, UserDTO.class);
    }

    /**
     * Get user profile
     */
    public CompletableFuture<ServiceResponse<UserProfileDTO>> getUserProfile(UUID userId) {
        return get("/api/v1/users/" + userId + "/profile", UserProfileDTO.class, null);
    }

    /**
     * Update user profile
     */
    public CompletableFuture<ServiceResponse<UserProfileDTO>> updateUserProfile(UUID userId, UserProfileUpdateRequest request) {
        return put("/api/v1/users/" + userId + "/profile", request, UserProfileDTO.class);
    }

    /**
     * Get user KYC status
     */
    public CompletableFuture<ServiceResponse<KycStatusDTO>> getKycStatus(UUID userId) {
        return get("/api/v1/users/" + userId + "/kyc", KycStatusDTO.class, null);
    }

    /**
     * Update KYC status
     */
    public CompletableFuture<ServiceResponse<KycStatusDTO>> updateKycStatus(UUID userId, KycUpdateRequest request) {
        return put("/api/v1/users/" + userId + "/kyc", request, KycStatusDTO.class);
    }

    /**
     * Verify user identity
     */
    public CompletableFuture<ServiceResponse<VerificationResultDTO>> verifyUser(UUID userId, VerificationRequest request) {
        return post("/api/v1/users/" + userId + "/verify", request, VerificationResultDTO.class);
    }

    /**
     * Get user risk assessment
     */
    public CompletableFuture<ServiceResponse<RiskAssessmentDTO>> getRiskAssessment(UUID userId) {
        return get("/api/v1/users/" + userId + "/risk", RiskAssessmentDTO.class, null);
    }

    /**
     * Search users with filters
     */
    public CompletableFuture<ServiceResponse<List<UserDTO>>> searchUsers(UserSearchRequest request) {
        Map<String, Object> queryParams = Map.of(
            "email", request.getEmail() != null ? request.getEmail() : "",
            "status", request.getStatus() != null ? request.getStatus() : "",
            "page", request.getPage(),
            "size", request.getSize()
        );
        return getList("/api/v1/users/search", 
            new ParameterizedTypeReference<StandardApiResponse<List<UserDTO>>>() {}, 
            queryParams);
    }

    /**
     * Check if user exists by email
     */
    public CompletableFuture<ServiceResponse<Boolean>> userExistsByEmail(String email) {
        Map<String, Object> queryParams = Map.of("email", email);
        return get("/api/v1/users/exists", Boolean.class, queryParams);
    }

    /**
     * Activate user
     */
    public CompletableFuture<ServiceResponse<Void>> activateUser(UUID userId) {
        return post("/api/v1/users/" + userId + "/activate", null, Void.class);
    }

    /**
     * Deactivate user
     */
    public CompletableFuture<ServiceResponse<Void>> deactivateUser(UUID userId, String reason) {
        Map<String, Object> request = Map.of("reason", reason);
        return post("/api/v1/users/" + userId + "/deactivate", request, Void.class);
    }

    /**
     * Get user authentication info
     */
    public CompletableFuture<ServiceResponse<UserAuthDTO>> getUserAuth(UUID userId) {
        return get("/api/v1/users/" + userId + "/auth", UserAuthDTO.class, null);
    }

    /**
     * Update user authentication settings
     */
    public CompletableFuture<ServiceResponse<UserAuthDTO>> updateUserAuth(UUID userId, AuthUpdateRequest request) {
        return put("/api/v1/users/" + userId + "/auth", request, UserAuthDTO.class);
    }

    /**
     * Get user status (blocked/suspended check)
     */
    public Map<String, Object> getUserStatus(String userId) {
        try {
            log.debug("Fetching user status for: {}", userId);
            String url = baseUrl + "/api/internal/users/" + userId + "/status";

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : Map.of("blocked", false, "suspended", false);

        } catch (Exception e) {
            log.error("Failed to fetch user status for {}", userId, e);
            // Fail safe - return blocked status if we can't verify
            return Map.of("blocked", true, "suspended", false, "error", e.getMessage());
        }
    }

    /**
     * Get user groups
     */
    public Map<String, Object> getUserGroups(String userId) {
        try {
            log.debug("Fetching user groups for: {}", userId);
            String url = baseUrl + "/api/internal/users/" + userId + "/groups";

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : Map.of("groups", List.of());

        } catch (Exception e) {
            log.error("Failed to fetch user groups for {}", userId, e);
            return Map.of("groups", List.of());
        }
    }

    @Override
    protected String getCurrentCorrelationId() {
        // Implementation would get from request context or MDC
        return org.slf4j.MDC.get("correlationId");
    }

    @Override
    protected String getCurrentAuthToken() {
        // Implementation would get from security context
        return org.springframework.security.core.context.SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getCredentials()
            .toString();
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserDTO {
        private UUID id;
        private String email;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private boolean emailVerified;
        private boolean phoneVerified;
        private String preferredLanguage;
        private String timezone;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserProfileDTO {
        private UUID userId;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private LocalDateTime dateOfBirth;
        private String nationality;
        private String occupation;
        private AddressDTO address;
        private String preferredLanguage;
        private String timezone;
        private boolean marketingConsent;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AddressDTO {
        private String street;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class KycStatusDTO {
        private UUID userId;
        private String status;
        private String level;
        private List<String> requiredDocuments;
        private List<String> submittedDocuments;
        private String rejectionReason;
        private LocalDateTime submissionDate;
        private LocalDateTime reviewDate;
        private LocalDateTime expirationDate;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RiskAssessmentDTO {
        private UUID userId;
        private String riskLevel;
        private java.math.BigDecimal riskScore;
        private List<String> riskFactors;
        private String assessmentReason;
        private LocalDateTime assessmentDate;
        private LocalDateTime nextReviewDate;
        private Map<String, Object> additionalData;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VerificationResultDTO {
        private UUID verificationId;
        private String status;
        private String method;
        private boolean success;
        private String failureReason;
        private LocalDateTime verifiedAt;
        private LocalDateTime expiresAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserAuthDTO {
        private UUID userId;
        private boolean twoFactorEnabled;
        private List<String> enabledMethods;
        private LocalDateTime lastLogin;
        private String lastLoginIp;
        private int failedLoginAttempts;
        private LocalDateTime lockedUntil;
        private boolean passwordExpired;
        private LocalDateTime passwordLastChanged;
    }

    // Request DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateUserRequest {
        private String email;
        private String password;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String preferredLanguage;
        private String timezone;
        private boolean marketingConsent;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UpdateUserRequest {
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String preferredLanguage;
        private String timezone;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserProfileUpdateRequest {
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private LocalDateTime dateOfBirth;
        private String nationality;
        private String occupation;
        private AddressDTO address;
        private String preferredLanguage;
        private String timezone;
        private boolean marketingConsent;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class KycUpdateRequest {
        private String status;
        private String level;
        private List<String> submittedDocuments;
        private String rejectionReason;
        private String reviewNotes;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VerificationRequest {
        private String method;
        private String documentType;
        private String documentNumber;
        private Map<String, Object> additionalData;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserSearchRequest {
        private String email;
        private String status;
        private String firstName;
        private String lastName;
        @Builder.Default
        private int page = 0;
        @Builder.Default
        private int size = 20;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AuthUpdateRequest {
        private boolean twoFactorEnabled;
        private List<String> enabledMethods;
        private String newPassword;
        private String currentPassword;
    }
}