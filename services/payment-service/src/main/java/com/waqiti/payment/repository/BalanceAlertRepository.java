package com.waqiti.payment.repository;

import com.waqiti.payment.domain.BalanceAlert;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Balance Alerts
 */
@Repository
public interface BalanceAlertRepository extends MongoRepository<BalanceAlert, String> {

    List<BalanceAlert> findByAccountId(String accountId);

    List<BalanceAlert> findByAccountIdAndAlertType(String accountId, String alertType);

    List<BalanceAlert> findByAccountIdAndResolvedFalse(String accountId);

    List<BalanceAlert> findBySeverity(String severity);

    List<BalanceAlert> findByAlertTimeBetween(LocalDateTime start, LocalDateTime end);
}
