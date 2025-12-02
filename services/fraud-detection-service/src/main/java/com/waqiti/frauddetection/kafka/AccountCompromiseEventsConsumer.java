package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.AccountCompromiseEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.CompromiseIncident;
import com.waqiti.frauddetection.repository.CompromiseIncidentRepository;
import com.waqiti.frauddetection.service.AccountSecurityService;
import com.waqiti.frauddetection.service.IncidentResponseService;
import com.waqiti.frauddetection.metrics.SecurityMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class AccountCompromiseEventsConsumer {
    
    private final CompromiseIncidentRepository incidentRepository;
    private final AccountSecurityService securityService;
    private final IncidentResponseService incidentResponseService;
    private final SecurityMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"account-compromise-events", "account-takeover-events", "credential-compromise-events"},
        groupId = "fraud-compromise-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleAccountCompromiseEvent(
            @Payload AccountCompromiseEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_PARTITION) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("compromise-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.error("Processing account compromise event: userId={}, type={}, severity={}, correlation={}",
            event.getUserId(), event.getCompromiseType(), event.getSeverity(), correlationId);
        
        try {
            switch (event.getEventType()) {
                case COMPROMISE_DETECTED:
                    processCompromiseDetected(event, correlationId);
                    break;
                case UNAUTHORIZED_ACCESS:
                    processUnauthorizedAccess(event, correlationId);
                    break;
                case CREDENTIAL_STUFFING:
                    processCredentialStuffing(event, correlationId);
                    break;
                case SESSION_HIJACKING:
                    processSessionHijacking(event, correlationId);
                    break;
                case ACCOUNT_LOCKED:
                    processAccountLocked(event, correlationId);
                    break;
                case RECOVERY_INITIATED:
                    processRecoveryInitiated(event, correlationId);
                    break;
                default:
                    log.warn("Unknown compromise event type: {}", event.getEventType());
                    break;
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process compromise event: userId={}, error={}",
                event.getUserId(), e.getMessage(), e);
            kafkaTemplate.send("account-compromise-events-dlq", Map.of(
                "originalEvent", event,
                "error", error.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processCompromiseDetected(AccountCompromiseEvent event, String correlationId) {
        log.error("Account compromise detected: userId={}, indicators={}", 
            event.getUserId(), event.getCompromiseIndicators());
        
        CompromiseIncident incident = CompromiseIncident.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .compromiseType(event.getCompromiseType())
            .severity(event.getSeverity())
            .indicators(event.getCompromiseIndicators())
            .detectedAt(LocalDateTime.now())
            .ipAddress(event.getIpAddress())
            .deviceId(event.getDeviceId())
            .correlationId(correlationId)
            .build();
        
        incidentRepository.save(incident);
        
        securityService.lockAccount(event.getUserId(), "Compromise detected");
        securityService.invalidateAllSessions(event.getUserId());
        securityService.requirePasswordReset(event.getUserId());
        
        FraudAlertEvent alert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getUserId())
            .alertType("ACCOUNT_COMPROMISE")
            .severity("CRITICAL")
            .riskScore(100.0)
            .riskFactors(event.getCompromiseIndicators())
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", alert);
        
        notificationService.sendSecurityAlert(
            "Account Compromise Detected",
            String.format("Account %s has been compromised. Immediate action taken.", event.getUserId()),
            NotificationService.Priority.CRITICAL
        );
        
        notificationService.sendNotification(
            event.getUserId(),
            "Account Security Alert",
            "Your account has been locked due to suspicious activity. Please reset your password immediately.",
            correlationId
        );
        
        metricsService.recordAccountCompromise(event.getCompromiseType(), event.getSeverity());
    }
    
    private void processUnauthorizedAccess(AccountCompromiseEvent event, String correlationId) {
        log.warn("Unauthorized access detected: userId={}, location={}", 
            event.getUserId(), event.getLocation());
        
        securityService.challengeAccess(event.getUserId(), event.getSessionId());
        
        metricsService.recordUnauthorizedAccess(event.getLocation());
    }
    
    private void processCredentialStuffing(AccountCompromiseEvent event, String correlationId) {
        log.error("Credential stuffing detected: userId={}, attempts={}", 
            event.getUserId(), event.getAttemptCount());
        
        securityService.blockIpAddress(event.getIpAddress(), 1440);
        securityService.requireMFA(event.getUserId());
        
        metricsService.recordCredentialStuffing(event.getAttemptCount());
    }
    
    private void processSessionHijacking(AccountCompromiseEvent event, String correlationId) {
        log.error("Session hijacking detected: userId={}, sessionId={}", 
            event.getUserId(), event.getSessionId());
        
        securityService.terminateSession(event.getSessionId());
        securityService.lockAccount(event.getUserId(), "Session hijacking detected");
        
        metricsService.recordSessionHijacking();
    }
    
    private void processAccountLocked(AccountCompromiseEvent event, String correlationId) {
        log.info("Account locked: userId={}, reason={}", 
            event.getUserId(), event.getLockReason());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Account Locked",
            String.format("Your account has been locked. Reason: %s", event.getLockReason()),
            correlationId
        );
        
        metricsService.recordAccountLocked(event.getLockReason());
    }
    
    private void processRecoveryInitiated(AccountCompromiseEvent event, String correlationId) {
        log.info("Recovery initiated: userId={}", event.getUserId());
        
        incidentResponseService.initiateRecovery(event.getUserId(), event.getRecoveryMethod());
        
        metricsService.recordRecoveryInitiated(event.getRecoveryMethod());
    }
}