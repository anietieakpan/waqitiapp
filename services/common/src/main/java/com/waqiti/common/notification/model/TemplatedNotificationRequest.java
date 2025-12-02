package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * Request for templated notifications
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class TemplatedNotificationRequest extends NotificationRequest {
    
    /**
     * Template ID
     */
    private String templateId;
    
    /**
     * Template version (optional)
     */
    private String templateVersion;
    
    /**
     * Template variables
     */
    private Map<String, Object> variables;
    
    /**
     * Language/locale override
     */
    private String locale;
    
    /**
     * Recipient information
     */
    private Map<String, Object> recipientInfo;
    
    /**
     * Channel-specific overrides
     */
    private Map<String, Object> channelOverrides;
    
    /**
     * Whether to use default values for missing variables
     */
    @Builder.Default
    private boolean useDefaults = true;
    
    /**
     * Whether to validate template variables
     */
    @Builder.Default
    private boolean validateVariables = true;
    
    /**
     * Custom headers for email templates
     */
    private Map<String, String> customHeaders;
    
}