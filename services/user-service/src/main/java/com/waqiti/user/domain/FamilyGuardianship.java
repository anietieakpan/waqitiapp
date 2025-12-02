package com.waqiti.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entity representing a family guardianship relationship
 * Manages guardian-dependent relationships with permissions and approval workflows
 */
@Entity
@Table(name = "family_guardianships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FamilyGuardianship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, name = "guardian_user_id")
    private UUID guardianUserId;

    @Column(nullable = false, name = "dependent_user_id")
    private UUID dependentUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "guardian_type")
    private GuardianType guardianType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "status")
    private GuardianshipStatus status;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "guardianship_permissions", 
                    joinColumns = @JoinColumn(name = "guardianship_id"))
    @Column(name = "permission")
    private Set<String> permissions = new HashSet<>();

    @Column(name = "established_by")
    private UUID establishedBy;

    @Column(name = "relationship_type")
    private String relationshipType; // Parent, Legal Guardian, etc.

    @Column(name = "legal_document_reference")
    private String legalDocumentReference; // Court order, etc.

    @Column(name = "emergency_contact", columnDefinition = "boolean default false")
    private Boolean emergencyContact = false;

    @Column(name = "financial_oversight", columnDefinition = "boolean default true")
    private Boolean financialOversight = true;

    @Column(name = "can_approve_transactions", columnDefinition = "boolean default true")
    private Boolean canApproveTransactions = true;

    @Column(name = "max_approval_amount")
    private java.math.BigDecimal maxApprovalAmount;

    @Column(name = "can_modify_limits", columnDefinition = "boolean default false")
    private Boolean canModifyLimits = false;

    @Column(name = "can_view_statements", columnDefinition = "boolean default true")
    private Boolean canViewStatements = true;

    @Column(name = "notification_preferences")
    private String notificationPreferences; // JSON string

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // For temporary guardianships

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Version
    private Long version;

    // Audit fields
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Creates a new family guardianship relationship
     */
    public static FamilyGuardianship create(UUID guardianUserId, UUID dependentUserId, 
                                          GuardianType guardianType, UUID establishedBy) {
        FamilyGuardianship guardianship = FamilyGuardianship.builder()
            .guardianUserId(guardianUserId)
            .dependentUserId(dependentUserId)
            .guardianType(guardianType)
            .status(GuardianshipStatus.PENDING)
            .permissions(getDefaultPermissions(guardianType))
            .establishedBy(establishedBy)
            .emergencyContact(false)
            .financialOversight(true)
            .canApproveTransactions(true)
            .canModifyLimits(guardianType == GuardianType.PRIMARY)
            .canViewStatements(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .createdBy(establishedBy)
            .build();

        return guardianship;
    }

    /**
     * Activates the guardianship relationship
     */
    public void activate(UUID activatedBy) {
        this.status = GuardianshipStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = activatedBy;
        this.lastActivityAt = LocalDateTime.now();
    }

    /**
     * Suspends the guardianship relationship
     */
    public void suspend(UUID suspendedBy, String reason) {
        this.status = GuardianshipStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = suspendedBy;
        // Store reason in notification preferences temporarily
        this.notificationPreferences = reason;
    }

    /**
     * Terminates the guardianship relationship
     */
    public void terminate(UUID terminatedBy, String reason) {
        this.status = GuardianshipStatus.TERMINATED;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = terminatedBy;
        this.notificationPreferences = reason;
    }

    /**
     * Updates guardian permissions
     */
    public void updatePermissions(Set<String> newPermissions, UUID updatedBy) {
        this.permissions = new HashSet<>(newPermissions);
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = updatedBy;
    }

    /**
     * Updates financial oversight settings
     */
    public void updateFinancialSettings(Boolean financialOversight, Boolean canApproveTransactions,
                                       java.math.BigDecimal maxApprovalAmount, Boolean canModifyLimits,
                                       UUID updatedBy) {
        this.financialOversight = financialOversight;
        this.canApproveTransactions = canApproveTransactions;
        this.maxApprovalAmount = maxApprovalAmount;
        this.canModifyLimits = canModifyLimits;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = updatedBy;
    }

    /**
     * Records guardian activity
     */
    public void recordActivity() {
        this.lastActivityAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if guardianship is active and not expired
     */
    public boolean isActiveAndValid() {
        return status == GuardianshipStatus.ACTIVE && 
               (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }

    /**
     * Checks if guardian can perform a specific financial action
     */
    public boolean canPerformFinancialAction(java.math.BigDecimal amount) {
        if (!isActiveAndValid() || !canApproveTransactions || !financialOversight) {
            return false;
        }
        
        return maxApprovalAmount == null || 
               amount.compareTo(maxApprovalAmount) <= 0;
    }

    /**
     * Gets default permissions for guardian type
     */
    private static Set<String> getDefaultPermissions(GuardianType guardianType) {
        Set<String> permissions = new HashSet<>();
        
        switch (guardianType) {
            case PRIMARY:
                permissions.add("ACCOUNT_MANAGEMENT");
                permissions.add("FINANCIAL_OVERSIGHT");
                permissions.add("LIMIT_MANAGEMENT");
                permissions.add("PROFILE_MANAGEMENT");
                permissions.add("GUARDIAN_MANAGEMENT");
                permissions.add("VIEW_FINANCIAL");
                permissions.add("BASIC_OVERSIGHT");
                break;
            case SECONDARY:
                permissions.add("FINANCIAL_OVERSIGHT");
                permissions.add("PROFILE_MANAGEMENT");
                permissions.add("VIEW_FINANCIAL");
                permissions.add("BASIC_OVERSIGHT");
                break;
            case EMERGENCY:
                permissions.add("FINANCIAL_OVERSIGHT");
                permissions.add("VIEW_FINANCIAL");
                permissions.add("BASIC_OVERSIGHT");
                break;
            case TEMPORARY:
                permissions.add("BASIC_OVERSIGHT");
                break;
        }
        
        return permissions;
    }

    // Setters with audit trail

    public void setGuardianType(GuardianType guardianType) {
        this.guardianType = guardianType;
        this.updatedAt = LocalDateTime.now();
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
        this.updatedAt = LocalDateTime.now();
    }

    public void setLegalDocumentReference(String legalDocumentReference) {
        this.legalDocumentReference = legalDocumentReference;
        this.updatedAt = LocalDateTime.now();
    }

    public void setEmergencyContact(Boolean emergencyContact) {
        this.emergencyContact = emergencyContact;
        this.updatedAt = LocalDateTime.now();
    }

    public void setCanViewStatements(Boolean canViewStatements) {
        this.canViewStatements = canViewStatements;
        this.updatedAt = LocalDateTime.now();
    }

    public void setNotificationPreferences(String notificationPreferences) {
        this.notificationPreferences = notificationPreferences;
        this.updatedAt = LocalDateTime.now();
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Guardian type enumeration
     */
    public enum GuardianType {
        PRIMARY,    // Full access and control
        SECONDARY,  // Limited access, can assist primary
        EMERGENCY,  // Emergency-only access
        TEMPORARY   // Time-limited access
    }

    /**
     * Guardianship status enumeration
     */
    public enum GuardianshipStatus {
        PENDING,     // Awaiting activation
        ACTIVE,      // Active and operational
        SUSPENDED,   // Temporarily suspended
        TERMINATED   // Permanently ended
    }
}