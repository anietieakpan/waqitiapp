package com.waqiti.common.gdpr;

import com.waqiti.common.gdpr.enums.*;
import com.waqiti.common.gdpr.model.*;
import com.waqiti.common.gdpr.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * GDPR Data Privacy Service - DEPRECATED
 *
 * ⚠️ WARNING: This shared GDPR module is DEPRECATED and will be removed in future versions.
 *
 * MIGRATION PATH:
 * - DO NOT use this service for new code
 * - Use the dedicated GDPR Service microservice instead: services/gdpr-service
 * - Access GDPR functionality via REST API: /api/v1/gdpr/*
 * - For integration, use Feign clients to call GDPR Service
 *
 * WHY DEPRECATED:
 * - Zero services currently use this module
 * - Duplicate functionality with gdpr-service
 * - Incomplete implementations (24 TODOs)
 * - GDPR Service is production-ready and comprehensive
 *
 * GDPR Service provides:
 * - Complete REST API for all GDPR operations
 * - Full GDPR Article implementations (15, 16, 17, 18, 20, 21, 22, 33, 34)
 * - Data collection orchestration via Feign clients
 * - Kafka event processing
 * - Redis-backed export storage
 * - Security (Keycloak), metrics, monitoring
 *
 * @deprecated Use {@link com.waqiti.gdpr.service.GDPRComplianceService} instead
 * @see com.waqiti.gdpr.service.GDPRComplianceService
 * @see com.waqiti.gdpr.controller.GDPRController
 * @author Waqiti Platform Team
 * @version 1.0 - DEPRECATED
 * @since 2025-10-20
 */
