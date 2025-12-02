package com.waqiti.payment.repository;

import com.waqiti.payment.domain.TokenizedCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRITICAL: Repository for PCI DSS Compliant Tokenized Card Operations
 * 
 * This repository provides secure data access for tokenized card information.
 * All queries are designed to maintain PCI DSS compliance by:
 * - Never exposing vault paths in results
 * - Enforcing user-based data isolation
 * - Supporting secure audit operations
 * - Enabling token lifecycle management
 * 
 * SECURITY FEATURES:
 * - User-based access control
 * - Secure token lookups
 * - Audit trail support
 * - Bulk cleanup operations
 * - Performance-optimized queries
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Repository
public interface TokenizedCardRepository extends JpaRepository<TokenizedCard, UUID> {
    
    /**
     * Find tokenized card by token and user ID
     * CRITICAL: Always enforce user-based access control
     */
    Optional<TokenizedCard> findByTokenAndUserId(String token, UUID userId);
    
    /**
     * Find all active tokenized cards for a user
     * Returns cards safe for API responses (vault paths should be nulled)
     */
    List<TokenizedCard> findByUserIdAndIsActiveTrue(UUID userId);
    
    /**
     * Find all tokenized cards for a user (including inactive)
     * For administrative and audit purposes
     */
    List<TokenizedCard> findByUserId(UUID userId);
    
    /**
     * Find tokenized cards by user and last 4 digits
     * Used for duplicate detection and customer service
     */
    List<TokenizedCard> findByUserIdAndLast4DigitsAndIsActiveTrue(UUID userId, String last4Digits);
    
    /**
     * Find tokenized card by token (admin use only)
     * WARNING: This bypasses user isolation - use with extreme caution
     */
    Optional<TokenizedCard> findByToken(String token);
    
    /**
     * Find tokenized cards by card type for a user
     */
    List<TokenizedCard> findByUserIdAndCardTypeAndIsActiveTrue(UUID userId, String cardType);
    
    /**
     * Find expiring tokens for renewal notification
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.expiresAt <= :expirationDate AND tc.isActive = true")
    List<TokenizedCard> findExpiringTokens(@Param("expirationDate") LocalDateTime expirationDate);
    
    /**
     * Find tokens expiring within specified days
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.expiresAt BETWEEN :now AND :futureDate AND tc.isActive = true")
    List<TokenizedCard> findTokensExpiringBetween(@Param("now") LocalDateTime now, @Param("futureDate") LocalDateTime futureDate);
    
    /**
     * Find unused tokens (for cleanup)
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.usageCount = 0 AND tc.createdAt <= :cutoffDate")
    List<TokenizedCard> findUnusedTokensOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Find high-usage tokens (for monitoring)
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.usageCount >= :usageThreshold AND tc.isActive = true")
    List<TokenizedCard> findHighUsageTokens(@Param("usageThreshold") Integer usageThreshold);
    
    /**
     * Find tokens by security level
     */
    List<TokenizedCard> findBySecurityLevelAndIsActiveTrue(String securityLevel);
    
    /**
     * Find tokens created within date range (for audit)
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.createdAt BETWEEN :startDate AND :endDate")
    List<TokenizedCard> findTokensCreatedBetween(@Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find tokens by user and creation date range
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.userId = :userId AND tc.createdAt BETWEEN :startDate AND :endDate")
    List<TokenizedCard> findByUserIdAndCreatedAtBetween(@Param("userId") UUID userId,
                                                       @Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find revoked tokens for audit
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.isActive = false AND tc.revokedAt IS NOT NULL")
    List<TokenizedCard> findRevokedTokens();
    
    /**
     * Find revoked tokens by reason
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.isActive = false AND tc.revocationReason = :reason")
    List<TokenizedCard> findByRevocationReason(@Param("reason") String reason);
    
    /**
     * Find tokens needing risk reassessment
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.riskAssessmentDate IS NULL OR tc.riskAssessmentDate <= :cutoffDate")
    List<TokenizedCard> findTokensNeedingRiskAssessment(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Find high-risk tokens
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.riskScore >= :riskThreshold AND tc.isActive = true")
    List<TokenizedCard> findHighRiskTokens(@Param("riskThreshold") Integer riskThreshold);
    
    /**
     * Find network tokens by provider
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.networkProvider = :provider AND tc.networkTokenStatus = 'ACTIVE'")
    List<TokenizedCard> findActiveNetworkTokensByProvider(@Param("provider") String provider);
    
    /**
     * Count active tokens for user
     */
    @Query("SELECT COUNT(tc) FROM TokenizedCard tc WHERE tc.userId = :userId AND tc.isActive = true")
    Long countActiveTokensByUser(@Param("userId") UUID userId);
    
