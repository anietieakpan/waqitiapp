package com.waqiti.audit.repository;

import com.waqiti.audit.domain.AlertStatus;
import com.waqiti.audit.domain.CriticalAuditAlert;
import com.waqiti.audit.domain.EscalationLevel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Critical Audit Alert entities
 */
@Repository
public interface CriticalAuditAlertRepository extends MongoRepository<CriticalAuditAlert, String> {

    Optional<CriticalAuditAlert> findByCriticalAlertId(UUID criticalAlertId);

    Optional<CriticalAuditAlert> findByOriginalAlertId(String originalAlertId);

    List<CriticalAuditAlert> findByUserId(String userId);

    List<CriticalAuditAlert> findByStatus(AlertStatus status);

    List<CriticalAuditAlert> findByEscalationLevel(EscalationLevel escalationLevel);

    List<CriticalAuditAlert> findByIncidentCategory(String incidentCategory);

    List<CriticalAuditAlert> findByService(String service);

    List<CriticalAuditAlert> findByEscalatedAtBetween(LocalDateTime start, LocalDateTime end);

    boolean existsByOriginalAlertId(String originalAlertId);

    long countByEscalationLevel(EscalationLevel escalationLevel);

    long countByStatus(AlertStatus status);
}
