package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Relationship Entity
 *
 * Represents relationships between customers such as beneficial ownership,
 * authorized signers, family relationships, and business associations.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_relationship", indexes = {
    @Index(name = "idx_customer_relationship_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_relationship_related", columnList = "related_customer_id"),
    @Index(name = "idx_customer_relationship_type", columnList = "relationship_type")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"customer", "relatedCustomer"})
@EqualsAndHashCode(of = "relationshipId")
public class CustomerRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "relationship_id", unique = true, nullable = false, length = 100)
    private String relationshipId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Column(name = "related_customer_id", nullable = false, length = 100)
    private String relatedCustomerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer relatedCustomer;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 50)
    private RelationshipType relationshipType;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_status", length = 20)
    @Builder.Default
    private RelationshipStatus relationshipStatus = RelationshipStatus.ACTIVE;

    @Column(name = "ownership_percentage", precision = 5, scale = 4)
    private BigDecimal ownershipPercentage;

    @Column(name = "authority_level", length = 50)
    private String authorityLevel;

    @Column(name = "valid_from", nullable = false)
    @Builder.Default
    private LocalDate validFrom = LocalDate.now();

    @Column(name = "valid_to")
    private LocalDate validTo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum RelationshipType {
        BENEFICIAL_OWNER,
        AUTHORIZED_SIGNER,
        POWER_OF_ATTORNEY,
        GUARDIAN,
        TRUSTEE,
        JOINT_ACCOUNT_HOLDER,
        FAMILY_MEMBER,
        SPOUSE,
        PARENT,
        CHILD,
        SIBLING,
        BUSINESS_PARTNER,
        SHAREHOLDER,
        DIRECTOR,
        OFFICER,
        EMPLOYEE,
        REPRESENTATIVE,
        REFERRER,
        OTHER
    }

    public enum RelationshipStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        TERMINATED,
        PENDING_APPROVAL
    }

    /**
     * Check if relationship is currently active
     *
     * @return true if active
     */
    public boolean isActive() {
        return relationshipStatus == RelationshipStatus.ACTIVE;
    }

    /**
     * Check if relationship is currently valid based on dates
     *
     * @return true if valid
     */
    public boolean isValid() {
        LocalDate now = LocalDate.now();
        boolean afterValidFrom = validFrom == null || !now.isBefore(validFrom);
        boolean beforeValidTo = validTo == null || !now.isAfter(validTo);
        return afterValidFrom && beforeValidTo && isActive();
    }

    /**
     * Check if relationship has expired
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return validTo != null && LocalDate.now().isAfter(validTo);
    }

    /**
     * Check if relationship involves ownership
     *
     * @return true if ownership percentage is set
     */
    public boolean hasOwnership() {
        return ownershipPercentage != null && ownershipPercentage.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if beneficial owner (ownership >= 25%)
     *
     * @return true if beneficial owner
     */
    public boolean isBeneficialOwner() {
        return hasOwnership() &&
               ownershipPercentage.compareTo(new BigDecimal("0.25")) >= 0;
    }

    /**
     * Check if majority owner (ownership > 50%)
     *
     * @return true if majority owner
     */
    public boolean isMajorityOwner() {
        return hasOwnership() &&
               ownershipPercentage.compareTo(new BigDecimal("0.50")) > 0;
    }

    /**
     * Check if has signing authority
     *
     * @return true if authorized signer or has power of attorney
     */
    public boolean hasSigningAuthority() {
        return relationshipType == RelationshipType.AUTHORIZED_SIGNER ||
               relationshipType == RelationshipType.POWER_OF_ATTORNEY;
    }

    /**
     * Terminate the relationship
     */
    public void terminate() {
        this.relationshipStatus = RelationshipStatus.TERMINATED;
        this.validTo = LocalDate.now();
    }

    /**
     * Suspend the relationship
     */
    public void suspend() {
        this.relationshipStatus = RelationshipStatus.SUSPENDED;
    }

    /**
     * Activate the relationship
     */
    public void activate() {
        this.relationshipStatus = RelationshipStatus.ACTIVE;
    }

    /**
     * Update ownership percentage
     *
     * @param percentage the new ownership percentage (0.0 to 1.0)
     */
    public void updateOwnership(BigDecimal percentage) {
        if (percentage != null &&
            percentage.compareTo(BigDecimal.ZERO) >= 0 &&
            percentage.compareTo(BigDecimal.ONE) <= 0) {
            this.ownershipPercentage = percentage;
        } else {
            throw new IllegalArgumentException("Ownership percentage must be between 0 and 1");
        }
    }
}