    /**
     * Count total usage across all user tokens
     */
    @Query("SELECT COALESCE(SUM(tc.usageCount), 0) FROM TokenizedCard tc WHERE tc.userId = :userId")
    Long sumUsageByUser(@Param("userId") UUID userId);
    
    /**
     * Find most recently used token for user
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.userId = :userId AND tc.isActive = true AND tc.lastUsedAt IS NOT NULL ORDER BY tc.lastUsedAt DESC LIMIT 1")
    Optional<TokenizedCard> findMostRecentlyUsedToken(@Param("userId") UUID userId);
    
    /**
     * Find tokens not used recently (for cleanup)
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.lastUsedAt <= :cutoffDate OR tc.lastUsedAt IS NULL")
    List<TokenizedCard> findTokensNotUsedSince(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Batch update operations for maintenance
     */
    
    /**
     * Mark expired tokens as inactive
     */
    @Modifying
    @Transactional
    @Query("UPDATE TokenizedCard tc SET tc.isActive = false WHERE tc.expiresAt <= :now AND tc.isActive = true")
    int markExpiredTokensInactive(@Param("now") LocalDateTime now);
    
    /**
     * Update usage count for token
     */
    @Modifying
    @Transactional
    @Query("UPDATE TokenizedCard tc SET tc.usageCount = tc.usageCount + 1, tc.lastUsedAt = :now WHERE tc.id = :tokenId")
    int incrementUsageCount(@Param("tokenId") UUID tokenId, @Param("now") LocalDateTime now);
    
    /**
     * Update risk scores in batch
     */
    @Modifying
    @Transactional
    @Query("UPDATE TokenizedCard tc SET tc.riskScore = :riskScore, tc.riskAssessmentDate = :assessmentDate WHERE tc.id IN :tokenIds")
    int updateRiskScores(@Param("tokenIds") List<UUID> tokenIds, 
                        @Param("riskScore") Integer riskScore,
                        @Param("assessmentDate") LocalDateTime assessmentDate);
    
    /**
     * Soft delete tokens (mark as inactive)
     */
    @Modifying
    @Transactional
    @Query("UPDATE TokenizedCard tc SET tc.isActive = false, tc.revokedAt = :now, tc.revocationReason = :reason WHERE tc.id IN :tokenIds")
    int softDeleteTokens(@Param("tokenIds") List<UUID> tokenIds, 
                        @Param("now") LocalDateTime now,
                        @Param("reason") String reason);
    
    /**
     * Hard delete old inactive tokens (for compliance)
     * WARNING: This permanently removes data
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TokenizedCard tc WHERE tc.isActive = false AND tc.revokedAt <= :cutoffDate")
    int purgeOldInactiveTokens(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Statistics and reporting queries
     */
    
    /**
     * Get token statistics by card type
     */
    @Query("SELECT tc.cardType, COUNT(tc), AVG(tc.usageCount) FROM TokenizedCard tc WHERE tc.isActive = true GROUP BY tc.cardType")
    List<Object[]> getTokenStatsByCardType();
    
    /**
     * Get token statistics by user
     */
    @Query("SELECT tc.userId, COUNT(tc), SUM(tc.usageCount), MAX(tc.lastUsedAt) FROM TokenizedCard tc WHERE tc.isActive = true GROUP BY tc.userId")
    List<Object[]> getTokenStatsByUser();
    
