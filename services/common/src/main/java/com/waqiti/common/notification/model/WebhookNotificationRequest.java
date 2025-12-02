package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Webhook notification request
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class WebhookNotificationRequest extends NotificationRequest {
    
    /**
     * Webhook endpoint URL
     */
    private String webhookUrl;
    
    /**
     * HTTP method to use
     */
    @Builder.Default
    private HttpMethod method = HttpMethod.POST;
    
    /**
     * Request headers
     */
    private Map<String, String> headers;
    
    /**
     * Request body payload
     */
    private Object payload;
    
    /**
     * Content type
     */
    @Builder.Default
    private String contentType = "application/json";
    
    /**
     * Authentication configuration
     */
    private Map<String, Object> authentication;
    
    /**
     * Timeout in seconds
     */
    @Builder.Default
    private int timeoutSeconds = 30;
    
    /**
     * Whether to verify SSL certificate
     */
    @Builder.Default
    private boolean verifySsl = true;
    
    /**
     * Success criteria
     */
    private Map<String, Object> successCriteria;
    
    /**
     * Whether to follow redirects
     */
    @Builder.Default
    private boolean followRedirects = true;
    
    /**
     * Signature configuration for webhook security
     */
    private Map<String, Object> signatureConfig;
    
    
    public enum HttpMethod {
        GET, POST, PUT, PATCH, DELETE
    }
    
    public enum AuthType {
        NONE, BASIC, BEARER, API_KEY, OAUTH2, CUSTOM
    }
}