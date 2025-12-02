package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KYCDocumentUploadRequest {
    private UUID userId;
    private String documentType; // PASSPORT, DRIVER_LICENSE, NATIONAL_ID, PROOF_OF_ADDRESS
    private MultipartFile file;
    private String documentNumber;
    private String issuingCountry;
    private String expiryDate;
}