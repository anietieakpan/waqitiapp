package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Customer Entity - Base customer record
 *
 * Represents the core customer profile supporting both individual and business customers.
 * This is the anchor entity for all customer-related data.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer", indexes = {
    @Index(name = "idx_customer_customer_id", columnList = "customer_id", unique = true),
    @Index(name = "idx_customer_status", columnList = "customer_status"),
    @Index(name = "idx_customer_type", columnList = "customer_type"),
    @Index(name = "idx_customer_kyc_status", columnList = "kyc_status"),
    @Index(name = "idx_customer_risk_level", columnList = "risk_level"),
    @Index(name = "idx_customer_segment", columnList = "customer_segment"),
    @Index(name = "idx_customer_onboarding_date", columnList = "onboarding_date"),
    @Index(name = "idx_customer_last_activity", columnList = "last_activity_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"individual", "business"})
@EqualsAndHashCode(of = "customerId")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_id", unique = true, nullable = false, length = 100)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_status", nullable = false, length = 20)
    @Builder.Default
    private CustomerStatus customerStatus = CustomerStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.MEDIUM;

    @Column(name = "customer_segment", length = 50)
    private String customerSegment;

    @Column(name = "preferred_language", length = 10)
    @Builder.Default
    private String preferredLanguage = "en";

    @Column(name = "preferred_currency", length = 3)
    @Builder.Default
    private String preferredCurrency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "kyc_verified_at")
    private LocalDateTime kycVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "aml_status", nullable = false, length = 20)
    @Builder.Default
    private AmlStatus amlStatus = AmlStatus.PENDING;

    @Column(name = "aml_verified_at")
    private LocalDateTime amlVerifiedAt;

    @Column(name = "relationship_manager_id", length = 100)
    private String relationshipManagerId;

    @Column(name = "onboarding_date", nullable = false)
    @Builder.Default
    private LocalDateTime onboardingDate = LocalDateTime.now();

    @Column(name = "onboarding_channel", length = 50)
    private String onboardingChannel;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_pep")
    @Builder.Default
    private Boolean isPep = false;

    @Column(name = "is_sanctioned")
    @Builder.Default
    private Boolean isSanctioned = false;

    @Column(name = "is_blocked")
    @Builder.Default
    private Boolean isBlocked = false;

    @Column(name = "blocked_reason", columnDefinition = "TEXT")
    private String blockedReason;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    // Relationships
    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CustomerIndividual individual;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CustomerBusiness business;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CustomerContact> contacts;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CustomerAddress> addresses;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CustomerIdentification> identifications;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CustomerPreference preference;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CustomerDocument> documents;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CustomerNote> notes_list;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CustomerInteraction> interactions;

    // Enums
    public enum CustomerType {
        INDIVIDUAL,
        BUSINESS
    }

    public enum CustomerStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        CLOSED,
        DECEASED
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum KycStatus {
        PENDING,
        IN_PROGRESS,
        VERIFIED,
        REJECTED,
        EXPIRED
    }

    public enum AmlStatus {
        PENDING,
        IN_PROGRESS,
        CLEARED,
        FLAGGED,
        REJECTED
    }

    // Helper Methods
    public boolean isActive() {
        return customerStatus == CustomerStatus.ACTIVE;
    }

    public boolean isBlocked() {
        return isBlocked != null && isBlocked;
    }

    public boolean isKycVerified() {
        return kycStatus == KycStatus.VERIFIED;
    }

    public boolean isAmlCleared() {
        return amlStatus == AmlStatus.CLEARED;
    }

    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }

    public void block(String reason) {
        this.isBlocked = true;
        this.blockedReason = reason;
        this.blockedAt = LocalDateTime.now();
        this.customerStatus = CustomerStatus.SUSPENDED;
    }

    public void unblock() {
        this.isBlocked = false;
        this.blockedReason = null;
        this.blockedAt = null;
        if (this.customerStatus == CustomerStatus.SUSPENDED) {
            this.customerStatus = CustomerStatus.ACTIVE;
        }
    }

    public void verifyKyc() {
        this.kycStatus = KycStatus.VERIFIED;
        this.kycVerifiedAt = LocalDateTime.now();
    }

    public void clearAml() {
        this.amlStatus = AmlStatus.CLEARED;
        this.amlVerifiedAt = LocalDateTime.now();
    }

    public void updateLastActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
        this.lastActivityAt = LocalDateTime.now();
    }
}
