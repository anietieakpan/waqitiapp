package com.waqiti.card.repository;

import com.waqiti.card.entity.CardLimit;
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
 * CardLimitRepository - Spring Data JPA repository for CardLimit entity
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardLimitRepository extends JpaRepository<CardLimit, UUID>, JpaSpecificationExecutor<CardLimit> {

    Optional<CardLimit> findByLimitId(String limitId);

    List<CardLimit> findByCardId(UUID cardId);

    List<CardLimit> findByUserId(UUID userId);

    List<CardLimit> findByLimitType(String limitType);

    @Query("SELECT l FROM CardLimit l WHERE l.cardId = :cardId AND l.isActive = true AND " +
           "(l.effectiveFrom IS NULL OR l.effectiveFrom <= :currentDateTime) AND " +
           "(l.effectiveUntil IS NULL OR l.effectiveUntil >= :currentDateTime) AND " +
           "l.deletedAt IS NULL ORDER BY l.priority DESC")
    List<CardLimit> findEffectiveLimitsByCardId(@Param("cardId") UUID cardId, @Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT l FROM CardLimit l WHERE l.cardId = :cardId AND l.limitType = :limitType AND l.isActive = true AND l.deletedAt IS NULL")
    List<CardLimit> findByCardIdAndLimitType(@Param("cardId") UUID cardId, @Param("limitType") String limitType);

    @Query("SELECT l FROM CardLimit l WHERE l.isTemporary = true AND l.temporaryLimitEnd < :currentDateTime AND l.deletedAt IS NULL")
    List<CardLimit> findExpiredTemporaryLimits(@Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT l FROM CardLimit l WHERE l.cardId = :cardId AND l.isTemporary = true AND " +
           "l.temporaryLimitStart <= :currentDateTime AND l.temporaryLimitEnd >= :currentDateTime AND l.deletedAt IS NULL")
    List<CardLimit> findActiveTemporaryLimitsByCardId(@Param("cardId") UUID cardId, @Param("currentDateTime") LocalDateTime currentDateTime);

    long countByCardId(UUID cardId);

    long countByIsActive(Boolean isActive);
}
