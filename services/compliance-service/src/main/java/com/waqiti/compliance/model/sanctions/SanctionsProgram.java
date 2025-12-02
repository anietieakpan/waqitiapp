package com.waqiti.compliance.model.sanctions;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sanctions Program Entity
 *
 * Represents sanctions programs from OFAC, EU, and UN.
 * Examples: UKRAINE-EO13661, IRAN, SYRIA, TERRORISM
 *
 * CRITICAL COMPLIANCE:
 * Each sanctioned entity must be linked to one or more sanctions programs
 * for proper regulatory reporting and risk assessment.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sanctions_programs", indexes = {
        @Index(name = "idx_sanctions_programs_code", columnList = "program_code"),
        @Index(name = "idx_sanctions_programs_jurisdiction", columnList = "jurisdiction"),
        @Index(name = "idx_sanctions_programs_active", columnList = "is_active")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_sanctions_program_code", columnNames = {"program_code"})
})
public class SanctionsProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    // Program identification
    @Column(name = "program_code", nullable = false, unique = true, length = 50)
    private String programCode;

    @Column(name = "program_name", nullable = false, length = 200)
    private String programName;

    @Column(name = "program_type", length = 100)
    private String programType;

    // Jurisdiction
    @Column(name = "jurisdiction", nullable = false, length = 10)
    private String jurisdiction; // US, EU, UN

    @Column(name = "issuing_authority", length = 200)
    private String issuingAuthority;

    // Legal basis
    @Column(name = "legal_reference", length = 500)
    private String legalReference;

    @Column(name = "executive_order", length = 100)
    private String executiveOrder;

    @Column(name = "regulation_reference", length = 100)
    private String regulationReference;

    // Program details
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_countries", length = 500)
    private String targetCountries; // Comma-separated ISO codes

    @Column(name = "target_sectors", length = 500)
    private String targetSectors;

    // Status
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    // Audit
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
