package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.CertificateSecurityService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.security.service.ThreatResponseService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
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

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class CertificateWarningsConsumer {

    private final CertificateSecurityService certificateSecurityService;
    private final SecurityNotificationService securityNotificationService;
    private final ThreatResponseService threatResponseService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
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
        successCounter = Counter.builder("certificate_warnings_processed_total")
            .description("Total number of successfully processed certificate warning events")
            .register(meterRegistry);
        errorCounter = Counter.builder("certificate_warnings_errors_total")
            .description("Total number of certificate warning processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("certificate_warnings_processing_duration")
            .description("Time taken to process certificate warning events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"certificate-warnings", "ssl-certificate-alerts", "cert-expiry-warnings"},
        groupId = "security-service-certificate-warnings-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "2"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "certificate-warnings", fallbackMethod = "handleCertificateWarningFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCertificateWarning(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("cert-warning-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String warningId = (String) event.get("warningId");
            String certificateId = (String) event.get("certificateId");
            String warningType = (String) event.get("warningType");
            String severity = (String) event.get("severity");
            String eventKey = String.format("%s-%s-%s", warningId, certificateId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing certificate warning: warningId={}, certId={}, type={}, severity={}",
                warningId, certificateId, warningType, severity);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String certificateName = (String) event.get("certificateName");
            String domain = (String) event.get("domain");
            LocalDateTime detectedAt = LocalDateTime.parse((String) event.get("detectedAt"));
            LocalDateTime expiryDate = event.get("expiryDate") != null ?
                LocalDateTime.parse((String) event.get("expiryDate")) : null;
            Integer daysUntilExpiry = (Integer) event.getOrDefault("daysUntilExpiry", 0);
            String issuer = (String) event.get("issuer");
            String subject = (String) event.get("subject");
            @SuppressWarnings("unchecked")
            List<String> affectedServices = (List<String>) event.getOrDefault("affectedServices", List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> certificateDetails = (Map<String, Object>) event.getOrDefault("certificateDetails", Map.of());
            String environment = (String) event.getOrDefault("environment", "UNKNOWN");
            Boolean isWildcard = (Boolean) event.getOrDefault("isWildcard", false);
            String keyLength = (String) event.getOrDefault("keyLength", "UNKNOWN");

            // Process certificate warning based on type
            switch (warningType) {
                case "EXPIRY_WARNING":
                    processExpiryWarning(warningId, certificateId, certificateName, domain,
                        expiryDate, daysUntilExpiry, affectedServices, severity, correlationId);
                    break;

                case "EXPIRED":
                    processExpiredCertificate(warningId, certificateId, certificateName, domain,
                        expiryDate, affectedServices, severity, correlationId);
                    break;

                case "INVALID_CERTIFICATE":
                    processInvalidCertificate(warningId, certificateId, certificateName, domain,
                        certificateDetails, affectedServices, severity, correlationId);
                    break;

                case "WEAK_CIPHER":
                    processWeakCipher(warningId, certificateId, certificateName, domain,
                        keyLength, certificateDetails, affectedServices, severity, correlationId);
                    break;

                case "REVOCATION_CHECK_FAILED":
                    processRevocationCheckFailed(warningId, certificateId, certificateName, domain,
                        issuer, certificateDetails, severity, correlationId);
                    break;

                case "CERTIFICATE_CHAIN_ERROR":
                    processCertificateChainError(warningId, certificateId, certificateName, domain,
                        issuer, certificateDetails, affectedServices, severity, correlationId);
                    break;

                case "HOSTNAME_MISMATCH":
                    processHostnameMismatch(warningId, certificateId, certificateName, domain,
                        subject, affectedServices, severity, correlationId);
                    break;

                case "SELF_SIGNED":
                    processSelfSigned(warningId, certificateId, certificateName, domain, issuer,
                        affectedServices, severity, correlationId);
                    break;

                default:
                    processGenericCertificateWarning(warningId, certificateId, warningType,
                        certificateName, domain, certificateDetails, severity, correlationId);
                    break;
            }

            // Assess security impact
            assessSecurityImpact(warningId, certificateId, warningType, severity, affectedServices,
                daysUntilExpiry, environment, isWildcard, correlationId);

            // Handle critical certificate issues
            if ("CRITICAL".equals(severity) || "EXPIRED".equals(warningType) || daysUntilExpiry <= 7) {
                handleCriticalCertificateIssue(warningId, certificateId, warningType, certificateName,
                    domain, daysUntilExpiry, affectedServices, correlationId);
            }

            // Schedule certificate renewal if needed
            if ("EXPIRY_WARNING".equals(warningType) && daysUntilExpiry <= 30) {
                scheduleCertificateRenewal(warningId, certificateId, certificateName, domain,
                    expiryDate, daysUntilExpiry, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("CERTIFICATE_WARNING_PROCESSED", certificateId,
                Map.of("warningId", warningId, "warningType", warningType, "severity", severity,
                    "certificateName", certificateName, "domain", domain, "daysUntilExpiry", daysUntilExpiry,
                    "affectedServicesCount", affectedServices.size(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process certificate warning event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("certificate-warnings-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCertificateWarningFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("cert-warning-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for certificate warning: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("certificate-warnings-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Certificate Warning Circuit Breaker Triggered",
                String.format("Certificate warning processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCertificateWarning(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-cert-warning-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Certificate warning permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String warningId = (String) event.get("warningId");
            String certificateId = (String) event.get("certificateId");
            String warningType = (String) event.get("warningType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("CERTIFICATE_WARNING_DLT_EVENT", certificateId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "warningId", warningId, "warningType", warningType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert for certificate warnings in DLT
            securityNotificationService.sendCriticalAlert(
                "CRITICAL: Certificate Warning DLT Event",
                String.format("CRITICAL: Certificate warning %s for certificate %s sent to DLT: %s. " +
                    "This may indicate a security vulnerability in certificate management.",
                    warningId, certificateId, exceptionMessage),
                Map.of("warningId", warningId, "certificateId", certificateId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse certificate warning DLT event: {}", eventJson, ex);
        }
    }

    private void processExpiryWarning(String warningId, String certificateId, String certificateName,
                                    String domain, LocalDateTime expiryDate, Integer daysUntilExpiry,
                                    List<String> affectedServices, String severity, String correlationId) {
        try {
            certificateSecurityService.processExpiryWarning(warningId, certificateId, certificateName,
                domain, expiryDate, daysUntilExpiry, affectedServices, severity);

            log.warn("Certificate expiry warning processed: warningId={}, cert={}, days={}",
                warningId, certificateName, daysUntilExpiry);

        } catch (Exception e) {
            log.error("Failed to process certificate expiry warning: warningId={}, cert={}",
                warningId, certificateName, e);
            throw new RuntimeException("Certificate expiry warning processing failed", e);
        }
    }

    private void processExpiredCertificate(String warningId, String certificateId, String certificateName,
                                         String domain, LocalDateTime expiryDate, List<String> affectedServices,
                                         String severity, String correlationId) {
        try {
            certificateSecurityService.processExpiredCertificate(warningId, certificateId, certificateName,
                domain, expiryDate, affectedServices, severity);

            log.error("Expired certificate processed: warningId={}, cert={}, expired={}",
                warningId, certificateName, expiryDate);

        } catch (Exception e) {
            log.error("Failed to process expired certificate: warningId={}, cert={}",
                warningId, certificateName, e);
            throw new RuntimeException("Expired certificate processing failed", e);
        }
    }

    private void processInvalidCertificate(String warningId, String certificateId, String certificateName,
                                         String domain, Map<String, Object> certificateDetails,
                                         List<String> affectedServices, String severity, String correlationId) {
        try {
            certificateSecurityService.processInvalidCertificate(warningId, certificateId, certificateName,
                domain, certificateDetails, affectedServices, severity);

            log.error("Invalid certificate processed: warningId={}, cert={}, domain={}",
                warningId, certificateName, domain);

        } catch (Exception e) {
            log.error("Failed to process invalid certificate: warningId={}, cert={}",
                warningId, certificateName, e);
            throw new RuntimeException("Invalid certificate processing failed", e);
        }
    }

    private void processWeakCipher(String warningId, String certificateId, String certificateName,
                                 String domain, String keyLength, Map<String, Object> certificateDetails,
                                 List<String> affectedServices, String severity, String correlationId) {
        try {
            certificateSecurityService.processWeakCipher(warningId, certificateId, certificateName,
                domain, keyLength, certificateDetails, affectedServices, severity);

            log.warn("Weak cipher warning processed: warningId={}, cert={}, keyLength={}",
                warningId, certificateName, keyLength);

        } catch (Exception e) {
            log.error("Failed to process weak cipher warning: warningId={}, cert={}",
                warningId, certificateName, e);
            throw new RuntimeException("Weak cipher warning processing failed", e);
        }
    }

    private void processRevocationCheckFailed(String warningId, String certificateId, String certificateName,
                                            String domain, String issuer, Map<String, Object> certificateDetails,
                                            String severity, String correlationId) {
        try {
            certificateSecurityService.processRevocationCheckFailed(warningId, certificateId, certificateName,
                domain, issuer, certificateDetails, severity);

            log.warn("Revocation check failed processed: warningId={}, cert={}, issuer={}",
                warningId, certificateName, issuer);

        } catch (Exception e) {
            log.error("Failed to process revocation check failed: warningId={}, cert={}",
                warningId, certificateName, e);
            throw new RuntimeException("Revocation check failed processing failed", e);
        }
    }

    private void processCertificateChainError(String warningId, String certificateId, String certificateName,
                                            String domain, String issuer, Map<String, Object> certificateDetails,
                                            List<String> affectedServices, String severity, String correlationId) {
        try {
            certificateSecurityService.processCertificateChainError(warningId, certificateId, certificateName,
                domain, issuer, certificateDetails, affectedServices, severity);

            log.error("Certificate chain error processed: warningId={}, cert={}, issuer={}",
                warningId, certificateName, issuer);

        } catch (Exception e) {
            log.error("Failed to process certificate chain error: warningId={}, cert={}",
                warningId, certificateName, e);
            throw new RuntimeException("Certificate chain error processing failed", e);
        }
    }

    private void processHostnameMismatch(String warningId, String certificateId, String certificateName,
                                       String domain, String subject, List<String> affectedServices,
                                       String severity, String correlationId) {
        try {
            certificateSecurityService.processHostnameMismatch(warningId, certificateId, certificateName,
                domain, subject, affectedServices, severity);

            log.error("Hostname mismatch processed: warningId={}, cert={}, domain={}, subject={}",
                warningId, certificateName, domain, subject);

        } catch (Exception e) {
            log.error("Failed to process hostname mismatch: warningId={}, cert={}",
                warningId, certificateName, e);
            throw new RuntimeException("Hostname mismatch processing failed", e);
        }
    }

    private void processSelfSigned(String warningId, String certificateId, String certificateName,
                                 String domain, String issuer, List<String> affectedServices,
                                 String severity, String correlationId) {
        try {
            certificateSecurityService.processSelfSigned(warningId, certificateId, certificateName,
                domain, issuer, affectedServices, severity);

            log.warn("Self-signed certificate processed: warningId={}, cert={}, domain={}",
                warningId, certificateName, domain);

        } catch (Exception e) {
            log.error("Failed to process self-signed certificate: warningId={}, cert={}",
                warningId, certificateName, e);
            throw new RuntimeException("Self-signed certificate processing failed", e);
        }
    }

    private void processGenericCertificateWarning(String warningId, String certificateId, String warningType,
                                                 String certificateName, String domain,
                                                 Map<String, Object> certificateDetails, String severity,
                                                 String correlationId) {
        try {
            certificateSecurityService.processGenericCertificateWarning(warningId, certificateId, warningType,
                certificateName, domain, certificateDetails, severity);

            log.info("Generic certificate warning processed: warningId={}, cert={}, type={}",
                warningId, certificateName, warningType);

        } catch (Exception e) {
            log.error("Failed to process generic certificate warning: warningId={}, cert={}",
                warningId, certificateName, e);
            throw new RuntimeException("Generic certificate warning processing failed", e);
        }
    }

    private void assessSecurityImpact(String warningId, String certificateId, String warningType,
                                    String severity, List<String> affectedServices, Integer daysUntilExpiry,
                                    String environment, Boolean isWildcard, String correlationId) {
        try {
            threatResponseService.assessCertificateSecurityImpact(warningId, certificateId, warningType,
                severity, affectedServices, daysUntilExpiry, environment, isWildcard);

            log.debug("Certificate security impact assessed: warningId={}, type={}, services={}",
                warningId, warningType, affectedServices.size());

        } catch (Exception e) {
            log.error("Failed to assess certificate security impact: warningId={}, cert={}",
                warningId, certificateId, e);
            // Don't throw exception as impact assessment failure shouldn't block processing
        }
    }

    private void handleCriticalCertificateIssue(String warningId, String certificateId, String warningType,
                                              String certificateName, String domain, Integer daysUntilExpiry,
                                              List<String> affectedServices, String correlationId) {
        try {
            certificateSecurityService.handleCriticalCertificateIssue(warningId, certificateId, warningType,
                certificateName, domain, daysUntilExpiry, affectedServices);

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Critical Certificate Issue",
                String.format("CRITICAL: Certificate issue for %s (%s): %s. Days until expiry: %d. " +
                    "Affected services: %d", certificateName, domain, warningType, daysUntilExpiry, affectedServices.size()),
                Map.of("warningId", warningId, "certificateId", certificateId, "warningType", warningType,
                    "certificateName", certificateName, "domain", domain, "daysUntilExpiry", daysUntilExpiry,
                    "correlationId", correlationId)
            );

            log.error("Critical certificate issue handled: warningId={}, cert={}, type={}",
                warningId, certificateName, warningType);

        } catch (Exception e) {
            log.error("Failed to handle critical certificate issue: warningId={}, cert={}",
                warningId, certificateName, e);
            // Don't throw exception as critical handling failure shouldn't block processing
        }
    }

    private void scheduleCertificateRenewal(String warningId, String certificateId, String certificateName,
                                          String domain, LocalDateTime expiryDate, Integer daysUntilExpiry,
                                          String correlationId) {
        try {
            certificateSecurityService.scheduleCertificateRenewal(warningId, certificateId, certificateName,
                domain, expiryDate, daysUntilExpiry);

            log.info("Certificate renewal scheduled: warningId={}, cert={}, expiry={}, days={}",
                warningId, certificateName, expiryDate, daysUntilExpiry);

        } catch (Exception e) {
            log.error("Failed to schedule certificate renewal: warningId={}, cert={}",
                warningId, certificateName, e);
            // Don't throw exception as renewal scheduling failure shouldn't block processing
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
}