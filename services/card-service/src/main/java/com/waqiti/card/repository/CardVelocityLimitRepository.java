package com.waqiti.card.repository;

import com.waqiti.card.entity.CardVelocityLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CardVelocityLimitRepository - Spring Data JPA repository for CardVelocityLimit entity
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardVelocityLimitRepository extends JpaRepository<CardVelocityLimit, UUID>, JpaSpecificationExecutor<CardVelocityLimit> {

    Optional<CardVelocityLimit> findByLimitId(String limitId);

    List<CardVelocityLimit> findByCardId(UUID cardId);

    List<CardVelocityLimit> findByUserId(UUID userId);

    List<CardVelocityLimit> findByLimitType(String limitType);

    @Query("SELECT l FROM CardVelocityLimit l WHERE l.isActive = true AND " +
           "(l.effectiveFrom IS NULL OR l.effectiveFrom <= :currentDateTime) AND " +
           "(l.effectiveUntil IS NULL OR l.effectiveUntil >= :currentDateTime) AND " +
           "l.deletedAt IS NULL ORDER BY l.priority DESC")
    List<CardVelocityLimit> findEffectiveLimits(@Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT l FROM CardVelocityLimit l WHERE " +
           "(l.cardId = :cardId OR l.appliesToAllCards = true) AND " +
           "l.isActive = true AND " +
           "(l.effectiveFrom IS NULL OR l.effectiveFrom <= :currentDateTime) AND " +
           "(l.effectiveUntil IS NULL OR l.effectiveUntil >= :currentDateTime) AND " +
           "l.deletedAt IS NULL ORDER BY l.priority DESC")
    List<CardVelocityLimit> findEffectiveLimitsForCard(@Param("cardId") UUID cardId, @Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT l FROM CardVelocityLimit l WHERE l.productId = :productId AND l.isActive = true AND l.deletedAt IS NULL")
    List<CardVelocityLimit> findLimitsByProductId(@Param("productId") String productId);

    @Query("SELECT l FROM CardVelocityLimit l WHERE l.totalBreaches > :threshold AND l.deletedAt IS NULL ORDER BY l.totalBreaches DESC")
    List<CardVelocityLimit> findFrequentlyBreachedLimits(@Param("threshold") Long threshold);

    long countByCardId(UUID cardId);

    long countByIsActive(Boolean isActive);
}
