package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * DTO for bulk notification requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkNotificationRequest {
    
    @NotEmpty
    private List<String> recipients;
    
    @NotBlank
    private String notificationType;
    
    @NotBlank
    private String title;
    
    @NotBlank
    private String message;
    
    private String template;
    private Map<String, Object> templateData;
    
    // Channel preferences
    @Builder.Default
    private boolean sendEmail = true;
    
    @Builder.Default
    private boolean sendSms = false;
    
    @Builder.Default
    private boolean sendPush = true;
    
    @Builder.Default
    private String priority = "NORMAL";
    
    private String scheduledAt;
    private Map<String, String> metadata;
}