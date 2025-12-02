package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.EvidenceType;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Evidence submission for dispute
 */
@Data
@Builder
public class EvidenceSubmission {
    
    @NotNull(message = "Evidence type is required")
    private EvidenceType evidenceType;
    
    @NotBlank(message = "Submitter ID is required")
    private String submittedBy;
    
    private String documentUrl;
    private String description;
    private Map<String, String> metadata;
    
    // For file uploads
    private byte[] documentData;
    private String documentName;
    private String documentMimeType;
    
    /**
     * Create receipt evidence submission
     */
    public static EvidenceSubmission receipt(String submittedBy, String documentUrl) {
        return EvidenceSubmission.builder()
            .evidenceType(EvidenceType.RECEIPT)
            .submittedBy(submittedBy)
            .documentUrl(documentUrl)
            .build();
    }
    
    /**
     * Create communication evidence submission
     */
    public static EvidenceSubmission communication(String submittedBy, String description, String documentUrl) {
        return EvidenceSubmission.builder()
            .evidenceType(EvidenceType.COMMUNICATION)
            .submittedBy(submittedBy)
            .description(description)
            .documentUrl(documentUrl)
            .build();
    }
    
    /**
     * Create system-generated evidence submission
     */
    public static EvidenceSubmission systemGenerated(EvidenceType type, String description, Map<String, String> metadata) {
        return EvidenceSubmission.builder()
            .evidenceType(type)
            .submittedBy("SYSTEM")
            .description(description)
            .metadata(metadata)
            .build();
    }
}