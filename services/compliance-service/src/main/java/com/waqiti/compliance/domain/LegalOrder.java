package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL P0 FIX: Legal Order Entity
 *
 * Represents court orders, legal freezes, garnishments, and other legal processes
 * requiring asset freezes or fund releases.
 *
 * Compliance Requirements:
 * - Federal Rules of Civil Procedure (FRCP)
 * - State garnishment laws
 * - IRS levy requirements (26 USC §6331)
 * - Child support enforcement (UIFSA)
 * - Criminal asset forfeiture (18 USC §983)
 *
 * Legal Order Types:
 * - COURT_ORDER: General court-ordered asset freeze
 * - GARNISHMENT: Wage/account garnishment
 * - TAX_LEVY: IRS/state tax authority levy
 * - CHILD_SUPPORT: Child support order
 * - CRIMINAL_FORFEITURE: Criminal asset seizure
 * - REGULATORY_FREEZE: SEC/FINRA/OCC freeze order
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-05
 */
@Entity
@Table(name = "legal_orders", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_order_number", columnList = "orderNumber", unique = true),
    @Index(name = "idx_order_type", columnList = "orderType"),
    @Index(name = "idx_order_status", columnList = "orderStatus"),
    @Index(name = "idx_issuing_authority", columnList = "issuingAuthority"),
    @Index(name = "idx_expiration_date", columnList = "expirationDate"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "order_id")
    private UUID orderId;

    /**
     * Court order/case number
     */
    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    /**
     * Type of legal order
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    /**
     * User ID subject to legal order
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Wallet ID to freeze (if applicable)
     */
    @Column(name = "wallet_id")
    private UUID walletId;

    /**
     * Amount to freeze/garnish (null = entire balance)
     */
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217)
     */
    @Column(name = "currency", length = 3)
    private String currency;

    /**
     * Issuing authority (court, agency, etc.)
     */
    @Column(name = "issuing_authority", nullable = false)
    private String issuingAuthority;

    /**
     * Issuing jurisdiction (state, federal, etc.)
     */
    @Column(name = "jurisdiction", nullable = false)
    private String jurisdiction;

    /**
     * Court/case identifier
     */
    @Column(name = "case_number")
    private String caseNumber;

    /**
     * Judge name
     */
    @Column(name = "judge_name")
    private String judgeName;

    /**
     * Order issuance date
     */
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    /**
     * Order expiration date (if applicable)
     */
    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    /**
     * Date order was received by platform
     */
    @Column(name = "received_date", nullable = false)
    private LocalDateTime receivedDate;

    /**
     * Order status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    @Builder.Default
    private OrderStatus orderStatus = OrderStatus.PENDING_REVIEW;

    /**
     * Verification status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    /**
     * Verified by (legal team member)
     */
    @Column(name = "verified_by")
    private String verifiedBy;

    /**
     * Verification date
     */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * Legal counsel assigned
     */
    @Column(name = "assigned_counsel")
    private String assignedCounsel;

    /**
     * Order description/notes
     */
    @Column(name = "description", length = 2000)
    private String description;

    /**
     * Compliance notes
     */
    @Column(name = "compliance_notes", length = 2000)
    private String complianceNotes;

    /**
     * Document storage path
     */
    @Column(name = "document_path")
    private String documentPath;

    /**
     * Freeze ID if wallet was frozen
     */
    @Column(name = "freeze_id")
    private UUID freezeId;

    /**
     * Funds released date
     */
    @Column(name = "funds_released_date")
    private LocalDateTime fundsReleasedDate;

    /**
     * Amount released
     */
    @Column(name = "amount_released", precision = 19, scale = 4)
    private BigDecimal amountReleased;

    /**
     * Priority level for processing
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    /**
     * Audit fields
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Correlation ID for event tracing
     */
    @Column(name = "correlation_id")
    private String correlationId;

    /**
     * Version for optimistic locking
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Legal order types
     */
    public enum OrderType {
        COURT_ORDER,
        GARNISHMENT,
        TAX_LEVY,
        CHILD_SUPPORT,
        CRIMINAL_FORFEITURE,
        REGULATORY_FREEZE,
        SUBPOENA,
        RESTRAINING_ORDER,
        OTHER
    }

    /**
     * Order processing status
     */
    public enum OrderStatus {
        PENDING_REVIEW,
        UNDER_REVIEW,
        VERIFIED,
        EXECUTED,
        PARTIALLY_EXECUTED,
        EXPIRED,
        RELEASED,
        APPEALED,
        REJECTED,
        ERROR
    }

    /**
     * Order verification status
     */
    public enum VerificationStatus {
        UNVERIFIED,
        PENDING_VERIFICATION,
        VERIFIED,
        VERIFICATION_FAILED,
        FRAUDULENT
    }

    /**
     * Processing priority
     */
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT,
        CRITICAL
    }
}
