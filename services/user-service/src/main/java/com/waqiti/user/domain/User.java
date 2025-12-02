package com.waqiti.user.domain;

import com.waqiti.common.security.EncryptedStringConverter;
import com.waqiti.common.security.FieldEncryption;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    private static final Pattern EMAIL_PATTERN = 
            Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    
    private static final Pattern PHONE_PATTERN = 
            Pattern.compile("^\\+[0-9]{10,15}$");

    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @FieldEncryption(dataType = FieldEncryption.DataType.PII, auditAccess = true)
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, unique = true)
    private String email;

    @FieldEncryption(dataType = FieldEncryption.DataType.PII, auditAccess = true)
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_number")
    private String phoneNumber;

    @FieldEncryption(dataType = FieldEncryption.DataType.PII, auditAccess = true)
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pending_email")
    private String pendingEmail;

    @FieldEncryption(dataType = FieldEncryption.DataType.PII, auditAccess = true)
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pending_phone_number")
    private String pendingPhoneNumber;

    @Column(nullable = false, name = "password_hash")
    private String passwordHash;

    @Setter
    @Column(name = "password_upgraded_at")
    private LocalDateTime passwordUpgradedAt;

    @Setter
    @Column(name = "password_hash_version", nullable = false)
    private Integer passwordHashVersion = 12; // Default to 12 for existing users

    @Setter
    @Column(name = "password_reset_required")
    private Boolean passwordResetRequired = false;

    @Setter
    @Column(name = "password_reset_reason")
    private String passwordResetReason;

    @Setter
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(nullable = false, name = "external_id")
    private String externalId;
    
    @Setter
    @Column(name = "keycloak_id", unique = true)
    private String keycloakId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    // KYC functionality has been fully migrated to the dedicated KYC microservice
    // All KYC-related operations should use the KYC service
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @BatchSize(size = 20)
    private Set<String> roles = new HashSet<>();

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Setter
    @Column(nullable = false, name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Setter
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Version
    private Long version;

    // Audit fields
    @Setter
    @Column(name = "created_by")
    private String createdBy;
    
    @Setter
    @Column(name = "updated_by")
    private String updatedBy;
    
    // Biometric flags
    @Setter
    @Column(name = "fingerprint_enabled")
    private boolean fingerprintEnabled = false;
    
    @Setter
    @Column(name = "face_id_enabled")
    private boolean faceIdEnabled = false;
    
    @Setter
    @Column(name = "voice_auth_enabled")
    private boolean voiceAuthEnabled = false;
    
    @Setter
    @Column(name = "behavioral_biometric_enabled")
    private boolean behavioralBiometricEnabled = false;
    
    @Setter
    @Column(name = "webauthn_enabled")
    private boolean webAuthnEnabled = false;
    
    // Fraud and security tracking fields
    @Column(name = "account_locked")
    private boolean accountLocked = false;
    
    @Column(name = "lock_reason")
    private String lockReason;
    
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    
    @Column(name = "transaction_restricted")
    private boolean transactionRestricted = false;
    
    @Column(name = "restriction_reason")
    private String restrictionReason;
    
    @Column(name = "restricted_until")
    private LocalDateTime restrictedUntil;
    
    @Column(name = "daily_transaction_limit")
    private BigDecimal dailyTransactionLimit;
    
    @Column(name = "requires_manual_review")
    private boolean requiresManualReview = false;
    
    @Column(name = "enhanced_monitoring")
    private boolean enhancedMonitoring = false;
    
    @Column(name = "monitoring_reason")
    private String monitoringReason;
    
    @Column(name = "monitoring_until")
    private LocalDateTime monitoringUntil;
    
    @Column(name = "requires_two_factor_auth")
    private boolean requiresTwoFactorAuth = false;
    
    @Column(name = "warning_count")
    private int warningCount = 0;
    
    @Column(name = "last_warning_date")
    private LocalDateTime lastWarningDate;
    
    @Column(name = "fraud_alert_count")
    private int fraudAlertCount = 0;
    
    @Column(name = "last_fraud_alert_date")
    private LocalDateTime lastFraudAlertDate;
    
    @Column(name = "fraud_risk_score")
    private Double fraudRiskScore = 0.0;
    
    // Account freeze fields
    @Setter
    @Column(name = "freeze_reason")
    private String freezeReason;
    
    @Setter
    @Column(name = "freeze_description", length = 1000)
    private String freezeDescription;
    
    @Setter
    @Column(name = "freeze_scope")
    private String freezeScope;
    
    @Setter
    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;
    
    @Setter
    @Column(name = "freeze_expiration_date")
    private LocalDateTime freezeExpirationDate;
    
    @Setter
    @Column(name = "is_temporary_freeze")
    private Boolean isTemporaryFreeze = false;
    
    @Setter
    @Column(name = "linked_freeze_reason")
    private String linkedFreezeReason;
    
    @Setter
    @Column(name = "linked_account_id")
    private String linkedAccountId;
    
    // Transaction capability flags
    @Setter
    @Column(name = "can_transact")
    private Boolean canTransact = true;
    
    @Setter
    @Column(name = "can_withdraw")
    private Boolean canWithdraw = true;
    
    @Setter
    @Column(name = "can_deposit")
    private Boolean canDeposit = true;
    
    // Enhanced monitoring fields  
    @Setter
    @Column(name = "monitoring_enabled")
    private Boolean monitoringEnabled = false;
    
    @Setter
    @Column(name = "monitoring_level")
    private String monitoringLevel;
    
    @Setter
    @Column(name = "monitoring_start_date")
    private LocalDateTime monitoringStartDate;
    
    @Setter
    @Column(name = "monitoring_end_date")
    private LocalDateTime monitoringEndDate;
    
    // Account closure fields
    @Setter
    @Column(name = "closed")
    private Boolean closed = false;
    
    @Setter
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    
    @Setter
    @Column(name = "closure_id")
    private String closureId;
    
    @Setter
    @Column(name = "account_status")
    private String accountStatus;
    
    @Setter
    @Column(name = "status_reason")
    private String statusReason;
    
    @Setter
    @Column(name = "first_name")
    private String firstName;
    
    @Setter
    @Column(name = "last_name")
    private String lastName;
    
    @Setter
    @Column(name = "date_of_birth_field")
    private java.time.LocalDate dateOfBirthField;
    
    @Setter
    @Column(name = "address")
    private String address;
    
    @Setter
    @Column(name = "blocked")
    private Boolean blocked = false;
    
    @Setter
    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;
    
    @Setter
    @Column(name = "block_reason")
    private String blockReason;
    
    @Setter
    @Column(name = "active")
    private Boolean active = true;
    
    @Setter
    @ElementCollection
    @CollectionTable(name = "user_linked_accounts", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "linked_account")
    private Set<String> linkedAccounts = new HashSet<>();
    
    @Setter
    @ElementCollection
    @CollectionTable(name = "user_integrated_services", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "service")
    private Set<String> integratedServices = new HashSet<>();

    /**
     * Creates a new user
     */
    public static User create(String username, String email, String passwordHash, String externalId) {
        validateUsername(username);
        validateEmail(email);
        
        User user = new User();
        user.username = username;
        user.email = email;
        user.passwordHash = passwordHash;
        user.externalId = externalId;
        user.status = UserStatus.PENDING;
        // KYC status is now managed by the dedicated KYC service
        user.roles.add("ROLE_USER");
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        return user;
    }

    /**
     * Activates the user account
     */
    public void activate() {
        if (this.status == UserStatus.ACTIVE) {
            throw new IllegalStateException("User is already active");
        }
        
        this.status = UserStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Suspends the user account
     */
    public void suspend(String reason) {
        this.status = UserStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Closes the user account permanently
     */
    public void close() {
        this.status = UserStatus.CLOSED;
        this.updatedAt = LocalDateTime.now();
    }


    /**
     * Adds a role to the user
     */
    public void addRole(String role) {
        this.roles.add(role);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the user's password
     */
    public void updatePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the user's phone number
     */
    public void updatePhoneNumber(String phoneNumber) {
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            validatePhoneNumber(phoneNumber);
        }
        
        this.phoneNumber = phoneNumber;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Validates if the user's status is active
     */
    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    /**
     * Validates the username format
     */
    private static void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        
        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalArgumentException("Username must be between 3 and 50 characters");
        }
        
        if (!username.matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Username can only contain letters, numbers, periods, underscores, and hyphens");
        }
    }

    /**
     * Validates the email format
     */
    private static void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }

    /**
     * Validates the phone number format
     */
    private static void validatePhoneNumber(String phoneNumber) {
        if (!PHONE_PATTERN.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("Invalid phone number format. Must start with + followed by 10-15 digits");
        }
    }
    
    /**
     * Get KYC status - deprecated, use KYC service instead
     */
    @Deprecated
    public KycStatus getKycStatus() {
        // Return NOT_STARTED as default since KYC is now handled by KYC service
        return KycStatus.NOT_STARTED;
    }
    
    // Fraud and security management methods
    
    public boolean isAccountLocked() {
        return accountLocked;
    }
    
    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getLockReason() {
        return lockReason;
    }
    
    public void setLockReason(String lockReason) {
        this.lockReason = lockReason;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
    
    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isTransactionRestricted() {
        return transactionRestricted;
    }
    
    public void setTransactionRestricted(boolean transactionRestricted) {
        this.transactionRestricted = transactionRestricted;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getRestrictionReason() {
        return restrictionReason;
    }
    
    public void setRestrictionReason(String restrictionReason) {
        this.restrictionReason = restrictionReason;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getRestrictedUntil() {
        return restrictedUntil;
    }
    
    public void setRestrictedUntil(LocalDateTime restrictedUntil) {
        this.restrictedUntil = restrictedUntil;
        this.updatedAt = LocalDateTime.now();
    }
    
    public BigDecimal getDailyTransactionLimit() {
        return dailyTransactionLimit;
    }
    
    public void setDailyTransactionLimit(BigDecimal dailyTransactionLimit) {
        this.dailyTransactionLimit = dailyTransactionLimit;
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isRequiresManualReview() {
        return requiresManualReview;
    }
    
    public void setRequiresManualReview(boolean requiresManualReview) {
        this.requiresManualReview = requiresManualReview;
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isEnhancedMonitoring() {
        return enhancedMonitoring;
    }
    
    public void setEnhancedMonitoring(boolean enhancedMonitoring) {
        this.enhancedMonitoring = enhancedMonitoring;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getMonitoringReason() {
        return monitoringReason;
    }
    
    public void setMonitoringReason(String monitoringReason) {
        this.monitoringReason = monitoringReason;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getMonitoringUntil() {
        return monitoringUntil;
    }
    
    public void setMonitoringUntil(LocalDateTime monitoringUntil) {
        this.monitoringUntil = monitoringUntil;
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isRequiresTwoFactorAuth() {
        return requiresTwoFactorAuth;
    }
    
    public void setRequiresTwoFactorAuth(boolean requiresTwoFactorAuth) {
        this.requiresTwoFactorAuth = requiresTwoFactorAuth;
        this.updatedAt = LocalDateTime.now();
    }
    
    public int getWarningCount() {
        return warningCount;
    }
    
    public void incrementWarningCount() {
        this.warningCount++;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getLastWarningDate() {
        return lastWarningDate;
    }
    
    public void setLastWarningDate(LocalDateTime lastWarningDate) {
        this.lastWarningDate = lastWarningDate;
        this.updatedAt = LocalDateTime.now();
    }
    
    public int getFraudAlertCount() {
        return fraudAlertCount;
    }
    
    public void incrementFraudAlertCount() {
        this.fraudAlertCount++;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getLastFraudAlertDate() {
        return lastFraudAlertDate;
    }
    
    public void setLastFraudAlertDate(LocalDateTime lastFraudAlertDate) {
        this.lastFraudAlertDate = lastFraudAlertDate;
        this.updatedAt = LocalDateTime.now();
    }
    
    public Double getFraudRiskScore() {
        return fraudRiskScore;
    }
    
    public void updateFraudRiskScore(Double newScore) {
        // Weighted average with historical score
        if (this.fraudRiskScore == null || this.fraudRiskScore == 0.0) {
            this.fraudRiskScore = newScore;
        } else {
            this.fraudRiskScore = (this.fraudRiskScore * 0.7) + (newScore * 0.3);
        }
        this.updatedAt = LocalDateTime.now();
    }

    public String getPendingEmail() {
        return pendingEmail;
    }

    public void setPendingEmail(String pendingEmail) {
        this.pendingEmail = pendingEmail;
        this.updatedAt = LocalDateTime.now();
    }

    public String getPendingPhoneNumber() {
        return pendingPhoneNumber;
    }

    public void setPendingPhoneNumber(String pendingPhoneNumber) {
        this.pendingPhoneNumber = pendingPhoneNumber;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Gets date of birth from user profile (helper method for guardianship logic)
     * Note: This is intentionally not stored in the User entity for data normalization.
     * Use UserProfile service to retrieve this information.
     */
    public java.time.LocalDate getDateOfBirth() {
        return dateOfBirthField;
    }
    
    public void setDateOfBirth(java.time.LocalDate dateOfBirth) {
        this.dateOfBirthField = dateOfBirth;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Get user ID as string for compatibility
     */
    public String getId() {
        return id != null ? id.toString() : null;
    }
    
    /**
     * Get user UUID ID
     */
    public UUID getUuidId() {
        return id;
    }
}