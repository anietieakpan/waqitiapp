package com.waqiti.user.repository;

import com.waqiti.user.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    
    Optional<EmailVerificationToken> findByTokenAndVerifiedFalse(String token);
    
    List<EmailVerificationToken> findByUserIdAndVerifiedFalse(UUID userId);
    
    List<EmailVerificationToken> findByUserIdAndVerifiedFalseOrderByCreatedAtDesc(UUID userId);
    
    @Query("SELECT t FROM EmailVerificationToken t WHERE t.userId = :userId " +
           "AND t.verified = false AND t.expiresAt > :now ORDER BY t.createdAt DESC")
    List<EmailVerificationToken> findActiveTokensByUserId(
            @Param("userId") UUID userId, 
            @Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(t) FROM EmailVerificationToken t WHERE t.userId = :userId " +
           "AND t.createdAt > :since")
    long countTokensCreatedSince(
            @Param("userId") UUID userId, 
            @Param("since") LocalDateTime since);
    
    @Query("SELECT t FROM EmailVerificationToken t WHERE t.expiresAt < :now " +
           "AND t.verified = false AND t.expiredAt IS NULL")
    List<EmailVerificationToken> findExpiredUnverifiedTokens(@Param("now") LocalDateTime now);
    
    void deleteByUserIdAndVerifiedTrue(UUID userId);
    
    @Query("DELETE FROM EmailVerificationToken t WHERE t.createdAt < :before")
    void deleteOldTokens(@Param("before") LocalDateTime before);
}