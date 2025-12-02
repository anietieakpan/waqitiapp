package com.waqiti.compliance.entity;

import com.waqiti.compliance.enums.CrimeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Regulatory Report Entity
 *
 * Tracks all regulatory reports filed with authorities including FinCEN SAR,
 * FBI IC3, SEC enforcement, and other regulatory bodies.
 *
 * Compliance: FinCEN SAR, FBI IC3, SEC Enforcement
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Entity
@Table(name = "regulatory_reports", indexes = {
    @Index(name = "idx_regulatory_reports_user", columnList = "user_id"),
    @Index(name = "idx_regulatory_reports_fincen", columnList = "fincen_reference_number"),
    @Index(name = "idx_regulatory_reports_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatoryReport {

    @Id
    @Column(name = "report_id", nullable = false, length = 255)
    private String reportId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "crime_type", nullable = false, length = 100)
    private CrimeType crimeType;

    @Type(type = "jsonb")
    @Column(name = "evidence", columnDefinition = "jsonb")
    private Map<String, Object> evidence;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Column(name = "reported_by", nullable = false, length = 255)
    private String reportedBy;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "fincen_reference_number", length = 255)
    private String finCENReferenceNumber;

    @Column(name = "fbi_reference_number", length = 255)
    private String fbiReferenceNumber;

    @Column(name = "sec_reference_number", length = 255)
    private String secReferenceNumber;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /**
     * Check if report has been filed with FinCEN
     */
    public boolean isFiledWithFinCEN() {
        return finCENReferenceNumber != null && !finCENReferenceNumber.isEmpty();
    }

    /**
     * Check if report is pending submission
     */
    public boolean isPending() {
        return "PENDING".equals(status) || "QUEUED".equals(status);
    }

    /**
     * Check if report has been successfully submitted
     */
    public boolean isSubmitted() {
        return "SUBMITTED".equals(status) || "ACCEPTED".equals(status);
    }
}
