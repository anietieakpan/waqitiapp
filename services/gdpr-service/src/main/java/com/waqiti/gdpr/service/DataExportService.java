package com.waqiti.gdpr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.encryption.EncryptionService;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.model.alert.DataExportRecoveryResult;
import com.waqiti.gdpr.domain.DataSubjectRequest;
import com.waqiti.gdpr.domain.ExportFormat;
import com.waqiti.gdpr.domain.RequestStatus;
import com.waqiti.gdpr.domain.RequestType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Data Export Service - Production-ready GDPR data export DLQ recovery
 *
 * Implements GDPR Article 15 (Right of Access) and Article 20 (Right to Data Portability)
 * with comprehensive error handling, metrics, and compliance validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final DataSubjectRequestService dataSubjectRequestService;
    private final GDPRComplianceService gdprComplianceService;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // FeignClients for data source integration
    private final com.waqiti.gdpr.client.UserServiceClient userServiceClient;
    private final com.waqiti.gdpr.client.TransactionServiceClient transactionServiceClient;
    private final com.waqiti.gdpr.client.ActivityLogServiceClient activityLogServiceClient;
    private final com.waqiti.gdpr.client.PreferencesServiceClient preferencesServiceClient;
    private final com.waqiti.gdpr.client.ConsentServiceClient consentServiceClient;
    private final com.waqiti.gdpr.client.NotificationServiceClient notificationServiceClient;

    // Redis for encrypted package storage
    private final org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;

    @Value("${gdpr.export.download-link-expiry-days:7}")
    private int downloadLinkExpiryDays;

    @Value("${gdpr.export.max-size-bytes:1073741824}") // 1GB default
    private long maxExportSizeBytes;

    @Value("${gdpr.export.encryption-algorithm:AES-256-GCM}")
    private String encryptionAlgorithm;

    @Value("${gdpr.response-deadline-days:30}")
    private int gdprResponseDeadlineDays;

    /**
     * Process data export events DLQ with full GDPR compliance validation
     *
     * @param exportData JSON payload containing export details
     * @param eventKey Kafka event key
     * @param correlationId Correlation ID for tracing
     * @param exportId Export request ID
     * @param subjectId Data subject (user) ID
     * @param requestType GDPR request type
     * @param timestamp Event timestamp
     * @return DataExportRecoveryResult with complete export details
     */
    @Transactional
    public DataExportRecoveryResult processDataExportEventsDlq(
            String exportData,
            String eventKey,
            String correlationId,
            String exportId,
            String subjectId,
            String requestType,
            Instant timestamp) {

        Timer.Sample sample = Timer.start(meterRegistry);

        log.info("Processing data export DLQ: exportId={} subjectId={} requestType={} correlationId={}",
                exportId, subjectId, requestType, correlationId);

        try {
            // Distributed idempotency check
            String idempotencyKey = String.format("data-export-dlq:%s:%s", exportId, eventKey);
            if (idempotencyService.wasProcessed(idempotencyKey)) {
                log.debug("Data export already processed (idempotent): exportId={} correlationId={}",
                        exportId, correlationId);

                return retrieveCachedResult(exportId, correlationId);
            }

            // Parse export data
            JsonNode exportNode = objectMapper.readTree(exportData);

            // Validate GDPR compliance requirements
            validateGdprCompliance(exportNode, exportId, subjectId, correlationId);

            // Check if export request exists
            DataSubjectRequest request = dataSubjectRequestService.findOrCreateExportRequest(
                    subjectId,
                    exportId,
                    RequestType.valueOf(requestType),
                    ExportFormat.JSON,
                    correlationId
            );

            // Validate deadline compliance
            boolean deadlineBreached = isDeadlineBreached(request.getDeadline());
            if (deadlineBreached) {
                log.error("GDPR deadline breached for export: exportId={} subjectId={} deadline={} correlationId={}",
                        exportId, subjectId, request.getDeadline(), correlationId);

                recordMetric("data_export_deadline_breached_total");
            }

            // Collect user data from all sources
            Map<String, Object> collectedData = collectUserDataForExport(
                    subjectId,
                    exportNode,
                    correlationId
            );

            // Validate data completeness
            DataCompletenessResult completeness = validateDataCompleteness(collectedData, correlationId);
            if (!completeness.isComplete()) {
                log.warn("Data collection incomplete: exportId={} missing={} correlationId={}",
                        exportId, completeness.getMissingCategories(), correlationId);

                recordMetric("data_export_incomplete_total");
            }

            // Check export size limits
            long exportSizeBytes = calculateExportSize(collectedData);
            if (exportSizeBytes > maxExportSizeBytes) {
                log.error("Export size exceeds limit: exportId={} size={} maxSize={} correlationId={}",
                        exportId, exportSizeBytes, maxExportSizeBytes, correlationId);

                return buildFailureResult(exportId, subjectId, requestType,
                        "Export size exceeds maximum allowed size",
                        exportSizeBytes, deadlineBreached, correlationId);
            }

            // Encrypt export package
            String encryptedPackage = encryptionService.encryptWithUserKey(
                    collectedData,
                    subjectId,
                    encryptionAlgorithm
            );

            // Generate secure download link
            String downloadUrl = generateSecureDownloadLink(
                    subjectId,
                    encryptedPackage,
                    ExportFormat.JSON,
                    correlationId
            );

            // Update request status
            request.setStatus(RequestStatus.COMPLETED);
            request.setCompletedAt(LocalDateTime.now());
            request.setExportUrl(downloadUrl);
            request.setExportExpiresAt(LocalDateTime.now().plusDays(downloadLinkExpiryDays));
            dataSubjectRequestService.save(request, correlationId);

            // Mark as processed (idempotency)
            idempotencyService.markProcessed(idempotencyKey,
                    Duration.ofDays(gdprResponseDeadlineDays + 7));

            // Comprehensive audit trail
            auditService.logGDPRActivity(buildAuditLog(
                    subjectId,
                    "DATA_EXPORT_COMPLETED",
                    exportId,
                    requestType,
                    exportSizeBytes,
                    correlationId
            ));

            // Build success result
            DataExportRecoveryResult result = DataExportRecoveryResult.builder()
                    .recovered(true)
                    .exportCompleted(true)
                    .exportRequestId(exportId)
                    .customerId(subjectId)
                    .requestType(requestType)
                    .exportFormat("JSON")
                    .exportUrl(downloadUrl)
                    .exportSizeBytes(exportSizeBytes)
                    .recordCount(collectedData.size())
                    .exportCompletedTimestamp(Instant.now())
                    .gdprCompliant(true)
                    .encrypted(true)
                    .regulatoryDeadlineDays(gdprResponseDeadlineDays)
                    .deadlineBreached(deadlineBreached)
                    .exportStatus("COMPLETED")
                    .exportDetails(String.format("Export completed successfully - %d records, %d bytes",
                            collectedData.size(), exportSizeBytes))
                    .correlationId(correlationId)
                    .timestamp(Instant.now())
                    .build();

            // Record success metrics
            recordMetric("data_export_dlq_success_total");
            sample.stop(Timer.builder("data_export_dlq_processing_duration")
                    .tag("status", "success")
                    .register(meterRegistry));

            log.info("Data export DLQ processed successfully: exportId={} subjectId={} size={} correlationId={}",
                    exportId, subjectId, exportSizeBytes, correlationId);

            return result;

        } catch (GdprViolationException e) {
            log.error("GDPR violation in data export: exportId={} subjectId={} correlationId={}",
                    exportId, subjectId, correlationId, e);

            recordMetric("data_export_gdpr_violation_total");
            sample.stop(Timer.builder("data_export_dlq_processing_duration")
                    .tag("status", "gdpr_violation")
                    .register(meterRegistry));

            return buildGdprViolationResult(exportId, subjectId, requestType, e, correlationId);

        } catch (ManualReviewRequiredException e) {
            log.warn("Data export requires manual review: exportId={} subjectId={} reason={} correlationId={}",
                    exportId, subjectId, e.getMessage(), correlationId);

            recordMetric("data_export_manual_review_total");
            sample.stop(Timer.builder("data_export_dlq_processing_duration")
                    .tag("status", "manual_review")
                    .register(meterRegistry));

            return buildManualReviewResult(exportId, subjectId, requestType, e.getMessage(), correlationId);

        } catch (Exception e) {
            log.error("Failed to process data export DLQ: exportId={} subjectId={} correlationId={}",
                    exportId, subjectId, correlationId, e);

            recordMetric("data_export_dlq_failure_total");
            sample.stop(Timer.builder("data_export_dlq_processing_duration")
                    .tag("status", "failure")
                    .register(meterRegistry));

            return buildFailureResult(exportId, subjectId, requestType, e.getMessage(),
                    0L, false, correlationId);
        }
    }

    /**
     * Generate secure download link with expiration
     */
    public String generateSecureDownloadLink(String userId, String encryptedPackage,
                                             ExportFormat format, String correlationId) {
        log.debug("Generating secure download link: userId={} format={} correlationId={}",
                userId, format, correlationId);

        // Generate secure token
        String secureToken = UUID.randomUUID().toString();

        // Store encrypted package with expiration
        storeEncryptedPackage(secureToken, encryptedPackage,
                Duration.ofDays(downloadLinkExpiryDays), correlationId);

        // Generate download URL
        String downloadUrl = String.format("https://api.example.com/gdpr/exports/%s/download", secureToken);

        log.debug("Secure download link generated: userId={} token={} expiresInDays={} correlationId={}",
                userId, secureToken, downloadLinkExpiryDays, correlationId);

        return downloadUrl;
    }

    /**
     * Update export status
     */
    @Transactional
    public void updateExportStatus(String exportId, ExportStatus status,
                                   String details, String correlationId) {
        log.info("Updating export status: exportId={} status={} correlationId={}",
                exportId, status, correlationId);

        try {
            DataSubjectRequest request = dataSubjectRequestService.findByExportId(exportId, correlationId);

            RequestStatus requestStatus = mapToRequestStatus(status);
            request.setStatus(requestStatus);
            request.setNotes(details);

            if (status == ExportStatus.COMPLETED) {
                request.setCompletedAt(LocalDateTime.now());
            }

            dataSubjectRequestService.save(request, correlationId);

            recordMetric("data_export_status_updated_total", "status", status.toString());

            log.info("Export status updated: exportId={} status={} correlationId={}",
                    exportId, status, correlationId);

        } catch (Exception e) {
            log.error("Failed to update export status: exportId={} status={} correlationId={}",
                    exportId, status, correlationId, e);
            throw new ExportStatusUpdateException("Failed to update export status", e);
        }
    }

    /**
     * Halt export due to GDPR violation or other critical issue
     */
    @Transactional
    public void haltExport(String exportId, HaltReason reason, String correlationId) {
        log.warn("Halting export: exportId={} reason={} correlationId={}",
                exportId, reason, correlationId);

        try {
            DataSubjectRequest request = dataSubjectRequestService.findByExportId(exportId, correlationId);

            request.setStatus(RequestStatus.REJECTED);
            request.setRejectionReason(String.format("Export halted: %s", reason));
            request.setCompletedAt(LocalDateTime.now());

            dataSubjectRequestService.save(request, correlationId);

            // Audit the halt
            auditService.logGDPRActivity(buildAuditLog(
                    request.getUserId(),
                    "DATA_EXPORT_HALTED",
                    exportId,
                    reason.toString(),
                    0L,
                    correlationId
            ));

            recordMetric("data_export_halted_total", "reason", reason.toString());

            log.warn("Export halted: exportId={} reason={} correlationId={}",
                    exportId, reason, correlationId);

        } catch (Exception e) {
            log.error("Failed to halt export: exportId={} reason={} correlationId={}",
                    exportId, reason, correlationId, e);
            throw new ExportHaltException("Failed to halt export", e);
        }
    }

    /**
     * Generate portable data link (GDPR Article 20)
     */
    public String generatePortableDataLink(String userId, PortableDataFormat portableData) {
        log.info("Generating portable data link: userId={}", userId);

        String secureToken = UUID.randomUUID().toString();

        // Store portable data with 30-day expiration (GDPR Article 20 requirement)
        storePortableData(secureToken, portableData, Duration.ofDays(30));

        return String.format("https://api.example.com/gdpr/portability/%s/download", secureToken);
    }

    // Helper methods

    private void validateGdprCompliance(JsonNode exportNode, String exportId,
                                       String subjectId, String correlationId) {
        log.debug("Validating GDPR compliance: exportId={} correlationId={}", exportId, correlationId);

        // Validate subject consent
        if (!exportNode.has("subjectConsent") || !exportNode.get("subjectConsent").asBoolean()) {
            throw new GdprViolationException("Missing or invalid subject consent");
        }

        // Validate legal basis
        if (!exportNode.has("legalBasis")) {
            throw new GdprViolationException("Missing legal basis for data processing");
        }

        // Validate data scope (data minimization principle)
        if (!exportNode.has("dataScope") || exportNode.get("dataScope").isEmpty()) {
            throw new GdprViolationException("Missing data scope - violates data minimization principle");
        }

        // Validate purpose limitation
        if (!exportNode.has("processingPurpose")) {
            throw new GdprViolationException("Missing processing purpose - violates purpose limitation principle");
        }

        log.debug("GDPR compliance validated: exportId={} correlationId={}", exportId, correlationId);
    }

    private Map<String, Object> collectUserDataForExport(String subjectId, JsonNode exportNode,
                                                          String correlationId) {
        log.debug("Collecting user data for export: subjectId={} correlationId={}",
                subjectId, correlationId);

        Map<String, Object> collectedData = new HashMap<>();

        try {
            // Collect personal data
            collectedData.put("personal", getUserPersonalData(subjectId));

            // Collect transaction data
            collectedData.put("transactions", getUserTransactions(subjectId));

            // Collect activity logs
            collectedData.put("activity", getUserActivityLogs(subjectId));

            // Collect preferences
            collectedData.put("preferences", getUserPreferences(subjectId));

            // Collect consent records
            collectedData.put("consents", getUserConsents(subjectId));

            // Collect communication history
            collectedData.put("communications", getUserCommunications(subjectId));

            // Collect metadata
            collectedData.put("metadata", buildExportMetadata(subjectId, correlationId));

            log.debug("User data collection complete: subjectId={} categories={} correlationId={}",
                    subjectId, collectedData.keySet().size(), correlationId);

            return collectedData;

        } catch (Exception e) {
            log.error("Failed to collect user data: subjectId={} correlationId={}",
                    subjectId, correlationId, e);
            throw new DataCollectionException("Failed to collect user data for export", e);
        }
    }

    private DataCompletenessResult validateDataCompleteness(Map<String, Object> data,
                                                            String correlationId) {
        log.debug("Validating data completeness: categories={} correlationId={}",
                data.keySet().size(), correlationId);

        List<String> expectedCategories = Arrays.asList(
                "personal", "transactions", "activity", "preferences", "consents", "communications"
        );

        List<String> missingCategories = new ArrayList<>();
        for (String category : expectedCategories) {
            if (!data.containsKey(category) || data.get(category) == null) {
                missingCategories.add(category);
            }
        }

        boolean complete = missingCategories.isEmpty();

        if (!complete) {
            log.warn("Data completeness check failed: missing={} correlationId={}",
                    missingCategories, correlationId);
        }

        return new DataCompletenessResult(complete, missingCategories);
    }

    private long calculateExportSize(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return json.getBytes().length;
        } catch (Exception e) {
            log.warn("Failed to calculate exact export size, using estimate", e);
            return data.toString().length();
        }
    }

    private boolean isDeadlineBreached(LocalDateTime deadline) {
        return LocalDateTime.now().isAfter(deadline);
    }

    private DataExportRecoveryResult retrieveCachedResult(String exportId, String correlationId) {
        // Return cached result for idempotent requests
        return DataExportRecoveryResult.builder()
                .recovered(true)
                .exportCompleted(true)
                .exportRequestId(exportId)
                .exportStatus("COMPLETED_CACHED")
                .exportDetails("Export already processed (idempotent)")
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
    }

    private DataExportRecoveryResult buildGdprViolationResult(String exportId, String subjectId,
                                                              String requestType,
                                                              GdprViolationException e,
                                                              String correlationId) {
        return DataExportRecoveryResult.builder()
                .recovered(false)
                .exportCompleted(false)
                .exportRequestId(exportId)
                .customerId(subjectId)
                .requestType(requestType)
                .gdprCompliant(false)
                .exportStatus("GDPR_VIOLATION")
                .exportDetails(String.format("GDPR violation: %s", e.getMessage()))
                .violationType(e.getViolationType())
                .requiresLegalReview(true)
                .requiresBreachNotification(e.requiresBreachNotification())
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
    }

    private DataExportRecoveryResult buildManualReviewResult(String exportId, String subjectId,
                                                             String requestType, String reason,
                                                             String correlationId) {
        return DataExportRecoveryResult.builder()
                .recovered(false)
                .exportCompleted(false)
                .exportRequestId(exportId)
                .customerId(subjectId)
                .requestType(requestType)
                .exportStatus("PENDING_MANUAL_REVIEW")
                .exportDetails(String.format("Manual review required: %s", reason))
                .reviewReason(reason)
                .requiresManualReview(true)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
    }

    private DataExportRecoveryResult buildFailureResult(String exportId, String subjectId,
                                                        String requestType, String errorMessage,
                                                        long exportSize, boolean deadlineBreached,
                                                        String correlationId) {
        return DataExportRecoveryResult.builder()
                .recovered(false)
                .exportCompleted(false)
                .exportRequestId(exportId)
                .customerId(subjectId)
                .requestType(requestType)
                .exportSizeBytes(exportSize)
                .deadlineBreached(deadlineBreached)
                .exportStatus("FAILED")
                .exportDetails(String.format("Export failed: %s", errorMessage))
                .errorMessage(errorMessage)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
    }

    private Map<String, Object> buildAuditLog(String userId, String activity, String exportId,
                                             String requestType, long exportSize, String correlationId) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("userId", userId);
        auditData.put("activity", activity);
        auditData.put("exportId", exportId);
        auditData.put("requestType", requestType);
        auditData.put("exportSize", exportSize);
        auditData.put("correlationId", correlationId);
        auditData.put("timestamp", Instant.now().toString());
        return auditData;
    }

    private RequestStatus mapToRequestStatus(ExportStatus exportStatus) {
        switch (exportStatus) {
            case COMPLETED:
                return RequestStatus.COMPLETED;
            case PENDING_MANUAL_REVIEW:
                return RequestStatus.IN_PROGRESS;
            case FAILED:
                return RequestStatus.REJECTED;
            default:
                return RequestStatus.IN_PROGRESS;
        }
    }

    private Map<String, Object> buildExportMetadata(String subjectId, String correlationId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subjectId", subjectId);
        metadata.put("exportTimestamp", Instant.now().toString());
        metadata.put("correlationId", correlationId);
        metadata.put("gdprArticles", Arrays.asList("Article 15", "Article 20"));
        metadata.put("dataController", "Waqiti Financial Services");
        metadata.put("exportVersion", "1.0");
        return metadata;
    }

    private void recordMetric(String metricName) {
        Counter.builder(metricName)
                .register(meterRegistry)
                .increment();
    }

    private void recordMetric(String metricName, String tagKey, String tagValue) {
        Counter.builder(metricName)
                .tag(tagKey, tagValue)
                .register(meterRegistry)
                .increment();
    }

    /**
     * PRODUCTION IMPLEMENTATION: Get user personal data from User Service
     * Replaces placeholder with actual FeignClient integration
     */
    private Map<String, Object> getUserPersonalData(String subjectId) {
        try {
            String correlationId = UUID.randomUUID().toString();
            com.waqiti.gdpr.dto.UserPersonalDataDTO personalData =
                userServiceClient.getUserPersonalData(subjectId, correlationId);

            if (personalData.isDataRetrievalFailed()) {
                log.warn("Failed to retrieve personal data: userId={} reason={}",
                    subjectId, personalData.getFailureReason());
            }

            // Convert DTO to Map for export package
            Map<String, Object> data = new HashMap<>();
            data.put("userId", personalData.getUserId());
            data.put("email", personalData.getEmail());
            data.put("phoneNumber", personalData.getPhoneNumber());
            data.put("firstName", personalData.getFirstName());
            data.put("lastName", personalData.getLastName());
            data.put("dateOfBirth", personalData.getDateOfBirth());
            data.put("nationality", personalData.getNationality());
            data.put("primaryAddress", personalData.getPrimaryAddress());
            data.put("kycData", personalData.getKycData());
            data.put("accountCreatedAt", personalData.getAccountCreatedAt());
            data.put("accountStatus", personalData.getAccountStatus());
            data.put("financialSummary", personalData.getFinancialSummary());
            data.put("retrievedAt", LocalDateTime.now());

            return data;

        } catch (Exception e) {
            log.error("Error retrieving personal data for user: {}", subjectId, e);
            return Map.of(
                "userId", subjectId,
                "dataType", "personal",
                "error", "Failed to retrieve data: " + e.getMessage(),
                "requiresManualReview", true
            );
        }
    }

    /**
     * PRODUCTION IMPLEMENTATION: Get user transactions from Transaction Service
     */
    private Map<String, Object> getUserTransactions(String subjectId) {
        try {
            String correlationId = UUID.randomUUID().toString();
            com.waqiti.gdpr.dto.UserTransactionsDataDTO transactionsData =
                transactionServiceClient.getUserTransactions(subjectId, null, null, correlationId);

            if (transactionsData.isDataRetrievalFailed()) {
                log.warn("Failed to retrieve transaction data: userId={} reason={}",
                    subjectId, transactionsData.getFailureReason());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("userId", transactionsData.getUserId());
            data.put("transactions", transactionsData.getTransactions());
            data.put("payments", transactionsData.getPayments());
            data.put("transfers", transactionsData.getTransfers());
            data.put("summary", transactionsData.getSummary());
            data.put("retrievedAt", LocalDateTime.now());

            return data;

        } catch (Exception e) {
            log.error("Error retrieving transactions for user: {}", subjectId, e);
            return Map.of(
                "userId", subjectId,
                "dataType", "transactions",
                "error", "Failed to retrieve data: " + e.getMessage(),
                "requiresManualReview", true
            );
        }
    }

    /**
     * PRODUCTION IMPLEMENTATION: Get user activity logs from Audit Service
     */
    private Map<String, Object> getUserActivityLogs(String subjectId) {
        try {
            String correlationId = UUID.randomUUID().toString();
            com.waqiti.gdpr.dto.UserActivityLogsDataDTO activityData =
                activityLogServiceClient.getUserActivityLogs(subjectId, correlationId);

            if (activityData.isDataRetrievalFailed()) {
                log.warn("Failed to retrieve activity logs: userId={} reason={}",
                    subjectId, activityData.getFailureReason());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("userId", activityData.getUserId());
            data.put("activityLogs", activityData.getActivityLogs());
            data.put("loginHistory", activityData.getLoginHistory());
            data.put("auditEvents", activityData.getAuditEvents());
            data.put("summary", activityData.getSummary());
            data.put("retrievedAt", LocalDateTime.now());

            return data;

        } catch (Exception e) {
            log.error("Error retrieving activity logs for user: {}", subjectId, e);
            return Map.of(
                "userId", subjectId,
                "dataType", "activity",
                "error", "Failed to retrieve data: " + e.getMessage(),
                "requiresManualReview", true
            );
        }
    }

    /**
     * PRODUCTION IMPLEMENTATION: Get user preferences from User Service
     */
    private Map<String, Object> getUserPreferences(String subjectId) {
        try {
            String correlationId = UUID.randomUUID().toString();
            com.waqiti.gdpr.dto.UserPreferencesDataDTO preferencesData =
                preferencesServiceClient.getUserPreferences(subjectId, correlationId);

            if (preferencesData.isDataRetrievalFailed()) {
                log.warn("Failed to retrieve preferences: userId={} reason={}",
                    subjectId, preferencesData.getFailureReason());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("userId", preferencesData.getUserId());
            data.put("notificationPreferences", preferencesData.getNotificationPreferences());
            data.put("privacyPreferences", preferencesData.getPrivacyPreferences());
            data.put("appPreferences", preferencesData.getAppPreferences());
            data.put("customPreferences", preferencesData.getCustomPreferences());
            data.put("retrievedAt", LocalDateTime.now());

            return data;

        } catch (Exception e) {
            log.error("Error retrieving preferences for user: {}", subjectId, e);
            return Map.of(
                "userId", subjectId,
                "dataType", "preferences",
                "error", "Failed to retrieve data: " + e.getMessage(),
                "requiresManualReview", true
            );
        }
    }

    /**
     * PRODUCTION IMPLEMENTATION: Get user consents from GDPR Service internal
     */
    private Map<String, Object> getUserConsents(String subjectId) {
        try {
            String correlationId = UUID.randomUUID().toString();
            com.waqiti.gdpr.dto.UserConsentsDataDTO consentsData =
                consentServiceClient.getUserConsents(subjectId, correlationId);

            if (consentsData.isDataRetrievalFailed()) {
                log.warn("Failed to retrieve consents: userId={} reason={}",
                    subjectId, consentsData.getFailureReason());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("userId", consentsData.getUserId());
            data.put("consentRecords", consentsData.getConsentRecords());
            data.put("consentHistory", consentsData.getConsentHistory());
            data.put("summary", consentsData.getSummary());
            data.put("retrievedAt", LocalDateTime.now());

            return data;

        } catch (Exception e) {
            log.error("Error retrieving consents for user: {}", subjectId, e);
            return Map.of(
                "userId", subjectId,
                "dataType", "consents",
                "error", "Failed to retrieve data: " + e.getMessage(),
                "requiresManualReview", true
            );
        }
    }

    /**
     * PRODUCTION IMPLEMENTATION: Get user communications from Notification Service
     */
    private Map<String, Object> getUserCommunications(String subjectId) {
        try {
            String correlationId = UUID.randomUUID().toString();
            com.waqiti.gdpr.dto.UserCommunicationsDataDTO communicationsData =
                notificationServiceClient.getUserCommunications(subjectId, correlationId);

            if (communicationsData.isDataRetrievalFailed()) {
                log.warn("Failed to retrieve communications: userId={} reason={}",
                    subjectId, communicationsData.getFailureReason());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("userId", communicationsData.getUserId());
            data.put("emails", communicationsData.getEmails());
            data.put("smsMessages", communicationsData.getSmsMessages());
            data.put("pushNotifications", communicationsData.getPushNotifications());
            data.put("summary", communicationsData.getSummary());
            data.put("retrievedAt", LocalDateTime.now());

            return data;

        } catch (Exception e) {
            log.error("Error retrieving communications for user: {}", subjectId, e);
            return Map.of(
                "userId", subjectId,
                "dataType", "communications",
                "error", "Failed to retrieve data: " + e.getMessage(),
                "requiresManualReview", true
            );
        }
    }

    /**
     * PRODUCTION IMPLEMENTATION: Store encrypted package in Redis with TTL
     * Implements secure storage for GDPR data export downloads
     */
    private void storeEncryptedPackage(String token, String encryptedPackage,
                                      Duration expiration, String correlationId) {
        try {
            String redisKey = String.format("gdpr:export:encrypted:%s", token);

            // Store in Redis with automatic expiration
            redisTemplate.opsForValue().set(redisKey, encryptedPackage, expiration);

            log.info("Stored encrypted GDPR package: token={} expiresIn={} size={} correlationId={}",
                token, expiration, encryptedPackage.length(), correlationId);

            // Store metadata separately for tracking
            String metadataKey = String.format("gdpr:export:metadata:%s", token);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("token", token);
            metadata.put("storedAt", LocalDateTime.now().toString());
            metadata.put("expiresAt", LocalDateTime.now().plus(expiration).toString());
            metadata.put("correlationId", correlationId);
            metadata.put("size", String.valueOf(encryptedPackage.length()));

            redisTemplate.opsForHash().putAll(metadataKey, metadata);
            redisTemplate.expire(metadataKey, expiration);

            log.debug("Stored export metadata: token={} correlationId={}", token, correlationId);

        } catch (Exception e) {
            log.error("Failed to store encrypted package in Redis: token={} correlationId={}",
                token, correlationId, e);
            throw new ExportStorageException("Failed to store encrypted export package", e);
        }
    }

    /**
     * PRODUCTION IMPLEMENTATION: Store portable data in Redis with TTL
     * Implements GDPR Article 20 (Data Portability) storage
     */
    private void storePortableData(String token, PortableDataFormat data, Duration expiration) {
        try {
            String redisKey = String.format("gdpr:portability:%s", token);

            // Convert portable data to JSON string
            String jsonData = objectMapper.writeValueAsString(data.getData());

            // Store in Redis with 30-day expiration (GDPR Article 20 requirement)
            redisTemplate.opsForValue().set(redisKey, jsonData, expiration);

            log.info("Stored portable data package: token={} expiresIn={} size={}",
                token, expiration, jsonData.length());

            // Store metadata
            String metadataKey = String.format("gdpr:portability:metadata:%s", token);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("token", token);
            metadata.put("storedAt", LocalDateTime.now().toString());
            metadata.put("expiresAt", LocalDateTime.now().plus(expiration).toString());
            metadata.put("size", String.valueOf(jsonData.length()));
            metadata.put("format", "JSON");

            redisTemplate.opsForHash().putAll(metadataKey, metadata);
            redisTemplate.expire(metadataKey, expiration);

        } catch (Exception e) {
            log.error("Failed to store portable data in Redis: token={}", token, e);
            throw new ExportStorageException("Failed to store portable data package", e);
        }
    }

    /**
     * Retrieve encrypted package from Redis
     */
    public String retrieveEncryptedPackage(String token) {
        try {
            String redisKey = String.format("gdpr:export:encrypted:%s", token);
            String encryptedPackage = redisTemplate.opsForValue().get(redisKey);

            if (encryptedPackage == null) {
                log.warn("Encrypted package not found or expired: token={}", token);
                throw new ExportNotFoundException("Export package not found or has expired");
            }

            log.info("Retrieved encrypted GDPR package: token={} size={}", token, encryptedPackage.length());
            return encryptedPackage;

        } catch (ExportNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve encrypted package from Redis: token={}", token, e);
            throw new ExportStorageException("Failed to retrieve encrypted export package", e);
        }
    }

    /**
     * Retrieve portable data from Redis
     */
    public Map<String, Object> retrievePortableData(String token) {
        try {
            String redisKey = String.format("gdpr:portability:%s", token);
            String jsonData = redisTemplate.opsForValue().get(redisKey);

            if (jsonData == null) {
                log.warn("Portable data not found or expired: token={}", token);
                throw new ExportNotFoundException("Portable data package not found or has expired");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonData, Map.class);

            log.info("Retrieved portable data package: token={} records={}", token, data.size());
            return data;

        } catch (ExportNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve portable data from Redis: token={}", token, e);
            throw new ExportStorageException("Failed to retrieve portable data package", e);
        }
    }

    // Inner classes and enums

    public enum ExportStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        PENDING_MANUAL_REVIEW,
        FAILED,
        CANCELLED
    }

    public enum HaltReason {
        GDPR_VIOLATION,
        SECURITY_CONCERN,
        DATA_INTEGRITY_ISSUE,
        TECHNICAL_FAILURE,
        LEGAL_HOLD
    }

    private static class DataCompletenessResult {
        private final boolean complete;
        private final List<String> missingCategories;

        public DataCompletenessResult(boolean complete, List<String> missingCategories) {
            this.complete = complete;
            this.missingCategories = missingCategories;
        }

        public boolean isComplete() {
            return complete;
        }

        public List<String> getMissingCategories() {
            return missingCategories;
        }
    }

    // Custom exceptions

    public static class GdprViolationException extends RuntimeException {
        private final String violationType;
        private final boolean breachNotification;

        public GdprViolationException(String message) {
            this(message, "GDPR_COMPLIANCE_VIOLATION", false);
        }

        public GdprViolationException(String message, String violationType, boolean breachNotification) {
            super(message);
            this.violationType = violationType;
            this.breachNotification = breachNotification;
        }

        public String getViolationType() {
            return violationType;
        }

        public boolean requiresBreachNotification() {
            return breachNotification;
        }
    }

    public static class ManualReviewRequiredException extends RuntimeException {
        public ManualReviewRequiredException(String message) {
            super(message);
        }
    }

    public static class DataCollectionException extends RuntimeException {
        public DataCollectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ExportStatusUpdateException extends RuntimeException {
        public ExportStatusUpdateException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ExportHaltException extends RuntimeException {
        public ExportHaltException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Placeholder class for portable data format
    public static class PortableDataFormat {
        private final Map<String, Object> data;

        public PortableDataFormat(Map<String, Object> data) {
            this.data = data;
        }

        public Map<String, Object> getData() {
            return data;
        }
    }
}
