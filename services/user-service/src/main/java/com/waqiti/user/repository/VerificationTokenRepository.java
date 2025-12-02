package com.waqiti.user.repository;

import com.waqiti.user.domain.VerificationToken;
import com.waqiti.user.domain.VerificationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    /**
     * Find a token by its value
     */
    Optional<VerificationToken> findByToken(String token);

    /**
     * SECURITY FIX: Find and lock token atomically to prevent TOCTOU race condition
     * This method acquires a pessimistic write lock on the token row, ensuring that
     * the validation and marking as used happen atomically without any race condition window.
     *
     * Critical for password reset, email verification, and other security-sensitive operations.
     *
     * @param token The token string to find and lock
     * @return Optional containing the locked token if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM VerificationToken t WHERE t.token = :token")
    Optional<VerificationToken> findByTokenWithLock(@Param("token") String token);
    
    /**
     * Find the most recent token for a user and verification type
     */
    Optional<VerificationToken> findTopByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, VerificationType type);
    
    /**
     * Find all tokens for a user
     */
    List<VerificationToken> findByUserId(UUID userId);
    
    /**
     * Find all tokens for a user and verification type
     */
    List<VerificationToken> findByUserIdAndType(UUID userId, VerificationType type);
    
    /**
     * Find expired tokens that have not been used
     */
    List<VerificationToken> findByUsedFalseAndExpiryDateBefore(LocalDateTime date);
}