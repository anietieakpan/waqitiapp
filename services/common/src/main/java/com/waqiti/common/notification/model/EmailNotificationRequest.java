package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * Email notification request with full configuration
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class EmailNotificationRequest extends NotificationRequest {
    
    /**
     * Recipient email addresses
     */
    private List<String> to;
    
    /**
     * CC recipients
     */
    private List<String> cc;
    
    /**
     * BCC recipients
     */
    private List<String> bcc;
    
    /**
     * Reply-to address
     */
    private String replyTo;
    
    /**
     * Email subject
     */
    private String subject;
    
    /**
     * Plain text content
     */
    private String textContent;
    
    /**
     * HTML content
     */
    private String htmlContent;
    
    /**
     * Template ID for templated emails
     */
    private String templateId;
    
    /**
     * Template variables (alias: templateData for backward compatibility)
     */
    private Map<String, Object> templateVariables;

    /**
     * Get template data (alias for templateVariables)
     */
    public Map<String, Object> getTemplateData() {
        return this.templateVariables;
    }

    /**
     * Set template data (alias for templateVariables)
     */
    public void setTemplateData(Map<String, Object> templateData) {
        this.templateVariables = templateData;
    }
    
    /**
     * Email attachments - filename to content mapping
     */
    private Map<String, String> attachments;
    
    /**
     * Custom headers
     */
    private Map<String, String> headers;
    
    // Email priority is inherited from NotificationRequest
    
    /**
     * Email tracking settings
     */
    @Builder.Default
    private boolean trackOpens = true;
    @Builder.Default
    private boolean trackClicks = true;
    @Builder.Default
    private boolean trackBounces = true;
    @Builder.Default
    private boolean trackComplaints = true;
    private String trackingDomain;
    
    /**
     * Whether to use TLS
     */
    @Builder.Default
    private boolean useTls = true;
    
    /**
     * Custom email tags for categorization
     */
    private List<String> tags;
    
    /**
     * Sender configuration
     */
    private String fromEmail;
    private String fromName;
    private String returnPath;
    private String configurationSetName;
    
    // Inner classes removed - fields flattened into main class
    
    public enum EmailPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}