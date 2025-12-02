package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.alert.FraudAlertService;
import com.waqiti.frauddetection.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Fraud Alerts
 * 
 * @author Waqiti Security Team
 */
@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    Optional<FraudAlert> findByAlertId(String alertId);

    List<FraudAlert> findByTransactionId(String transactionId);

    List<FraudAlert> findByUserId(String userId);

    List<FraudAlert> findByStatus(FraudAlertService.AlertStatus status);

    List<FraudAlert> findBySeverity(FraudAlertService.AlertSeverity severity);

    List<FraudAlert> findByStatusAndSeverity(
        FraudAlertService.AlertStatus status, 
        FraudAlertService.AlertSeverity severity
    );

    long countByStatus(FraudAlertService.AlertStatus status);

    long countByCreatedAtAfter(LocalDateTime after);

    long countBySeverityAndCreatedAtAfter(
        FraudAlertService.AlertSeverity severity, 
        LocalDateTime after
    );

    long countByConfirmedFraudTrueAndCreatedAtAfter(LocalDateTime after);

    @Query("SELECT a FROM FraudAlert a WHERE a.status = :status " +
           "AND a.createdAt >= :startDate ORDER BY a.severity DESC, a.createdAt DESC")
    List<FraudAlert> findRecentAlertsByStatus(
        @Param("status") FraudAlertService.AlertStatus status,
        @Param("startDate") LocalDateTime startDate
    );

    @Query("SELECT a FROM FraudAlert a WHERE a.userId = :userId " +
           "AND a.createdAt >= :startDate ORDER BY a.createdAt DESC")
    List<FraudAlert> findRecentAlertsByUser(
        @Param("userId") String userId,
        @Param("startDate") LocalDateTime startDate
    );
}