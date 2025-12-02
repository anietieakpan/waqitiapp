package com.waqiti.common.kyc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentResponse {

    private String id;
    private String verificationId;
    private DocumentType documentType;
    private DocumentStatus status;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime expiresAt;
    
    private String documentNumber;
    private String issuingCountry;
    private Boolean isFront;
    
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> extractedData;
    
    private String rejectionReason;
    private String downloadUrl;
    private Integer downloadUrlExpiresIn;
    
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> metadata;
    
    public enum DocumentType {
        PASSPORT,
        DRIVERS_LICENSE,
        NATIONAL_ID,
        PROOF_OF_ADDRESS,
        BANK_STATEMENT,
        UTILITY_BILL,
        SELFIE,
        SELFIE_WITH_DOCUMENT,
        OTHER
    }
    
    public enum DocumentStatus {
        PENDING,
        PROCESSING,
        VERIFIED,
        REJECTED,
        EXPIRED
    }
}