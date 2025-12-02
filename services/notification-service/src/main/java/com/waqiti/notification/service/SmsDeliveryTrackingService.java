package com.waqiti.notification.service;

import com.waqiti.notification.domain.SmsDeliveryStatus;
import com.waqiti.notification.domain.SmsMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface SmsDeliveryTrackingService {
    
    /**
     * Send SMS with delivery tracking
     */
    CompletableFuture<SmsDeliveryStatus> sendAndTrackSms(SmsMessage message);
    
    /**
     * Get delivery status for a message
     */
    SmsDeliveryStatus getDeliveryStatus(String messageId);
    
    /**
     * Update delivery status from webhook
     */
    void updateDeliveryStatus(String messageId, SmsDeliveryStatus status);
    
    /**
     * Get delivery statistics for a time period
     */
    Map<String, Object> getDeliveryStatistics(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get failed deliveries for retry
     */
    List<SmsMessage> getFailedDeliveries(LocalDateTime since);
    
    /**
     * Retry failed SMS delivery
     */
    CompletableFuture<SmsDeliveryStatus> retryDelivery(String messageId);
    
    /**
     * Get delivery report for user
     */
    List<SmsDeliveryStatus> getUserDeliveryReport(String userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Cancel scheduled SMS
     */
    boolean cancelScheduledSms(String messageId);
    
    /**
     * Get pending deliveries
     */
    List<SmsMessage> getPendingDeliveries();
}