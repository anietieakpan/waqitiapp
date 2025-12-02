package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * DTO for user validation requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserValidationRequest {
    
    @NotBlank
    private String userId;
    
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private String validationType;
    private Map<String, String> context;
}