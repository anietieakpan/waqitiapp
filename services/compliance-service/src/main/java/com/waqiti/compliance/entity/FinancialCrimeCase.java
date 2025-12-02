package com.waqiti.compliance.entity;

import com.waqiti.compliance.enums.CrimeSeverity;
import com.waqiti.compliance.enums.CrimeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Financial Crime Case Entity
 *
 * Represents a financial crime investigation case with complete audit trail.
 * Supports FinCEN SAR filing, law enforcement reporting, and evidence tracking.
 *
 * Compliance: BSA/AML, FinCEN, USA PATRIOT Act
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Entity
@Table(name = "financial_crime_cases", indexes = {
    @Index(name = "idx_crime_cases_user", columnList = "user_id"),
    @Index(name = "idx_crime_cases_status", columnList = "status"),
    @Index(name = "idx_crime_cases_type", columnList = "crime_type"),
    @Index(name = "idx_crime_cases_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialCrimeCase {

    @Id
    @Column(name = "case_id", nullable = false, length = 255)
    private String caseId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "crime_type", nullable = false, length = 100)
    private CrimeType crimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    private CrimeSeverity severity;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "priority", nullable = false, length = 10)
    private String priority;

    @Type(type = "jsonb")
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "assigned_to", nullable = false, length = 255)
    private String assignedTo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Type(type = "jsonb")
    @Column(name = "linked_transactions", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> linkedTransactions = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /**
     * Add linked transaction to case
     */
    public void addLinkedTransaction(String transactionId) {
        if (linkedTransactions == null) {
            linkedTransactions = new ArrayList<>();
        }
        if (!linkedTransactions.contains(transactionId)) {
            linkedTransactions.add(transactionId);
        }
    }

    /**
     * Check if case is high priority
     */
    public boolean isHighPriority() {
        return "P0".equals(priority) || "P1".equals(priority);
    }

    /**
     * Check if case is open
     */
    public boolean isOpen() {
        return "OPEN".equals(status) || "INVESTIGATING".equals(status);
    }
}
