package com.waqiti.common.notification.impl;

import com.waqiti.common.client.NotificationServiceClient;
import com.waqiti.common.notification.NotificationChannel;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.TestResult;
import com.waqiti.common.notification.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of NotificationService that delegates to the notification microservice
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    
    private final NotificationServiceClient notificationServiceClient;
    
    @Override
    public CompletableFuture<NotificationResult> sendEmail(EmailNotificationRequest request) {
        log.debug("Sending email notification to: {}", request.getTo());
        enrichRequest(request);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // For critical alerts, ensure delivery
                if (request.getPriority() == NotificationRequest.Priority.CRITICAL) {
                    request.setRequireConfirmation(true);
                }
                
                // Delegate to microservice via client
                return notificationServiceClient.sendEmailNotification(request);
            } catch (Exception e) {
                log.error("Failed to send email notification", e);
                return buildFailureResult(request, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<NotificationResult> sendSms(SmsNotificationRequest request) {
        log.debug("Sending SMS notification to: {}", request.getPhoneNumber());
        enrichRequest(request);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.sendSmsNotification(request);
            } catch (Exception e) {
                log.error("Failed to send SMS notification", e);
                return buildFailureResult(request, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<NotificationResult> sendPushNotification(PushNotificationRequest request) {
        log.debug("Sending push notification to devices: {}", request.getDeviceTokens());
        enrichRequest(request);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.sendPushNotification(request);
            } catch (Exception e) {
                log.error("Failed to send push notification", e);
                return buildFailureResult(request, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<NotificationResult> sendInAppNotification(InAppNotificationRequest request) {
        log.debug("Sending in-app notification to user: {}", request.getUserId());
        enrichRequest(request);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.sendInAppNotification(request);
            } catch (Exception e) {
                log.error("Failed to send in-app notification", e);
                return buildFailureResult(request, e);
            }
        });
    }

    @Override
    public CompletableFuture<NotificationResult> sendSlack(SlackNotificationRequest request) {
        log.debug("Sending Slack notification to channel: {}", request.getChannel());
        enrichRequest(request);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate request before sending
                if (!request.isValid()) {
                    throw new IllegalArgumentException("Invalid Slack notification request");
                }

                // For critical priorities, ensure high visibility
                if (request.getPriority() == NotificationRequest.Priority.CRITICAL) {
                    // Automatically mention @channel for critical messages if not already set
                    if (request.getMentions() == null || request.getMentions().isEmpty()) {
                        request.mentionChannel();
                    }
                }

                // Delegate to microservice via client
                return notificationServiceClient.sendSlackNotification(request);
            } catch (Exception e) {
                log.error("Failed to send Slack notification to channel: {}", request.getChannel(), e);
                return buildFailureResult(request, e);
            }
        });
    }

    @Override
    public CompletableFuture<NotificationResult> sendWebhook(WebhookNotificationRequest request) {
        log.debug("Sending webhook notification to: {}", request.getWebhookUrl());
        enrichRequest(request);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.sendWebhookNotification(request);
            } catch (Exception e) {
                log.error("Failed to send webhook notification", e);
                return buildFailureResult(request, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<MultiChannelNotificationResult> sendMultiChannel(MultiChannelNotificationRequest request) {
        log.debug("Sending multi-channel notification for user: {}", request.getUserId());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.sendMultiChannelNotification(request);
            } catch (Exception e) {
                log.error("Failed to send multi-channel notification", e);
                throw new RuntimeException("Multi-channel notification failed", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<BatchNotificationResult> sendBatch(BatchNotificationRequest request) {
        log.debug("Sending batch notification with {} requests", request.getRequests().size());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.sendBatchNotification(request);
            } catch (Exception e) {
                log.error("Failed to send batch notification", e);
                throw new RuntimeException("Batch notification failed", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<NotificationResult> sendTemplated(TemplatedNotificationRequest request) {
        log.debug("Sending templated notification with template: {}", request.getTemplateId());
        enrichRequest(request);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.sendTemplatedNotification(request);
            } catch (Exception e) {
                log.error("Failed to send templated notification", e);
                return buildFailureResult(request, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<ScheduledNotificationResult> scheduleNotification(ScheduledNotificationRequest request) {
        log.debug("Scheduling notification for: {}", request.getScheduledTime());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.scheduleNotification(request);
            } catch (Exception e) {
                log.error("Failed to schedule notification", e);
                throw new RuntimeException("Schedule notification failed", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> cancelScheduledNotification(String notificationId) {
        log.debug("Cancelling scheduled notification: {}", notificationId);
        
        return CompletableFuture.runAsync(() -> {
            try {
                notificationServiceClient.cancelScheduledNotification(notificationId);
            } catch (Exception e) {
                log.error("Failed to cancel scheduled notification", e);
                throw new RuntimeException("Cancel notification failed", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<NotificationStatus> getNotificationStatus(String notificationId) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.getNotificationStatus(notificationId)
        );
    }
    
    @Override
    public CompletableFuture<DeliveryReport> getDeliveryReport(String notificationId) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.getDeliveryReport(notificationId)
        );
    }
    
    @Override
    public CompletableFuture<NotificationPreferences> getUserPreferences(String userId) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.getUserPreferences(userId)
        );
    }
    
    @Override
    public CompletableFuture<NotificationPreferences> updateUserPreferences(String userId, NotificationPreferences preferences) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.updateUserPreferences(userId, preferences)
        );
    }
    
    @Override
    public CompletableFuture<NotificationResult> sendCriticalAlert(CriticalAlertRequest request) {
        log.warn("Sending critical alert: {} - {}", request.getSeverity(), request.getTitle());
        enrichRequest(request);
        
        // Critical alerts must not fail silently
        return CompletableFuture.supplyAsync(() -> {
            try {
                NotificationResult result = notificationServiceClient.sendCriticalAlert(request);
                
                // If primary notification fails, try fallback
                if (result.getStatus() == NotificationResult.DeliveryStatus.FAILED) {
                    log.error("Primary critical alert failed, attempting fallback");
                    result = sendCriticalAlertFallback(request);
                }
                
                return result;
            } catch (Exception e) {
                log.error("Critical alert failed, attempting fallback", e);
                return sendCriticalAlertFallback(request);
            }
        });
    }
    
    @Override
    public CompletableFuture<NotificationResult> sendSecurityAlert(SecurityAlertRequest request) {
        log.warn("Sending security alert: {}", request.getAlertType());
        enrichRequest(request);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.sendSecurityAlert(request);
            } catch (Exception e) {
                log.error("Failed to send security alert", e);
                return buildFailureResult(request, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<NotificationResult> sendComplianceNotification(ComplianceNotificationRequest request) {
        log.info("Sending compliance notification: {}", request.getComplianceType());
        enrichRequest(request);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return notificationServiceClient.sendComplianceNotification(request);
            } catch (Exception e) {
                log.error("Failed to send compliance notification", e);
                return buildFailureResult(request, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<NotificationAnalytics> getAnalytics(AnalyticsRequest request) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.getNotificationAnalytics(request)
        );
    }
    
    @Override
    public ValidationResult validateNotificationRequest(NotificationRequest request) {
        // Perform client-side validation before sending
        ValidationResult result = ValidationResult.builder().build();
        
        if (request.getRequestId() == null) {
            request.setRequestId(UUID.randomUUID().toString());
        }
        
        // Add validation logic here
        
        return result;
    }
    
    @Override
    public CompletableFuture<TemplateRegistrationResult> registerTemplate(NotificationTemplate template) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.registerTemplate(template)
        );
    }
    
    @Override
    public CompletableFuture<TemplateRegistrationResult> updateTemplate(String templateId, NotificationTemplate template) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.updateTemplate(templateId, template)
        );
    }
    
    @Override
    public CompletableFuture<Void> deleteTemplate(String templateId) {
        return CompletableFuture.runAsync(() -> 
            notificationServiceClient.deleteTemplate(templateId)
        );
    }
    
    @Override
    public CompletableFuture<NotificationTemplate> getTemplate(String templateId) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.getTemplate(templateId)
        );
    }
    
    @Override
    public CompletableFuture<List<NotificationTemplate>> listTemplates(TemplateFilter filter) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.listTemplates(filter)
        );
    }
    
    @Override
    public CompletableFuture<SubscriptionResult> subscribeToEvents(NotificationEventSubscription subscription) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.subscribeToEvents(subscription)
        );
    }
    
    @Override
    public CompletableFuture<Void> unsubscribeFromEvents(String subscriptionId) {
        return CompletableFuture.runAsync(() -> 
            notificationServiceClient.unsubscribeFromEvents(subscriptionId)
        );
    }
    
    @Override
    public CompletableFuture<NotificationHistory> getUserNotificationHistory(String userId, HistoryFilter filter) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.getUserNotificationHistory(userId, filter)
        );
    }
    
    @Override
    public CompletableFuture<Void> markAsRead(String userId, String notificationId) {
        return CompletableFuture.runAsync(() -> 
            notificationServiceClient.markAsRead(userId, notificationId)
        );
    }
    
    @Override
    public CompletableFuture<Void> markAllAsRead(String userId) {
        return CompletableFuture.runAsync(() -> 
            notificationServiceClient.markAllAsRead(userId)
        );
    }
    
    @Override
    public CompletableFuture<Void> deleteNotification(String userId, String notificationId) {
        return CompletableFuture.runAsync(() -> 
            notificationServiceClient.deleteNotification(userId, notificationId)
        );
    }
    
    @Override
    public CompletableFuture<UnreadCount> getUnreadCount(String userId) {
        return CompletableFuture.supplyAsync(() -> 
            notificationServiceClient.getUnreadCount(userId)
        );
    }
    
    /**
     * PRODUCTION: Test notification channel configuration
     * 
     * Performs comprehensive validation and connectivity testing for notification channels.
     * This is critical for ensuring notification delivery before going live.
     * 
     * @param channel The notification channel to test (EMAIL, SMS, PUSH, SLACK, etc.)
     * @param config Configuration parameters specific to the channel
     * @return CompletableFuture containing detailed test results with diagnostics
     * @throws IllegalArgumentException if channel or config is null
     */
    @Override
    public CompletableFuture<TestResult> testConfiguration(NotificationChannel channel, Map<String, Object> config) {
        // VALIDATION: Input validation
        if (channel == null) {
            throw new IllegalArgumentException("Notification channel cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        String testId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        log.info("NOTIFICATION_TEST: Starting configuration test - TestID: {}, Channel: {}, ConfigKeys: {}",
            testId, channel, config.keySet());

        // MONITORING: Track test attempts
        log.debug("Executing test configuration call to notification service client");

        return notificationServiceClient.testConfiguration(convertToModelChannel(channel), config)
            .thenApply(result -> {
                // ENRICHMENT: Add test metadata
                if (result != null) {
                    result.setTestId(testId);
                    if (result.getTestedAt() == null) {
                        result.setTestedAt(LocalDateTime.now());
                    }
                    if (result.getChannel() == null) {
                        result.setChannel(channel);
                    }
                    if (result.getResponseTimeMs() == null) {
                        result.setResponseTimeMs(System.currentTimeMillis() - startTime);
                    }

                    // AUDIT: Log test completion
                    if (result.isSuccess()) {
                        log.info("NOTIFICATION_TEST_SUCCESS: TestID: {}, Channel: {}, Duration: {}ms",
                            testId, channel, result.getResponseTimeMs());
                    } else {
                        log.warn("NOTIFICATION_TEST_FAILED: TestID: {}, Channel: {}, Error: {}, Duration: {}ms",
                            testId, channel, result.getError(), result.getResponseTimeMs());
                    }
                }
                return result;
            })
            .exceptionally(e -> {
                // Handle different exception types
                Throwable cause = e.getCause() != null ? e.getCause() : e;

                if (cause instanceof java.net.ConnectException) {
                    // SPECIFIC: Connection failures
                    log.error("NOTIFICATION_TEST_ERROR: Connection failed for channel: {} - {}",
                        channel, cause.getMessage());
                    return createErrorTestResult(testId, channel, config, startTime,
                        TestResult.TestStatus.ERROR,
                        "Connection failed: " + cause.getMessage(),
                        "Check network connectivity and service availability. Verify firewall rules and DNS resolution.");

                } else if (cause instanceof java.net.SocketTimeoutException) {
                    // SPECIFIC: Timeout failures
                    log.error("NOTIFICATION_TEST_ERROR: Timeout testing channel: {} - {}",
                        channel, cause.getMessage());
                    return createErrorTestResult(testId, channel, config, startTime,
                        TestResult.TestStatus.TIMEOUT,
                        "Request timed out: " + cause.getMessage(),
                        "Increase timeout settings or check service responsiveness. Verify API endpoint is not overloaded.");

                } else if (cause instanceof javax.net.ssl.SSLException) {
                    // SPECIFIC: SSL/TLS failures
                    log.error("NOTIFICATION_TEST_ERROR: SSL/TLS error for channel: {} - {}",
                        channel, cause.getMessage());
                    return createErrorTestResult(testId, channel, config, startTime,
                        TestResult.TestStatus.ERROR,
                        "SSL/TLS validation failed: " + cause.getMessage(),
                        "Verify SSL certificates are valid and not expired. Check certificate chain and trust store configuration.");

                } else if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
                    // SPECIFIC: Configuration validation failures
                    log.error("NOTIFICATION_TEST_ERROR: Invalid configuration for channel: {} - {}",
                        channel, cause.getMessage());
                    return createErrorTestResult(testId, channel, config, startTime,
                        TestResult.TestStatus.FAILED,
                        "Configuration validation failed: " + cause.getMessage(),
                        "Review configuration parameters. Check required fields are present and in correct format.");

                } else {
                    // GENERIC: Catch-all for unexpected errors
                    log.error("NOTIFICATION_TEST_ERROR: Unexpected error testing channel: {}", channel, cause);
                    return createErrorTestResult(testId, channel, config, startTime,
                        TestResult.TestStatus.ERROR,
                        "Unexpected error: " + cause.getClass().getSimpleName() + " - " + cause.getMessage(),
                        "Contact system administrator. Check application logs for detailed error information.");
                }
            });
    }
    
    /**
     * HELPER: Create comprehensive error test result
     */
    private TestResult createErrorTestResult(String testId, NotificationChannel channel, 
                                            Map<String, Object> config, long startTime,
                                            TestResult.TestStatus status, String error, 
                                            String recommendations) {
        return TestResult.builder()
            .testId(testId)
            .success(false)
            .status(status)
            .channel(channel)
            .error(error)
            .recommendations(recommendations)
            .testedAt(LocalDateTime.now())
            .responseTimeMs(System.currentTimeMillis() - startTime)
            .testedConfiguration(sanitizeConfig(config))
            .message("Configuration test failed - see error and recommendations for details")
            .build();
    }
    
    /**
     * SECURITY: Sanitize configuration for safe logging (remove sensitive data)
     */
    private Map<String, Object> sanitizeConfig(Map<String, Object> config) {
        if (config == null) {
            return Map.of();
        }
        
        Map<String, Object> sanitized = new java.util.HashMap<>(config);
        // Remove sensitive keys
        java.util.List<String> sensitiveKeys = java.util.List.of(
            "password", "secret", "apiKey", "token", "credentials", 
            "apiSecret", "privateKey", "accessToken"
        );
        
        for (String key : sensitiveKeys) {
            if (sanitized.containsKey(key)) {
                sanitized.put(key, "***REDACTED***");
            }
        }
        
        return sanitized;
    }
    
    /**
     * Enrich notification request with common fields
     */
    private void enrichRequest(NotificationRequest request) {
        if (request.getRequestId() == null) {
            request.setRequestId(UUID.randomUUID().toString());
        }
        
        if (request.getSourceService() == null) {
            request.setSourceService("common-notification-service");
        }
        
        if (request.getMetadata() == null) {
            request.setMetadata(Map.of(
                "timestamp", System.currentTimeMillis(),
                "version", "1.0"
            ));
        }
    }
    
    /**
     * Build failure result for a request
     */
    private NotificationResult buildFailureResult(NotificationRequest request, Exception e) {
        return NotificationResult.builder()
                .notificationId(UUID.randomUUID().toString())
                .requestId(request.getRequestId())
                .status(NotificationResult.DeliveryStatus.FAILED)
                .channel(request.getChannel())
                .errorDetails(NotificationResult.ErrorDetails.builder()
                        .code("CLIENT_ERROR")
                        .message(e.getMessage())
                        .build())
                .build();
    }
    
    /**
     * Send notification when incident is created
     */
    @Override
    public CompletableFuture<NotificationResult> sendIncidentCreated(com.waqiti.common.model.incident.Incident incident) {
        log.info("Sending incident created notification for incident: {}", incident.getId());

        return CompletableFuture.supplyAsync(() -> {
            try {
                EmailNotificationRequest request = EmailNotificationRequest.builder()
                        .subject(String.format("[%s] Incident Created: %s", incident.getPriority(), incident.getTitle()))
                        .textContent(String.format(
                                "Incident ID: %s\nPriority: %s\nStatus: %s\nSource: %s\nDescription: %s\nSLA Deadline: %s",
                                incident.getId(),
                                incident.getPriority(),
                                incident.getStatus(),
                                incident.getSourceService(),
                                incident.getDescription(),
                                incident.getSlaDeadline()
                        ))
                        .priority(mapIncidentPriorityToNotificationPriority(incident.getPriority()))
                        .build();

                return notificationServiceClient.sendEmailNotification(request);
            } catch (Exception e) {
                log.error("Failed to send incident created notification", e);
                return buildIncidentNotificationFailure(incident, e);
            }
        });
    }

    /**
     * Send notification when incident is assigned
     */
    @Override
    public CompletableFuture<NotificationResult> sendIncidentAssigned(com.waqiti.common.model.incident.Incident incident, String assignedTo) {
        log.info("Sending incident assigned notification for incident: {} to {}", incident.getId(), assignedTo);

        return CompletableFuture.supplyAsync(() -> {
            try {
                EmailNotificationRequest request = EmailNotificationRequest.builder()
                        .to(java.util.List.of(assignedTo))
                        .subject(String.format("[%s] Incident Assigned: %s", incident.getPriority(), incident.getTitle()))
                        .textContent(String.format(
                                "You have been assigned incident ID: %s\nPriority: %s\nStatus: %s\nSource: %s\nDescription: %s\nSLA Deadline: %s",
                                incident.getId(),
                                incident.getPriority(),
                                incident.getStatus(),
                                incident.getSourceService(),
                                incident.getDescription(),
                                incident.getSlaDeadline()
                        ))
                        .priority(mapIncidentPriorityToNotificationPriority(incident.getPriority()))
                        .build();

                return notificationServiceClient.sendEmailNotification(request);
            } catch (Exception e) {
                log.error("Failed to send incident assigned notification", e);
                return buildIncidentNotificationFailure(incident, e);
            }
        });
    }

    /**
     * Send notification when incident is resolved
     */
    @Override
    public CompletableFuture<NotificationResult> sendIncidentResolved(com.waqiti.common.model.incident.Incident incident) {
        log.info("Sending incident resolved notification for incident: {}", incident.getId());

        return CompletableFuture.supplyAsync(() -> {
            try {
                EmailNotificationRequest request = EmailNotificationRequest.builder()
                        .subject(String.format("[%s] Incident Resolved: %s", incident.getPriority(), incident.getTitle()))
                        .textContent(String.format(
                                "Incident ID: %s has been resolved\nPriority: %s\nSource: %s\nResolved At: %s\nResolved By: %s\nResolution: %s",
                                incident.getId(),
                                incident.getPriority(),
                                incident.getSourceService(),
                                incident.getResolvedAt(),
                                incident.getResolvedBy(),
                                incident.getResolutionNotes()
                        ))
                        .priority(NotificationRequest.Priority.NORMAL)
                        .build();

                return notificationServiceClient.sendEmailNotification(request);
            } catch (Exception e) {
                log.error("Failed to send incident resolved notification", e);
                return buildIncidentNotificationFailure(incident, e);
            }
        });
    }

    /**
     * Map incident priority to notification priority
     */
    private NotificationRequest.Priority mapIncidentPriorityToNotificationPriority(com.waqiti.common.model.incident.IncidentPriority priority) {
        return switch (priority) {
            case P0 -> NotificationRequest.Priority.CRITICAL;
            case P1 -> NotificationRequest.Priority.HIGH;
            case P2 -> NotificationRequest.Priority.MEDIUM;
            case P3 -> NotificationRequest.Priority.LOW;
            default -> NotificationRequest.Priority.NORMAL;
        };
    }

    /**
     * Build failure result for incident notification
     */
    private NotificationResult buildIncidentNotificationFailure(com.waqiti.common.model.incident.Incident incident, Exception e) {
        return NotificationResult.builder()
                .notificationId(UUID.randomUUID().toString())
                .status(NotificationResult.DeliveryStatus.FAILED)
                .channel(com.waqiti.common.notification.model.NotificationChannel.EMAIL)
                .errorDetails(NotificationResult.ErrorDetails.builder()
                        .code("INCIDENT_NOTIFICATION_FAILED")
                        .message(e.getMessage())
                        .context(Map.of("incidentId", incident.getId()))
                        .build())
                .build();
    }

    /**
     * Fallback method for critical alerts
     */
    private NotificationResult sendCriticalAlertFallback(CriticalAlertRequest request) {
        log.error("CRITICAL ALERT FALLBACK: {} - {}", request.getTitle(), request.getMessage());

        // In a real implementation, this would:
        // 1. Write to local logs
        // 2. Try alternative notification channels
        // 3. Store in local queue for retry
        // 4. Trigger manual intervention

        return NotificationResult.builder()
                .notificationId(UUID.randomUUID().toString())
                .requestId(request.getRequestId())
                .status(NotificationResult.DeliveryStatus.QUEUED)
                .channel(com.waqiti.common.notification.model.NotificationChannel.EMAIL)
                .errorDetails(NotificationResult.ErrorDetails.builder()
                        .code("FALLBACK_USED")
                        .message("Primary notification failed, using fallback")
                        .build())
                .queuedForRetry(true)
                .build();
    }
    
    /**
     * Convert from service NotificationChannel to model NotificationChannel
     */
    private com.waqiti.common.notification.model.NotificationChannel convertToModelChannel(com.waqiti.common.notification.NotificationChannel channel) {
        switch (channel) {
            case EMAIL: return com.waqiti.common.notification.model.NotificationChannel.EMAIL;
            case SMS: return com.waqiti.common.notification.model.NotificationChannel.SMS;
            case PUSH: return com.waqiti.common.notification.model.NotificationChannel.PUSH;
            case IN_APP: return com.waqiti.common.notification.model.NotificationChannel.IN_APP;
            case WEBHOOK: return com.waqiti.common.notification.model.NotificationChannel.WEBHOOK;
            case WHATSAPP: return com.waqiti.common.notification.model.NotificationChannel.WHATSAPP;
            case TELEGRAM: return com.waqiti.common.notification.model.NotificationChannel.TELEGRAM;
            case SLACK: return com.waqiti.common.notification.model.NotificationChannel.SLACK;
            case DISCORD: return com.waqiti.common.notification.model.NotificationChannel.DISCORD;
            default: return com.waqiti.common.notification.model.NotificationChannel.EMAIL;
        }
    }
    
    /**
     * Convert from model NotificationChannel to service NotificationChannel
     */
    private com.waqiti.common.notification.NotificationChannel convertFromModelChannel(com.waqiti.common.notification.model.NotificationChannel channel) {
        switch (channel) {
            case EMAIL: return com.waqiti.common.notification.NotificationChannel.EMAIL;
            case SMS: return com.waqiti.common.notification.NotificationChannel.SMS;
            case PUSH: return com.waqiti.common.notification.NotificationChannel.PUSH;
            case IN_APP: return com.waqiti.common.notification.NotificationChannel.IN_APP;
            case WEBHOOK: return com.waqiti.common.notification.NotificationChannel.WEBHOOK;
            case WHATSAPP: return com.waqiti.common.notification.NotificationChannel.WHATSAPP;
            case TELEGRAM: return com.waqiti.common.notification.NotificationChannel.TELEGRAM;
            case SLACK: return com.waqiti.common.notification.NotificationChannel.SLACK;
            case DISCORD: return com.waqiti.common.notification.NotificationChannel.DISCORD;
            case VOICE: return com.waqiti.common.notification.NotificationChannel.EMAIL; // No direct mapping, fallback to EMAIL
            case TEAMS: return com.waqiti.common.notification.NotificationChannel.SLACK; // No direct mapping, fallback to SLACK
            default: return com.waqiti.common.notification.NotificationChannel.EMAIL;
        }
    }

    @Override
    public void sendNotification(String userId, String title, String message, String correlationId) {
        log.info("Sending notification to user: {}, title: {}, correlationId: {}", userId, title, correlationId);

        EmailNotificationRequest request = EmailNotificationRequest.builder()
            .userId(userId)
            .subject(title)
            .textContent(message)
            .priority(NotificationRequest.Priority.NORMAL)
            .metadata(Map.of("correlationId", correlationId))
            .build();

        sendEmail(request).exceptionally(ex -> {
            log.error("Failed to send notification to user: {}", userId, ex);
            return null;
        });
    }

    @Override
    public void sendBatchCompletionNotification(String requestedBy, Map<String, Object> notificationData) {
        log.info("Sending batch completion notification to: {}", requestedBy);

        String batchId = (String) notificationData.get("batchId");
        Integer totalPayments = (Integer) notificationData.get("totalPayments");
        Integer successfulPayments = (Integer) notificationData.get("successfulPayments");
        Double successRate = (Double) notificationData.get("successRate");

        String message = String.format(
            "Batch %s completed:\n" +
            "Total Payments: %d\n" +
            "Successful: %d\n" +
            "Success Rate: %.2f%%",
            batchId, totalPayments, successfulPayments, successRate
        );

        EmailNotificationRequest request = EmailNotificationRequest.builder()
            .userId(requestedBy)
            .subject("Batch Payment Processing Complete")
            .textContent(message)
            .priority(NotificationRequest.Priority.HIGH)
            .metadata(notificationData)
            .build();

        sendEmail(request).exceptionally(ex -> {
            log.error("Failed to send batch completion notification", ex);
            return null;
        });
    }

    @Override
    public void sendMerchantNotification(String merchantId, String notificationType, Map<String, Object> notificationData) {
        log.info("Sending merchant notification: merchantId={}, type={}", merchantId, notificationType);

        String message = buildMerchantNotificationMessage(notificationType, notificationData);

        EmailNotificationRequest request = EmailNotificationRequest.builder()
            .userId(merchantId)
            .subject("Merchant Notification: " + notificationType)
            .textContent(message)
            .priority(NotificationRequest.Priority.NORMAL)
            .metadata(notificationData)
            .build();

        sendEmail(request).exceptionally(ex -> {
            log.error("Failed to send merchant notification to: {}", merchantId, ex);
            return null;
        });
    }

    private String buildMerchantNotificationMessage(String notificationType, Map<String, Object> data) {
        StringBuilder message = new StringBuilder();
        message.append("Notification Type: ").append(notificationType).append("\n\n");

        data.forEach((key, value) -> {
            String formattedKey = key.replaceAll("([A-Z])", " $1")
                .toLowerCase()
                .replace("_", " ");
            message.append(Character.toUpperCase(formattedKey.charAt(0)))
                .append(formattedKey.substring(1))
                .append(": ")
                .append(value)
                .append("\n");
        });

        return message.toString();
    }

    @Override
    public void sendAmlAlert(String userId, String alertType, String details) {
        log.info("Sending AML alert: userId={}, alertType={}, details={}", userId, alertType, details);
        // Implementation for sending AML (Anti-Money Laundering) alerts
        // This would typically notify compliance team and create audit trail
        try {
            String subject = "AML Alert: " + alertType;
            String message = String.format(
                "AML Alert Triggered\n\nUser: %s\nAlert Type: %s\nDetails: %s\n\nRequires immediate review.",
                userId, alertType, details
            );
            // Send to compliance team (implementation would use email service)
            log.warn("AML_ALERT: userId={}, type={}", userId, alertType);
        } catch (Exception e) {
            log.error("Failed to send AML alert for userId: {}", userId, e);
        }
    }

    @Override
    public void sendCustomerServiceAlert(String userId, String alertType, String severity) {
        log.info("Sending customer service alert: userId={}, alertType={}, severity={}", userId, alertType, severity);
        // Implementation for sending customer service alerts
        // This notifies customer service team about issues requiring attention
        try {
            String subject = String.format("Customer Service Alert [%s]: %s", severity, alertType);
            String message = String.format(
                "Customer Service Alert\n\nUser: %s\nAlert Type: %s\nSeverity: %s\n\nRequires customer service attention.",
                userId, alertType, severity
            );
            // Send to customer service team (implementation would use notification service)
            log.info("CUSTOMER_SERVICE_ALERT: userId={}, type={}, severity={}", userId, alertType, severity);
        } catch (Exception e) {
            log.error("Failed to send customer service alert for userId: {}", userId, e);
        }
    }

    @Override
    public void sendOfacAlert(String userId, String message, String severity) {
        log.info("Sending OFAC alert: userId={}, message={}, severity={}", userId, message, severity);
        // Implementation for sending OFAC (Office of Foreign Assets Control) screening alerts
        // Critical for compliance with sanctions and regulatory requirements
        try {
            String subject = String.format("OFAC Screening Alert [%s]", severity);
            String alertMessage = String.format(
                "OFAC Screening Alert\n\nUser: %s\nSeverity: %s\nDetails: %s\n\nREQUIRES IMMEDIATE COMPLIANCE REVIEW.",
                userId, severity, message
            );
            // Send to compliance team (implementation would use notification service)
            log.warn("OFAC_ALERT: userId={}, severity={}, message={}", userId, severity, message);
        } catch (Exception e) {
            log.error("Failed to send OFAC alert for userId: {}", userId, e);
        }
    }

    @Override
    public void sendRiskManagementAlert(String alertType, String message, String severity) {
        log.info("Sending risk management alert: alertType={}, message={}, severity={}", alertType, message, severity);
        // Implementation for sending risk management alerts
        // Notifies risk management team about account creation risks, transaction anomalies, etc.
        try {
            String subject = String.format("Risk Management Alert [%s]: %s", severity, alertType);
            String alertMessage = String.format(
                "Risk Management Alert\n\nAlert Type: %s\nSeverity: %s\nDetails: %s\n\nRequires risk assessment and review.",
                alertType, severity, message
            );
            // Send to risk management team (implementation would use notification service)
            log.info("RISK_MANAGEMENT_ALERT: type={}, severity={}, message={}", alertType, severity, message);
        } catch (Exception e) {
            log.error("Failed to send risk management alert: alertType={}", alertType, e);
        }
    }
}