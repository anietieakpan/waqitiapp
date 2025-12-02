package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * DTO for user validation results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserValidationResult {
    
    private boolean valid;
    private String userId;
    private String status;
    private String errorCode;
    private String errorMessage;
    private List<String> validationErrors;
    private String sessionId;
}