@Deprecated(since = "1.0-SNAPSHOT", forRemoval = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class GDPRDataPrivacyService {

    private final GDPRDataRepositoryRegistry repositoryRegistry;
    private final GDPRConsentRepository consentRepository;
    private final GDPRDataExportRepository exportRepository;
    private final GDPRDataDeletionRepository deletionRepository;
    private final GDPRAuditLogRepository auditLogRepository;
    private final GDPRNotificationService notificationService;

    /**
     * Article 15: Right to Access
     * Data subject can request a copy of all their personal data
     */
    @Transactional(readOnly = true)
    public CompletableFuture<GDPRDataExport> exportUserData(UUID userId, String requestReason) {
        log.info("Processing GDPR data export request for user: {}", userId);

        // Create export request record
        GDPRDataExport exportRequest = GDPRDataExport.builder()
                .userId(userId)
                .requestedAt(LocalDateTime.now())
                .status(GDPRDataExport.ExportStatus.PENDING)
                .format(GDPRDataExport.ExportFormat.JSON)
                .build();
        exportRepository.save(exportRequest);

        // Audit log - Using enum from enums package
        auditLog(userId, GDPRAction.DATA_EXPORT_REQUESTED,
                "User requested data export: " + requestReason);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> userData = new HashMap<>();

                // Collect data from all registered repositories
                for (GDPRDataRepository repository : repositoryRegistry.getAllRepositories()) {
                    String dataCategory = repository.getDataCategory();
                    Object categoryData = repository.getUserData(userId);

                    if (categoryData != null) {
                        userData.put(dataCategory, categoryData);
                        log.debug("Collected {} data for user {}", dataCategory, userId);
                    }
                }

                // Build comprehensive export
                exportRequest.setStatus(GDPRDataExport.ExportStatus.COMPLETED);
                exportRequest.setCompletedAt(LocalDateTime.now());
                exportRequest.setDownloadUrl(generateSecureDownloadUrl(exportRequest));
                exportRepository.save(exportRequest);

                // Notify user
                notificationService.notifyDataExportReady(userId, exportRequest.getDownloadUrl());

                auditLog(userId, GDPRAction.DATA_EXPORT_COMPLETED,
                        "Data export completed successfully");

                log.info("GDPR data export completed for user: {}", userId);
                return exportRequest;

            } catch (Exception e) {
                log.error("Failed to export user data for user: {}", userId, e);
                exportRequest.setStatus(GDPRDataExport.ExportStatus.FAILED);
                exportRequest.setErrorMessage(e.getMessage());
                exportRepository.save(exportRequest);

                auditLog(userId, GDPRAction.DATA_EXPORT_FAILED,
                        "Data export failed: " + e.getMessage());

                throw new RuntimeException("Failed to export user data", e);
            }
        });
    }

    /**
     * Article 17: Right to Erasure (Right to be Forgotten)
     */
    @Transactional
    public GDPRDataDeletionResult deleteUserData(
            UUID userId,
            String deletionReason,
            boolean hardDelete) {

        log.warn("Processing GDPR data deletion request for user: {}", userId);

        // Create deletion request record
        GDPRDataDeletionResult deletionRequest = GDPRDataDeletionResult.builder()
                .userId(userId)
                .requestedAt(LocalDateTime.now())
                .deletionReason(deletionReason)
                .status(GDPRDataDeletionResult.DeletionStatus.PENDING_APPROVAL)
                .build();
        deletionRepository.save(deletionRequest);

        auditLog(userId, GDPRAction.DATA_DELETION_REQUESTED,
                "User requested data deletion: " + deletionReason);

        try {
            Map<String, String> deletionResults = new HashMap<>();
            List<String> retainedData = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            int totalDeleted = 0;
            int totalAnonymized = 0;
            int totalRetained = 0;

            // Delete data from all registered repositories
            for (GDPRDataRepository repository : repositoryRegistry.getAllRepositories()) {
                String dataCategory = repository.getDataCategory();

                try {
                    // Check if data must be retained for legal reasons
                    if (repository.mustRetainForLegalReasons(userId)) {
                        log.info("Retaining {} data for user {} due to legal requirements",
                                dataCategory, userId);
                        retainedData.add(dataCategory + " (legal retention)");
                        totalRetained++;
                        continue;
                    }

                    // Perform deletion (soft or hard)
                    boolean deleted;
                    if (hardDelete) {
                        deleted = repository.hardDeleteUserData(userId);
                        if (deleted) totalDeleted++;
                    } else {
                        deleted = repository.softDeleteUserData(userId);
                        if (deleted) totalAnonymized++;
                    }

                    if (deleted) {
                        log.info("Deleted {} data for user {}", dataCategory, userId);
                    }

                } catch (Exception e) {
                    log.error("Failed to delete {} data for user {}", dataCategory, userId, e);
                    errors.add(dataCategory + ": " + e.getMessage());
                }
            }

            // Update deletion result
            deletionRequest.markCompleted(totalDeleted, totalAnonymized, totalRetained);
            deletionRepository.save(deletionRequest);

            // Notify user
            notificationService.notifyDataDeletionCompleted(userId, deletionRequest);

            auditLog(userId, GDPRAction.DATA_DELETION_COMPLETED,
                    String.format("Data deletion completed: %d deleted, %d anonymized, %d retained",
                            totalDeleted, totalAnonymized, totalRetained));

            log.info("GDPR data deletion completed for user: {}", userId);
            return deletionRequest;

        } catch (Exception e) {
            log.error("Failed to delete user data for user: {}", userId, e);
            deletionRequest.setStatus(GDPRDataDeletionResult.DeletionStatus.FAILED);
            deletionRequest.setErrorMessage(e.getMessage());
            deletionRepository.save(deletionRequest);

            auditLog(userId, GDPRAction.DATA_DELETION_FAILED,
                    "Data deletion failed: " + e.getMessage());

            throw new RuntimeException("Failed to delete user data", e);
        }
    }

    /**
     * Article 16: Right to Rectification
     */
    @Transactional
    public void rectifyUserData(UUID userId, String dataCategory, Map<String, Object> corrections) {
        log.info("Processing data rectification for user: {}, category: {}", userId, dataCategory);

        GDPRDataRepository repository = repositoryRegistry.getRepository(dataCategory);
        if (repository == null) {
            throw new IllegalArgumentException("Unknown data category: " + dataCategory);
        }

        repository.rectifyUserData(userId, corrections);

        auditLog(userId, GDPRAction.DATA_RECTIFIED,
                String.format("Data rectified in category: %s, fields: %s",
                        dataCategory, corrections.keySet()));

        log.info("Data rectification completed for user: {}", userId);
    }

    /**
     * Article 18: Right to Restriction of Processing
     */
    @Transactional
    public void restrictProcessing(UUID userId, String reason) {
        log.info("Restricting data processing for user: {}", userId);

        for (GDPRDataRepository repository : repositoryRegistry.getAllRepositories()) {
            repository.restrictProcessing(userId);
        }

        auditLog(userId, GDPRAction.PROCESSING_RESTRICTED,
                "Processing restricted: " + reason);

        notificationService.notifyProcessingRestricted(userId);
    }

    /**
     * Article 20: Right to Data Portability
     * FIXED: Use enum from enums package explicitly
     */
    @Transactional(readOnly = true)
    public byte[] exportPortableData(UUID userId, com.waqiti.common.gdpr.enums.ExportFormat format) {
        log.info("Exporting portable data for user: {} in format: {}", userId, format);

        CompletableFuture<GDPRDataExport> exportFuture =
                exportUserData(userId, "Data portability request");

        GDPRDataExport export = exportFuture.join();

        byte[] portableData;
        switch (format) {
            case JSON:
                portableData = convertToJson(export);
                break;
            case XML:
                portableData = convertToXml(export);
                break;
            case CSV:
                portableData = convertToCsv(export);
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }

        auditLog(userId, GDPRAction.DATA_PORTABILITY_EXPORT,
                "Portable data exported in format: " + format);

        return portableData;
    }

    /**
     * Consent Management (Article 7)
     * FIXED: Use enum from ConsentRecord inner class
     */
    @Transactional
    public void recordConsent(UUID userId, ConsentRecord.ConsentType consentType, boolean granted) {
        log.info("Recording consent for user: {}, type: {}, granted: {}",
                userId, consentType, granted);

        ConsentRecord consent = ConsentRecord.builder()
                .userId(userId)
                .consentType(consentType)
                .isGranted(granted)
                .grantedAt(granted ? LocalDateTime.now() : null)
                .revokedAt(!granted ? LocalDateTime.now() : null)
                .build();

        consentRepository.save(consent);

        auditLog(userId, granted ? GDPRAction.CONSENT_GRANTED : GDPRAction.CONSENT_REVOKED,
                String.format("Consent %s for %s", granted ? "granted" : "revoked", consentType));

        // If consent revoked, stop related processing
        if (!granted) {
            stopProcessingForConsentType(userId, consentType);
        }
    }

    /**
     * Article 33/34: Breach Notification
     */
    @Transactional
    public void notifyDataBreach(DataBreachNotification breach) {
        log.error("Data breach notification: {}", breach);

        // Notify supervisory authority (if high risk)
        if (isHighRisk(breach.getSeverity())) {
            notificationService.notifySupervisoryAuthority(breach);
        }

        // Notify affected users (if high risk to rights/freedoms)
        if (requiresUserNotification(breach.getSeverity())) {
            for (UUID userId : breach.getAffectedUserIds()) {
                notificationService.notifyUserOfBreach(userId, breach);
                auditLog(userId, GDPRAction.BREACH_NOTIFICATION_SENT,
                        "User notified of data breach");
            }
        }

        log.warn("Data breach processing completed. Affected users: {}",
                breach.getAffectedUserIds().size());
    }

    /**
     * Data Protection Impact Assessment (DPIA)
     * Article 35: Required for high-risk processing
     * FIXED: Use enum from enums package
     */
    public GDPRDataProtectionImpactAssessment conductDPIA(
            String processingActivity,
            String purpose,
            List<String> dataCategories) {

        log.info("Conducting DPIA for: {}", processingActivity);

        GDPRDataProtectionImpactAssessment dpia = GDPRDataProtectionImpactAssessment.builder()
                .processingActivity(processingActivity)
                .purpose(purpose)
                .dataCategories(dataCategories)
                .assessmentDate(LocalDateTime.now())
                .riskLevel(calculateRiskLevel(dataCategories))
                .mitigationMeasures(identifyMitigationMeasures(dataCategories))
                .requiresDPO(isDPORequired())
                .build();

        log.info("DPIA completed with risk level: {}", dpia.getRiskLevel());
        return dpia;
    }

    /**
     * Get consent history for user
     */
    private List<ConsentRecord> getConsentHistory(UUID userId) {
        return consentRepository.findByUserId(userId).stream()
                .map(consent -> ConsentRecord.builder()
                        .consentType(consent.getConsentType())
                        .isGranted(consent.getIsGranted())
                        .grantedAt(consent.getGrantedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get processing activities for user
     */
    private List<ProcessingActivity> getProcessingActivities(UUID userId) {
        return List.of(
                ProcessingActivity.builder()
                        .activityName("Payment Processing")
                        .legalBasis("Contract performance")
                        .purpose("Process financial transactions")
                        .build(),
                ProcessingActivity.builder()
                        .activityName("Fraud Detection")
                        .legalBasis("Legitimate interest")
                        .purpose("Protect against fraud and financial crime")
                        .build()
        );
    }

    /**
     * Get data retention information
     */
    private Map<String, RetentionInfo> getRetentionInformation(UUID userId) {
        Map<String, RetentionInfo> retentionInfo = new HashMap<>();

        for (GDPRDataRepository repository : repositoryRegistry.getAllRepositories()) {
            retentionInfo.put(
                    repository.getDataCategory(),
                    RetentionInfo.builder()
                            .retentionPeriod(repository.getRetentionPeriod().toString())
                            .legalBasis(repository.getRetentionLegalBasis())
                            .build()
            );
        }

        return retentionInfo;
    }

    /**
     * Get third-party data sharing information
     */
    private List<ThirdPartySharing> getThirdPartySharing(UUID userId) {
        return List.of(
                ThirdPartySharing.builder()
                        .thirdPartyName("Payment Processor (Stripe)")
                        .purpose("Process payments")
                        .sharedDataCategories(List.of("Payment card data", "Transaction details"))
                        .build()
        );
    }

    /**
     * Build data subject information
     */
    private DataSubjectInfo buildDataSubjectInfo(UUID userId) {
        return DataSubjectInfo.builder()
                .userId(userId)
                .build();
    }

    /**
     * Generate secure download URL for export
     */
    private String generateSecureDownloadUrl(GDPRDataExport export) {
        String token = UUID.randomUUID().toString();
        return String.format("https://api.example.com/gdpr/exports/%s?token=%s&expires=%d",
                export.getUserId(), token, System.currentTimeMillis() + 86400000);
    }

    /**
     * Convert export to JSON format
     */
    private byte[] convertToJson(GDPRDataExport export) {
        // TODO: Implement JSON conversion using Jackson ObjectMapper
        return new byte[0];
    }

    /**
     * Convert export to XML format
     */
    private byte[] convertToXml(GDPRDataExport export) {
        // TODO: Implement XML conversion using JAXB
        return new byte[0];
    }

    /**
     * Convert export to CSV format
     */
    private byte[] convertToCsv(GDPRDataExport export) {
        // TODO: Implement CSV conversion
        return new byte[0];
    }

    /**
     * Stop processing for specific consent type
     */
    private void stopProcessingForConsentType(UUID userId, ConsentRecord.ConsentType consentType) {
        log.info("Stopping processing for user: {}, consent type: {}", userId, consentType);

        switch (consentType) {
            case MARKETING:
                // Unsubscribe from marketing communications
                break;
            case ANALYTICS:
                // Stop analytics tracking
                break;
            case THIRD_PARTY_SHARING:
                // Stop sharing with third parties
                break;
            default:
                break;
        }
    }

    /**
     * Get current request IP address
     */
    private String getCurrentIpAddress() {
        // TODO: Extract from RequestContextHolder
        return "0.0.0.0";
    }

    /**
     * Get current request user agent
     */
    private String getCurrentUserAgent() {
        // TODO: Extract from RequestContextHolder
        return "Unknown";
    }

    /**
     * Calculate risk level for DPIA
     * FIXED: Use enum from enums package
     */
    private com.waqiti.common.gdpr.enums.RiskLevel calculateRiskLevel(List<String> dataCategories) {
        boolean hasSensitiveData = dataCategories.stream()
                .anyMatch(cat -> cat.toLowerCase().contains("health") ||
                        cat.toLowerCase().contains("biometric") ||
                        cat.toLowerCase().contains("political") ||
                        cat.toLowerCase().contains("financial"));

        return hasSensitiveData ?
                com.waqiti.common.gdpr.enums.RiskLevel.HIGH :
                com.waqiti.common.gdpr.enums.RiskLevel.MEDIUM;
    }

    /**
     * Identify mitigation measures for DPIA
     */
    private List<String> identifyMitigationMeasures(List<String> dataCategories) {
        return List.of(
                "End-to-end encryption",
                "Access controls and RBAC",
                "Regular security audits",
                "Data minimization",
                "Pseudonymization where possible"
        );
    }

    /**
     * Check if Data Protection Officer is required
     */
    private boolean isDPORequired() {
        return true; // Financial services typically require DPO
    }

    /**
     * Check if breach severity is high risk
     */
    private boolean isHighRisk(String severity) {
        return "HIGH".equalsIgnoreCase(severity) || "CRITICAL".equalsIgnoreCase(severity);
    }

    /**
     * Check if breach requires user notification
     */
    private boolean requiresUserNotification(String severity) {
        return "HIGH".equalsIgnoreCase(severity) || "CRITICAL".equalsIgnoreCase(severity);
    }

    /**
     * Audit log helper
     * FIXED: Use GDPRAuditLog entity with GDPRAction enum
     */
    private void auditLog(UUID userId, com.waqiti.common.gdpr.enums.GDPRAction action, String details) {
        GDPRAuditLog auditLog = GDPRAuditLog.builder()
                .userId(userId)
                .action(action)  // Store the enum directly
                .details(details)
                .timestamp(LocalDateTime.now())
                .ipAddress(getCurrentIpAddress())
                .build();

        auditLogRepository.save(auditLog);
    }
}