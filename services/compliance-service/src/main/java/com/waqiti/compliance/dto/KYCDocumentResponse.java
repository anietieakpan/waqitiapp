package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KYCDocumentResponse {
    private UUID documentId;
    private UUID userId;
    private String documentType;
    private String status; // UPLOADED, VERIFIED, REJECTED, EXPIRED
    private String fileName;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private LocalDateTime verifiedAt;
    private String verificationResult;
    private String rejectionReason;
}