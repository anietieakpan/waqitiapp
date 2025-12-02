package com.waqiti.kyc.dto.request;

import com.waqiti.kyc.domain.KYCVerification.KYCStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    @NotNull(message = "Decision is required")
    private KYCStatus decision; // APPROVED, REJECTED, REQUIRES_ADDITIONAL_INFO

    @Size(max = 1000, message = "Comments must not exceed 1000 characters")
    private String comments;

    private String rejectionReason;

    private Map<String, String> checkResults;

    private String riskScore;

    private String reviewerId;

    private boolean requiresEscalation;

    private String escalationReason;

    private Map<String, Object> additionalData;
}