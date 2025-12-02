package com.waqiti.kyc.kafka;

import com.waqiti.common.events.compliance.KycProcessingErrorsEvent;
import com.waqiti.kyc.repository.KYCVerificationRepository;
import com.waqiti.kyc.repository.VerificationDocumentRepository;
import com.waqiti.kyc.service.KYCVerificationService;
import com.waqiti.kyc.service.DocumentVerificationService;
import com.waqiti.kyc.service.CacheService;
import com.waqiti.kyc.domain.KYCVerification;
import com.waqiti.kyc.domain.VerificationDocument;
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
public class KycProcessingErrorsConsumer {

    private final KYCVerificationRepository verificationRepository;
    private final VerificationDocumentRepository documentRepository;
    private final KYCVerificationService verificationService;
    private final DocumentVerificationService documentVerificationService;
    private final CacheService cacheService;
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
        successCounter = Counter.builder("kyc_processing_errors_processed_total")
            .description("Total number of successfully processed KYC processing error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("kyc_processing_errors_errors_total")
            .description("Total number of KYC processing error handling errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("kyc_processing_errors_processing_duration")
            .description("Time taken to process KYC processing error events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"kyc-processing-errors", "kyc-error-handling", "kyc-retry-queue"},
        groupId = "kyc-processing-errors-service-group",
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
    @CircuitBreaker(name = "kyc-processing-errors", fallbackMethod = "handleKycProcessingErrorsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleKycProcessingErrorsEvent(
            @Payload KycProcessingErrorsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("kyc-error-%s-p%d-o%d", event.getUserId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getUserId(), event.getErrorCode(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing KYC error: userId={}, errorType={}, errorCode={}, severity={}",
                event.getUserId(), event.getErrorType(), event.getErrorCode(), event.getErrorSeverity());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getErrorType()) {
                case "DOCUMENT_PROCESSING_ERROR":
                    handleDocumentProcessingError(event, correlationId);
                    break;

                case "VERIFICATION_TIMEOUT":
                    handleVerificationTimeout(event, correlationId);
                    break;

                case "PROVIDER_ERROR":
                    handleProviderError(event, correlationId);
                    break;

                case "VALIDATION_ERROR":
                    handleValidationError(event, correlationId);
                    break;

                case "NETWORK_ERROR":
                    handleNetworkError(event, correlationId);
                    break;

                case "SYSTEM_ERROR":
                    handleSystemError(event, correlationId);
                    break;

                case "COMPLIANCE_ERROR":
                    handleComplianceError(event, correlationId);
                    break;

                case "DATA_CORRUPTION":
                    handleDataCorruptionError(event, correlationId);
                    break;

                case "RATE_LIMIT_ERROR":
                    handleRateLimitError(event, correlationId);
                    break;

                case "AUTHENTICATION_ERROR":
                    handleAuthenticationError(event, correlationId);
                    break;

                default:
                    handleGenericError(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logUserEvent("KYC_PROCESSING_ERROR_HANDLED", event.getUserId(),
                Map.of("errorType", event.getErrorType(), "errorCode", event.getErrorCode(),
                    "errorSeverity", event.getErrorSeverity(), "processingStage", event.getProcessingStage(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process KYC processing error event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("kyc-processing-errors-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleKycProcessingErrorsEventFallback(
            KycProcessingErrorsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("kyc-error-fallback-%s-p%d-o%d", event.getUserId(), partition, offset);

        log.error("Circuit breaker fallback triggered for KYC processing error: userId={}, error={}",
            event.getUserId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("kyc-processing-errors-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "KYC Processing Error Circuit Breaker Triggered",
                String.format("KYC error handling for user %s failed: %s", event.getUserId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltKycProcessingErrorsEvent(
            @Payload KycProcessingErrorsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-kyc-error-%s-%d", event.getUserId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - KYC processing error permanently failed: userId={}, topic={}, error={}",
            event.getUserId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logUserEvent("KYC_PROCESSING_ERROR_DLT_EVENT", event.getUserId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "errorType", event.getErrorType(), "errorCode", event.getErrorCode(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "KYC Processing Error Dead Letter Event",
                String.format("KYC error handling for user %s sent to DLT: %s", event.getUserId(), exceptionMessage),
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

    private void handleDocumentProcessingError(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling document processing error: userId={}, documentId={}",
                event.getUserId(), event.getDocumentId());

            // Find the verification and document
            Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());
            if (verification.isPresent()) {
                // Mark document as failed if it exists
                if (event.getDocumentId() != null) {
                    Optional<VerificationDocument> document = documentRepository.findById(event.getDocumentId());
                    if (document.isPresent()) {
                        VerificationDocument doc = document.get();
                        doc.setStatus("PROCESSING_FAILED");
                        doc.setLastProcessedAt(LocalDateTime.now());
                        doc.setProcessingError(event.getErrorMessage());
                        documentRepository.save(doc);
                    }
                }

                // Check if retry is possible
                if (event.getRetryCount() < event.getMaxRetries()) {
                    scheduleRetry(event, correlationId);
                } else {
                    markVerificationFailed(verification.get(), "DOCUMENT_PROCESSING_FAILED", correlationId);
                }
            }

            // Update cache with error status
            cacheService.putKycStatus(event.getUserId(), "PROCESSING_ERROR",
                "DOCUMENT_ERROR", LocalDateTime.now());

        } catch (Exception e) {
            log.error("Failed to handle document processing error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleVerificationTimeout(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling verification timeout: userId={}, processingStage={}",
                event.getUserId(), event.getProcessingStage());

            Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());
            if (verification.isPresent()) {
                // Check if retry is possible
                if (event.getRetryCount() < event.getMaxRetries()) {
                    scheduleRetry(event, correlationId);
                } else {
                    markVerificationFailed(verification.get(), "VERIFICATION_TIMEOUT", correlationId);
                }
            }

            // Update cache with timeout status
            cacheService.putKycStatus(event.getUserId(), "TIMEOUT_ERROR",
                event.getProcessingStage(), LocalDateTime.now());

        } catch (Exception e) {
            log.error("Failed to handle verification timeout: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleProviderError(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling provider error: userId={}, provider={}",
                event.getUserId(), event.getVerificationProvider());

            // Try to switch to backup provider if available
            Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());
            if (verification.isPresent()) {
                KYCVerification kycVerification = verification.get();

                // Switch to backup provider if retry count allows
                if (event.getRetryCount() < event.getMaxRetries()) {
                    switchProviderAndRetry(kycVerification, event, correlationId);
                } else {
                    markVerificationFailed(kycVerification, "PROVIDER_ERROR", correlationId);
                }
            }

        } catch (Exception e) {
            log.error("Failed to handle provider error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleValidationError(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling validation error: userId={}, errorCode={}",
                event.getUserId(), event.getErrorCode());

            Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());
            if (verification.isPresent()) {
                KYCVerification kycVerification = verification.get();
                kycVerification.setStatus("VALIDATION_ERROR");
                kycVerification.setErrorMessage(event.getErrorMessage());
                kycVerification.setLastProcessedAt(LocalDateTime.now());
                verificationRepository.save(kycVerification);

                // Send notification for user to fix validation issues
                notificationService.sendUserNotification(
                    event.getUserId(),
                    "Verification Document Issue",
                    "There was an issue with your submitted documents. Please review and resubmit.",
                    "EMAIL"
                );
            }

        } catch (Exception e) {
            log.error("Failed to handle validation error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleNetworkError(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling network error: userId={}, provider={}",
                event.getUserId(), event.getVerificationProvider());

            // Network errors are usually transient, schedule retry
            if (event.getRetryCount() < event.getMaxRetries()) {
                scheduleRetry(event, correlationId);
            } else {
                Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());
                if (verification.isPresent()) {
                    markVerificationFailed(verification.get(), "NETWORK_ERROR", correlationId);
                }
            }

        } catch (Exception e) {
            log.error("Failed to handle network error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleSystemError(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling system error: userId={}, errorCode={}",
                event.getUserId(), event.getErrorCode());

            // System errors require immediate attention
            notificationService.sendOperationalAlert(
                "KYC System Error",
                String.format("System error in KYC processing for user %s: %s",
                    event.getUserId(), event.getErrorMessage()),
                "HIGH"
            );

            // Update verification status
            Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());
            if (verification.isPresent()) {
                markVerificationFailed(verification.get(), "SYSTEM_ERROR", correlationId);
            }

        } catch (Exception e) {
            log.error("Failed to handle system error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleComplianceError(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling compliance error: userId={}, errorCode={}",
                event.getUserId(), event.getErrorCode());

            // Compliance errors require special handling
            notificationService.sendComplianceAlert(
                "KYC Compliance Error",
                String.format("Compliance issue in KYC processing for user %s: %s",
                    event.getUserId(), event.getErrorMessage()),
                "CRITICAL",
                Map.of("userId", event.getUserId(), "verificationId", event.getVerificationId())
            );

            Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());
            if (verification.isPresent()) {
                KYCVerification kycVerification = verification.get();
                kycVerification.setStatus("COMPLIANCE_ERROR");
                kycVerification.setErrorMessage(event.getErrorMessage());
                kycVerification.setRequiresManualReview(true);
                verificationRepository.save(kycVerification);
            }

        } catch (Exception e) {
            log.error("Failed to handle compliance error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleDataCorruptionError(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling data corruption error: userId={}, documentId={}",
                event.getUserId(), event.getDocumentId());

            // Data corruption requires immediate investigation
            notificationService.sendCriticalAlert(
                "KYC Data Corruption Detected",
                String.format("Data corruption detected for user %s: %s",
                    event.getUserId(), event.getErrorMessage()),
                Map.of("userId", event.getUserId(), "documentId", event.getDocumentId())
            );

            Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());
            if (verification.isPresent()) {
                markVerificationFailed(verification.get(), "DATA_CORRUPTION", correlationId);
            }

        } catch (Exception e) {
            log.error("Failed to handle data corruption error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleRateLimitError(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling rate limit error: userId={}, provider={}",
                event.getUserId(), event.getVerificationProvider());

            // Rate limit errors should be retried with exponential backoff
            scheduleDelayedRetry(event, correlationId, calculateBackoffDelay(event.getRetryCount()));

        } catch (Exception e) {
            log.error("Failed to handle rate limit error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleAuthenticationError(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling authentication error: userId={}, provider={}",
                event.getUserId(), event.getVerificationProvider());

            // Authentication errors with providers require immediate attention
            notificationService.sendOperationalAlert(
                "KYC Provider Authentication Error",
                String.format("Authentication failed with provider %s for user %s",
                    event.getVerificationProvider(), event.getUserId()),
                "HIGH"
            );

            Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());
            if (verification.isPresent()) {
                markVerificationFailed(verification.get(), "AUTHENTICATION_ERROR", correlationId);
            }

        } catch (Exception e) {
            log.error("Failed to handle authentication error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleGenericError(KycProcessingErrorsEvent event, String correlationId) {
        try {
            log.info("Handling generic error: userId={}, errorType={}",
                event.getUserId(), event.getErrorType());

            // Generic retry logic
            if (event.getRetryCount() < event.getMaxRetries()) {
                scheduleRetry(event, correlationId);
            } else {
                Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());
                if (verification.isPresent()) {
                    markVerificationFailed(verification.get(), event.getErrorType(), correlationId);
                }
            }

        } catch (Exception e) {
            log.error("Failed to handle generic error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void scheduleRetry(KycProcessingErrorsEvent event, String correlationId) {
        kafkaTemplate.send("kyc-retry-queue", Map.of(
            "userId", event.getUserId(),
            "verificationId", event.getVerificationId(),
            "retryCount", event.getRetryCount() + 1,
            "maxRetries", event.getMaxRetries(),
            "correlationId", correlationId,
            "scheduledAt", Instant.now().plusSeconds(calculateBackoffDelay(event.getRetryCount())),
            "originalError", event.getErrorMessage()
        ));

        log.info("Scheduled retry for user {}: attempt {}/{}",
            event.getUserId(), event.getRetryCount() + 1, event.getMaxRetries());
    }

    private void scheduleDelayedRetry(KycProcessingErrorsEvent event, String correlationId, long delaySeconds) {
        kafkaTemplate.send("kyc-retry-queue", Map.of(
            "userId", event.getUserId(),
            "verificationId", event.getVerificationId(),
            "retryCount", event.getRetryCount() + 1,
            "maxRetries", event.getMaxRetries(),
            "correlationId", correlationId,
            "scheduledAt", Instant.now().plusSeconds(delaySeconds),
            "originalError", event.getErrorMessage()
        ));

        log.info("Scheduled delayed retry for user {}: attempt {}/{} in {} seconds",
            event.getUserId(), event.getRetryCount() + 1, event.getMaxRetries(), delaySeconds);
    }

    private void switchProviderAndRetry(KYCVerification verification, KycProcessingErrorsEvent event, String correlationId) {
        // Logic to switch to backup provider
        String currentProvider = verification.getProvider();
        String backupProvider = getBackupProvider(currentProvider);

        if (backupProvider != null) {
            verification.setProvider(backupProvider);
            verification.setLastProcessedAt(LocalDateTime.now());
            verificationRepository.save(verification);

            // Send to retry queue with new provider
            kafkaTemplate.send("kyc-provider-retry", Map.of(
                "userId", event.getUserId(),
                "verificationId", event.getVerificationId(),
                "newProvider", backupProvider,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            log.info("Switched provider for user {} from {} to {}",
                event.getUserId(), currentProvider, backupProvider);
        } else {
            markVerificationFailed(verification, "NO_BACKUP_PROVIDER", correlationId);
        }
    }

    private void markVerificationFailed(KYCVerification verification, String reason, String correlationId) {
        verification.setStatus("FAILED");
        verification.setErrorMessage(reason);
        verification.setLastProcessedAt(LocalDateTime.now());
        verificationRepository.save(verification);

        // Update cache
        cacheService.putKycStatus(verification.getUserId(), "FAILED", reason, LocalDateTime.now());

        // Send failure notification
        kafkaTemplate.send("kyc-notifications", Map.of(
            "userId", verification.getUserId(),
            "notificationType", "VERIFICATION_FAILED",
            "kycStatus", "FAILED",
            "message", "Verification failed: " + reason,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Marked verification as failed for user {}: {}", verification.getUserId(), reason);
    }

    private String getBackupProvider(String currentProvider) {
        // Simple backup provider logic
        switch (currentProvider) {
            case "ONFIDO": return "JUMIO";
            case "JUMIO": return "TRULIOO";
            case "TRULIOO": return "ONFIDO";
            default: return null;
        }
    }

    private long calculateBackoffDelay(int retryCount) {
        // Exponential backoff: 2^retryCount seconds, max 300 seconds (5 minutes)
        return Math.min(300, (long) Math.pow(2, retryCount));
    }
}