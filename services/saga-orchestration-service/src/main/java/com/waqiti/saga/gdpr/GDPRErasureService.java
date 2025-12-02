package com.waqiti.saga.gdpr;

import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.repository.SagaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GDPR Right to Erasure Service
 *
 * CRITICAL: Implements GDPR Article 17 - Right to Erasure
 *
 * When a user requests data deletion, this service:
 * 1. Pseudonymizes user identifiers (cannot delete financial records)
 * 2. Removes PII from context data
 * 3. Maintains audit trail (required for compliance)
 * 4. Records erasure event
 *
 * Compliance:
 * - GDPR Article 17: Right to erasure
 * - GDPR Article 6: Lawful basis for retention (legal obligation)
 * - Financial records: 7-year retention required (SOX, BSA)
 *
 * Implementation Strategy:
 * - Pseudonymization (not deletion) - preserves financial integrity
 * - PII removal from free-text fields
 * - Audit trail of erasure
 *
 * Usage:
 * <pre>
 * gdprErasureService.anonymizeUserSagas("user-123", "User requested deletion");
 * </pre>
 */
@Service
public class GDPRErasureService {

    private static final Logger logger = LoggerFactory.getLogger(GDPRErasureService.class);
    private static final String ANONYMIZED_PREFIX = "ANONYMIZED_";

    private final SagaRepository sagaRepository;

    public GDPRErasureService(SagaRepository sagaRepository) {
        this.sagaRepository = sagaRepository;
    }

    /**
     * Anonymize all saga data for a user (GDPR erasure)
     *
     * IMPORTANT: Does NOT delete saga records (financial records must be retained)
     * Instead: Pseudonymizes user identifiers and removes PII
     *
     * @param userId User ID to anonymize
     * @param reason Reason for erasure (for audit trail)
     * @return Number of saga records anonymized
     */
    @Transactional
    public int anonymizeUserSagas(String userId, String reason) {
        logger.info("GDPR Erasure: Starting anonymization for user: {} - Reason: {}", userId, reason);

        // Find all sagas initiated by this user
        List<SagaExecution> userSagas = sagaRepository.findByInitiatedBy(userId);

        if (userSagas.isEmpty()) {
            logger.info("No saga records found for user: {}", userId);
            return 0;
        }

        int anonymizedCount = 0;
        String anonymizedId = generateAnonymizedId();

        for (SagaExecution saga : userSagas) {
            try {
                anonymizeSaga(saga, anonymizedId, reason);
                sagaRepository.save(saga);
                anonymizedCount++;

                logger.debug("Anonymized saga: {} for user: {}", saga.getSagaId(), userId);

            } catch (Exception e) {
                logger.error("Failed to anonymize saga: {} for user: {}", saga.getSagaId(), userId, e);
                // Continue with other sagas (best-effort)
            }
        }

        logger.info("GDPR Erasure: Anonymized {} saga records for user: {}", anonymizedCount, userId);

        // TODO: Publish GDPR erasure event to Kafka
        // publishErasureEvent(userId, anonymizedCount, reason);

        return anonymizedCount;
    }

    /**
     * Anonymize a single saga execution
     *
     * Pseudonymization strategy:
     * - Replace user ID with anonymous identifier
     * - Remove PII from context (names, emails, addresses)
     * - Preserve financial data (amounts, transaction IDs)
     * - Record erasure metadata
     *
     * @param saga Saga execution to anonymize
     * @param anonymizedId Anonymous identifier
     * @param reason Reason for erasure
     */
    private void anonymizeSaga(SagaExecution saga, String anonymizedId, String reason) {
        // Pseudonymize user identifier
        saga.setInitiatedBy(anonymizedId);

        // Add GDPR erasure metadata to context
        saga.setContextValue("gdpr_erased", true);
        saga.setContextValue("gdpr_erasure_date", LocalDateTime.now());
        saga.setContextValue("gdpr_erasure_reason", reason);

        // Remove PII from context while preserving financial data
        anonymizeContextData(saga);

        logger.debug("Saga {} anonymized with ID: {}", saga.getSagaId(), anonymizedId);
    }

    /**
     * Remove PII from saga context while preserving financial data
     *
     * Removes:
     * - User names
     * - Email addresses
     * - Phone numbers
     * - Physical addresses
     * - IP addresses
     *
     * Preserves:
     * - Transaction IDs
     * - Amounts
     * - Currency codes
     * - Timestamps
     * - Status codes
     *
     * @param saga Saga execution
     */
    private void anonymizeContextData(SagaExecution saga) {
        // Keys that contain PII (to be removed or anonymized)
        String[] piiKeys = {
            "userName", "fullName", "firstName", "lastName",
            "email", "emailAddress",
            "phone", "phoneNumber", "mobile",
            "address", "streetAddress", "city", "state", "postalCode",
            "ipAddress", "deviceId", "senderName", "recipientName"
        };

        for (String key : piiKeys) {
            if (saga.getContextValue(key) != null) {
                saga.setContextValue(key, "[REDACTED]");
                logger.trace("Redacted PII field: {} in saga: {}", key, saga.getSagaId());
            }
        }

        // Preserve financial data (amounts, IDs, timestamps)
        // These are required for compliance and audit
        // Examples: "amount", "currency", "transactionId", "timestamp", "status"
    }

    /**
     * Generate unique anonymized identifier
     *
     * Format: ANONYMIZED_[UUID]
     * Example: ANONYMIZED_a1b2c3d4-e5f6-7890-abcd-ef1234567890
     *
     * @return Anonymous identifier
     */
    private String generateAnonymizedId() {
        return ANONYMIZED_PREFIX + UUID.randomUUID().toString();
    }

    /**
     * Check if a user ID is anonymized
     *
     * @param userId User ID to check
     * @return true if anonymized, false otherwise
     */
    public boolean isAnonymized(String userId) {
        return userId != null && userId.startsWith(ANONYMIZED_PREFIX);
    }

    /**
     * Get anonymization statistics for reporting
     *
     * @return Anonymization statistics
     */
    public AnonymizationStatistics getStatistics() {
        long totalSagas = sagaRepository.count();
        long anonymizedSagas = sagaRepository.countByInitiatedByStartingWith(ANONYMIZED_PREFIX);

        return new AnonymizationStatistics(
            totalSagas,
            anonymizedSagas,
            totalSagas > 0 ? (double) anonymizedSagas / totalSagas * 100 : 0.0
        );
    }

    /**
     * Anonymization statistics DTO
     */
    public record AnonymizationStatistics(
        long totalSagas,
        long anonymizedSagas,
        double anonymizationPercentage
    ) {}
}
