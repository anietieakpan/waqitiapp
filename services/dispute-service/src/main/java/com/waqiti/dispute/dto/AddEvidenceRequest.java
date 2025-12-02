package com.waqiti.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * Request DTO for adding evidence to a dispute
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddEvidenceRequest {

    @NotBlank(message = "Dispute ID is required")
    private String disputeId;

    @NotNull(message = "File is required")
    private MultipartFile file;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Evidence type is required")
    private String evidenceType;

    @NotBlank(message = "Uploaded by is required")
    private String uploadedBy;
}
