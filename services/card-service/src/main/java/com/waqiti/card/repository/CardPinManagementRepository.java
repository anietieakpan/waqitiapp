package com.waqiti.card.repository;

import com.waqiti.card.entity.CardPinManagement;
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
 * CardPinManagementRepository - Spring Data JPA repository for CardPinManagement entity
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardPinManagementRepository extends JpaRepository<CardPinManagement, UUID>, JpaSpecificationExecutor<CardPinManagement> {

    Optional<CardPinManagement> findByPinEventId(String pinEventId);

    List<CardPinManagement> findByCardId(UUID cardId);

    List<CardPinManagement> findByUserId(UUID userId);

    List<CardPinManagement> findByEventType(String eventType);

    @Query("SELECT p FROM CardPinManagement p WHERE p.cardId = :cardId ORDER BY p.eventDate DESC")
    List<CardPinManagement> findByCardIdOrderByEventDateDesc(@Param("cardId") UUID cardId);

    @Query("SELECT p FROM CardPinManagement p WHERE p.cardId = :cardId AND p.eventType = 'PIN_CHANGE' ORDER BY p.eventDate DESC")
    List<CardPinManagement> findPinChangeHistoryByCardId(@Param("cardId") UUID cardId);

    @Query("SELECT p FROM CardPinManagement p WHERE p.cardId = :cardId AND p.verificationSuccessful = false AND p.eventDate > :sinceDate")
    List<CardPinManagement> findFailedVerificationsByCardIdSince(@Param("cardId") UUID cardId, @Param("sinceDate") LocalDateTime sinceDate);

    @Query("SELECT p FROM CardPinManagement p WHERE p.isLockoutEvent = true AND p.unlockDate IS NULL AND p.lockedUntil > :currentDateTime AND p.deletedAt IS NULL")
    List<CardPinManagement> findActiveLockouts(@Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT p FROM CardPinManagement p WHERE p.isSuspicious = true AND p.deletedAt IS NULL ORDER BY p.eventDate DESC")
    List<CardPinManagement> findSuspiciousEvents();

    @Query("SELECT p FROM CardPinManagement p WHERE p.isResetEvent = true AND p.resetCompleted = false AND p.deletedAt IS NULL")
    List<CardPinManagement> findPendingResets();

    long countByCardIdAndEventType(UUID cardId, String eventType);
}
