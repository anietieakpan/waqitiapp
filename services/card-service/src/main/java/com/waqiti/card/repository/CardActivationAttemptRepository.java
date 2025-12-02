package com.waqiti.card.repository;

import com.waqiti.card.entity.CardActivationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CardActivationAttemptRepository - Data access for card activation attempts
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Repository
public interface CardActivationAttemptRepository extends JpaRepository<CardActivationAttempt, UUID> {

    /**
     * Count failed activation attempts for a card since a timestamp
     *
     * @param cardId Card ID
     * @param since Since timestamp
     * @return Count of failed attempts
     */
    @Query("SELECT COUNT(a) FROM CardActivationAttempt a WHERE a.cardId = :cardId " +
           "AND a.success = false AND a.attemptTimestamp >= :since")
    long countFailedAttemptsSince(@Param("cardId") UUID cardId, @Param("since") LocalDateTime since);

    /**
     * Find all activation attempts for a card
     *
     * @param cardId Card ID
     * @return List of activation attempts
     */
    List<CardActivationAttempt> findByCardIdOrderByAttemptTimestampDesc(UUID cardId);

    /**
     * Find recent failed attempts for a card
     *
     * @param cardId Card ID
     * @param since Since timestamp
     * @return List of failed attempts
     */
    @Query("SELECT a FROM CardActivationAttempt a WHERE a.cardId = :cardId " +
           "AND a.success = false AND a.attemptTimestamp >= :since " +
           "ORDER BY a.attemptTimestamp DESC")
    List<CardActivationAttempt> findFailedAttemptsSince(
            @Param("cardId") UUID cardId,
            @Param("since") LocalDateTime since
    );

    /**
     * Delete old activation attempts (data retention)
     *
     * @param before Delete attempts before this timestamp
     */
    void deleteByAttemptTimestampBefore(LocalDateTime before);
}
