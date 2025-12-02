package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "aml_screenings")
public class AMLScreening {
    @Id
    private String id;
    private String eventId;
    private String userId;
    private String transactionId;

    // Entity information
    private String entityId;
    private String entityType;
    private String entityName;
    private String entityCountry;

    // Screening details
    private AMLScreeningType screeningType;
    private String screeningProvider;
    private String screeningReference;
    private String screeningReason;
    private AMLStatus status;
    
    // Subject information
    private String subjectName;
    private String subjectDateOfBirth;
    private String subjectNationality;
    private String subjectAddress;
    private String subjectIdentification;
    
    // Screening results
    private AMLResult result;
    private Double riskScore;
    private AMLRiskLevel riskLevel;
    private List<SanctionMatch> matches;
    private List<String> watchlistHits;
    private List<String> sanctionMatches;
    private List<String> pepMatches;

    // Individual screening results
    private Integer sanctionsHits;
    private LocalDateTime sanctionsScreenedAt;
    private String sanctionsError;
    private Integer pepHits;
    private LocalDateTime pepScreenedAt;
    private String pepError;
    private Integer adverseMediaHits;
    private LocalDateTime adverseMediaScreenedAt;
    private String adverseMediaError;

    // Match details
    private Integer totalMatches;
    private Integer highRiskMatches;
    private Integer mediumRiskMatches;
    private Integer lowRiskMatches;
    private boolean falsePositive;
    private String falsePositiveReason;

    // Review and decision
    private String reviewerId;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
    private AMLDecision decision;
    private String decisionReason;
    private LocalDateTime decisionMadeAt;
    private boolean escalated;
    private String escalationReason;

    // Manual review requirements
    private boolean requiresManualReview;
    private LocalDateTime reviewDeadline;
    private String reviewPriority;
    private List<String> requiredActions;
    
    // Transaction context
    private BigDecimal transactionAmount;
    private String transactionCurrency;
    private String transactionType;
    private String originCountry;
    private String destinationCountry;
    
    // Compliance actions
    private boolean blocked;
    private LocalDateTime blockedAt;
    private String blockReason;
    private boolean reported;
    private LocalDateTime reportedAt;
    private String reportReference;
    
    // Provider response
    private Map<String, Object> providerResponse;
    private String providerRecommendation;
    private List<String> complianceFlags;
    
    // Processing
    private LocalDateTime screeningStarted;
    private LocalDateTime screeningCompleted;
    private LocalDateTime completedAt;
    private Long screeningTimeMs;
    private Long processingTimeMs;

    // Metadata
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // === NESTED CLASSES AND ENUMS ===

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AMLMatch {
        private String matchId;
        private String listName;
        private String matchType;
        private String matchedName;
        private Double confidence;
        private String category;
        private String description;
        private Map<String, String> details;
    }

    public enum AMLScreeningType {
        CUSTOMER_ONBOARDING,
        TRANSACTION_MONITORING,
        PERIODIC_REVIEW,
        EVENT_TRIGGERED,
        MANUAL_REQUEST
    }

    public enum AMLStatus {
        INITIATED,
        IN_PROGRESS,
        COMPLETED,
        UNDER_REVIEW,
        APPROVED,
        REJECTED,
        ESCALATED,
        FAILED
    }

    public enum AMLResult {
        CLEAR,
        HIT,
        POTENTIAL_MATCH,
        REVIEW_REQUIRED,
        HIGH_RISK,
        BLOCKED
    }

    public enum AMLDecision {
        APPROVE,
        REJECT,
        ESCALATE,
        REQUEST_MORE_INFO,
        ONGOING_MONITORING
    }
}