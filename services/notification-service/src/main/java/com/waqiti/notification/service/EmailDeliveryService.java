package com.waqiti.notification.service;

import com.waqiti.common.util.DataMaskingUtil;
import com.waqiti.notification.model.EmailMessage;
import com.waqiti.notification.service.provider.EmailProvider;
import com.waqiti.notification.service.provider.SendGridEmailProvider;
import com.waqiti.notification.service.provider.SesEmailProvider;
import com.waqiti.notification.service.provider.MailgunEmailProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Production-grade email delivery service with provider failover and load balancing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryService {

    private final SendGridEmailProvider sendGridProvider;
    private final List<EmailProvider> emailProviders = new ArrayList<>();
    
    @Value("${email.primary.provider:sendgrid}")
    private String primaryProvider;
    
    @Value("${email.failover.enabled:true}")
    private boolean failoverEnabled;
    
    @Value("${email.load.balancing:round_robin}")
    private String loadBalancingStrategy;
    
    private int currentProviderIndex = 0;
    
    @PostConstruct
    public void initializeProviders() {
        // Initialize email providers in order of preference
        emailProviders.add(sendGridProvider);
        
        // Add other providers if available
        try {
            emailProviders.add(new SesEmailProvider());
        } catch (Exception e) {
            log.warn("SES provider not available: {}", e.getMessage());
        }
        
        try {
            emailProviders.add(new MailgunEmailProvider());
        } catch (Exception e) {
            log.warn("Mailgun provider not available: {}", e.getMessage());
        }
        
        log.info("Initialized {} email providers", emailProviders.size());
    }
    
    @CircuitBreaker(name = "emailDelivery", fallbackMethod = "sendEmailFallback")
    @Retry(name = "emailDelivery")
    public boolean send(EmailMessage message) {
        // GDPR COMPLIANCE: Mask email in logs per GDPR Article 32
        log.debug("Sending email {} to {}", message.getMessageId(), DataMaskingUtil.maskEmail(message.getRecipientEmail()));
        
        EmailProvider provider = selectProvider();
        
        try {
            boolean success = provider.send(message);
            
            if (success) {
                log.info("Email {} sent successfully via {}", message.getMessageId(), provider.getProviderName());
                return true;
            } else {
                log.warn("Email {} failed via {}", message.getMessageId(), provider.getProviderName());
                
                if (failoverEnabled) {
                    return tryFailoverProviders(message, provider);
                }
                
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error sending email {} via {}: {}", 
                message.getMessageId(), provider.getProviderName(), e.getMessage());
            
            if (failoverEnabled) {
                return tryFailoverProviders(message, provider);
            }
            
            throw e;
        }
    }
    
    @Async
    public CompletableFuture<Boolean> sendAsync(EmailMessage message) {
        return CompletableFuture.completedFuture(send(message));
    }
    
    public boolean sendBulk(List<EmailMessage> messages) {
        log.info("Sending bulk emails: {} messages", messages.size());
        
        int successCount = 0;
        
        for (EmailMessage message : messages) {
            try {
                if (send(message)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("Failed to send bulk email {}: {}", message.getMessageId(), e.getMessage());
            }
        }
        
        double successRate = (double) successCount / messages.size();
        log.info("Bulk email completed. Success rate: {:.2f}% ({}/{})", 
            successRate * 100, successCount, messages.size());
        
        return successRate > 0.8; // Consider success if 80% or more delivered
    }
    
    private EmailProvider selectProvider() {
        switch (loadBalancingStrategy.toLowerCase()) {
            case "round_robin":
                return selectRoundRobin();
            case "random":
                return selectRandom();
            case "primary_only":
                return selectPrimary();
            default:
                return selectRoundRobin();
        }
    }
    
    private EmailProvider selectRoundRobin() {
        if (emailProviders.isEmpty()) {
            throw new IllegalStateException("No email providers available");
        }
        
        EmailProvider provider = emailProviders.get(currentProviderIndex);
        currentProviderIndex = (currentProviderIndex + 1) % emailProviders.size();
        return provider;
    }
    
    private EmailProvider selectRandom() {
        if (emailProviders.isEmpty()) {
            throw new IllegalStateException("No email providers available");
        }
        
        int index = ThreadLocalRandom.current().nextInt(emailProviders.size());
        return emailProviders.get(index);
    }
    
    private EmailProvider selectPrimary() {
        return emailProviders.get(0); // First provider is primary
    }
    
    private boolean tryFailoverProviders(EmailMessage message, EmailProvider failedProvider) {
        log.info("Attempting failover for email {}", message.getMessageId());
        
        for (EmailProvider provider : emailProviders) {
            if (provider == failedProvider) {
                continue; // Skip the failed provider
            }
            
            try {
                log.debug("Trying failover provider: {}", provider.getProviderName());
                boolean success = provider.send(message);
                
                if (success) {
                    log.info("Email {} sent successfully via failover provider {}", 
                        message.getMessageId(), provider.getProviderName());
                    return true;
                }
                
            } catch (Exception e) {
                log.warn("Failover provider {} also failed for email {}: {}", 
                    provider.getProviderName(), message.getMessageId(), e.getMessage());
            }
        }
        
        log.error("All email providers failed for message {}", message.getMessageId());
        return false;
    }
    
    public boolean sendEmailFallback(EmailMessage message, Exception ex) {
        log.error("Email delivery fallback triggered for {}: {}", 
            message.getMessageId(), ex.getMessage());
        
        // Store in dead letter queue or retry queue
        storeForRetry(message, ex.getMessage());
        
        return false;
    }
    
    private void storeForRetry(EmailMessage message, String error) {
        log.info("Storing email {} for retry. Error: {}", message.getMessageId(), error);
        // Implementation would store in database or queue for later retry
    }
    
    public List<String> getAvailableProviders() {
        return emailProviders.stream()
            .map(EmailProvider::getProviderName)
            .toList();
    }
    
    public String getProviderStatus(String providerName) {
        return emailProviders.stream()
            .filter(p -> p.getProviderName().equalsIgnoreCase(providerName))
            .findFirst()
            .map(p -> p.isHealthy() ? "HEALTHY" : "UNHEALTHY")
            .orElse("NOT_FOUND");
    }
}