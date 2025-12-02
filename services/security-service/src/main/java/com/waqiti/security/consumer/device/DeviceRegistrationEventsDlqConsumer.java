package com.waqiti.security.consumer.device;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.security.service.DeviceService;
import com.waqiti.security.service.DeviceSecurityService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceRegistrationEventsDlqConsumer extends BaseDlqConsumer {

    private final DeviceService deviceService;
    private final DeviceSecurityService deviceSecurityService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public DeviceRegistrationEventsDlqConsumer(DeviceService deviceService,
                                               DeviceSecurityService deviceSecurityService,
                                               MeterRegistry meterRegistry) {
        super("device-registration-events-dlq");
        this.deviceService = deviceService;
        this.deviceSecurityService = deviceSecurityService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("device_registration_events_dlq_processed_total")
                .description("Total device registration events DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("device_registration_events_dlq_errors_total")
                .description("Total device registration events DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("device_registration_events_dlq_duration")
                .description("Device registration events DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "device-registration-events-dlq",
        groupId = "device-service-device-registration-events-dlq-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.max-poll-interval-ms=300000",
            "spring.kafka.consumer.session-timeout-ms=30000"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "device-registration-dlq", fallbackMethod = "handleDeviceRegistrationDlqFallback")
    public void handleDeviceRegistrationEventsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Device-Id", required = false) String deviceId,
            @Header(value = "X-User-Id", required = false) String userId,
            @Header(value = "X-Device-Type", required = false) String deviceType,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Device registration event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing device registration DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, deviceId={}, userId={}, deviceType={}",
                     topic, partition, offset, record.key(), correlationId, deviceId, userId, deviceType);

            String registrationData = record.value();
            validateDeviceRegistrationData(registrationData, eventId);

            // Perform security validation before processing
            performSecurityValidation(registrationData, correlationId);

            // Process device registration DLQ with security checks
            DeviceRegistrationRecoveryResult result = deviceService.processDeviceRegistrationEventsDlq(
                registrationData,
                record.key(),
                correlationId,
                deviceId,
                userId,
                deviceType,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result based on registration status
            if (result.isRegistered()) {
                handleSuccessfulRegistration(result, correlationId);
            } else if (result.isSecurityBlocked()) {
                handleSecurityBlocked(result, correlationId);
            } else if (result.requiresManualVerification()) {
                handleManualVerificationRequired(result, correlationId);
            } else {
                handleFailedRegistration(result, eventId, correlationId);
            }

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed device registration DLQ: eventId={}, deviceId={}, " +
                    "correlationId={}, registrationStatus={}",
                    eventId, result.getDeviceId(), correlationId, result.getRegistrationStatus());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in device registration DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (SecurityException e) {
            errorCounter.increment();
            log.error("Security violation in device registration DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleSecurityViolation(record, e, correlationId);
            throw e; // Security violations must be retried
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in device registration DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in device registration DLQ: eventId={}, correlationId={}",
                     eventId, correlationId, e);
            handleCriticalFailure(record, e, correlationId);
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.ORIGINAL_OFFSET) long originalOffset,
            @Header(KafkaHeaders.ORIGINAL_PARTITION) int originalPartition) {

        String correlationId = generateCorrelationId();
        log.error("Device registration event sent to DLT - SECURITY CONCERN: " +
                 "topic={}, originalPartition={}, originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        // Execute device security incident protocol
        executeDeviceSecurityIncidentProtocol(record, topic, exceptionMessage, correlationId);

        // Store for security audit
        storeForSecurityAudit(record, topic, exceptionMessage, correlationId);

        // Send security team alert
        sendSecurityTeamAlert(record, topic, exceptionMessage, correlationId);

        // Update DLT metrics
        Counter.builder("device_registration_events_dlt_events_total")
                .description("Total device registration events sent to DLT")
                .tag("topic", topic)
                .tag("security_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    public void handleDeviceRegistrationDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String deviceId, String userId, String deviceType,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for device registration DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store in security review queue
        storeInSecurityReviewQueue(record, correlationId);

        // Send device security team alert
        sendDeviceSecurityTeamAlert(correlationId, ex);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("device_registration_dlq_circuit_breaker_activations_total")
                .tag("security_impact", "medium")
                .register(meterRegistry)
                .increment();
    }

    private void validateDeviceRegistrationData(String registrationData, String eventId) {
        if (registrationData == null || registrationData.trim().isEmpty()) {
            throw new ValidationException("Device registration data is null or empty for eventId: " + eventId);
        }

        if (!registrationData.contains("deviceId")) {
            throw new ValidationException("Device registration data missing deviceId for eventId: " + eventId);
        }

        if (!registrationData.contains("userId")) {
            throw new ValidationException("Device registration data missing userId for eventId: " + eventId);
        }

        if (!registrationData.contains("deviceFingerprint")) {
            throw new ValidationException("Device registration data missing deviceFingerprint for eventId: " + eventId);
        }

        // Validate device security requirements
        validateDeviceSecurityRequirements(registrationData, eventId);
    }

    private void validateDeviceSecurityRequirements(String registrationData, String eventId) {
        try {
            JsonNode data = objectMapper.readTree(registrationData);

            // Check for required security fields
            if (!data.has("deviceFingerprint") || data.get("deviceFingerprint").asText().isEmpty()) {
                throw new SecurityException("Missing or invalid device fingerprint for eventId: " + eventId);
            }

            if (!data.has("ipAddress") || data.get("ipAddress").asText().isEmpty()) {
                log.warn("Device registration missing IP address for security tracking: eventId={}", eventId);
            }

            // Check for suspicious patterns
            String userAgent = data.has("userAgent") ? data.get("userAgent").asText() : "";
            if (userAgent.toLowerCase().contains("bot") || userAgent.toLowerCase().contains("crawler")) {
                throw new SecurityException("Suspicious user agent detected in device registration: " + eventId);
            }

        } catch (Exception e) {
            throw new ValidationException("Failed to validate device security requirements: " + e.getMessage());
        }
    }

    private void performSecurityValidation(String registrationData, String correlationId) {
        try {
            DeviceSecurityValidationResult securityCheck = deviceSecurityService.validateRegistration(
                registrationData,
                correlationId
            );

            if (!securityCheck.isValid()) {
                throw new SecurityException("Device security validation failed: " + securityCheck.getFailureReason());
            }

            if (securityCheck.isSuspicious()) {
                log.warn("Device registration flagged as suspicious: correlationId={}, reason={}",
                        correlationId, securityCheck.getSuspiciousReason());
                // Continue processing but flag for additional monitoring
            }

        } catch (Exception e) {
            log.error("Device security validation error: correlationId={}", correlationId, e);
            throw new SecurityException("Device security validation failed: " + e.getMessage(), e);
        }
    }

    private void handleSuccessfulRegistration(DeviceRegistrationRecoveryResult result, String correlationId) {
        log.info("Device successfully registered: deviceId={}, userId={}, correlationId={}",
                result.getDeviceId(), result.getUserId(), correlationId);

        // Update device status
        deviceService.updateDeviceStatus(
            result.getDeviceId(),
            DeviceStatus.REGISTERED,
            correlationId
        );

        // Create device profile
        deviceProfileService.createProfile(
            result.getDeviceId(),
            result.getUserId(),
            result.getDeviceFingerprint(),
            result.getDeviceMetadata(),
            correlationId
        );

        // Send registration confirmation
        notificationService.sendDeviceRegistrationConfirmation(
            result.getUserId(),
            result.getDeviceId(),
            result.getDeviceType(),
            correlationId
        );

        // Update device metrics
        deviceMetricsService.recordRegistration(
            result.getDeviceType(),
            result.getRegistrationMethod(),
            correlationId
        );

        // Enable device for authentication
        deviceAuthenticationService.enableDevice(
            result.getDeviceId(),
            result.getUserId(),
            correlationId
        );
    }

    private void handleSecurityBlocked(DeviceRegistrationRecoveryResult result, String correlationId) {
        log.warn("Device registration blocked for security: deviceId={}, userId={}, reason={}, correlationId={}",
                result.getDeviceId(), result.getUserId(), result.getSecurityBlockReason(), correlationId);

        // Update device status to blocked
        deviceService.updateDeviceStatus(
            result.getDeviceId(),
            DeviceStatus.SECURITY_BLOCKED,
            correlationId
        );

        // Create security incident
        deviceSecurityIncidentService.createIncident(
            IncidentType.DEVICE_REGISTRATION_SECURITY_BLOCK,
            result.getDeviceId(),
            result.getUserId(),
            result.getSecurityBlockReason(),
            correlationId,
            Severity.MEDIUM
        );

        // Send security notification
        notificationService.sendDeviceSecurityBlockNotification(
            result.getUserId(),
            result.getDeviceId(),
            result.getSecurityBlockReason(),
            correlationId
        );

        // Add to security monitoring
        deviceSecurityMonitoringService.addToWatchlist(
            result.getDeviceId(),
            result.getDeviceFingerprint(),
            result.getSecurityBlockReason(),
            correlationId
        );
    }

    private void handleManualVerificationRequired(DeviceRegistrationRecoveryResult result, String correlationId) {
        log.info("Device registration requires manual verification: deviceId={}, userId={}, reason={}, correlationId={}",
                result.getDeviceId(), result.getUserId(), result.getVerificationReason(), correlationId);

        // Update device status to pending verification
        deviceService.updateDeviceStatus(
            result.getDeviceId(),
            DeviceStatus.PENDING_VERIFICATION,
            correlationId
        );

        // Queue for manual verification
        manualVerificationQueue.add(
            ManualVerificationRequest.builder()
                .deviceId(result.getDeviceId())
                .userId(result.getUserId())
                .verificationReason(result.getVerificationReason())
                .deviceFingerprint(result.getDeviceFingerprint())
                .correlationId(correlationId)
                .priority(Priority.MEDIUM)
                .assignedTo("DEVICE_SECURITY_TEAM")
                .requiresUserContact(false)
                .build()
        );

        // Send verification pending notification
        notificationService.sendDeviceVerificationPendingNotification(
            result.getUserId(),
            result.getDeviceId(),
            correlationId
        );
    }

    private void handleFailedRegistration(DeviceRegistrationRecoveryResult result, String eventId, String correlationId) {
        log.error("Device registration recovery failed: deviceId={}, userId={}, reason={}, correlationId={}",
                result.getDeviceId(), result.getUserId(), result.getFailureReason(), correlationId);

        // Update device status to registration failed
        deviceService.updateDeviceStatus(
            result.getDeviceId(),
            DeviceStatus.REGISTRATION_FAILED,
            correlationId
        );

        // Escalate to device operations team
        deviceOperationsEscalationService.escalateRegistrationFailure(
            result.getDeviceId(),
            result.getUserId(),
            result.getFailureReason(),
            eventId,
            correlationId,
            EscalationPriority.MEDIUM
        );

        // Send failure notification
        notificationService.sendDeviceRegistrationFailureNotification(
            result.getUserId(),
            result.getDeviceId(),
            result.getFailureReason(),
            correlationId
        );

        // Create support ticket if user-facing
        if (result.isUserFacing()) {
            supportTicketService.createTicket(
                TicketType.DEVICE_REGISTRATION_FAILURE,
                result.getUserId(),
                String.format("Device registration failed: %s", result.getFailureReason()),
                Priority.MEDIUM,
                correlationId
            );
        }
    }

    private void handleSecurityViolation(ConsumerRecord<String, String> record,
                                        SecurityException e, String correlationId) {
        // Create device security violation record
        deviceSecurityViolationRepository.save(
            DeviceSecurityViolation.builder()
                .deviceId(extractDeviceId(record.value()))
                .userId(extractUserId(record.value()))
                .violationType(ViolationType.DEVICE_REGISTRATION_SECURITY)
                .description(e.getMessage())
                .correlationId(correlationId)
                .severity(Severity.HIGH)
                .timestamp(Instant.now())
                .requiresInvestigation(true)
                .source("device-registration-events-dlq")
                .deviceFingerprint(extractDeviceFingerprint(record.value()))
                .build()
        );

        // Send immediate security team alert
        deviceSecurityAlertService.sendCriticalAlert(
            DeviceSecurityAlertType.REGISTRATION_SECURITY_VIOLATION,
            e.getMessage(),
            correlationId
        );

        // Block device fingerprint temporarily
        String deviceFingerprint = extractDeviceFingerprint(record.value());
        if (deviceFingerprint != null && !deviceFingerprint.equals("unknown")) {
            deviceFingerprintBlockingService.temporaryBlock(
                deviceFingerprint,
                BlockReason.SECURITY_VIOLATION,
                Duration.ofHours(24),
                correlationId
            );
        }
    }

    private void executeDeviceSecurityIncidentProtocol(ConsumerRecord<String, String> record,
                                                       String topic, String exceptionMessage,
                                                       String correlationId) {
        try {
            // Execute comprehensive device security incident response
            DeviceSecurityIncidentResult incident = deviceSecurityIncidentService.executeProtocol(
                record.key(),
                record.value(),
                topic,
                exceptionMessage,
                correlationId
            );

            if (incident.requiresUserNotification()) {
                log.info("User notification required for device security incident: correlationId={}, userId={}",
                        correlationId, incident.getAffectedUserId());

                // Send security incident notification
                deviceSecurityNotificationService.sendSecurityIncidentNotification(
                    incident.getAffectedUserId(),
                    incident.getIncidentType(),
                    correlationId
                );
            }

            if (incident.requiresDeviceBlocking()) {
                // Block all devices with same fingerprint
                deviceService.blockDevicesByFingerprint(
                    incident.getDeviceFingerprint(),
                    BlockReason.SECURITY_INCIDENT,
                    correlationId
                );
            }
        } catch (Exception e) {
            log.error("Device security incident protocol failed: correlationId={}", correlationId, e);
        }
    }

    private void storeForSecurityAudit(ConsumerRecord<String, String> record, String topic,
                                       String exceptionMessage, String correlationId) {
        deviceSecurityAuditRepository.save(
            DeviceSecurityAuditRecord.builder()
                .sourceTopic(topic)
                .deviceId(extractDeviceId(record.value()))
                .userId(extractUserId(record.value()))
                .messageKey(record.key())
                .messageValue(record.value())
                .failureReason(exceptionMessage)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .status(AuditStatus.PENDING_INVESTIGATION)
                .securityLevel(SecurityLevel.HIGH)
                .requiresReporting(true)
                .deviceFingerprint(extractDeviceFingerprint(record.value()))
                .build()
        );
    }

    private void sendSecurityTeamAlert(ConsumerRecord<String, String> record, String topic,
                                       String exceptionMessage, String correlationId) {
        deviceSecurityTeamAlertService.sendCriticalAlert(
            AlertType.DEVICE_REGISTRATION_PERMANENT_FAILURE,
            "Device registration permanently failed - potential security issue",
            Map.of(
                "topic", topic,
                "deviceId", extractDeviceId(record.value()),
                "userId", extractUserId(record.value()),
                "deviceFingerprint", extractDeviceFingerprint(record.value()),
                "error", exceptionMessage,
                "correlationId", correlationId,
                "securityImpact", "MEDIUM",
                "requiredAction", "Security team investigation required"
            )
        );
    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        if (processedEvents.size() > 10000) {
            cleanupOldProcessedEvents();
        }
    }

    private void cleanupOldProcessedEvents() {
        long cutoffTime = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        processedEvents.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }

    private String extractDeviceId(String value) {
        try {
            return objectMapper.readTree(value).get("deviceId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractUserId(String value) {
        try {
            return objectMapper.readTree(value).get("userId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractDeviceFingerprint(String value) {
        try {
            return objectMapper.readTree(value).get("deviceFingerprint").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }
}