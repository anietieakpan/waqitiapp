package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.DisputeType;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Request to create a new dispute
 */
@Data
@Builder
public class DisputeRequest {
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotNull(message = "Dispute type is required")
    private DisputeType disputeType;
    
    @NotBlank(message = "Reason is required")
    @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
    private String reason;
    
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;
    
    private String contactEmail;
    private String contactPhone;
    
    // Initial evidence URLs if available
    private String[] evidenceUrls;
    
    // Additional metadata
    private Map<String, String> metadata;
    
    // For chargeback disputes
    private String chargebackCode;
    private String providerCaseId;
    
    /**
     * Create fraud dispute request
     */
    public static DisputeRequest fraudDispute(String transactionId, String userId, String reason) {
        return DisputeRequest.builder()
            .transactionId(transactionId)
            .userId(userId)
            .disputeType(DisputeType.FRAUD)
            .reason(reason)
            .build();
    }
    
    /**
     * Create service issue dispute request
     */
    public static DisputeRequest serviceDispute(String transactionId, String userId, String reason, String description) {
        return DisputeRequest.builder()
            .transactionId(transactionId)
            .userId(userId)
            .disputeType(DisputeType.SERVICE_ISSUE)
            .reason(reason)
            .description(description)
            .build();
    }
    
    /**
     * Create chargeback dispute request
     */
    public static DisputeRequest chargebackDispute(String transactionId, String userId, String chargebackCode, String providerCaseId) {
        return DisputeRequest.builder()
            .transactionId(transactionId)
            .userId(userId)
            .disputeType(DisputeType.CHARGEBACK)
            .reason("Chargeback initiated by card issuer")
            .chargebackCode(chargebackCode)
            .providerCaseId(providerCaseId)
            .build();
    }
}