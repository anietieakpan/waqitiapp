package com.waqiti.notification.service.provider;

import com.waqiti.notification.model.EmailMessage;

/**
 * Interface for email delivery providers
 */
public interface EmailProvider {
    
    /**
     * Send an email message
     * @param message the email message to send
     * @return true if sent successfully, false otherwise
     */
    boolean send(EmailMessage message);
    
    /**
     * Get the provider name
     * @return provider name
     */
    String getProviderName();
    
    /**
     * Check if the provider is healthy and available
     * @return true if healthy, false otherwise
     */
    boolean isHealthy();
    
    /**
     * Get provider-specific configuration or status information
     * @return configuration map
     */
    java.util.Map<String, Object> getStatus();
    
    /**
     * Initialize the provider with configuration
     * @param config configuration parameters
     */
    void initialize(java.util.Map<String, Object> config);
    
    /**
     * Check if provider supports bulk sending
     * @return true if bulk sending is supported
     */
    default boolean supportsBulkSending() {
        return false;
    }
    
    /**
     * Send multiple emails in a single batch (if supported)
     * @param messages list of email messages
     * @return number of successfully sent messages
     */
    default int sendBulk(java.util.List<EmailMessage> messages) {
        int successCount = 0;
        for (EmailMessage message : messages) {
            if (send(message)) {
                successCount++;
            }
        }
        return successCount;
    }
}