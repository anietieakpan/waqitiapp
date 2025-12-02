package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

import java.util.List;
import java.util.Map;

/**
 * DTO for email notification requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailNotificationRequest {
    
    @NotBlank
    @Email
    private String email;
    
    @NotBlank
    private String subject;
    
    @NotBlank
    private String message;
    
    private String userId;
    private String template;
    private Map<String, Object> templateData;
    
    @Builder.Default
    private String priority = "NORMAL";
    
    private String fromEmail;
    private String fromName;
    private List<String> ccEmails;
    private List<String> bccEmails;
    
    @Builder.Default
    private boolean htmlContent = false;
    
    private List<EmailAttachment> attachments;
    
    private Map<String, String> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailAttachment {
        private String filename;
        private String contentType;
        private byte[] content;
        private String contentId;
    }
}