package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response containing audit trail verification results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditVerificationResponse {
    
    @JsonProperty("is_valid")
    private Boolean isValid;
    
    @JsonProperty("total_events")
    private Integer totalEvents;
    
    @JsonProperty("verified_events")
    private Integer verifiedEvents;
    
    @JsonProperty("integrity_issues")
    private List<String> integrityIssues;
    
    @JsonProperty("verification_timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime verificationTimestamp;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("verification_details")
    private List<VerificationDetail> verificationDetails;
    
    @JsonProperty("chain_integrity_verified")
    private Boolean chainIntegrityVerified;
    
    @JsonProperty("signature_verification_count")
    private Integer signatureVerificationCount;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VerificationDetail {
        
        @JsonProperty("event_id")
        private String eventId;
        
        @JsonProperty("verification_type")
        private String verificationType;
        
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("details")
        private String details;
    }
}