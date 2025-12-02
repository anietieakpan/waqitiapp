package com.waqiti.notification.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized metrics service for notification system
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    
    // Counters
    private Counter emailsSentCounter;
    private Counter emailsDeliveredCounter;
    private Counter emailsFailedCounter;
    private Counter emailsBouncedCounter;
    private Counter emailsComplaintCounter;
    private Counter emailsOpenedCounter;
    private Counter emailsClickedCounter;
    private Counter emailsUnsubscribedCounter;
    
    private Counter smsSentCounter;
    private Counter smsDeliveredCounter;
    private Counter smsFailedCounter;
    
    private Counter pushSentCounter;
    private Counter pushDeliveredCounter;
    private Counter pushFailedCounter;
    
    private Counter inAppSentCounter;
    private Counter inAppDeliveredCounter;
    private Counter inAppReadCounter;
    private Counter inAppDismissedCounter;
    
    // Timers
    private Timer emailProcessingTimer;
    private Timer smsProcessingTimer;
    private Timer pushProcessingTimer;
    private Timer inAppProcessingTimer;
    
    // Gauges (using atomic values)
    private final AtomicLong activeEmailConnections = new AtomicLong(0);
    private final AtomicLong activeSmsConnections = new AtomicLong(0);
    private final AtomicLong activePushConnections = new AtomicLong(0);
    private final AtomicLong activeInAppConnections = new AtomicLong(0);
    
    private final AtomicLong queuedEmails = new AtomicLong(0);
    private final AtomicLong queuedSms = new AtomicLong(0);
    private final AtomicLong queuedPush = new AtomicLong(0);
    private final AtomicLong queuedInApp = new AtomicLong(0);
    
    // Category-specific metrics storage
    private final Map<String, CategoryMetrics> categoryMetrics = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initializeMetrics() {
        // Email metrics
        emailsSentCounter = Counter.builder("notifications.emails.sent.total")
            .description("Total number of emails sent")
            .register(meterRegistry);
        
        emailsDeliveredCounter = Counter.builder("notifications.emails.delivered.total")
            .description("Total number of emails delivered")
            .register(meterRegistry);
        
        emailsFailedCounter = Counter.builder("notifications.emails.failed.total")
            .description("Total number of emails failed")
            .register(meterRegistry);
        
        emailsBouncedCounter = Counter.builder("notifications.emails.bounced.total")
            .description("Total number of emails bounced")
            .register(meterRegistry);
        
        emailsComplaintCounter = Counter.builder("notifications.emails.complaints.total")
            .description("Total number of email complaints")
            .register(meterRegistry);
        
        emailsOpenedCounter = Counter.builder("notifications.emails.opened.total")
            .description("Total number of emails opened")
            .register(meterRegistry);
        
        emailsClickedCounter = Counter.builder("notifications.emails.clicked.total")
            .description("Total number of emails clicked")
            .register(meterRegistry);
        
        emailsUnsubscribedCounter = Counter.builder("notifications.emails.unsubscribed.total")
            .description("Total number of email unsubscribes")
            .register(meterRegistry);
        
        // SMS metrics
        smsSentCounter = Counter.builder("notifications.sms.sent.total")
            .description("Total number of SMS sent")
            .register(meterRegistry);
        
        smsDeliveredCounter = Counter.builder("notifications.sms.delivered.total")
            .description("Total number of SMS delivered")
            .register(meterRegistry);
        
        smsFailedCounter = Counter.builder("notifications.sms.failed.total")
            .description("Total number of SMS failed")
            .register(meterRegistry);
        
        // Push notification metrics
        pushSentCounter = Counter.builder("notifications.push.sent.total")
            .description("Total number of push notifications sent")
            .register(meterRegistry);
        
        pushDeliveredCounter = Counter.builder("notifications.push.delivered.total")
            .description("Total number of push notifications delivered")
            .register(meterRegistry);
        
        pushFailedCounter = Counter.builder("notifications.push.failed.total")
            .description("Total number of push notifications failed")
            .register(meterRegistry);
        
        // In-app notification metrics
        inAppSentCounter = Counter.builder("notifications.inapp.sent.total")
            .description("Total number of in-app notifications sent")
            .register(meterRegistry);
        
        inAppDeliveredCounter = Counter.builder("notifications.inapp.delivered.total")
            .description("Total number of in-app notifications delivered")
            .register(meterRegistry);
        
        inAppReadCounter = Counter.builder("notifications.inapp.read.total")
            .description("Total number of in-app notifications read")
            .register(meterRegistry);
        
        inAppDismissedCounter = Counter.builder("notifications.inapp.dismissed.total")
            .description("Total number of in-app notifications dismissed")
            .register(meterRegistry);
        
        // Timers
        emailProcessingTimer = Timer.builder("notifications.email.processing.duration")
            .description("Email processing duration")
            .register(meterRegistry);
        
        smsProcessingTimer = Timer.builder("notifications.sms.processing.duration")
            .description("SMS processing duration")
            .register(meterRegistry);
        
        pushProcessingTimer = Timer.builder("notifications.push.processing.duration")
            .description("Push notification processing duration")
            .register(meterRegistry);
        
        inAppProcessingTimer = Timer.builder("notifications.inapp.processing.duration")
            .description("In-app notification processing duration")
            .register(meterRegistry);
        
        // Gauges
        Gauge.builder("notifications.connections.email.active")
            .description("Number of active email connections")
            .register(meterRegistry, activeEmailConnections, AtomicLong::get);
        
        Gauge.builder("notifications.connections.sms.active")
            .description("Number of active SMS connections")
            .register(meterRegistry, activeSmsConnections, AtomicLong::get);
        
        Gauge.builder("notifications.connections.push.active")
            .description("Number of active push connections")
            .register(meterRegistry, activePushConnections, AtomicLong::get);
        
        Gauge.builder("notifications.connections.inapp.active")
            .description("Number of active in-app connections")
            .register(meterRegistry, activeInAppConnections, AtomicLong::get);
        
        Gauge.builder("notifications.queue.email.size")
            .description("Number of queued emails")
            .register(meterRegistry, queuedEmails, AtomicLong::get);
        
        Gauge.builder("notifications.queue.sms.size")
            .description("Number of queued SMS")
            .register(meterRegistry, queuedSms, AtomicLong::get);
        
        Gauge.builder("notifications.queue.push.size")
            .description("Number of queued push notifications")
            .register(meterRegistry, queuedPush, AtomicLong::get);
        
        Gauge.builder("notifications.queue.inapp.size")
            .description("Number of queued in-app notifications")
            .register(meterRegistry, queuedInApp, AtomicLong::get);
        
        log.info("Notification metrics initialized successfully");
    }
    
    // Email metrics methods
    public void recordEmailSent(String category) {
        emailsSentCounter.increment();
        getCategoryMetrics(category).emailsSent.incrementAndGet();
    }
    
    public void recordEmailDelivered(String recipientEmail) {
        emailsDeliveredCounter.increment();
        log.debug("Email delivered metric recorded for: {}", maskEmail(recipientEmail));
    }
    
    public void recordEmailFailed(String recipientEmail, String errorCode) {
        emailsFailedCounter.increment();
        log.debug("Email failed metric recorded for: {} with error: {}", maskEmail(recipientEmail), errorCode);
    }
    
    public void recordEmailBounce(String recipientEmail, String bounceType) {
        emailsBouncedCounter.increment();
        log.debug("Email bounce metric recorded for: {} type: {}", maskEmail(recipientEmail), bounceType);
    }
    
    public void recordEmailComplaint(String recipientEmail, String complaintType) {
        emailsComplaintCounter.increment();
        log.debug("Email complaint metric recorded for: {} type: {}", maskEmail(recipientEmail), complaintType);
    }
    
    public void recordEmailOpen(String recipientEmail) {
        emailsOpenedCounter.increment();
        log.debug("Email open metric recorded for: {}", maskEmail(recipientEmail));
    }
    
    public void recordEmailClick(String recipientEmail, String url) {
        emailsClickedCounter.increment();
        log.debug("Email click metric recorded for: {} URL: {}", maskEmail(recipientEmail), url);
    }
    
    public void recordEmailUnsubscribe(String recipientEmail, String category) {
        emailsUnsubscribedCounter.increment();
        log.debug("Email unsubscribe metric recorded for: {} category: {}", maskEmail(recipientEmail), category);
    }
    
    public void recordDeliveryRate(String category, double deliveryRate) {
        CategoryMetrics metrics = getCategoryMetrics(category);
        metrics.deliveryRate.set(Double.doubleToLongBits(deliveryRate));
        
        Gauge.builder("notifications.delivery.rate")
            .tag("category", category)
            .description("Delivery rate for category: " + category)
            .register(meterRegistry, metrics.deliveryRate, value -> Double.longBitsToDouble(value.get()));
    }
    
    public void recordBounceRate(String category, double bounceRate) {
        CategoryMetrics metrics = getCategoryMetrics(category);
        metrics.bounceRate.set(Double.doubleToLongBits(bounceRate));
        
        Gauge.builder("notifications.bounce.rate")
            .tag("category", category)
            .description("Bounce rate for category: " + category)
            .register(meterRegistry, metrics.bounceRate, value -> Double.longBitsToDouble(value.get()));
    }
    
    // SMS metrics methods
    public void recordSmsSent(String category) {
        smsSentCounter.increment();
        getCategoryMetrics(category).smsSent.incrementAndGet();
    }
    
    public void recordSmsDelivered(String phoneNumber) {
        smsDeliveredCounter.increment();
        log.debug("SMS delivered metric recorded for: {}", maskPhoneNumber(phoneNumber));
    }
    
    public void recordSmsFailed(String phoneNumber, String errorCode) {
        smsFailedCounter.increment();
        log.debug("SMS failed metric recorded for: {} with error: {}", maskPhoneNumber(phoneNumber), errorCode);
    }
    
    // Push notification metrics methods
    public void recordPushSent(String category) {
        pushSentCounter.increment();
        getCategoryMetrics(category).pushSent.incrementAndGet();
    }
    
    public void recordPushDelivered(String deviceId) {
        pushDeliveredCounter.increment();
        log.debug("Push notification delivered metric recorded for device: {}", maskDeviceId(deviceId));
    }
    
    public void recordPushFailed(String deviceId, String errorCode) {
        pushFailedCounter.increment();
        log.debug("Push notification failed metric recorded for device: {} with error: {}", maskDeviceId(deviceId), errorCode);
    }
    
    // In-app notification metrics methods
    public void recordInAppSent(String category) {
        inAppSentCounter.increment();
        getCategoryMetrics(category).inAppSent.incrementAndGet();
    }
    
    public void recordInAppDelivered(String userId) {
        inAppDeliveredCounter.increment();
        log.debug("In-app notification delivered metric recorded for user: {}", maskUserId(userId));
    }
    
    public void recordInAppRead(String userId) {
        inAppReadCounter.increment();
        log.debug("In-app notification read metric recorded for user: {}", maskUserId(userId));
    }
    
    public void recordInAppDismissed(String userId) {
        inAppDismissedCounter.increment();
        log.debug("In-app notification dismissed metric recorded for user: {}", maskUserId(userId));
    }
    
    // Generic metric recording
    public void recordMetric(String metricName, double value, Map<String, String> tags) {
        Gauge.Builder builder = Gauge.builder(metricName).description("Custom notification metric");
        
        if (tags != null) {
            tags.forEach(builder::tag);
        }
        
        builder.register(meterRegistry, () -> value);
        log.debug("Custom metric recorded: {} = {} with tags: {}", metricName, value, tags);
    }
    
    // Connection tracking
    public void incrementActiveConnections(String channel) {
        switch (channel.toLowerCase()) {
            case "email":
                activeEmailConnections.incrementAndGet();
                break;
            case "sms":
                activeSmsConnections.incrementAndGet();
                break;
            case "push":
                activePushConnections.incrementAndGet();
                break;
            case "inapp":
                activeInAppConnections.incrementAndGet();
                break;
        }
    }
    
    public void decrementActiveConnections(String channel) {
        switch (channel.toLowerCase()) {
            case "email":
                activeEmailConnections.decrementAndGet();
                break;
            case "sms":
                activeSmsConnections.decrementAndGet();
                break;
            case "push":
                activePushConnections.decrementAndGet();
                break;
            case "inapp":
                activeInAppConnections.decrementAndGet();
                break;
        }
    }
    
    // Queue size tracking
    public void updateQueueSize(String channel, long size) {
        switch (channel.toLowerCase()) {
            case "email":
                queuedEmails.set(size);
                break;
            case "sms":
                queuedSms.set(size);
                break;
            case "push":
                queuedPush.set(size);
                break;
            case "inapp":
                queuedInApp.set(size);
                break;
        }
    }
    
    // Utility methods
    private CategoryMetrics getCategoryMetrics(String category) {
        return categoryMetrics.computeIfAbsent(category, k -> new CategoryMetrics());
    }
    
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        return parts[0].substring(0, Math.min(2, parts[0].length())) + "***@" + parts[1];
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) return "****";
        return "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
    
    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) return "****";
        return deviceId.substring(0, 4) + "****" + deviceId.substring(deviceId.length() - 4);
    }
    
    private String maskUserId(String userId) {
        if (userId == null || userId.length() < 6) return "****";
        return userId.substring(0, 3) + "****" + userId.substring(userId.length() - 3);
    }
    
    // Inner class for category-specific metrics
    private static class CategoryMetrics {
        final AtomicLong emailsSent = new AtomicLong(0);
        final AtomicLong smsSent = new AtomicLong(0);
        final AtomicLong pushSent = new AtomicLong(0);
        final AtomicLong inAppSent = new AtomicLong(0);
        final AtomicLong deliveryRate = new AtomicLong(Double.doubleToLongBits(1.0));
        final AtomicLong bounceRate = new AtomicLong(Double.doubleToLongBits(0.0));
    }
}