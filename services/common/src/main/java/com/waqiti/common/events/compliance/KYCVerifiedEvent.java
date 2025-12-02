package com.waqiti.common.events.compliance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KYCVerifiedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "Correlation ID is required")
    @JsonProperty("correlation_id")
    private String correlationId;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @JsonProperty("event_version")
    private String eventVersion = "1.0";

    @NotBlank(message = "Event source is required")
    @JsonProperty("source")
    private String source;

    @NotBlank(message = "Verification ID is required")
    @JsonProperty("verification_id")
    private String verificationId;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "KYC level is required")
    @JsonProperty("kyc_level")
    private String kycLevel;

    @JsonProperty("verification_type")
    private String verificationType;

    @NotBlank(message = "Verified by is required")
    @JsonProperty("verified_by")
    private String verifiedBy;

    @NotNull(message = "Verified at timestamp is required")
    @JsonProperty("verified_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime verifiedAt;

    @JsonProperty("previous_kyc_level")
    private String previousKycLevel;

    @JsonProperty("documents_verified")
    private Integer documentsVerified;

    @JsonProperty("risk_score")
    private Integer riskScore;

    @JsonProperty("compliance_notes")
    private String complianceNotes;
}