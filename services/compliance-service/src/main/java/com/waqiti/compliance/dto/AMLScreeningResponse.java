package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLScreeningResponse {
    
    private UUID screeningId;
    private String transactionId;
    private String status; // APPROVED, REJECTED, PENDING_REVIEW
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private Double riskScore;
    private Boolean requiresManualReview;
    private List<String> alerts;
    private List<String> matchedRules;
    private String recommendation;
    private LocalDateTime screenedAt;
    private Map<String, Object> screeningDetails;
    private String reviewerNotes;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
}