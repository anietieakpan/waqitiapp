package com.waqiti.card.repository;

import com.waqiti.card.entity.CardStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CardStatementRepository - Spring Data JPA repository for CardStatement entity
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardStatementRepository extends JpaRepository<CardStatement, UUID>, JpaSpecificationExecutor<CardStatement> {

    Optional<CardStatement> findByStatementId(String statementId);

    Optional<CardStatement> findByStatementNumber(String statementNumber);

    List<CardStatement> findByCardId(UUID cardId);

    Page<CardStatement> findByCardId(UUID cardId, Pageable pageable);

    List<CardStatement> findByUserId(UUID userId);

    Page<CardStatement> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT s FROM CardStatement s WHERE s.cardId = :cardId ORDER BY s.statementDate DESC")
    List<CardStatement> findByCardIdOrderByStatementDateDesc(@Param("cardId") UUID cardId);

    @Query("SELECT s FROM CardStatement s WHERE s.cardId = :cardId AND s.isCurrentStatement = true AND s.deletedAt IS NULL")
    Optional<CardStatement> findCurrentStatementByCardId(@Param("cardId") UUID cardId);

    @Query("SELECT s FROM CardStatement s WHERE s.cardId = :cardId AND s.statementYear = :year AND s.statementMonth = :month AND s.deletedAt IS NULL")
    Optional<CardStatement> findByCardIdAndYearMonth(@Param("cardId") UUID cardId, @Param("year") Integer year, @Param("month") Integer month);

    @Query("SELECT s FROM CardStatement s WHERE s.cardId = :cardId AND s.periodStartDate <= :date AND s.periodEndDate >= :date AND s.deletedAt IS NULL")
    Optional<CardStatement> findByCardIdAndDate(@Param("cardId") UUID cardId, @Param("date") LocalDate date);

    List<CardStatement> findByStatementStatus(String statementStatus);

    @Query("SELECT s FROM CardStatement s WHERE s.isPaid = false AND s.paymentDueDate < :currentDate AND s.deletedAt IS NULL")
    List<CardStatement> findOverdueStatements(@Param("currentDate") LocalDate currentDate);

    @Query("SELECT s FROM CardStatement s WHERE s.isPaid = false AND s.paymentDueDate BETWEEN :currentDate AND :futureDate AND s.deletedAt IS NULL")
    List<CardStatement> findStatementsDueSoon(@Param("currentDate") LocalDate currentDate, @Param("futureDate") LocalDate futureDate);

    @Query("SELECT s FROM CardStatement s WHERE s.isFinalized = false AND s.deletedAt IS NULL")
    List<CardStatement> findUnfinalizedStatements();

    @Query("SELECT s FROM CardStatement s WHERE s.isEmailed = false AND s.isFinalized = true AND s.deletedAt IS NULL")
    List<CardStatement> findStatementsNotEmailed();

    long countByCardId(UUID cardId);

    long countByStatementStatus(String statementStatus);

    @Query("SELECT COUNT(s) FROM CardStatement s WHERE s.cardId = :cardId AND s.isPaid = false AND s.deletedAt IS NULL")
    long countUnpaidStatementsByCardId(@Param("cardId") UUID cardId);
}
