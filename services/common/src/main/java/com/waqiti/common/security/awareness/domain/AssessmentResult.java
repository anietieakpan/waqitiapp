package com.waqiti.common.security.awareness.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Assessment Result
 *
 * Tracks individual employee assessment attempts and results.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Entity
@Table(name = "assessment_results", indexes = {
        @Index(name = "idx_result_employee", columnList = "employee_id"),
        @Index(name = "idx_result_assessment", columnList = "assessment_id"),
        @Index(name = "idx_result_completed", columnList = "completed_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "result_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private QuarterlySecurityAssessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "attempt_number")
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "passed")
    private Boolean passed;

    @Type(JsonBinaryType.class)
    @Column(name = "answers", columnDefinition = "jsonb")
    private String answers; // JSON array of answers

    @Column(name = "questions_answered")
    @Builder.Default
    private Integer questionsAnswered = 0;

    @Column(name = "correct_answers")
    @Builder.Default
    private Integer correctAnswers = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Calculate score based on correct answers
     */
    public void calculateScore() {
        if (questionsAnswered > 0) {
            this.score = BigDecimal.valueOf(correctAnswers)
                    .divide(BigDecimal.valueOf(questionsAnswered), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Determine if passed based on assessment passing score
            if (assessment != null && assessment.getPassingScore() != null) {
                this.passed = this.score.compareTo(
                        BigDecimal.valueOf(assessment.getPassingScore())
                ) >= 0;
            }
        }
    }

    /**
     * Complete the assessment
     */
    public void complete() {
        this.completedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.durationMinutes = (int) java.time.Duration
                    .between(this.startedAt, this.completedAt)
                    .toMinutes();
        }
        calculateScore();
    }

    /**
     * Convenience getters for compatibility
     */
    public UUID getAssessmentId() {
        return assessment != null ? assessment.getId() : null;
    }

    public UUID getEmployeeId() {
        return employee != null ? employee.getId() : null;
    }

    public void setTimeTakenMinutes(int minutes) {
        this.durationMinutes = minutes;
    }

    public void setScorePercentage(int percentage) {
        this.score = BigDecimal.valueOf(percentage);
    }

    public void setRequiresRemediation(boolean requires) {
        // This is derived from passed status
    }

    public void setFeedbackProvided(String feedback) {
        // Could store in answers JSON if needed
    }

    public void setAnswersData(List<com.waqiti.common.security.awareness.model.QuestionResult> results) {
        // Store as JSON
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            this.answers = mapper.writeValueAsString(results);
        } catch (Exception e) {
            // Ignore
        }
    }

    public boolean isPassed() {
        return Boolean.TRUE.equals(this.passed);
    }
}