package com.waqiti.user.service;

import com.waqiti.user.domain.User;
import com.waqiti.user.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * GDPR Anonymization Service (P1-010)
 *
 * Implements GDPR Article 17: Right to Erasure ("Right to be Forgotten")
 *
 * COMPLIANCE FEATURES:
 * - Soft delete: Mark users as deleted without physical removal
 * - Anonymization: Replace PII with irreversible pseudonymous data
 * - Audit trail: Complete log of all erasure requests
 * - Retention: 7-year retention period for legal compliance
 * - Hard delete: Physical deletion after retention period
 *
 * DATA LIFECYCLE:
 * 1. User requests deletion
 * 2. Immediate soft delete + anonymization
 * 3. Data retained for 7 years (legal compliance)
 * 4. Automated hard delete after retention period
 *
 * ANONYMIZATION STRATEGY:
 * - Email: anonymized_{hash}@anonymized.local
 * - Username: user_{hash}
 * - Name fields: Anonymized_{hash}
 * - Phone, address, DOB: NULL
 * - Irreversible: Cannot be reversed to original data
 *
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GdprAnonymizationService {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    // Metrics
    private final Counter softDeleteCounter;
    private final Counter anonymizationCounter;
    private final Counter hardDeleteCounter;

    public GdprAnonymizationService(UserRepository userRepository,
                                   JdbcTemplate jdbcTemplate,
                                   MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.softDeleteCounter = Counter.builder("gdpr.soft.delete")
                .description("Number of GDPR soft delete operations")
                .tag("compliance", "gdpr")
                .register(meterRegistry);

        this.anonymizationCounter = Counter.builder("gdpr.anonymization")
                .description("Number of GDPR anonymization operations")
                .tag("compliance", "gdpr")
                .register(meterRegistry);

        this.hardDeleteCounter = Counter.builder("gdpr.hard.delete")
                .description("Number of GDPR hard delete operations (post-retention)")
                .tag("compliance", "gdpr")
                .register(meterRegistry);
    }

    /**
     * Soft delete user with GDPR-compliant anonymization
     *
     * This method:
     * 1. Marks user as deleted (deleted_at timestamp)
     * 2. Anonymizes all PII fields
     * 3. Creates audit trail in gdpr_erasure_requests
     * 4. Schedules hard deletion after 7 years
     *
     * @param userId User ID to delete
     * @param reason Reason for deletion (USER_REQUEST, ADMIN_REQUEST, etc.)
     * @return Deletion result with request ID and scheduled hard delete date
     * @throws IllegalStateException if user not found or already deleted
     */
    @Transactional
    public DeletionResult softDeleteUser(UUID userId, String reason) {
        log.info("GDPR: Initiating soft delete for user={}, reason={}", userId, reason);

        // Verify user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        // Check if already deleted
        if (user.getDeletedAt() != null) {
            log.warn("GDPR: User already deleted: userId={}, deletedAt={}", userId, user.getDeletedAt());
            throw new IllegalStateException("User already deleted: " + userId);
        }

        try {
            // Call database function for atomic soft delete + anonymization
            String sql = "SELECT soft_delete_user(?, ?)";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, userId, reason);

            softDeleteCounter.increment();
            anonymizationCounter.increment();

            log.info("GDPR: Soft delete completed successfully: userId={}, requestId={}",
                    userId, result.get("request_id"));

            // Create audit event
            createAuditEvent(userId, "SOFT_DELETE", reason, result);

            return new DeletionResult(
                    userId,
                    UUID.fromString(result.get("request_id").toString()),
                    LocalDateTime.parse(result.get("deleted_at").toString()),
                    LocalDateTime.parse(result.get("hard_delete_after").toString()),
                    "SUCCESS"
            );

        } catch (Exception e) {
            log.error("GDPR: Soft delete failed: userId={}", userId, e);
            throw new RuntimeException("Failed to soft delete user: " + e.getMessage(), e);
        }
    }

    /**
     * Anonymize user data only (without soft delete)
     *
     * Use case: Anonymize inactive accounts or comply with data minimization
     *
     * @param userId User ID to anonymize
     * @param reason Reason for anonymization
     * @return Anonymization result
     */
    @Transactional
    public AnonymizationResult anonymizeUser(UUID userId, String reason) {
        log.info("GDPR: Anonymizing user data: userId={}, reason={}", userId, reason);

        try {
            String sql = "SELECT anonymize_user_data(?, ?)";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, userId, reason);

            anonymizationCounter.increment();

            log.info("GDPR: Anonymization completed: userId={}", userId);

            return new AnonymizationResult(
                    userId,
                    LocalDateTime.parse(result.get("anonymized_at").toString()),
                    reason,
                    "SUCCESS"
            );

        } catch (Exception e) {
            log.error("GDPR: Anonymization failed: userId={}", userId, e);
            throw new RuntimeException("Failed to anonymize user: " + e.getMessage(), e);
        }
    }

    /**
     * Hard delete user (physical deletion after retention period)
     *
     * WARNING: This is irreversible!
     * Only called after 7-year retention period has expired
     *
     * @param userId User ID to hard delete
     * @return Hard deletion result
     * @throws IllegalStateException if retention period not expired
     */
    @Transactional
    public HardDeletionResult hardDeleteUser(UUID userId) {
        log.warn("GDPR: Initiating HARD DELETE (physical deletion): userId={}", userId);

        try {
            String sql = "SELECT hard_delete_user(?)";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, userId);

            hardDeleteCounter.increment();

            log.warn("GDPR: HARD DELETE completed (data physically removed): userId={}", userId);

            // Create critical audit event
            createCriticalAuditEvent(userId, "HARD_DELETE", "RETENTION_POLICY_EXPIRED");

            return new HardDeletionResult(
                    userId,
                    LocalDateTime.parse(result.get("hard_deleted_at").toString()),
                    "PHYSICALLY_DELETED"
            );

        } catch (Exception e) {
            log.error("GDPR: Hard delete failed: userId={}", userId, e);
            throw new RuntimeException("Failed to hard delete user: " + e.getMessage(), e);
        }
    }

    /**
     * Cleanup expired users (automated batch process)
     *
     * Finds and hard-deletes users whose retention period has expired
     * Should be run via scheduled job (monthly recommended)
     *
     * @return Number of users hard deleted
     */
    @Transactional
    public int cleanupExpiredUsers() {
        log.info("GDPR: Starting automated cleanup of expired users");

        try {
            String sql = "SELECT * FROM cleanup_expired_users()";
            var results = jdbcTemplate.queryForList(sql);

            int deletedCount = results.size();

            log.info("GDPR: Cleanup completed: {} users hard deleted", deletedCount);

            return deletedCount;

        } catch (Exception e) {
            log.error("GDPR: Cleanup failed", e);
            throw new RuntimeException("Failed to cleanup expired users: " + e.getMessage(), e);
        }
    }

    /**
     * Check if user is deleted (soft or hard)
     *
     * @param userId User ID to check
     * @return true if user is deleted or doesn't exist
     */
    public boolean isUserDeleted(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getDeletedAt() != null)
                .orElse(true);  // Treat non-existent users as deleted
    }

    /**
     * Get GDPR erasure request status
     *
     * @param userId User ID
     * @return Erasure request details if exists
     */
    public ErasureRequestStatus getErasureStatus(UUID userId) {
        String sql = """
            SELECT id, request_type, status, created_at, processing_completed_at, hard_delete_after
            FROM gdpr_erasure_requests
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, userId);

            return new ErasureRequestStatus(
                    UUID.fromString(result.get("id").toString()),
                    userId,
                    result.get("request_type").toString(),
                    result.get("status").toString(),
                    LocalDateTime.parse(result.get("created_at").toString()),
                    result.get("processing_completed_at") != null
                            ? LocalDateTime.parse(result.get("processing_completed_at").toString())
                            : null,
                    result.get("hard_delete_after") != null
                            ? LocalDateTime.parse(result.get("hard_delete_after").toString())
                            : null
            );

        } catch (Exception e) {
            log.debug("No erasure request found for user={}", userId);
            return null;
        }
    }

    /**
     * Create audit event for GDPR operations
     */
    private void createAuditEvent(UUID userId, String action, String reason, Map<String, Object> details) {
        log.info("GDPR Audit: userId={}, action={}, reason={}, details={}",
                userId, action, reason, details);

        // Increment audit metric
        meterRegistry.counter("gdpr.audit.events",
                "action", action,
                "reason", reason).increment();
    }

    /**
     * Create critical audit event (for hard deletes)
     */
    private void createCriticalAuditEvent(UUID userId, String action, String reason) {
        log.warn("GDPR CRITICAL Audit: userId={}, action={}, reason={}",
                userId, action, reason);

        meterRegistry.counter("gdpr.audit.critical.events",
                "action", action,
                "reason", reason).increment();
    }

    // ========================================
    // Result DTOs
    // ========================================

    public record DeletionResult(
            UUID userId,
            UUID requestId,
            LocalDateTime deletedAt,
            LocalDateTime hardDeleteAfter,
            String status
    ) {}

    public record AnonymizationResult(
            UUID userId,
            LocalDateTime anonymizedAt,
            String reason,
            String status
    ) {}

    public record HardDeletionResult(
            UUID userId,
            LocalDateTime hardDeletedAt,
            String status
    ) {}

    public record ErasureRequestStatus(
            UUID requestId,
            UUID userId,
            String requestType,
            String status,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            LocalDateTime hardDeleteAfter
    ) {}
}
