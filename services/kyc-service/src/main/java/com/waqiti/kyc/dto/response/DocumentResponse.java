package com.waqiti.kyc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.waqiti.kyc.domain.VerificationDocument.DocumentStatus;
import com.waqiti.kyc.domain.VerificationDocument.DocumentType;
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
    private String downloadUrl; // Pre-signed URL for download
    private Integer downloadUrlExpiresIn; // Seconds until URL expires
    
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> metadata;
}