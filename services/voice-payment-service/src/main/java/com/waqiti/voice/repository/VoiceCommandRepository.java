package com.waqiti.voice.repository;

import com.waqiti.voice.domain.VoiceCommand;
import com.waqiti.voice.domain.VoiceCommand.CommandType;
import com.waqiti.voice.domain.VoiceCommand.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for VoiceCommand entity operations
 *
 * Provides comprehensive query methods for voice command management including:
 * - Session-based queries
 * - Status and type filtering
 * - Temporal queries (time-based)
 * - Analytics and reporting queries
 *
 * Performance Optimizations:
 * - Query hints for read-only queries
 * - Indexed column usage
 * - Pagination support
 * - Batch operations
 */
@Repository
public interface VoiceCommandRepository extends JpaRepository<VoiceCommand, UUID> {

    // ========== Basic Lookup Queries ==========

    /**
     * Find command by user and session (most common query)
     */
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Optional<VoiceCommand> findByUserIdAndSessionId(UUID userId, String sessionId);

    /**
     * Find command by unique command ID
     */
    Optional<VoiceCommand> findByCommandId(String commandId);

    /**
     * Find all commands for a session ordered by creation time
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.sessionId = :sessionId ORDER BY vc.createdAt DESC")
    List<VoiceCommand> findBySessionIdOrderByCreatedAtDesc(@Param("sessionId") String sessionId);

    // ========== User-based Queries ==========

    /**
     * Find all commands for a user with pagination
     */
    Page<VoiceCommand> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find user's commands within date range
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.userId = :userId " +
           "AND vc.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY vc.createdAt DESC")
    List<VoiceCommand> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find user's recent commands (last N)
     */
    @Query(value = "SELECT vc FROM VoiceCommand vc WHERE vc.userId = :userId " +
                   "ORDER BY vc.createdAt DESC")
    List<VoiceCommand> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);

    // ========== Status-based Queries ==========

    /**
     * Find commands by status
     */
    List<VoiceCommand> findByProcessingStatus(ProcessingStatus status);

    /**
     * Find user's commands by status
     */
    List<VoiceCommand> findByUserIdAndProcessingStatus(UUID userId, ProcessingStatus status);

    /**
     * Find pending confirmations for user
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.userId = :userId " +
           "AND vc.confirmationRequired = true " +
           "AND vc.isConfirmed = false " +
           "AND vc.processingStatus = 'CONFIRMING' " +
           "ORDER BY vc.createdAt DESC")
    List<VoiceCommand> findPendingConfirmations(@Param("userId") UUID userId);

    /**
     * Find expired commands pending confirmation
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.confirmationRequired = true " +
           "AND vc.isConfirmed = false " +
           "AND vc.processingStatus = 'CONFIRMING' " +
           "AND vc.createdAt < :expiryThreshold")
    List<VoiceCommand> findExpiredPendingConfirmations(@Param("expiryThreshold") LocalDateTime expiryThreshold);

    // ========== Command Type Queries ==========

    /**
     * Find commands by type
     */
    List<VoiceCommand> findByCommandType(CommandType commandType);

    /**
     * Find user's payment commands
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.userId = :userId " +
           "AND vc.commandType IN ('SEND_PAYMENT', 'REQUEST_PAYMENT', 'PAY_BILL', 'TRANSFER_FUNDS', 'SPLIT_BILL') " +
           "ORDER BY vc.createdAt DESC")
    List<VoiceCommand> findPaymentCommandsByUserId(@Param("userId") UUID userId, Pageable pageable);

    // ========== Session-based Queries ==========

    /**
     * Count commands in session
     */
    long countBySessionId(String sessionId);

    /**
     * Find active session for user
     */
    @Query("SELECT vc.sessionId FROM VoiceCommand vc WHERE vc.userId = :userId " +
           "AND vc.processingStatus IN ('RECEIVED', 'TRANSCRIBING', 'PARSING', 'VALIDATING', 'CONFIRMING', 'PROCESSING') " +
           "GROUP BY vc.sessionId " +
           "ORDER BY MAX(vc.createdAt) DESC")
    List<String> findActiveSessionsByUserId(@Param("userId") UUID userId);

    // ========== Analytics Queries ==========

    /**
     * Count commands by status for user
     */
    @Query("SELECT vc.processingStatus, COUNT(vc) FROM VoiceCommand vc " +
           "WHERE vc.userId = :userId " +
           "GROUP BY vc.processingStatus")
    List<Object[]> countByStatusForUser(@Param("userId") UUID userId);

    /**
     * Calculate average confidence score for user
     */
    @Query("SELECT AVG(vc.confidenceScore) FROM VoiceCommand vc " +
           "WHERE vc.userId = :userId AND vc.confidenceScore IS NOT NULL")
    Double calculateAverageConfidenceScore(@Param("userId") UUID userId);

    /**
     * Find low confidence commands (quality monitoring)
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.confidenceScore < :threshold " +
           "AND vc.createdAt > :sinceDate " +
           "ORDER BY vc.confidenceScore ASC")
    List<VoiceCommand> findLowConfidenceCommands(
            @Param("threshold") Double threshold,
            @Param("sinceDate") LocalDateTime sinceDate,
            Pageable pageable);

    // ========== Failed Commands Queries ==========

    /**
     * Find failed commands for retry
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.processingStatus = 'FAILED' " +
           "AND vc.retryCount < 3 " +
           "AND vc.createdAt > :cutoffDate " +
           "ORDER BY vc.createdAt ASC")
    List<VoiceCommand> findFailedCommandsForRetry(
            @Param("cutoffDate") LocalDateTime cutoffDate,
            Pageable pageable);

    /**
     * Find user's failed commands
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.userId = :userId " +
           "AND vc.processingStatus = 'FAILED' " +
           "ORDER BY vc.createdAt DESC")
    List<VoiceCommand> findFailedCommandsByUserId(@Param("userId") UUID userId, Pageable pageable);

    // ========== Payment-specific Queries ==========

    /**
     * Find command by payment ID
     */
    Optional<VoiceCommand> findByPaymentId(UUID paymentId);

    /**
     * Find pending payment commands for user
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.userId = :userId " +
           "AND vc.commandType IN ('SEND_PAYMENT', 'PAY_BILL', 'TRANSFER_FUNDS') " +
           "AND vc.processingStatus IN ('CONFIRMING', 'PROCESSING') " +
           "AND vc.amount IS NOT NULL " +
           "ORDER BY vc.createdAt DESC")
    List<VoiceCommand> findPendingPaymentCommands(@Param("userId") UUID userId);

    // ========== Bulk Operations ==========

    /**
     * Update expired commands to EXPIRED status
     */
    @Modifying
    @Query("UPDATE VoiceCommand vc SET vc.processingStatus = 'EXPIRED' " +
           "WHERE vc.confirmationRequired = true " +
           "AND vc.isConfirmed = false " +
           "AND vc.processingStatus = 'CONFIRMING' " +
           "AND vc.createdAt < :expiryThreshold")
    int markExpiredCommands(@Param("expiryThreshold") LocalDateTime expiryThreshold);

    /**
     * Delete old commands (data retention policy)
     */
    @Modifying
    @Query("DELETE FROM VoiceCommand vc WHERE vc.createdAt < :cutoffDate " +
           "AND vc.processingStatus IN ('COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED')")
    int deleteOldCommands(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========== Language-based Queries ==========

    /**
     * Find commands by language
     */
    List<VoiceCommand> findByLanguage(String language, Pageable pageable);

    /**
     * Count commands by language (analytics)
     */
    @Query("SELECT vc.language, COUNT(vc) FROM VoiceCommand vc " +
           "WHERE vc.createdAt > :sinceDate " +
           "GROUP BY vc.language " +
           "ORDER BY COUNT(vc) DESC")
    List<Object[]> countByLanguageSince(@Param("sinceDate") LocalDateTime sinceDate);

    // ========== Security & Biometric Queries ==========

    /**
     * Find commands with failed biometric verification
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.voiceSignatureMatch = false " +
           "AND vc.securityScore IS NOT NULL " +
           "AND vc.createdAt > :sinceDate " +
           "ORDER BY vc.createdAt DESC")
    List<VoiceCommand> findFailedBiometricVerifications(
            @Param("sinceDate") LocalDateTime sinceDate,
            Pageable pageable);

    // ========== Existence Checks ==========

    /**
     * Check if user has any pending commands
     */
    boolean existsByUserIdAndProcessingStatusIn(UUID userId, List<ProcessingStatus> statuses);

    /**
     * Check if command exists in session
     */
    boolean existsBySessionIdAndCommandId(String sessionId, String commandId);

    // ========== Custom Complex Queries ==========

    /**
     * Find commands for session with detailed info (avoid N+1)
     */
    @Query("SELECT vc FROM VoiceCommand vc " +
           "WHERE vc.sessionId = :sessionId " +
           "ORDER BY vc.createdAt ASC")
    List<VoiceCommand> findSessionCommandsWithDetails(@Param("sessionId") String sessionId);

    /**
     * Get command statistics for user
     */
    @Query("SELECT " +
           "COUNT(vc), " +
           "COUNT(CASE WHEN vc.processingStatus = 'COMPLETED' THEN 1 END), " +
           "COUNT(CASE WHEN vc.processingStatus = 'FAILED' THEN 1 END), " +
           "AVG(vc.confidenceScore) " +
           "FROM VoiceCommand vc WHERE vc.userId = :userId")
    Object[] getUserCommandStatistics(@Param("userId") UUID userId);

    /**
     * Find duplicate commands (same user, similar text, within time window)
     * Used for fraud detection
     */
    @Query("SELECT vc FROM VoiceCommand vc WHERE vc.userId = :userId " +
           "AND vc.transcribedText = :transcribedText " +
           "AND vc.createdAt BETWEEN :startTime AND :endTime " +
           "AND vc.id <> :excludeId")
    List<VoiceCommand> findPotentialDuplicates(
            @Param("userId") UUID userId,
            @Param("transcribedText") String transcribedText,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeId") UUID excludeId);
}
