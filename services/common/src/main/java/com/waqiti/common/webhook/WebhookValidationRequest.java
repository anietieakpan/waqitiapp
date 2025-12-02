package com.waqiti.common.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request object for webhook validation.
 * Contains all necessary information to validate an incoming webhook.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookValidationRequest {
    
    /**
     * The webhook provider (e.g., "stripe", "paypal", "razorpay")
     */
    private String provider;
    
    /**
     * The raw webhook payload
     */
    private String payload;
    
    /**
     * The signature provided in the webhook headers
     */
    private String signature;
    
    /**
     * The webhook secret for signature validation
     */
    private String secret;
    
    /**
     * The timestamp from webhook headers (for replay attack prevention)
     */
    private String timestamp;
    
    /**
     * Optional nonce for additional replay protection
     */
    private String nonce;
    
    /**
     * The source IP address of the webhook
     */
    private String sourceIp;
    
    /**
     * Additional headers from the webhook request
     */
    private java.util.Map<String, String> headers;
    
    /**
     * The HTTP method used (POST, PUT, etc.)
     */
    private String httpMethod;
    
    /**
     * The endpoint path that received the webhook
     */
    private String endpointPath;
}