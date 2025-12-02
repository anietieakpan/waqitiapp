package com.waqiti.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enterprise-grade Permission entity for fine-grained access control.
 *
 * Permission Naming Convention:
 * - Format: RESOURCE:ACTION
 * - Examples:
 *   - USER:READ
 *   - USER:WRITE
 *   - USER:DELETE
 *   - PAYMENT:PROCESS
 *   - PAYMENT:REFUND
 *   - TRANSACTION:VIEW
 *   - COMPLIANCE:AUDIT
 *   - SYSTEM:CONFIGURE
 *
 * Categories:
 * - USER_MANAGEMENT
 * - PAYMENT_PROCESSING
 * - WALLET_OPERATIONS
 * - COMPLIANCE_AUDIT
 * - FRAUD_DETECTION
 * - REPORTING
 * - SYSTEM_ADMINISTRATION
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Entity
@Table(name = "permissions", indexes = {
    @Index(name = "idx_permissions_name", columnList = "name", unique = true),
    @Index(name = "idx_permissions_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private PermissionCategory category;

    @Column(name = "resource", nullable = false, length = 50)
    private String resource;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "is_system_permission", nullable = false)
    @Builder.Default
    private Boolean isSystemPermission = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // Enums
    public enum PermissionCategory {
        USER_MANAGEMENT,
        PAYMENT_PROCESSING,
        WALLET_OPERATIONS,
        COMPLIANCE_AUDIT,
        FRAUD_DETECTION,
        REPORTING,
        SYSTEM_ADMINISTRATION,
        API_ACCESS,
        MERCHANT_OPERATIONS,
        SUPPORT_OPERATIONS,
        ANALYTICS
    }
}
