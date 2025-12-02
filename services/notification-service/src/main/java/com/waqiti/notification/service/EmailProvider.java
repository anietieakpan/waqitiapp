package com.waqiti.notification.service;

import com.waqiti.notification.dto.EmailRequest;
import com.waqiti.notification.dto.EmailResponse;

/**
 * Email Provider Interface
 * 
 * Defines the contract for email service providers
 */
public interface EmailProvider {
    
    /**
     * Sends an email through the provider
     * 
     * @param request The email request details
     * @return EmailResponse containing the result
     */
    EmailResponse sendEmail(EmailRequest request);
    
    /**
     * Gets the delivery status of an email
     * 
     * @param messageId The message identifier
     * @return EmailResponse with current status
     */
    EmailResponse getEmailStatus(String messageId);
    
    /**
     * Checks if the provider is healthy and responsive
     * 
     * @return true if the provider is healthy
     */
    boolean isHealthy();
    
    /**
     * Gets the provider name
     * 
     * @return The name of the email provider
     */
    String getProviderName();
}