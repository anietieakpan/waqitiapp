package com.waqiti.audit.repository;

import com.waqiti.audit.domain.AuditAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for audit alerts
 */
@Repository
public interface AuditAlertRepository extends JpaRepository<AuditAlert, String> {
    
    List<AuditAlert> findByAlertType(String alertType);
    
    List<AuditAlert> findByStatus(String status);
    
    List<AuditAlert> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    List<AuditAlert> findBySeverity(String severity);
}