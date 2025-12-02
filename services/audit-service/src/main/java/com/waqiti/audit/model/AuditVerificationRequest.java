package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request for audit trail verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditVerificationRequest {
    
    @JsonProperty("entity_id")
    @Size(max = 100)
    private String entityId;
    
    @JsonProperty("entity_type")
    @Size(max = 50)
    private String entityType;
    
    @JsonProperty("start_date")
    private LocalDate startDate;
    
    @JsonProperty("end_date")
    private LocalDate endDate;
    
    @JsonProperty("verification_type")
    private VerificationType verificationType;
    
    @JsonProperty("include_chain_verification")
    private Boolean includeChainVerification;
    
    @JsonProperty("include_signature_verification")
    private Boolean includeSignatureVerification;
    
    public enum VerificationType {
        INTEGRITY_ONLY,
        CHAIN_INTEGRITY,
        FULL_VERIFICATION
    }
}