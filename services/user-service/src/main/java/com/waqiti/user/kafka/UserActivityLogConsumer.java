package com.waqiti.user.kafka;

import com.waqiti.user.event.UserActivityLogEvent;
import com.waqiti.user.service.UserActivityLogService;
import com.waqiti.user.service.UserRiskAssessmentService;
import com.waqiti.user.service.ThreatIntelligenceService;
import com.waqiti.user.service.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Production-grade Kafka consumer for user activity log events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityLogConsumer {

    private final UserActivityLogService activityLogService;
    private final UserRiskAssessmentService riskAssessmentService;
    private final ThreatIntelligenceService threatIntelligenceService;
    private final SecurityAuditService securityAuditService;

    @KafkaListener(topics = "user-activity-logs", groupId = "activity-log-processor")
    public void processActivityLog(@Payload UserActivityLogEvent event,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 Acknowledgment acknowledgment) {
        try {
            log.info("Processing activity log for user: {} type: {} category: {} success: {}", 
                    event.getUserId(), event.getActivityType(), event.getActivityCategory(), 
                    event.isSuccessful());
            
            // Validate event
            validateActivityLogEvent(event);
            
            // Store activity log
            activityLogService.logUserActivity(
                event.getUserId(),
                event.getActivityType(),
                event.getActivityCategory(),
                event.getActivityDescription(),
                event.getActivityTime(),
                event.getActivityMetadata()
            );
            
            // Track device and location
            activityLogService.trackDeviceAndLocation(
                event.getUserId(),
                event.getDeviceId(),
                event.getDeviceType(),
                event.getIpAddress(),
                event.getLocation(),
                event.getCountry(),
                event.getCity()
            );
            
            // Analyze for suspicious activity
            if (event.isSuspicious()) {
                handleSuspiciousActivity(event);
            }
            
            // Update risk assessment
            if (event.getRiskScore() != null) {
                riskAssessmentService.updateActivityRiskScore(
                    event.getUserId(),
                    event.getActivityType(),
                    event.getRiskScore(),
                    event.getActivityTime()
                );
            }
            
            // Track API performance metrics
            if (event.getApiEndpoint() != null) {
                activityLogService.trackApiMetrics(
                    event.getApiEndpoint(),
                    event.getHttpMethod(),
                    event.getResponseCode(),
                    event.getResponseTimeMs()
                );
            }
            
            // Handle failed activities
            if (!event.isSuccessful() && event.getFailureReason() != null) {
                handleFailedActivity(event);
            }
            
            // Perform threat analysis
            threatIntelligenceService.analyzeActivityPattern(
                event.getUserId(),
                event.getActivityType(),
                event.getIpAddress(),
                event.getLocation()
            );
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed activity log for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process activity log for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Activity log processing failed", e);
        }
    }

    private void validateActivityLogEvent(UserActivityLogEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for activity log");
        }
        
        if (event.getActivityType() == null || event.getActivityType().trim().isEmpty()) {
            throw new IllegalArgumentException("Activity type is required");
        }
        
        if (event.getActivityCategory() == null || event.getActivityCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Activity category is required");
        }
    }

    private void handleSuspiciousActivity(UserActivityLogEvent event) {
        // Log to security audit
        securityAuditService.logSuspiciousActivity(
            event.getUserId(),
            event.getActivityType(),
            event.getSuspiciousReason(),
            event.getIpAddress(),
            event.getDeviceId(),
            event.getActivityTime()
        );
        
        // Trigger security alert
        riskAssessmentService.triggerSecurityAlert(
            event.getUserId(),
            "SUSPICIOUS_ACTIVITY",
            event.getSuspiciousReason()
        );
        
        // Check if immediate action needed
        if ("CRITICAL".equals(event.getRiskScore())) {
            riskAssessmentService.applyImmediateSecurityMeasures(
                event.getUserId(),
                event.getActivityType()
            );
        }
        
        // Report to threat intelligence
        threatIntelligenceService.reportSuspiciousActivity(
            event.getUserId(),
            event.getActivityType(),
            event.getSuspiciousReason(),
            event.getIpAddress(),
            event.getLocation()
        );
    }

    private void handleFailedActivity(UserActivityLogEvent event) {
        // Track failure patterns
        activityLogService.trackFailurePattern(
            event.getUserId(),
            event.getActivityType(),
            event.getFailureReason(),
            event.getActivityTime()
        );
        
        // Check for brute force attempts
        if ("LOGIN".equals(event.getActivityType()) && 
            "AUTHENTICATION".equals(event.getActivityCategory())) {
            
            int failedAttempts = activityLogService.getRecentFailedLoginAttempts(
                event.getUserId(),
                event.getIpAddress()
            );
            
            if (failedAttempts >= 5) {
                riskAssessmentService.handleBruteForceAttempt(
                    event.getUserId(),
                    event.getIpAddress(),
                    failedAttempts
                );
            }
        }
        
        // Analyze failure reason for patterns
        threatIntelligenceService.analyzeFailurePatterns(
            event.getUserId(),
            event.getActivityType(),
            event.getFailureReason()
        );
    }
}