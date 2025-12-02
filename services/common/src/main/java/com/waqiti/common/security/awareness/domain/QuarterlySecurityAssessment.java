package com.waqiti.common.security.awareness.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Quarterly Security Assessment
 *
 * Represents a quarterly security knowledge assessment for employees.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Entity
@Table(name = "quarterly_security_assessments", indexes = {
        @Index(name = "idx_assessment_quarter", columnList = "quarter, year"),
        @Index(name = "idx_assessment_status", columnList = "status")
})
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class QuarterlySecurityAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "assessment_id")
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "quarter", nullable = false)
    private Integer quarter; // 1, 2, 3, or 4

    @Column(name = "year", nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, length = 50)
    private AssessmentType assessmentType;

    @Type(JsonBinaryType.class)
    @Column(name = "questions", columnDefinition = "jsonb")
    private String questions; // JSON array of questions

    @Column(name = "total_questions")
    @Builder.Default
    private Integer totalQuestions = 0;

    @Column(name = "passing_score")
    @Builder.Default
    private Integer passingScore = 70;

    @Column(name = "time_limit_minutes")
    @Builder.Default
    private Integer timeLimitMinutes = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AssessmentStatus status = AssessmentStatus.DRAFT;

    @Column(name = "available_from")
    private LocalDateTime availableFrom;

    @Column(name = "available_until")
    private LocalDateTime availableUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version")
    private Long version;

    public enum AssessmentType {
        GENERAL_SECURITY,
        PHISHING_AWARENESS,
        DATA_PROTECTION,
        PASSWORD_SECURITY,
        SOCIAL_ENGINEERING,
        COMPLIANCE_TRAINING,
        INCIDENT_RESPONSE
    }

    public enum AssessmentStatus {
        DRAFT,
        PUBLISHED,
        IN_PROGRESS,
        COMPLETED,
        ARCHIVED
    }

    /**
     * Check if assessment is currently available
     */
    public boolean isAvailable() {
        LocalDateTime now = LocalDateTime.now();
        return status == AssessmentStatus.PUBLISHED &&
                (availableFrom == null || now.isAfter(availableFrom)) &&
                (availableUntil == null || now.isBefore(availableUntil));
    }

    /**
     * Publish assessment
     */
    public void publish() {
        this.status = AssessmentStatus.PUBLISHED;
        if (this.availableFrom == null) {
            this.availableFrom = LocalDateTime.now();
        }
    }

    /**
     * Convenience getters for compatibility
     */
    public String getAssessmentName() {
        return this.title;
    }

    public List<String> getTargetRoles() {
        return Collections.emptyList(); // Default: all roles
    }

    public Integer getPassingScorePercentage() {
        return this.passingScore;
    }
}