    /**
     * Get daily tokenization volume
     */
    @Query("SELECT DATE(tc.createdAt), COUNT(tc) FROM TokenizedCard tc WHERE tc.createdAt >= :startDate GROUP BY DATE(tc.createdAt) ORDER BY DATE(tc.createdAt)")
    List<Object[]> getDailyTokenizationVolume(@Param("startDate") LocalDateTime startDate);
    
    /**
     * Get token distribution by environment
     */
    @Query("SELECT tc.environment, COUNT(tc) FROM TokenizedCard tc GROUP BY tc.environment")
    List<Object[]> getTokenDistributionByEnvironment();
    
    /**
     * Get security level distribution
     */
    @Query("SELECT tc.securityLevel, COUNT(tc) FROM TokenizedCard tc WHERE tc.isActive = true GROUP BY tc.securityLevel")
    List<Object[]> getSecurityLevelDistribution();
    
    /**
     * Find tokens requiring compliance review
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.pciCompliant = false OR tc.tokenizationVersion != :currentVersion")
    List<TokenizedCard> findTokensRequiringComplianceReview(@Param("currentVersion") String currentVersion);
    
    /**
     * Advanced security queries
     */
    
    /**
     * Find potentially suspicious token patterns
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.usageCount > :unusualUsageThreshold AND tc.createdAt >= :recentDate")
    List<TokenizedCard> findSuspiciousTokenActivity(@Param("unusualUsageThreshold") Integer threshold,
                                                   @Param("recentDate") LocalDateTime recentDate);
    
    /**
     * Find tokens with mismatched geographical usage
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.tokenizedLocation != tc.deviceType AND tc.isActive = true")
    List<TokenizedCard> findTokensWithGeographicalMismatch();
    
    /**
     * Find duplicate device fingerprints across users
     */
    @Query("SELECT tc.deviceFingerprint, COUNT(DISTINCT tc.userId) FROM TokenizedCard tc WHERE tc.deviceFingerprint IS NOT NULL GROUP BY tc.deviceFingerprint HAVING COUNT(DISTINCT tc.userId) > 1")
    List<Object[]> findDuplicateDeviceFingerprints();
    
    /**
     * Compliance and audit support
     */
    
    /**
     * Check if user has reached token limit
     */
    @Query("SELECT COUNT(tc) >= :maxTokensPerUser FROM TokenizedCard tc WHERE tc.userId = :userId AND tc.isActive = true")
    Boolean hasUserReachedTokenLimit(@Param("userId") UUID userId, @Param("maxTokensPerUser") Integer maxTokensPerUser);
    
    /**
     * Get audit trail for token
     */
    @Query("SELECT tc.correlationId, tc.createdAt, tc.lastUsedAt, tc.usageCount, tc.revokedAt, tc.revocationReason FROM TokenizedCard tc WHERE tc.token = :token")
    List<Object[]> getTokenAuditTrail(@Param("token") String token);
    
    /**
     * Find tokens created from specific IP ranges (for fraud investigation)
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.tokenizedIpAddress LIKE :ipPattern")
    List<TokenizedCard> findTokensByIpPattern(@Param("ipPattern") String ipPattern);
    
    /**
     * Performance monitoring
     */
    
    /**
     * Find most active tokens in time period
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.lastUsedAt >= :startDate ORDER BY tc.usageCount DESC LIMIT :limit")
    List<TokenizedCard> findMostActiveTokensSince(@Param("startDate") LocalDateTime startDate, @Param("limit") Integer limit);
    
    /**
     * Find unused tokens by user
     */
    @Query("SELECT tc FROM TokenizedCard tc WHERE tc.userId = :userId AND tc.usageCount = 0 AND tc.isActive = true")
    List<TokenizedCard> findUnusedTokensByUser(@Param("userId") UUID userId);
}