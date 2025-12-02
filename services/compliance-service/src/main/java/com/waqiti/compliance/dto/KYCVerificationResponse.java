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
public class KYCVerificationResponse {
    private UUID verificationId;
    private UUID userId;
    private String status; // INITIATED, DOCUMENT_PENDING, IN_REVIEW, APPROVED, REJECTED
    private String kycLevel;
    private List<String> documentsRequired;
    private List<String> documentsSubmitted;
    private Map<String, String> verificationResults;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime verifiedAt;
    private String reviewerNotes;
    private Double confidenceScore;
    private List<String> riskFlags;
}