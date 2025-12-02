package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * DTO for SMS notification requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmsNotificationRequest {
    
    @NotBlank
    private String phoneNumber;
    
    @NotBlank
    private String message;
    
    private String userId;
    private String template;
    private Map<String, Object> templateData;
    
    @Builder.Default
    private String priority = "NORMAL";
    
    private String senderId;
    private String countryCode;
    private boolean unicode;
    
    private Map<String, String> metadata;
}