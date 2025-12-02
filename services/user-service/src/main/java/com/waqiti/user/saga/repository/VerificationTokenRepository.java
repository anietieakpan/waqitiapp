package com.waqiti.user.saga.repository;

import com.waqiti.user.saga.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Verification Token Repository
 */
@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /**
     * Find token by token string
     */
    Optional<VerificationToken> findByToken(String token);

    /**
     * Find valid token for user
     */
    @Query("SELECT vt FROM VerificationToken vt WHERE vt.userId = :userId " +
           "AND vt.type = :type AND vt.isUsed = false AND vt.expiresAt > :now")
    Optional<VerificationToken> findValidTokenForUser(
        @Param("userId") UUID userId,
        @Param("type") VerificationToken.TokenType type,
        @Param("now") LocalDateTime now
    );

    /**
     * Delete expired tokens (cleanup job)
     */
    @Modifying
    @Query("DELETE FROM VerificationToken vt WHERE vt.expiresAt < :cutoffDate")
    int deleteExpiredTokens(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Delete all tokens for user (account deletion)
     */
    @Modifying
    @Query("DELETE FROM VerificationToken vt WHERE vt.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
