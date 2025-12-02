package com.waqiti.kyc.kafka;

import com.waqiti.common.events.compliance.KYCVerifiedEvent;
import com.waqiti.kyc.repository.KYCVerificationRepository;
import com.waqiti.kyc.repository.VerificationDocumentRepository;
import com.waqiti.kyc.service.KYCVerificationService;
import com.waqiti.kyc.service.CacheService;
import com.waqiti.kyc.service.ComplianceReportingService;
import com.waqiti.kyc.domain.KYCVerification;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class KycVerifiedConsumer {

    private final KYCVerificationRepository verificationRepository;
    private final VerificationDocumentRepository documentRepository;
    private final KYCVerificationService verificationService;
    private final CacheService cacheService;
    private final ComplianceReportingService complianceReportingService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("kyc_verified_processed_total")
            .description("Total number of successfully processed KYC verified events")
            .register(meterRegistry);
        errorCounter = Counter.builder("kyc_verified_errors_total")
            .description("Total number of KYC verified processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("kyc_verified_processing_duration")
            .description("Time taken to process KYC verified events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"kyc-verified", "kyc-verification-completed", "kyc-status-verified"},
        groupId = "kyc-verified-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "kyc-verified", fallbackMethod = "handleKycVerifiedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleKycVerifiedEvent(
            @Payload KYCVerifiedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("kyc-verified-%s-p%d-o%d", event.getUserId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getUserId(), event.getKycLevel(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing KYC verified: userId={}, verificationId={}, kycLevel={}, verifiedBy={}",
                event.getUserId(), event.getVerificationId(), event.getKycLevel(), event.getVerifiedBy());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Process the verification completion
            processVerificationCompleted(event, correlationId);

            // Update KYC status and cache
            updateKycStatusAndCache(event, correlationId);

            // Handle compliance requirements
            handleComplianceRequirements(event, correlationId);

            // Update user privileges based on KYC level
            updateUserPrivileges(event, correlationId);

            // Send notifications
            sendVerificationCompletedNotifications(event, correlationId);

            // Publish downstream events
            publishDownstreamEvents(event, correlationId);

            // Generate compliance reports if required
            generateComplianceReports(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logUserEvent("KYC_VERIFIED_EVENT_PROCESSED", event.getUserId(),
                Map.of("verificationId", event.getVerificationId(), "kycLevel", event.getKycLevel(),
                    "previousKycLevel", event.getPreviousKycLevel(), "verifiedBy", event.getVerifiedBy(),
                    "documentsVerified", event.getDocumentsVerified(), "riskScore", event.getRiskScore(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process KYC verified event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("kyc-verified-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleKycVerifiedEventFallback(
            KYCVerifiedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("kyc-verified-fallback-%s-p%d-o%d", event.getUserId(), partition, offset);

        log.error("Circuit breaker fallback triggered for KYC verified: userId={}, error={}",
            event.getUserId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("kyc-verified-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "KYC Verified Circuit Breaker Triggered",
                String.format("KYC verification processing for user %s failed: %s", event.getUserId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltKycVerifiedEvent(
            @Payload KYCVerifiedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-kyc-verified-%s-%d", event.getUserId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - KYC verified permanently failed: userId={}, topic={}, error={}",
            event.getUserId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logUserEvent("KYC_VERIFIED_DLT_EVENT", event.getUserId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "verificationId", event.getVerificationId(), "kycLevel", event.getKycLevel(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "KYC Verified Dead Letter Event",
                String.format("KYC verification for user %s sent to DLT: %s", event.getUserId(), exceptionMessage),
                Map.of("userId", event.getUserId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processVerificationCompleted(KYCVerifiedEvent event, String correlationId) {
        try {
            // Find and update the verification record
            Optional<KYCVerification> verificationOpt = verificationRepository.findByVerificationId(event.getVerificationId());

            if (verificationOpt.isPresent()) {
                KYCVerification verification = verificationOpt.get();

                // Update verification status
                verification.setStatus("VERIFIED");
                verification.setKycLevel(event.getKycLevel());
                verification.setVerifiedAt(event.getVerifiedAt());
                verification.setVerifiedBy(event.getVerifiedBy());
                verification.setRiskScore(event.getRiskScore());
                verification.setComplianceNotes(event.getComplianceNotes());
                verification.setLastProcessedAt(LocalDateTime.now());

                // Save updated verification
                verificationRepository.save(verification);

                // Update document statuses
                if (event.getDocumentsVerified() != null && event.getDocumentsVerified() > 0) {
                    updateDocumentStatuses(verification.getId(), correlationId);
                }

                log.info("Updated verification record: userId={}, verificationId={}, level={}",
                    event.getUserId(), event.getVerificationId(), event.getKycLevel());
            } else {
                log.warn("Verification record not found: verificationId={}", event.getVerificationId());

                // Create new verification record from event data
                createVerificationFromEvent(event, correlationId);
            }

        } catch (Exception e) {
            log.error("Failed to process verification completion: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void updateKycStatusAndCache(KYCVerifiedEvent event, String correlationId) {
        try {
            // Update cache with verified status
            cacheService.putKycStatus(
                event.getUserId(),
                "VERIFIED",
                event.getKycLevel(),
                event.getVerifiedAt()
            );

            // Also cache risk score and compliance info
            cacheService.putKycRiskScore(event.getUserId(), event.getRiskScore());
            cacheService.putKycComplianceInfo(event.getUserId(), event.getComplianceNotes());

            log.info("Updated KYC cache: userId={}, status=VERIFIED, level={}",
                event.getUserId(), event.getKycLevel());

        } catch (Exception e) {
            log.error("Failed to update KYC status and cache: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleComplianceRequirements(KYCVerifiedEvent event, String correlationId) {
        try {
            // Check if higher KYC level triggers additional compliance requirements
            switch (event.getKycLevel()) {
                case "KYC_LEVEL_3":
                case "KYC_ENHANCED":
                    // Trigger enhanced due diligence
                    kafkaTemplate.send("enhanced-due-diligence-requests", Map.of(
                        "userId", event.getUserId(),
                        "verificationId", event.getVerificationId(),
                        "kycLevel", event.getKycLevel(),
                        "riskScore", event.getRiskScore(),
                        "correlationId", correlationId,
                        "timestamp", Instant.now()
                    ));
                    break;

                case "KYC_INSTITUTIONAL":
                    // Trigger institutional compliance checks
                    kafkaTemplate.send("institutional-compliance-checks", Map.of(
                        "userId", event.getUserId(),
                        "verificationId", event.getVerificationId(),
                        "correlationId", correlationId,
                        "timestamp", Instant.now()
                    ));
                    break;
            }

            // Check for high-risk indicators
            if (event.getRiskScore() != null && event.getRiskScore() > 75) {
                kafkaTemplate.send("high-risk-user-alerts", Map.of(
                    "userId", event.getUserId(),
                    "riskScore", event.getRiskScore(),
                    "kycLevel", event.getKycLevel(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }

            log.info("Processed compliance requirements: userId={}, level={}, riskScore={}",
                event.getUserId(), event.getKycLevel(), event.getRiskScore());

        } catch (Exception e) {
            log.error("Failed to handle compliance requirements: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void updateUserPrivileges(KYCVerifiedEvent event, String correlationId) {
        try {
            // Send event to update user privileges based on KYC level
            kafkaTemplate.send("user-privilege-updates", Map.of(
                "userId", event.getUserId(),
                "kycLevel", event.getKycLevel(),
                "previousKycLevel", event.getPreviousKycLevel(),
                "verifiedAt", event.getVerifiedAt(),
                "riskScore", event.getRiskScore(),
                "updateType", "KYC_VERIFICATION_COMPLETED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Send to account limits service to update transaction limits
            kafkaTemplate.send("account-limits-updates", Map.of(
                "userId", event.getUserId(),
                "kycLevel", event.getKycLevel(),
                "verificationId", event.getVerificationId(),
                "action", "UPDATE_LIMITS_FOR_KYC_LEVEL",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Updated user privileges: userId={}, level={}",
                event.getUserId(), event.getKycLevel());

        } catch (Exception e) {
            log.error("Failed to update user privileges: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendVerificationCompletedNotifications(KYCVerifiedEvent event, String correlationId) {
        try {
            // Send user notification
            kafkaTemplate.send("kyc-notifications", Map.of(
                "userId", event.getUserId(),
                "notificationType", "VERIFICATION_COMPLETED",
                "kycStatus", "VERIFIED",
                "kycLevel", event.getKycLevel(),
                "notificationChannel", "EMAIL",
                "subject", "Identity Verification Complete",
                "message", String.format("Your identity verification is complete. KYC Level: %s", event.getKycLevel()),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Send internal notification for high-value verifications
            if ("KYC_LEVEL_3".equals(event.getKycLevel()) || "KYC_ENHANCED".equals(event.getKycLevel())) {
                notificationService.sendInternalNotification(
                    "compliance-team",
                    "High-Level KYC Verification Completed",
                    String.format("User %s completed %s verification. Risk Score: %s",
                        event.getUserId(), event.getKycLevel(), event.getRiskScore()),
                    Map.of("userId", event.getUserId(), "kycLevel", event.getKycLevel())
                );
            }

            log.info("Sent verification completed notifications: userId={}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send verification completed notifications: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void publishDownstreamEvents(KYCVerifiedEvent event, String correlationId) {
        try {
            // Publish to customer lifecycle events
            kafkaTemplate.send("customer-lifecycle-events", Map.of(
                "userId", event.getUserId(),
                "eventType", "KYC_VERIFICATION_COMPLETED",
                "kycLevel", event.getKycLevel(),
                "verificationId", event.getVerificationId(),
                "verifiedAt", event.getVerifiedAt(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Publish to analytics for KYC completion metrics
            kafkaTemplate.send("analytics-events", Map.of(
                "eventType", "KYC_VERIFIED",
                "userId", event.getUserId(),
                "kycLevel", event.getKycLevel(),
                "previousKycLevel", event.getPreviousKycLevel(),
                "riskScore", event.getRiskScore(),
                "documentsVerified", event.getDocumentsVerified(),
                "verificationTime", event.getVerifiedAt(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Publish to risk assessment service
            kafkaTemplate.send("risk-assessment-events", Map.of(
                "userId", event.getUserId(),
                "eventType", "KYC_COMPLETED",
                "kycLevel", event.getKycLevel(),
                "riskScore", event.getRiskScore(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Published downstream events: userId={}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to publish downstream events: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void generateComplianceReports(KYCVerifiedEvent event, String correlationId) {
        try {
            // Generate compliance report for high-risk or high-level verifications
            boolean shouldGenerateReport = event.getRiskScore() != null && event.getRiskScore() > 50
                || "KYC_ENHANCED".equals(event.getKycLevel())
                || "KYC_INSTITUTIONAL".equals(event.getKycLevel());

            if (shouldGenerateReport) {
                complianceReportingService.generateKycCompletionReport(
                    event.getUserId(),
                    event.getVerificationId(),
                    event.getKycLevel(),
                    event.getRiskScore(),
                    correlationId
                );

                log.info("Generated compliance report: userId={}, level={}, riskScore={}",
                    event.getUserId(), event.getKycLevel(), event.getRiskScore());
            }

        } catch (Exception e) {
            log.error("Failed to generate compliance reports: {}", e.getMessage(), e);
            // Don't throw - this is not critical for the main flow
        }
    }

    private void updateDocumentStatuses(String verificationId, String correlationId) {
        try {
            // Find all documents for this verification
            var documents = documentRepository.findByVerificationId(verificationId);

            for (var document : documents) {
                if ("PENDING_VERIFICATION".equals(document.getStatus()) || "UNDER_REVIEW".equals(document.getStatus())) {
                    document.setStatus("VERIFIED");
                    document.setVerifiedAt(LocalDateTime.now());
                    document.setLastProcessedAt(LocalDateTime.now());
                    documentRepository.save(document);
                }
            }

            log.info("Updated document statuses for verification: {}", verificationId);

        } catch (Exception e) {
            log.error("Failed to update document statuses: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void createVerificationFromEvent(KYCVerifiedEvent event, String correlationId) {
        try {
            KYCVerification verification = new KYCVerification();
            verification.setUserId(event.getUserId());
            verification.setVerificationId(event.getVerificationId());
            verification.setStatus("VERIFIED");
            verification.setKycLevel(event.getKycLevel());
            verification.setVerifiedAt(event.getVerifiedAt());
            verification.setVerifiedBy(event.getVerifiedBy());
            verification.setRiskScore(event.getRiskScore());
            verification.setComplianceNotes(event.getComplianceNotes());
            verification.setCreatedAt(LocalDateTime.now());
            verification.setLastProcessedAt(LocalDateTime.now());

            verificationRepository.save(verification);

            log.info("Created verification record from event: userId={}, verificationId={}",
                event.getUserId(), event.getVerificationId());

        } catch (Exception e) {
            log.error("Failed to create verification from event: {}", e.getMessage(), e);
            throw e;
        }
    }
}