package com.waqiti.tokenization.repository;

import com.waqiti.tokenization.domain.Token;
import com.waqiti.tokenization.domain.TokenStatus;
import com.waqiti.tokenization.domain.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Token Repository
 *
 * Data access layer for tokenized data
 *
 * @author Waqiti Platform Engineering
 */
@Repository
public interface TokenRepository extends JpaRepository<Token, String> {

    /**
     * Find token by token value
     */
    Optional<Token> findByToken(String token);

    /**
     * Find token by token value and user ID (for ownership validation)
     */
    Optional<Token> findByTokenAndUserId(String token, String userId);

    /**
     * Find all active tokens for a user
     */
    List<Token> findByUserIdAndStatus(String userId, TokenStatus status);

    /**
     * Find tokens by type for a user
     */
    List<Token> findByUserIdAndType(String userId, TokenType type);

    /**
     * Find expired tokens that need cleanup
     */
    @Query("SELECT t FROM Token t WHERE t.expiresAt < :now AND t.status = 'ACTIVE'")
    List<Token> findExpiredTokens(@Param("now") Instant now);

    /**
     * Update token status
     */
    @Modifying
    @Query("UPDATE Token t SET t.status = :status, t.updatedAt = :updatedAt WHERE t.token = :token")
    int updateTokenStatus(
        @Param("token") String token,
        @Param("status") TokenStatus status,
        @Param("updatedAt") Instant updatedAt
    );

    /**
     * Increment usage count
     */
    @Modifying
    @Query("UPDATE Token t SET t.usageCount = t.usageCount + 1, t.lastUsedAt = :lastUsedAt WHERE t.token = :token")
    int incrementUsageCount(
        @Param("token") String token,
        @Param("lastUsedAt") Instant lastUsedAt
    );

    /**
     * Count active tokens by user
     */
    long countByUserIdAndStatus(String userId, TokenStatus status);

    /**
     * Count tokens by type
     */
    long countByType(TokenType type);

    /**
     * Check if token exists
     */
    boolean existsByToken(String token);

    /**
     * Delete expired and revoked tokens older than retention period
     */
    @Modifying
    @Query("DELETE FROM Token t WHERE (t.status = 'EXPIRED' OR t.status = 'REVOKED') AND t.updatedAt < :cutoffDate")
    int deleteOldTokens(@Param("cutoffDate") Instant cutoffDate);
}
