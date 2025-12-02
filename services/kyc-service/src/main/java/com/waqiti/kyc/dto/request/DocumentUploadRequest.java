package com.waqiti.kyc.dto.request;

import com.waqiti.kyc.domain.VerificationDocument.DocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadRequest {

    @NotNull(message = "Document type is required")
    private DocumentType documentType;

    @NotNull(message = "Document file is required")
    private MultipartFile file;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private String documentNumber; // Passport number, license number, etc.

    private String issuingCountry;

    private String expiryDate;

    private boolean isFront; // For documents with front/back

    private String metadata;
}