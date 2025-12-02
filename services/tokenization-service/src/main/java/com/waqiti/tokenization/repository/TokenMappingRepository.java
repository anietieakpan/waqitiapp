package com.waqiti.tokenization.repository;

import com.waqiti.tokenization.domain.TokenMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Token Mapping Repository
 *
 * @author Waqiti Security Team
 */
@Repository
public interface TokenMappingRepository extends JpaRepository<TokenMapping, UUID> {

    /**
     * Find token mapping by token and user ID (for authorization)
     */
    Optional<TokenMapping> findByTokenAndUserId(String token, UUID userId);

    /**
     * Find active tokens for user
     */
    @Query("SELECT tm FROM TokenMapping tm WHERE tm.userId = :userId AND tm.active = true")
    List<TokenMapping> findActiveTokensByUserId(@Param("userId") UUID userId);

    /**
     * Find expired tokens for cleanup
     */
    @Query("SELECT tm FROM TokenMapping tm WHERE tm.active = true AND tm.expiresAt < :now")
    List<TokenMapping> findExpiredTokens(@Param("now") Instant now);

    /**
     * Find tokens by card BIN (for fraud analysis)
     */
    @Query("SELECT tm FROM TokenMapping tm WHERE tm.cardBin = :cardBin AND tm.active = true")
    List<TokenMapping> findByCardBin(@Param("cardBin") String cardBin);

    /**
     * Count active tokens for user
     */
    @Query("SELECT COUNT(tm) FROM TokenMapping tm WHERE tm.userId = :userId AND tm.active = true")
    long countActiveTokensByUserId(@Param("userId") UUID userId);
}
