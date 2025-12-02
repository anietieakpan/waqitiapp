package com.waqiti.common.application.services;

import java.math.BigDecimal;
import com.waqiti.common.domain.services.ComplianceService;
import com.waqiti.common.domain.valueobjects.*;
import com.waqiti.common.domain.Money;
import com.waqiti.common.events.EventGateway;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * User Application Service
 * Orchestrates user-related use cases including registration, verification, and profile management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserApplicationService {
    
    private final ComplianceService complianceService;
    private final EventGateway eventGateway;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * Register new user - complete orchestration
     */
    @Transactional
    public CompletableFuture<UserRegistrationResult> registerUser(RegisterUserRequest request) {
        log.info("Registering new user: email={}, phoneNumber={}", 
                request.getEmail(), request.getPhoneNumber());
        
        return CompletableFuture
                .supplyAsync(() -> validateRegistrationRequest(request))
                .thenCompose(this::checkDuplicateUser)
                .thenCompose(this::performInitialKyc)
                .thenCompose(this::createUserAccount)
                .thenCompose(this::createDefaultWallet)
                .thenCompose(this::sendVerificationEmail)
                .thenCompose(this::publishUserEvents)
                .exceptionally(this::handleRegistrationFailure);
    }
    
    /**
     * Complete KYC verification
     */
    @Transactional
    public KycVerificationResult completeKycVerification(KycVerificationRequest request) {
        log.info("Processing KYC verification for user: {}", request.getUserId());
        
        // Validate KYC documents
        ComplianceService.KycValidationResult kycResult = complianceService.validateKyc(
                ComplianceService.KycValidationRequest.builder()
                        .userId(request.getUserId())
                        .amount(Money.ngn(BigDecimal.ZERO)) // No specific amount for KYC
                        .identityDocumentVerified(request.isIdentityDocumentProvided())
                        .addressVerified(request.isAddressDocumentProvided())
                        .incomeSourceVerified(request.isIncomeProofProvided())
                        .biometricVerified(request.isBiometricProvided())
                        .build());
        
        if (!kycResult.isCompliant()) {
            return KycVerificationResult.builder()
                    .success(false)
                    .userId(request.getUserId())
                    .kycLevel(kycResult.getKycLevel())
                    .missingDocuments(kycResult.getMissingDocuments())
                    .message("KYC verification incomplete")
                    .build();
        }
        
        // Update user KYC status (would call repository)
        
        // Publish KYC completed event
        eventGateway.publishEvent("user.kyc.completed", Map.of(
                "userId", request.getUserId().getValue(),
                "kycLevel", kycResult.getKycLevel().toString(),
                "completedAt", Instant.now()
        ));
        
        return KycVerificationResult.builder()
                .success(true)
                .userId(request.getUserId())
                .kycLevel(kycResult.getKycLevel())
                .verifiedAt(Instant.now())
                .message("KYC verification completed successfully")
                .build();
    }
    
    /**
     * Update user profile
     */
    @Transactional
    public UpdateProfileResult updateUserProfile(UpdateProfileRequest request) {
        log.info("Updating profile for user: {}", request.getUserId());
        
        // Validate profile updates
        List<String> validationErrors = validateProfileUpdate(request);
        
        if (!validationErrors.isEmpty()) {
            return UpdateProfileResult.builder()
                    .success(false)
                    .errors(validationErrors)
                    .build();
        }
        
        // Update user profile (would call repository)
        
        // Publish profile updated event
        eventGateway.publishEvent("user.profile.updated", Map.of(
                "userId", request.getUserId().getValue(),
                "updatedFields", request.getUpdatedFields(),
                "updatedAt", Instant.now()
        ));
        
        return UpdateProfileResult.builder()
                .success(true)
                .userId(request.getUserId())
                .message("Profile updated successfully")
                .build();
    }
    
    /**
     * Suspend user account
     */
    @Transactional
    public AccountSuspensionResult suspendAccount(SuspendAccountRequest request) {
        log.info("Suspending account for user: {}, reason: {}", 
                request.getUserId(), request.getReason());
        
        // Check if account can be suspended
        // Update account status (would call repository)
        
        // Publish account suspended event
        eventGateway.publishEvent("user.account.suspended", Map.of(
                "userId", request.getUserId().getValue(),
                "reason", request.getReason(),
                "suspendedBy", request.getSuspendedBy().getValue(),
                "suspendedAt", Instant.now()
        ));
        
        return AccountSuspensionResult.builder()
                .success(true)
                .userId(request.getUserId())
                .suspendedAt(Instant.now())
                .message("Account suspended successfully")
                .build();
    }
    
    /**
     * Reactivate user account
     */
    @Transactional
    public AccountReactivationResult reactivateAccount(ReactivateAccountRequest request) {
        log.info("Reactivating account for user: {}", request.getUserId());
        
        // Verify reactivation eligibility
        // Update account status (would call repository)
        
        // Publish account reactivated event
        eventGateway.publishEvent("user.account.reactivated", Map.of(
                "userId", request.getUserId().getValue(),
                "reactivatedBy", request.getReactivatedBy().getValue(),
                "reactivatedAt", Instant.now()
        ));
        
        return AccountReactivationResult.builder()
                .success(true)
                .userId(request.getUserId())
                .reactivatedAt(Instant.now())
                .message("Account reactivated successfully")
                .build();
    }
    
    /**
     * Delete user account (GDPR compliance)
     */
    @Transactional
    public AccountDeletionResult deleteAccount(DeleteAccountRequest request) {
        log.info("Processing account deletion for user: {}", request.getUserId());
        
        // Check if account can be deleted (no pending transactions, etc.)
        // Anonymize user data
        // Mark account as deleted (soft delete)
        
        // Publish account deleted event
        eventGateway.publishEvent("user.account.deleted", Map.of(
                "userId", request.getUserId().getValue(),
                "reason", request.getReason(),
                "deletedAt", Instant.now()
        ));
        
        return AccountDeletionResult.builder()
                .success(true)
                .userId(request.getUserId())
                .deletedAt(Instant.now())
                .message("Account deletion processed successfully")
                .build();
    }
    
    // Private orchestration methods
    
    private UserRegistrationContext validateRegistrationRequest(RegisterUserRequest request) {
        Email email = Email.of(request.getEmail());
        PhoneNumber phoneNumber = PhoneNumber.of(request.getPhoneNumber());
        
        // Validate password complexity
        if (request.getPassword().length() < 8) {
            throw new RegistrationValidationException("Password must be at least 8 characters");
        }
        
        return UserRegistrationContext.builder()
                .request(request)
                .email(email)
                .phoneNumber(phoneNumber)
                .userId(UserId.generate())
                .build();
    }
    
    private CompletableFuture<UserRegistrationContext> checkDuplicateUser(UserRegistrationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Check if email or phone already exists (would call repository)
            // For now, assume no duplicates
            
            context.setDuplicateCheckPassed(true);
            return context;
        });
    }
    
    private CompletableFuture<UserRegistrationContext> performInitialKyc(UserRegistrationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Perform initial KYC checks
            ComplianceService.KycValidationResult kycResult = complianceService.validateKyc(
                    ComplianceService.KycValidationRequest.builder()
                            .userId(context.getUserId())
                            .amount(Money.ngn(BigDecimal.ZERO))
                            .identityDocumentVerified(false)
                            .addressVerified(false)
                            .build());
            
            context.setKycLevel(kycResult.getKycLevel());
            return context;
        });
    }
    
    private CompletableFuture<UserRegistrationContext> createUserAccount(UserRegistrationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Create user entity (would call repository)
            String hashedPassword = passwordEncoder.encode(context.getRequest().getPassword());
            
            context.setAccountCreated(true);
            context.setCreatedAt(Instant.now());
            return context;
        });
    }
    
    private CompletableFuture<UserRegistrationContext> createDefaultWallet(UserRegistrationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Create default wallet for user
            AccountNumber walletAccount = AccountNumber.generate();
            
            context.setWalletAccountNumber(walletAccount);
            return context;
        });
    }
    
    private CompletableFuture<UserRegistrationContext> sendVerificationEmail(UserRegistrationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Send verification email (would call email service)
            
            context.setVerificationEmailSent(true);
            return context;
        });
    }
    
    private CompletableFuture<UserRegistrationResult> publishUserEvents(UserRegistrationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            // Publish user registered event
            eventGateway.publishEvent("user.registered", Map.of(
                    "userId", context.getUserId().getValue(),
                    "email", context.getEmail().getValue(),
                    "phoneNumber", context.getPhoneNumber().getValue(),
                    "registeredAt", context.getCreatedAt()
            ));
            
            return UserRegistrationResult.builder()
                    .success(true)
                    .userId(context.getUserId())
                    .email(context.getEmail())
                    .phoneNumber(context.getPhoneNumber())
                    .walletAccountNumber(context.getWalletAccountNumber())
                    .message("User registered successfully")
                    .build();
        });
    }
    
    private UserRegistrationResult handleRegistrationFailure(Throwable throwable) {
        log.error("User registration failed", throwable);
        
        return UserRegistrationResult.builder()
                .success(false)
                .message("Registration failed: " + throwable.getMessage())
                .build();
    }
    
    private List<String> validateProfileUpdate(UpdateProfileRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getEmail() != null) {
            try {
                Email.of(request.getEmail());
            } catch (Exception e) {
                errors.add("Invalid email format");
            }
        }
        
        if (request.getPhoneNumber() != null) {
            try {
                PhoneNumber.of(request.getPhoneNumber());
            } catch (Exception e) {
                errors.add("Invalid phone number format");
            }
        }
        
        if (request.getAddress() != null) {
            // Validate address fields
        }
        
        return errors;
    }
    
    // Request and Result classes
    
    @Data
    @Builder
    public static class RegisterUserRequest {
        private String email;
        private String phoneNumber;
        private String password;
        private String firstName;
        private String lastName;
        private LocalDate dateOfBirth;
        private String country;
        private String referralCode;
    }
    
    @Data
    @Builder
    public static class UserRegistrationResult {
        private boolean success;
        private UserId userId;
        private Email email;
        private PhoneNumber phoneNumber;
        private AccountNumber walletAccountNumber;
        private String message;
        private List<String> errors;
    }
    
    @Data
    @Builder
    public static class KycVerificationRequest {
        private UserId userId;
        private boolean identityDocumentProvided;
        private boolean addressDocumentProvided;
        private boolean incomeProofProvided;
        private boolean biometricProvided;
        private Map<String, String> documentUrls;
    }
    
    @Data
    @Builder
    public static class KycVerificationResult {
        private boolean success;
        private UserId userId;
        private ComplianceService.KycLevel kycLevel;
        private List<String> missingDocuments;
        private Instant verifiedAt;
        private String message;
    }
    
    @Data
    @Builder
    public static class UpdateProfileRequest {
        private UserId userId;
        private String email;
        private String phoneNumber;
        private String firstName;
        private String lastName;
        private Address address;
        private Map<String, String> updatedFields;
    }
    
    @Data
    @Builder
    public static class UpdateProfileResult {
        private boolean success;
        private UserId userId;
        private String message;
        private List<String> errors;
    }
    
    @Data
    @Builder
    public static class SuspendAccountRequest {
        private UserId userId;
        private UserId suspendedBy;
        private String reason;
        private Instant suspendUntil;
    }
    
    @Data
    @Builder
    public static class AccountSuspensionResult {
        private boolean success;
        private UserId userId;
        private Instant suspendedAt;
        private String message;
    }
    
    @Data
    @Builder
    public static class ReactivateAccountRequest {
        private UserId userId;
        private UserId reactivatedBy;
        private String reason;
    }
    
    @Data
    @Builder
    public static class AccountReactivationResult {
        private boolean success;
        private UserId userId;
        private Instant reactivatedAt;
        private String message;
    }
    
    @Data
    @Builder
    public static class DeleteAccountRequest {
        private UserId userId;
        private String reason;
        private boolean confirmDeletion;
    }
    
    @Data
    @Builder
    public static class AccountDeletionResult {
        private boolean success;
        private UserId userId;
        private Instant deletedAt;
        private String message;
    }
    
    // Internal context classes
    
    @Data
    @Builder
    private static class UserRegistrationContext {
        private RegisterUserRequest request;
        private UserId userId;
        private Email email;
        private PhoneNumber phoneNumber;
        private AccountNumber walletAccountNumber;
        private ComplianceService.KycLevel kycLevel;
        private boolean duplicateCheckPassed;
        private boolean accountCreated;
        private boolean verificationEmailSent;
        private Instant createdAt;
    }
    
    // Custom exceptions
    
    public static class RegistrationValidationException extends RuntimeException {
        public RegistrationValidationException(String message) {
            super(message);
        }
    }
    
    public static class DuplicateUserException extends RuntimeException {
        public DuplicateUserException(String message) {
            super(message);
        }
    }
}