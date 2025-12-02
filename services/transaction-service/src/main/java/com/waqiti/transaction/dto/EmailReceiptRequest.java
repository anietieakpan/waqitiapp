package com.waqiti.transaction.dto;

import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for emailing receipts
 */
@Data
public class EmailReceiptRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    private String subject;
    private String message;
    private boolean includeCompanyBranding = true;
}