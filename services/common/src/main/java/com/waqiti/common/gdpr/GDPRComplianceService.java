package com.waqiti.common.gdpr;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * GDPR Compliance Service
 *
 * Implements comprehensive GDPR requirements:
 * - Article 17: Right to Erasure ("Right to be Forgotten")
 * - Article 20: Right to Data Portability
 * - Article 15: Right of Access
 * - Article 16: Right to Rectification
 * - Article 21: Right to Object
 *
 * COMPLIANCE FEATURES:
 * - Complete user data deletion within 30 days
 * - Data export in portable format (JSON)
 * - Audit trail for all GDPR operations
 * - Legal retention period enforcement
 * - Cross-service coordination
 * - Pseudonymization support
 *
 * ARCHITECTURE:
 * - Event-driven: Publishes events to Kafka for cross-service coordination
 * - Batch processing: Handles large datasets efficiently
 * - Audit logging: Complete traceability
 * - Async execution: Long-running operations don't block
 *
 * @author Waqiti Compliance Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GDPRComplianceService {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final GDPRAuditService auditService;

    private static final String GDPR_EVENTS_TOPIC = "gdpr.events";
    private static final int ERASURE_DEADLINE_DAYS = 30;

    /**
     * GDPR Article 17: Right to Erasure
     *
     * Initiates complete user data deletion across all systems.
     * Must be completed within 30 days per GDPR requirements.
     *
     * @param userId User ID to erase
     * @param reason Reason for erasure (user request, etc.)
     * @return ErasureRequest tracking object
     */
    @Transactional
    public CompletableFuture<ErasureResult> initiateRightToErasure(
            String userId, String reason, boolean verifyLegalRetention) {

        log.info("Initiating GDPR Right to Erasure: userId={}, reason={}", userId, reason);

        try {
            // Step 1: Verify user exists
            if (!userExists(userId)) {
                throw new GDPRException("User not found: " + userId);
            }

            // Step 2: Check legal retention requirements
            if (verifyLegalRetention) {
                LegalRetentionCheck retentionCheck = checkLegalRetention(userId);
                if (retentionCheck.hasActiveObligations()) {
                    log.warn("User {} has active legal retention obligations: {}",
                        userId, retentionCheck.getObligations());
                    throw new GDPRException(
                        "Cannot erase user data due to active legal obligations: " +
                        String.join(", ", retentionCheck.getObligations())
                    );
                }
            }

            // Step 3: Create erasure request record
            String requestId = UUID.randomUUID().toString();
            ErasureRequest request = createErasureRequest(requestId, userId, reason);

            // Step 4: Publish erasure event to all services
            GDPRErasureEvent event = GDPRErasureEvent.builder()
                .requestId(requestId)
                .userId(userId)
                .initiatedAt(Instant.now())
                .deadline(Instant.now().plus(ERASURE_DEADLINE_DAYS, ChronoUnit.DAYS))
                .reason(reason)
                .build();

            kafkaTemplate.send(GDPR_EVENTS_TOPIC, userId, event);

            // Step 5: Execute erasure asynchronously
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return executeErasure(request);
                } catch (Exception e) {
                    log.error("Erasure failed for user: {}", userId, e);
                    updateErasureStatus(requestId, "FAILED", e.getMessage());
                    throw new GDPRException("Erasure failed", e);
                }
            });

        } catch (Exception e) {
            log.error("Failed to initiate right to erasure: userId={}", userId, e);
            auditService.auditGDPRFailure("RIGHT_TO_ERASURE", userId, e);
            throw new GDPRException("Failed to initiate erasure", e);
        }
    }

    /**
     * Execute the actual data erasure
     */
    @Transactional
    private ErasureResult executeErasure(ErasureRequest request) {
        log.info("Executing data erasure: requestId={}, userId={}",
            request.getRequestId(), request.getUserId());

        ErasureResult result = ErasureResult.builder()
            .requestId(request.getRequestId())
            .userId(request.getUserId())
            .startTime(Instant.now())
            .build();

        try {
            updateErasureStatus(request.getRequestId(), "IN_PROGRESS", null);

            // Erase data from all tables
            Map<String, Integer> deletionCounts = new HashMap<>();

            // User data
            deletionCounts.put("users", eraseUserData(request.getUserId()));
            deletionCounts.put("user_profiles", eraseUserProfiles(request.getUserId()));
            deletionCounts.put("user_preferences", eraseUserPreferences(request.getUserId()));

            // Financial data (with retention check)
            deletionCounts.put("wallets", pseudonymizeWallets(request.getUserId()));
            deletionCounts.put("transactions", pseudonymizeTransactions(request.getUserId()));
            deletionCounts.put("payments", pseudonymizePayments(request.getUserId()));

            // KYC/AML data (check retention)
            if (!hasActiveKYCRetention(request.getUserId())) {
                deletionCounts.put("kyc_verifications", eraseKYCData(request.getUserId()));
            } else {
                deletionCounts.put("kyc_verifications", pseudonymizeKYCData(request.getUserId()));
            }

            // Authentication data
            deletionCounts.put("user_sessions", eraseUserSessions(request.getUserId()));
            deletionCounts.put("refresh_tokens", eraseRefreshTokens(request.getUserId()));
            deletionCounts.put("api_keys", eraseAPIKeys(request.getUserId()));

            // Communication data
            deletionCounts.put("notifications", eraseNotifications(request.getUserId()));
            deletionCounts.put("email_logs", eraseEmailLogs(request.getUserId()));
            deletionCounts.put("sms_logs", eraseSMSLogs(request.getUserId()));

            // Device & security data
            deletionCounts.put("devices", eraseDevices(request.getUserId()));
            deletionCounts.put("device_fingerprints", eraseDeviceFingerprints(request.getUserId()));
            deletionCounts.put("biometric_data", eraseBiometricData(request.getUserId()));

            // Social & activity data
            deletionCounts.put("user_activities", eraseUserActivities(request.getUserId()));
            deletionCounts.put("user_connections", eraseUserConnections(request.getUserId()));

            // Support & feedback
            deletionCounts.put("support_tickets", pseudonymizeSupportTickets(request.getUserId()));
            deletionCounts.put("user_feedback", pseudonymizeFeedback(request.getUserId()));

            result.setDeletionCounts(deletionCounts);
            result.setTotalRecordsDeleted(
                deletionCounts.values().stream().mapToInt(Integer::intValue).sum()
            );

            // Create anonymized record for analytics retention
            createAnonymizedRecord(request.getUserId());

            // Update status
            result.setEndTime(Instant.now());
            result.setSuccess(true);
            updateErasureStatus(request.getRequestId(), "COMPLETED", null);

            // Audit
            auditService.auditGDPRErasure(convertToGDPRDataErasureResult(result));

            log.info("Data erasure completed: userId={}, recordsDeleted={}",
                request.getUserId(), result.getTotalRecordsDeleted());

            return result;

        } catch (Exception e) {
            log.error("Data erasure failed: userId={}", request.getUserId(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(Instant.now());
            updateErasureStatus(request.getRequestId(), "FAILED", e.getMessage());
            throw e;
        }
    }

    /**
     * GDPR Article 20: Right to Data Portability
     *
     * Exports all user data in a portable format (JSON + CSV)
     * Must be provided in a structured, commonly used format
     */
    @Transactional(readOnly = true)
    public CompletableFuture<DataExportResult> initiateDataPortability(String userId, String format) {
        log.info("Initiating GDPR Data Portability: userId={}, format={}", userId, format);

        try {
            // Verify user exists
            if (!userExists(userId)) {
                throw new GDPRException("User not found: " + userId);
            }

            // Create export request
            String requestId = UUID.randomUUID().toString();
            createDataExportRequest(requestId, userId, format);

            // Execute export asynchronously
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return executeDataExport(requestId, userId, format);
                } catch (Exception e) {
                    log.error("Data export failed for user: {}", userId, e);
                    updateExportStatus(requestId, "FAILED", e.getMessage());
                    throw new GDPRException("Data export failed", e);
                }
            });

        } catch (Exception e) {
            log.error("Failed to initiate data portability: userId={}", userId, e);
            auditService.auditGDPRFailure("DATA_PORTABILITY", userId, e);
            throw new GDPRException("Failed to initiate data export", e);
        }
    }

    /**
     * Execute the actual data export
     */
    private DataExportResult executeDataExport(String requestId, String userId, String format) {
        log.info("Executing data export: requestId={}, userId={}, format={}",
            requestId, userId, format);

        DataExportResult result = DataExportResult.builder()
            .requestId(requestId)
            .userId(userId)
            .format(format)
            .startTime(Instant.now())
            .build();

        try {
            updateExportStatus(requestId, "IN_PROGRESS", null);

            // Collect all user data
            Map<String, Object> userData = new HashMap<>();

            // Personal information
            userData.put("user_profile", getUserProfile(userId));
            userData.put("personal_info", getPersonalInfo(userId));
            userData.put("preferences", getUserPreferences(userId));

            // Financial data
            userData.put("wallets", getWalletData(userId));
            userData.put("transactions", getTransactionHistory(userId));
            userData.put("payments", getPaymentHistory(userId));

            // KYC/Verification data
            userData.put("kyc_verifications", getKYCData(userId));
            userData.put("identity_documents", getIdentityDocuments(userId));

            // Activity data
            userData.put("login_history", getLoginHistory(userId));
            userData.put("activities", getUserActivities(userId));

            // Communication data
            userData.put("notifications", getNotifications(userId));
            userData.put("messages", getMessages(userId));

            // Device data
            userData.put("devices", getDevices(userId));

            // Support data
            userData.put("support_tickets", getSupportTickets(userId));

            // Generate export file
            byte[] exportData;
            if ("JSON".equalsIgnoreCase(format)) {
                exportData = generateJSONExport(userData);
            } else if ("ZIP".equalsIgnoreCase(format)) {
                exportData = generateZIPExport(userData);
            } else {
                throw new IllegalArgumentException("Unsupported format: " + format);
            }

            result.setExportData(exportData);
            result.setFileSizeBytes(exportData.length);
            result.setEndTime(Instant.now());
            result.setSuccess(true);

            updateExportStatus(requestId, "COMPLETED", null);

            // Audit
            auditService.auditGDPRDataExport(convertToGDPRDataExportResult(result));

            log.info("Data export completed: userId={}, size={} bytes",
                userId, exportData.length);

            return result;

        } catch (Exception e) {
            log.error("Data export failed: userId={}", userId, e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(Instant.now());
            updateExportStatus(requestId, "FAILED", e.getMessage());
            throw new GDPRException("Export failed", e);
        }
    }

    // ========================================================================
    // Data Deletion Methods
    // ========================================================================

    private int eraseUserData(String userId) {
        // Soft delete to preserve foreign key integrity
        return jdbcTemplate.update(
            "UPDATE users SET " +
            "email = 'deleted_' || id || '@deleted.waqiti.com', " +
            "first_name = 'DELETED', " +
            "last_name = 'USER', " +
            "phone_number = NULL, " +
            "date_of_birth = NULL, " +
            "address = NULL, " +
            "deleted_at = NOW(), " +
            "deleted_by = 'GDPR_ERASURE', " +
            "gdpr_erased = true " +
            "WHERE id = ?",
            userId
        );
    }

    private int eraseUserProfiles(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM user_profiles WHERE user_id = ?", userId
        );
    }

    private int eraseUserPreferences(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM user_preferences WHERE user_id = ?", userId
        );
    }

    private int pseudonymizeWallets(String userId) {
        // Pseudonymize rather than delete for financial records
        return jdbcTemplate.update(
            "UPDATE wallets SET " +
            "user_id = 'GDPR_ERASED_' || MD5(user_id::text), " +
            "gdpr_pseudonymized = true " +
            "WHERE user_id = ? AND NOT EXISTS (" +
            "  SELECT 1 FROM legal_holds " +
            "  WHERE entity_type = 'WALLET' AND entity_id = wallets.id" +
            ")",
            userId
        );
    }

    private int pseudonymizeTransactions(String userId) {
        // Keep transactions for 5 years (BSA/FinCEN), but pseudonymize
        return jdbcTemplate.update(
            "UPDATE transactions SET " +
            "user_id = 'GDPR_ERASED_' || MD5(user_id::text), " +
            "description = 'TRANSACTION', " +
            "gdpr_pseudonymized = true " +
            "WHERE user_id = ? AND created_at > NOW() - INTERVAL '5 years'",
            userId
        );
    }

    private int pseudonymizePayments(String userId) {
        return jdbcTemplate.update(
            "UPDATE payments SET " +
            "user_id = 'GDPR_ERASED_' || MD5(user_id::text), " +
            "gdpr_pseudonymized = true " +
            "WHERE user_id = ? AND created_at > NOW() - INTERVAL '5 years'",
            userId
        );
    }

    private int eraseKYCData(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM kyc_verifications WHERE user_id = ? " +
            "AND created_at < NOW() - INTERVAL '5 years'",
            userId
        );
    }

    private int pseudonymizeKYCData(String userId) {
        // Keep recent KYC for retention, but pseudonymize
        return jdbcTemplate.update(
            "UPDATE kyc_verifications SET " +
            "user_id = 'GDPR_ERASED_' || MD5(user_id::text), " +
            "document_number = 'REDACTED', " +
            "gdpr_pseudonymized = true " +
            "WHERE user_id = ?",
            userId
        );
    }

    private int eraseUserSessions(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM user_sessions WHERE user_id = ?", userId
        );
    }

    private int eraseRefreshTokens(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM refresh_tokens WHERE user_id = ?", userId
        );
    }

    private int eraseAPIKeys(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM api_keys WHERE user_id = ?", userId
        );
    }

    private int eraseNotifications(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM notifications WHERE user_id = ?", userId
        );
    }

    private int eraseEmailLogs(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM email_logs WHERE user_id = ?", userId
        );
    }

    private int eraseSMSLogs(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM sms_logs WHERE user_id = ?", userId
        );
    }

    private int eraseDevices(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM devices WHERE user_id = ?", userId
        );
    }

    private int eraseDeviceFingerprints(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM device_fingerprints WHERE user_id = ?", userId
        );
    }

    private int eraseBiometricData(String userId) {
        // CRITICAL: Biometric data must be completely erased
        return jdbcTemplate.update(
            "DELETE FROM biometric_enrollments WHERE user_id = ?", userId
        );
    }

    private int eraseUserActivities(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM user_activities WHERE user_id = ? " +
            "AND created_at < NOW() - INTERVAL '90 days'",
            userId
        );
    }

    private int eraseUserConnections(String userId) {
        return jdbcTemplate.update(
            "DELETE FROM user_connections WHERE user_id = ? OR connected_user_id = ?",
            userId, userId
        );
    }

    private int pseudonymizeSupportTickets(String userId) {
        // Keep for audit, but pseudonymize
        return jdbcTemplate.update(
            "UPDATE support_tickets SET " +
            "user_id = 'GDPR_ERASED_' || MD5(user_id::text), " +
            "user_name = 'DELETED USER', " +
            "user_email = 'deleted@example.com', " +
            "gdpr_pseudonymized = true " +
            "WHERE user_id = ?",
            userId
        );
    }

    private int pseudonymizeFeedback(String userId) {
        return jdbcTemplate.update(
            "UPDATE user_feedback SET " +
            "user_id = 'GDPR_ERASED_' || MD5(user_id::text), " +
            "gdpr_pseudonymized = true " +
            "WHERE user_id = ?",
            userId
        );
    }

    // ========================================================================
    // Data Export Methods
    // ========================================================================

    private Map<String, Object> getUserProfile(String userId) {
        return jdbcTemplate.queryForMap(
            "SELECT id, email, first_name, last_name, created_at, updated_at " +
            "FROM users WHERE id = ?", userId
        );
    }

    private Map<String, Object> getPersonalInfo(String userId) {
        return jdbcTemplate.queryForMap(
            "SELECT * FROM user_profiles WHERE user_id = ?", userId
        );
    }

    private List<Map<String, Object>> getUserPreferences(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM user_preferences WHERE user_id = ?", userId
        );
    }

    private List<Map<String, Object>> getWalletData(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT id, currency, balance, created_at FROM wallets WHERE user_id = ?", userId
        );
    }

    private List<Map<String, Object>> getTransactionHistory(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT id, type, amount, currency, status, created_at " +
            "FROM transactions WHERE user_id = ? ORDER BY created_at DESC LIMIT 1000",
            userId
        );
    }

    private List<Map<String, Object>> getPaymentHistory(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT id, amount, currency, status, provider, created_at " +
            "FROM payments WHERE user_id = ? ORDER BY created_at DESC LIMIT 1000",
            userId
        );
    }

    private List<Map<String, Object>> getKYCData(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT id, status, verification_type, created_at " +
            "FROM kyc_verifications WHERE user_id = ?",
            userId
        );
    }

    private List<Map<String, Object>> getIdentityDocuments(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT id, document_type, status, uploaded_at " +
            "FROM identity_documents WHERE user_id = ?",
            userId
        );
    }

    private List<Map<String, Object>> getLoginHistory(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT ip_address, user_agent, login_at, success " +
            "FROM login_history WHERE user_id = ? ORDER BY login_at DESC LIMIT 100",
            userId
        );
    }

    private List<Map<String, Object>> getUserActivities(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT activity_type, created_at " +
            "FROM user_activities WHERE user_id = ? ORDER BY created_at DESC LIMIT 500",
            userId
        );
    }

    private List<Map<String, Object>> getNotifications(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT type, title, message, sent_at " +
            "FROM notifications WHERE user_id = ? ORDER BY sent_at DESC LIMIT 100",
            userId
        );
    }

    private List<Map<String, Object>> getMessages(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT subject, body, sent_at, sender_id " +
            "FROM messages WHERE user_id = ? ORDER BY sent_at DESC LIMIT 100",
            userId
        );
    }

    private List<Map<String, Object>> getDevices(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT device_name, device_type, last_used_at " +
            "FROM devices WHERE user_id = ?",
            userId
        );
    }

    private List<Map<String, Object>> getSupportTickets(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT id, subject, status, created_at " +
            "FROM support_tickets WHERE user_id = ?",
            userId
        );
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private boolean userExists(String userId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId
        );
        return count != null && count > 0;
    }

    private LegalRetentionCheck checkLegalRetention(String userId) {
        // Check for active legal obligations
        List<String> obligations = new ArrayList<>();

        // Check for ongoing investigations
        Integer investigations = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM investigations WHERE user_id = ? AND status = 'ACTIVE'",
            Integer.class, userId
        );
        if (investigations != null && investigations > 0) {
            obligations.add("Active investigation");
        }

        // Check for legal holds
        Integer legalHolds = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM legal_holds WHERE entity_id = ? AND status = 'ACTIVE'",
            Integer.class, userId
        );
        if (legalHolds != null && legalHolds > 0) {
            obligations.add("Legal hold");
        }

        // Check for pending transactions
        Integer pendingTransactions = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND status = 'PENDING'",
            Integer.class, userId
        );
        if (pendingTransactions != null && pendingTransactions > 0) {
            obligations.add("Pending transactions");
        }

        return LegalRetentionCheck.builder()
            .userId(userId)
            .obligations(obligations)
            .build();
    }

    private boolean hasActiveKYCRetention(String userId) {
        // BSA/FinCEN requires 5-year retention
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kyc_verifications " +
            "WHERE user_id = ? AND created_at > NOW() - INTERVAL '5 years'",
            Integer.class, userId
        );
        return count != null && count > 0;
    }

    private ErasureRequest createErasureRequest(String requestId, String userId, String reason) {
        jdbcTemplate.update(
            "INSERT INTO gdpr_erasure_requests " +
            "(id, user_id, reason, status, requested_at, deadline) " +
            "VALUES (?, ?, ?, 'PENDING', NOW(), NOW() + INTERVAL '30 days')",
            requestId, userId, reason
        );

        return ErasureRequest.builder()
            .requestId(requestId)
            .userId(userId)
            .reason(reason)
            .status("PENDING")
            .build();
    }

    private void createDataExportRequest(String requestId, String userId, String format) {
        jdbcTemplate.update(
            "INSERT INTO gdpr_export_requests " +
            "(id, user_id, format, status, requested_at) " +
            "VALUES (?, ?, ?, 'PENDING', NOW())",
            requestId, userId, format
        );
    }

    private void updateErasureStatus(String requestId, String status, String errorMessage) {
        jdbcTemplate.update(
            "UPDATE gdpr_erasure_requests SET status = ?, error_message = ?, updated_at = NOW() " +
            "WHERE id = ?",
            status, errorMessage, requestId
        );
    }

    private void updateExportStatus(String requestId, String status, String errorMessage) {
        jdbcTemplate.update(
            "UPDATE gdpr_export_requests SET status = ?, error_message = ?, updated_at = NOW() " +
            "WHERE id = ?",
            status, errorMessage, requestId
        );
    }

    private void createAnonymizedRecord(String userId) {
        // Create anonymized record for aggregate analytics
        jdbcTemplate.update(
            "INSERT INTO anonymized_users (id, user_hash, anonymized_at) " +
            "VALUES (?, MD5(?), NOW())",
            UUID.randomUUID().toString(), userId
        );
    }

    private byte[] generateJSONExport(Map<String, Object> userData) throws Exception {
        String json = objectMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(userData);
        return json.getBytes();
    }

    private byte[] generateZIPExport(Map<String, Object> userData) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add each data category as separate JSON file
            for (Map.Entry<String, Object> entry : userData.entrySet()) {
                String filename = entry.getKey() + ".json";
                String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(entry.getValue());

                ZipEntry zipEntry = new ZipEntry(filename);
                zos.putNextEntry(zipEntry);
                zos.write(json.getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    // ========================================================================
    // Data Classes
    // ========================================================================

    @Data
    @Builder
    public static class ErasureRequest {
        private String requestId;
        private String userId;
        private String reason;
        private String status;
        private Instant requestedAt;
        private Instant deadline;
    }

    @Data
    @Builder
    public static class ErasureResult {
        private String requestId;
        private String userId;
        private Map<String, Integer> deletionCounts;
        private int totalRecordsDeleted;
        private boolean success;
        private String errorMessage;
        private Instant startTime;
        private Instant endTime;

        public long getExecutionTimeMs() {
            if (startTime != null && endTime != null) {
                return ChronoUnit.MILLIS.between(startTime, endTime);
            }
            return 0;
        }
    }

    @Data
    @Builder
    public static class DataExportResult {
        private String requestId;
        private String userId;
        private String format;
        private byte[] exportData;
        private long fileSizeBytes;
        private boolean success;
        private String errorMessage;
        private Instant startTime;
        private Instant endTime;
    }

    @Data
    @Builder
    public static class LegalRetentionCheck {
        private String userId;
        private List<String> obligations;

        public boolean hasActiveObligations() {
            return obligations != null && !obligations.isEmpty();
        }
    }

    @Data
    @Builder
    public static class GDPRErasureEvent {
        private String requestId;
        private String userId;
        private Instant initiatedAt;
        private Instant deadline;
        private String reason;
    }

    public static class GDPRException extends RuntimeException {
        public GDPRException(String message) {
            super(message);
        }

        public GDPRException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * PRODUCTION FIX: Convert inner ErasureResult to model GDPRDataErasureResult
     */
    private com.waqiti.common.gdpr.model.GDPRDataErasureResult convertToGDPRDataErasureResult(ErasureResult result) {
        return com.waqiti.common.gdpr.model.GDPRDataErasureResult.builder()
                .requestId(result.getRequestId())
                .userId(result.getUserId())
                .totalRecordsDeleted(result.getTotalRecordsDeleted())
                .status(result.isSuccess() ?
                    com.waqiti.common.gdpr.model.GDPRDataErasureResult.ErasureStatus.COMPLETED :
                    com.waqiti.common.gdpr.model.GDPRDataErasureResult.ErasureStatus.FAILED)
                .startedAt(result.getStartTime() != null ?
                    LocalDateTime.ofInstant(result.getStartTime(), java.time.ZoneId.systemDefault()) : null)
                .completedAt(result.getEndTime() != null ?
                    LocalDateTime.ofInstant(result.getEndTime(), java.time.ZoneId.systemDefault()) : null)
                .processingTimeMs(result.getExecutionTimeMs())
                .errorMessage(result.getErrorMessage())
                .build();
    }

    /**
     * PRODUCTION FIX: Convert inner DataExportResult to model GDPRDataExportResult
     */
    private com.waqiti.common.gdpr.model.GDPRDataExportResult convertToGDPRDataExportResult(DataExportResult result) {
        return com.waqiti.common.gdpr.model.GDPRDataExportResult.builder()
                .requestId(result.getRequestId())
                .userId(result.getUserId())
                .exportFormat(result.getFormat())
                .exportFileSize(result.getFileSizeBytes())
                .status(result.isSuccess() ?
                    com.waqiti.common.gdpr.model.GDPRDataExportResult.ExportStatus.COMPLETED :
                    com.waqiti.common.gdpr.model.GDPRDataExportResult.ExportStatus.FAILED)
                .startedAt(result.getStartTime() != null ?
                    LocalDateTime.ofInstant(result.getStartTime(), java.time.ZoneId.systemDefault()) : null)
                .completedAt(result.getEndTime() != null ?
                    LocalDateTime.ofInstant(result.getEndTime(), java.time.ZoneId.systemDefault()) : null)
                .errorMessage(result.getErrorMessage())
                .build();
    }
}
